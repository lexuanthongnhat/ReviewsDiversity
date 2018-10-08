package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.baseline.FreqBasedTopSets;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSentence;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;
import edu.ucr.cs.dblab.nle020.utils.Utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

/**
 * @author nhat
 * Compare different Top-k sentence summarization methods on various automatic metrics
 * (contrast to survey based metrics)
 */
public class BaselineComparison {
  private static final String SUMMARY_DIR = TopPairsProgram.OUTPUT_FOLDER;
  public static final String BASELINE_SUMMARY_DIR = SUMMARY_DIR + "baseline/summary/";
  private static final int ROUNDING_DIGIT = 2;
//  public static final int[] K_LIST = {3, 5, 10, 15, 20};
  public static final int[] K_LIST = {3};
  private static Map<String, MethodType> methodToType = new HashMap<>();
  private static List<String> methods = new ArrayList<>();

  // Some randomRelatedMethods should be run (re-sample) several time to get stable performances
  private static List<String> randomRelatedMethods = new ArrayList<>();
  private static final int RE_SAMPLE_RANDOM_METHOD = 1;

  static {
    methodToType.put("greedy", MethodType.OUR);
    methodToType.put("ilp", MethodType.OUR);
    methodToType.put("rr", MethodType.OUR);
    methodToType.put("most_popular", MethodType.BASELINE);
    methodToType.put("proportional", MethodType.BASELINE);
    methodToType.put("textrank", MethodType.BASELINE);
    methodToType.put("lexrank", MethodType.BASELINE);
    methodToType.put("lsa", MethodType.BASELINE);

    methods.addAll(Arrays.asList("greedy",
                                 "most_popular", "proportional",
                                 "textrank", "lexrank", "lsa"));
    randomRelatedMethods.addAll(Arrays.asList("most_popular", "proportional"));
  }

  private enum MethodType {OUR, BASELINE}

  /**
   * Metric:
   * COVERAGE - based on concept-sentiment distance without ancestor-successor relation
   * DIST_ERROR - sentiment difference with regard to concept distribution
   */
  private enum MeasureType {
    COVERAGE, DIST_ERROR
  }

  public BaselineComparison() {
  }

  /**
   * Evaluate summaries' quality under multiple measures
   *
   * @param k                    number of sentences selected to a summary
   * @param appliedSentThreshold sentiment threshold used in summarizing process
   * @param measures             list of metrics
   * @param sample               percentage of dataset to evaluate
   * @return map from measure to performance result that is a table of method-performance
   */
  private static Map<Measure, Map<String, Double>> evaluate(int k, double appliedSentThreshold,
                                                            List<Measure> measures,
                                                            double sample) {
    Map<String, Map<Integer, List<SentimentSentence>>> methodToSummaries = importMethodSummaries(
        k, appliedSentThreshold);
    final List<Integer> sampleDocs = sampleDoctors(methodToSummaries, sample);
    System.out.println("Number of doctors in the sample: " + sampleDocs.size());

    Map<Measure, Map<String, Double>> measureToResults = new HashMap<>();
    for (Measure measure : measures) {
      Map<String, Double> results = new HashMap<>();
      for (String method : methods) {
        double error = randomRelatedMethods.contains(method) && RE_SAMPLE_RANDOM_METHOD > 1 ?
            evaluateWithReSample(method, k, sampleDocs, measure, ROUNDING_DIGIT) :
            measure.evaluate(methodToSummaries.get(method), sampleDocs, ROUNDING_DIGIT);
        results.put(method, error);
      }

      measureToResults.put(measure, results);
    }
    return measureToResults;
  }

  /**
   * Several summarizers involve randomization choice at some extents. These summarizers should be
   * re-sampled a number of time to get stable results.
   */
  private static Double evaluateWithReSample(String method, int k,
                                             List<Integer> docIds, Measure measure,
                                             int roundingDigit){
    List<Double> errors = new ArrayList<>();
    for (int i = 0; i < RE_SAMPLE_RANDOM_METHOD; ++i) {
      Map<Integer, List<SentimentSet>> docToSummaries = FreqBasedTopSets.summarizeDoctorReviews(
          TopPairsProgram.DOC_TO_REVIEWS_PATH, TopPairsProgram.SetOption.SENTENCE, k,method);
      Map<Integer, List<SentimentSentence>> docToSentimentSets = new HashMap<>();
      for (Integer doc : docToSummaries.keySet()) {
        List<SentimentSet> sets = docToSummaries.get(doc);
        docToSentimentSets.put(
            doc,
            sets.stream().map(s -> (SentimentSentence) s).collect(Collectors.toList()));
      }
      errors.add(measure.evaluate(docToSentimentSets, docIds, roundingDigit + 1));
    }
    double averageError = errors.stream().collect(Collectors.averagingDouble(e -> e));
    return Utils.rounding(averageError, roundingDigit);
  }

  /**
   * Sample dataset to get only a percentage
   *
   * @param methodToSummaries summaries produced by various summarization methods
   * @param sample            percentage of dataset to return
   * @return sample of doctor list
   */
  private static List<Integer> sampleDoctors(
      Map<String, Map<Integer, List<SentimentSentence>>> methodToSummaries, double sample) {
    List<Integer> minimalDocs = new ArrayList<>();
    for (Map<Integer, List<SentimentSentence>> docToSentences : methodToSummaries.values()) {
      Set<Integer> currentMethodDocs = docToSentences.keySet();
      if (minimalDocs.isEmpty())
        minimalDocs.addAll(currentMethodDocs);
      minimalDocs.removeIf(doc -> !currentMethodDocs.contains(doc));
    }

    if (sample == 1.0)
      return minimalDocs;
    else {
      Random rd = new Random();
      return minimalDocs.stream().filter(d -> rd.nextDouble() < sample)
          .collect(Collectors.toList());
    }
  }

  /**
   * Import all summaries created by different methods
   *
   * @param k         number of sentences selected to a summary
   * @param threshold sentiment distance threshold used in summarization process
   * @return map of method to summaries
   */
  private static Map<String, Map<Integer, List<SentimentSentence>>> importMethodSummaries(
      int k, double threshold) {
    final Map<String, Map<Integer, List<SentimentSentence>>> mthToSummaries = new HashMap<>();
    for (String method : methods) {
      String summaryPath;
      if (methodToType.get(method) == MethodType.OUR)
        summaryPath = SUMMARY_DIR + "top_sentence/k" + k + "_threshold" +
            Double.toString(threshold) + "/top_SENTENCE_result_1000_" + method + "_set.txt";
      else
        summaryPath = BASELINE_SUMMARY_DIR + "top_sentence_" + method + "_k" + k + ".txt";
      final Map<Integer, List<SentimentSentence>> docToTopSentences =
          importSummaryFromJson(summaryPath);
      mthToSummaries.put(method, docToTopSentences);
    }

    return mthToSummaries;
  }

  private static void exportResultInCsv(String outputPath,
                                        Map<Integer, Map<String, Double>> kToResults) {
    StringBuilder outputString = new StringBuilder();
    outputString.append(resultLineCsv("k", methods));

    List<Integer> ks = new ArrayList<>(kToResults.keySet());
    Collections.sort(ks);
    for (Integer k : ks) {
      List<Double> orderedResults = methods.stream()
          .map(method -> kToResults.get(k).get(method))
          .collect(Collectors.toList());
      outputString.append(resultLineCsv(k, orderedResults));
    }

    try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath),
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
      writer.write(outputString.toString());
      System.out.println("Exported evaluation result to \"" + outputPath + "\"");
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static <K, T> String resultLineCsv(K k, List<T> columns) {
    StringBuilder builder = new StringBuilder(k.toString());
    builder.append(",");
    for (T column : columns) {
      builder.append(String.valueOf(column));
      builder.append(",");
    }
    if (builder.length() > 0 && builder.charAt(builder.length() - 1) == ',')
      builder.deleteCharAt(builder.length() - 1);
    builder.append("\n");
    return builder.toString();
  }

  public static void main(String[] args) {
    long startTime = System.currentTimeMillis();
    // Threshold parameters of COVERAGE measures
    int[] conceptDiffThresholds = {2, 3, 4};
    double[] sentimentDiffThresholds = {0.3};
    final List<Measure> measures = Measure.initMeasures(conceptDiffThresholds,
        sentimentDiffThresholds,
        TopPairsProgram.DOC_TO_REVIEWS_PATH);

    final double appliedSentThreshold = 0.5;  // Sentiment threshold used when generating summaries
    // Evaluate with various measures
    Map<Measure, Map<Integer, Map<String, Double>>> measureToKResults = new HashMap<>();
    for (int k : K_LIST) {
      final Map<Measure, Map<String, Double>> measureToResults = evaluate(k, appliedSentThreshold,
          measures, 1.0);
      for (Measure measure : measureToResults.keySet()) {
        if (!measureToKResults.containsKey(measure))
          measureToKResults.put(measure, new HashMap<>());
        measureToKResults.get(measure).put(k, measureToResults.get(measure));
      }
    }

    // Save results to files, one measure per file
    for (Measure measure : measures) {
      String outputPath = SUMMARY_DIR + "baseline/" + measure.pathForm() + ".csv";
      exportResultInCsv(outputPath, measureToKResults.get(measure));
    }
    Utils.printRunningTime(startTime, "Finished evaluation", true);
  }

  private static Map<Integer, List<ConceptSentimentPair>> importRawDataset(
      String docToReviewsPath) {
    Map<Integer, List<ConceptSentimentPair>> docToConceptSentimentPairDataset = new HashMap<>();
    Map<Integer, List<SentimentSet>> docToSentences =
        TopPairsProgram.importDocToSentimentSentences(docToReviewsPath, false);

    for (Integer docId : docToSentences.keySet()) {
      docToConceptSentimentPairDataset.put(docId, new ArrayList<>());
      docToSentences.get(docId).forEach(
          sentence -> docToConceptSentimentPairDataset.get(docId).addAll(sentence.getPairs()));
    }
    return docToConceptSentimentPairDataset;
  }

  private static Map<Integer, List<SentimentSentence>> importSummaryFromJson(String inputPath) {
    Map<Integer, List<SentimentSentence>> result = new HashMap<>();
    ObjectMapper mapper = new ObjectMapper();
    try (BufferedReader reader = Files.newBufferedReader(Paths.get(inputPath))) {
      result = mapper.readValue(reader,
          new TypeReference<Map<Integer, List<SentimentSentence>>>() {
          });
    } catch (IOException e) {
      e.printStackTrace();
    }
    return result;
  }

  /**
   * Measure represents how to evaluate summary's quality
   */
  static class Measure {
    private static final int DISABLE_CONCEPT_DISTANCE = 1000;
    private MeasureType type;
    private int conceptDiffThreshold;
    private double sentimentDiffThreshold;
    private Function<Double, Double> penalizeFunc;
    private String penalizeName;
    Map<Integer, List<ConceptSentimentPair>> docToRawData;

    private Measure(MeasureType type, int conceptDiffThreshold, double sentimentDiffThreshold,
                   Map<Integer, List<ConceptSentimentPair>> docToRawData) {
      this.type = type;
      this.conceptDiffThreshold = conceptDiffThreshold;
      this.sentimentDiffThreshold = sentimentDiffThreshold;
      this.docToRawData = docToRawData;
      this.penalizeFunc = null;
    }

    /**
     * Init Sentiment Distribution Error measure
     * @param penalizeFunc calculate the penalty when the concepts and its ancestors are missing in
     *                     the summary.
     */
    private static Measure initDistErrorMeasure(MeasureType type, Map<Integer,
                                                List<ConceptSentimentPair>> docToRawData,
                                                Function<Double, Double> penalizeFunc,
                                                String penalizeName) {
      Measure measure = new Measure(type, -1, -1.0, docToRawData);
      measure.penalizeFunc = penalizeFunc;
      measure.penalizeName = penalizeName;
      return measure;
    }

    private static List<Measure> initMeasures(int[] conceptDiffThresholds,
                                              double[] sentimentDiffThresholds,
                                              String rawDataPath) {
      Map<Integer, List<ConceptSentimentPair>> docToRawData = importRawDataset(rawDataPath);
      List<Measure> measures = new ArrayList<>();
      measures.add(Measure.initDistErrorMeasure(MeasureType.DIST_ERROR, docToRawData,
                                                Math::abs, "no_penalize"));
      measures.add(Measure.initDistErrorMeasure(MeasureType.DIST_ERROR, docToRawData,
                                                s -> Math.max(Math.abs(1 - s), Math.abs(-1 - s)),
                                                "penalize"));
      /*for (int conceptDiffThreshold : conceptDiffThresholds) {
        for (double sentimentDiffThreshold : sentimentDiffThresholds) {
          measures.add(new Measure(MeasureType.COVERAGE, conceptDiffThreshold,
              sentimentDiffThreshold, docToRawData));
        }
      }*/
      return measures;
    }

    public Double evaluate(Map<Integer, List<SentimentSentence>> docToSummary,
                           List<Integer> docIds,
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
    private Double evalDistError(Map<Integer, List<SentimentSentence>> docToTopSentences,
                                 List<Integer> docIds,
                                 int roundingDigit) {
      double error = 0.0;
      for (Integer docId : docIds) {
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
    private Double evalCoverage(Map<Integer, List<SentimentSentence>> docToTopSentences,
                                List<Integer> docIds,
                                int roundingDigit) {
      List<Double> coverages = new ArrayList<>();
      for (Integer docId : docIds) {
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

    private String pathForm() {
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

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Measure measure = (Measure) o;
      return conceptDiffThreshold == measure.conceptDiffThreshold &&
          Double.compare(measure.sentimentDiffThreshold, sentimentDiffThreshold) == 0 &&
          penalizeName.equalsIgnoreCase(((Measure) o).penalizeName) &&
          type == measure.type;
    }

    @Override
    public int hashCode() {
      int result;
      long temp;
      result = type.hashCode();
      result = 31 * result + conceptDiffThreshold;
      temp = Double.doubleToLongBits(sentimentDiffThreshold);
      result = 31 * result + (int) (temp ^ (temp >>> 32));
      result = 31 * result + penalizeFunc.hashCode();
      result = 31 * result + penalizeName.hashCode();
      return result;
    }
  }

  /**
   * Genealogy book stores ancestor-successor relationships of medical concepts
   * in a set of concept-sentiment pairs
   */
  private static class Genealogy {
    private Map<String, Set<String>> ancestorToSuccessors = new HashMap<>();
    private Map<String, Set<String>> successorToAncestors = new HashMap<>();
    private Set<String> concepts = new HashSet<>();

    private Genealogy(Collection<ConceptSentimentPair> pairs) {
      Map<String, Set<String>> cuiToDeweys = new HashMap<>();
      for (ConceptSentimentPair pair : pairs) {
        String cui = pair.getCui();
        concepts.add(cui);
        if (!cuiToDeweys.containsKey(cui))
          cuiToDeweys.put(cui, pair.getDeweys());
        if (!ancestorToSuccessors.containsKey(cui))
          ancestorToSuccessors.put(cui, new HashSet<>());
        if (!successorToAncestors.containsKey(cui))
          successorToAncestors.put(cui, new HashSet<>());
      }

      for (String ancestor : concepts) {
        for (String successor : concepts) {
          if (ancestor.equalsIgnoreCase(successor))
            continue;

          boolean done = false;
          for (String ancestorDewey : cuiToDeweys.get(ancestor)) {
            for (String successorDewey : cuiToDeweys.get(successor)) {
              if (is_successor(ancestorDewey, successorDewey)) {
                ancestorToSuccessors.get(ancestor).add(successor);
                successorToAncestors.get(successor).add(ancestor);
                done = true;
                break;
              }
            }
            if (done)
              break;
          }
        }
      }
    }

    /**
     * Check if childDewey is actually a successor of ancestorDewey
     * Note: if two Dewey are the same, return false (a dewey is not its own child)
     */
    private static boolean is_successor(String ancestorDewey, String childDewey) {
      if (ancestorDewey.startsWith(childDewey)) {
        return ancestorDewey.length() > childDewey.length() &&
                ancestorDewey.charAt(childDewey.length()) == '.';
      }
      return false;
    }

    private Set<String> getSuccessors(String cui) {
      return ancestorToSuccessors.get(cui);
    }

    public Set<String> getConcepts() {
      return this.concepts;
    }
  }
}