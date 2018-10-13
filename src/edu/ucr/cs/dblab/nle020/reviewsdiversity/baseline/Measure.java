package edu.ucr.cs.dblab.nle020.reviewsdiversity.baseline;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSentence;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;
import edu.ucr.cs.dblab.nle020.utils.Utils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import static edu.ucr.cs.dblab.nle020.reviewsdiversity.TopPairsProgram.importDocToSentimentSentences;

/**
 * Measure represents how to evaluate summary's quality
 */
public class Measure {
  /**
   * Metric:
   * COVERAGE - based on concept-sentiment distance without ancestor-successor relation
   * DIST_ERROR - sentiment difference with regard to concept distribution
   */
  enum MeasureType {
    COVERAGE, DIST_ERROR
  }

  private static final int DISABLE_CONCEPT_DISTANCE = 1000;
  private MeasureType type;
  private int conceptDiffThreshold;
  private double sentimentDiffThreshold;
  private Function<Double, Double> penalizeFunc;
  private String penalizeName;
  private Map<String, List<ConceptSentimentPair>> docToRawData;

  private Measure(MeasureType type,
                  int conceptDiffThreshold,
                  double sentimentDiffThreshold,
                  Map<String, List<ConceptSentimentPair>> docToRawData) {
    this.type = type;
    this.conceptDiffThreshold = conceptDiffThreshold;
    this.sentimentDiffThreshold = sentimentDiffThreshold;
    this.docToRawData = docToRawData;
    this.penalizeFunc = null;
    this.penalizeName = "";
  }

  /**
   * Init Sentiment Distribution Error measure
   *
   * @param penalizeFunc calculate the penalty when the concepts and its ancestors are missing in
   *                     the summary.
   */
  private static Measure initDistErrorMeasure(MeasureType type,
                                              Map<String, List<ConceptSentimentPair>> docToRawData,
                                              Function<Double, Double> penalizeFunc,
                                              String penalizeName) {
    Measure measure = new Measure(type, -1, -1.0, docToRawData);
    measure.penalizeFunc = penalizeFunc;
    measure.penalizeName = penalizeName;
    return measure;
  }

  static List<Measure> initMeasures(int[] conceptDiffThresholds,
                                    double[] sentimentDiffThresholds,
                                    String rawDataPath) {
    Map<String, List<ConceptSentimentPair>> docToRawData = importRawDataset(rawDataPath);
    List<Measure> measures = new ArrayList<>();
    measures.add(Measure.initDistErrorMeasure(MeasureType.DIST_ERROR, docToRawData,
        Math::abs, "no_penalize"));
    measures.add(Measure.initDistErrorMeasure(MeasureType.DIST_ERROR, docToRawData,
        s -> Math.max(Math.abs(1 - s), Math.abs(-1 - s)),"penalize"));

    for (int conceptDiffThreshold : conceptDiffThresholds) {
      for (double sentimentDiffThreshold : sentimentDiffThresholds) {
        measures.add(new Measure(MeasureType.COVERAGE, conceptDiffThreshold,
            sentimentDiffThreshold, docToRawData));
      }
    }
    return measures;
  }

  public Double evaluate(Map<String, List<SentimentSentence>> docToSummary,
                         List<String> docIds,
                         int roundingDigit) {
    if (type == MeasureType.COVERAGE) {
      return evalCoverage(docToSummary, docIds, roundingDigit);
    } else {
      return evalDistError(docToSummary, docIds, roundingDigit);
    }
  }

  /**
   * Evaluate the error of summary based on the differences of every original concept's sentiment
   * and the summarized ones.
   *
   * @param docToTopSentences map of doctor and corresponding doctor review's summary
   * @return positive, lower is better
   */
  private Double evalDistError(Map<String, List<SentimentSentence>> docToTopSentences,
                               List<String> docIds,
                               int roundingDigit) {
    double error = 0.0;
    for (String docId : docIds) {
      List<ConceptSentimentPair> rawPairs = new ArrayList<>(docToRawData.get(docId));
      final Map<String, List<Double>> rawSentDist = collectSentiments(rawPairs);
      Genealogy genealogy = new Genealogy(rawPairs);

      List<ConceptSentimentPair> sumPairs = new ArrayList<>();
      docToTopSentences.get(docId).forEach(
          sentence -> sumPairs.addAll(sentence.getPairs()));
      final Map<String, List<Double>> sumSentDist = estimateSumSentiments(sumPairs, genealogy);

      List<Double> squareErrors = new ArrayList<>();
      for (String concept : rawSentDist.keySet()) {
        List<Double> sumSents = sumSentDist.get(concept);
        for (Double sent : rawSentDist.get(concept)) {
          if (sumSents == null || sumSents.size() < 1)
            squareErrors.add(Math.pow(this.penalizeFunc.apply(sent), 2));
          else
            sumSents.stream().map(s -> Math.pow(s - sent, 2))
                .min(Double::compare).ifPresent(squareErrors::add);
        }
      }
      error = Math.sqrt(squareErrors.stream().mapToDouble(e -> e).sum() / squareErrors.size());
    }
    return Utils.rounding(error, roundingDigit);
  }

  /**
   * Collect all sentiments of a concept together
   *
   * @param pairs set of concept-sentiment pairs
   * @return map of concept and its sentiments
   */
  private Map<String, List<Double>> collectSentiments(Collection<ConceptSentimentPair> pairs) {
    Map<String, List<Double>> conceptToSentiments = new HashMap<>();
    for (ConceptSentimentPair pair : pairs) {
      String cui = pair.getCui();
      if (!conceptToSentiments.containsKey(cui))
        conceptToSentiments.put(cui, new ArrayList<>());
      List<Double> sentiments = DoubleStream
          .generate(() -> (double) pair.getSentiment())
          .limit(pair.getCount()).boxed().collect(Collectors.toList());
      conceptToSentiments.get(cui).addAll(sentiments);
    }
    return conceptToSentiments;
  }

  /**
   * Estimate the concepts' sentiment embedded in the summary.
   * If a concept doesn't has any sentiments, try to utilize its ancestors' sentiments.
   * I.e., pushing a concept's sentiments down to its successors if needed, but not the other
   * way around (from successor to ancestor).
   *
   * @param pairs     Set of concept-sentiment pairs in the summary
   * @param genealogy Genealogy book of concepts appearing in the raw review set
   * @return Concept and its set of (estimated) sentiments
   */
  private Map<String, List<Double>> estimateSumSentiments(Collection<ConceptSentimentPair> pairs,
                                                          Genealogy genealogy) {
    final Map<String, List<Double>> sentDist = collectSentiments(pairs);

    Map<String, List<Double>> conceptToSentiments = new HashMap<>();
    for (String concept : sentDist.keySet()) {
      conceptToSentiments.put(concept, new ArrayList<>(sentDist.get(concept)));
    }
    final Set<String> originalSumConcepts = new HashSet<>(sentDist.keySet());

    // Propagate sentiment between ancestors and successors
    for (String concept : originalSumConcepts) {
      List<Double> singleConceptSentiments = sentDist.get(concept);
      // Push concept's sentiments down to its successors
      for (String successor : genealogy.getSuccessors(concept)) {
//          if (!successor.equalsIgnoreCase(concept)) {
        // Only push to the concepts not existing in the summary
        if (!originalSumConcepts.contains(successor)) {
          if (!conceptToSentiments.containsKey(successor))
            conceptToSentiments.put(successor, new ArrayList<>());
          conceptToSentiments.get(successor).addAll(singleConceptSentiments);
        }
      }
    }

    return conceptToSentiments;
  }

  /**
   * Evaluate the COVERAGE metric that is based on concept distance threshold and sentiment
   * distance threshold. This metric does not take into account the ancestor-successor relation.
   *
   * @param docToTopSentences map of doctor and corresponding doctor review's summary
   * @return Coverage from 0 to 1 (higher is better)
   */
  private Double evalCoverage(Map<String, List<SentimentSentence>> docToTopSentences,
                              List<String> docIds,
                              int roundingDigit) {
    List<Double> coverages = new ArrayList<>();
    for (String docId : docIds) {
      List<ConceptSentimentPair> pairs = docToRawData.get(docId);
      List<ConceptSentimentPair> uncoveredPairs = new ArrayList<>(pairs);

      Set<ConceptSentimentPair> hostingPairs = new HashSet<>();
      docToTopSentences.get(docId).forEach(
          sentence -> hostingPairs.addAll(sentence.getPairs()));
      for (ConceptSentimentPair hostingPair : hostingPairs) {
        uncoveredPairs.removeIf(pair -> cover(hostingPair, pair,
            conceptDiffThreshold, sentimentDiffThreshold));
      }

      coverages.add(1.0 - (double) uncoveredPairs.size() / pairs.size());
    }
    double result = coverages.stream().collect(Collectors.averagingDouble(coverage -> coverage));
    return Utils.rounding(result, roundingDigit);
  }


  /**
   * The hostingPair can cover a pair if and only if the their concept distance is less than a
   * distance threshold and their sentiment difference is also less than a sentiment threshold
   *
   * @return True if hostingPair can cover pair, False otherwise
   */
  private boolean cover(ConceptSentimentPair hostingPair,
                        ConceptSentimentPair pair,
                        int conceptDistanceThreshold,
                        double sentimentDistanceThreshold) {
    if (Math.abs(hostingPair.getSentiment() - pair.getSentiment()) > sentimentDistanceThreshold)
      return false;
    else if (conceptDistanceThreshold == DISABLE_CONCEPT_DISTANCE)
      // Special case: don't care if two concepts are on different branches.
      return true;
    else return conceptDistance(hostingPair, pair) <= conceptDistanceThreshold;
  }


  /**
   * Distance between two concepts in the concept hierarchy.
   * The sum of two concepts' path to their common ancestor.
   *
   * @return The minimum number of hops between two concepts in the hierarchy
   */
  private int conceptDistance(ConceptSentimentPair hostingPair, ConceptSentimentPair pair) {

    int min = Constants.INVALID_DISTANCE;
    for (String hostingDewey : hostingPair.getDeweys()) {
      for (String clientDewey : pair.getDeweys()) {
        int distance = Constants.INVALID_DISTANCE;

        if (hostingDewey.equals(clientDewey))
          distance = 0;
        else {
          String[] hostings = hostingDewey.split("\\.");
          String[] clients = clientDewey.split("\\.");

          int minLength = hostings.length <= clients.length ? hostings.length : clients.length;
          for (int i = 0; i < minLength; ++i) {
            if (!hostings[i].equalsIgnoreCase(clients[i])) {
              distance = (hostings.length - i) + (clients.length - i);
              break;
            }
          }
        }
        if (distance < min)
          min = distance;
      }
    }
    return min;
  }

  String pathForm() {
    StringBuilder pathStr = new StringBuilder(type.toString().toLowerCase());
    if (conceptDiffThreshold >= 0 && sentimentDiffThreshold >= 0) {
      pathStr.append("_distance");
      pathStr.append(conceptDiffThreshold);
      pathStr.append("_sentiment");
      pathStr.append(Math.round(10 * sentimentDiffThreshold));
    }
    if (this.type == MeasureType.DIST_ERROR) {
      if (this.penalizeName.equalsIgnoreCase("penalize"))
        pathStr.append("_penalize");
    }
    return pathStr.toString();
  }

  private static Map<String, List<ConceptSentimentPair>> importRawDataset(
      String docToReviewsPath) {
    Map<String, List<ConceptSentimentPair>> docToConceptSentimentPairDataset = new HashMap<>();
    Map<String, List<SentimentSet>> docToSentences =
        importDocToSentimentSentences(docToReviewsPath, false, -1);

    for (String docId : docToSentences.keySet()) {
      docToConceptSentimentPairDataset.put(docId, new ArrayList<>());
      docToSentences.get(docId).forEach(
          sentence -> docToConceptSentimentPairDataset.get(docId).addAll(sentence.getPairs()));
    }
    return docToConceptSentimentPairDataset;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Measure measure = (Measure) o;
    return conceptDiffThreshold == measure.conceptDiffThreshold &&
        Double.compare(measure.sentimentDiffThreshold, sentimentDiffThreshold) == 0 &&
        type == measure.type &&
        Objects.equals(penalizeName, measure.penalizeName);
  }

  @Override
  public int hashCode() {

    return Objects.hash(type, conceptDiffThreshold, sentimentDiffThreshold, penalizeName);
  }
}
