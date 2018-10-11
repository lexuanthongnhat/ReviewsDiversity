package edu.ucr.cs.dblab.nle020.reviewsdiversity.baseline;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.BaselineComparison;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.TopPairsProgram;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.TopPairsProgram.SetOption;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;
import edu.ucr.cs.dblab.nle020.utils.Utils;
import org.apache.commons.cli.*;

import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This class provides frequency-based summarizers that select top k sentences/reviews.
 * <p>
 * The general use case is selecting k SentimentSets from a provided SentimentSet list.
 * The summarizers mainly cluster concepts embedded in SentimentSet list into multiple polarized
 * concept groups. For example, concept "headache" is divided to "headache_pos" and "headache_neg".
 * The sentiment and frequency/count of the polarized concepts are utilized for SentimentSet
 * selection.
 */
public class FreqBasedTopSets {
  private static final int[] K_LIST = BaselineComparison.K_LIST;
  private static Map<String, Selector> methods = new HashMap<>();

  static {
    // This method picks top k most popular polarized concepts, then randomly pick one SentimentSet
    // per polarized concept candidate.
    methods.put("most_popular",
        new Selector(MostPopularPicker::new, FreqBasedTopSets::randomPick));

    // This method picks polarized concepts proportionally to their frequency/count, then pick one
    // SentimentSet with the most extreme sentiment per polarized concept candidate.
    methods.put("proportional",
        new Selector(ProportionalToCountPicker::new, FreqBasedTopSets::maxPick));
  }

  public static void main(String[] args) {
    CommandLineParser parser = new DefaultParser();
    Options options = new Options();
    options.addOption("h", "help", false, "print this message");
    options.addOption(Option.builder().longOpt("input-file").hasArg().argName("FILE")
        .desc("Input file path.").build());
    options.addOption(Option.builder().longOpt("output-dir").hasArg().argName("DIR")
        .desc("Output directory.").build());
    try {
      CommandLine commandLine = parser.parse(options, args);

      if (commandLine.hasOption("h")) {
        HelpFormatter formatter = new HelpFormatter();
        String header = "This class offers frequency-based summarizers that select top " +
            "k sentences/reviews.\n\nThe general use case is selecting k SentimentSets from a " +
            "provided SentimentSet list. The summarizers mainly cluster concepts embedded in " +
            "SentimentSet list into multiple polarized concept groups. For example, concept " +
            "\"headache\" is divided to \"headache_pos\" and \"headache_neg\". The sentiment and " +
            "frequency/count of the polarized concepts are utilized for SentimentSet selection.";
        formatter.printHelp("FreqBasedTopSets", header, options, "", true);
      } else {
        String inputFile = commandLine.getOptionValue("input-file", TopPairsProgram.DOC_TO_REVIEWS_PATH);
        String outputDir = commandLine.getOptionValue("output-dir", BaselineComparison.BASELINE_SUMMARY_DIR);
        experimentSummarization(inputFile, outputDir);
      }
    } catch (ParseException e) {
      System.out.println("Unexpected exception:" + e.getMessage());
    }
  }

  private static void experimentSummarization(String inputFile, String outputDir) {
    long startTime = System.currentTimeMillis();
    SetOption setOption = SetOption.SENTENCE;
    for (int k : K_LIST) {
      for (String method : methods.keySet()) {
        Map<String, List<SentimentSet>> docToTopKSentences = summarizeDoctorReviews(
            inputFile, setOption, k, method);

        String outputPath = Paths.get(outputDir,
            "top_" + setOption.toString().toLowerCase() + "_" + method + "_k" + k + ".txt")
            .toString();
        TopPairsProgram.outputTopKToJson(outputPath, docToTopKSentences);
        System.out.println("Exported to \"" + outputPath + "\"");
      }
    }
    Utils.printRunningTime(startTime, "Finished topK baselines");
  }

  public static Map<String, List<SentimentSet>> summarizeDoctorReviews(
      String inputPath, TopPairsProgram.SetOption setOption, int k, String method) {
    Map<String, List<SentimentSet>> docToSentimentSets = importSentimentSets(inputPath, setOption);
    Map<String, List<SentimentSet>> docToTopKSets = docToSentimentSets.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey,
            e -> selectTopKSentimentSets(e.getValue(), k, methods.get(method))));
    return docToTopKSets;
  }

  /**
   * Main method controlling the process of selecting top k representative SentimentSets
   */
  private static List<SentimentSet> selectTopKSentimentSets(List<SentimentSet> sentimentSets,
                                                            int k,
                                                            Selector selector) {
    if (sentimentSets.size() <= k)
      return new ArrayList<>(sentimentSets);

    List<PolarizedConcept> polarizedConcepts = PolarizedConcept.polarizeConcept(sentimentSets);
    NextPolarizedConceptPicker nextConceptPicker = selector.nextConPickerConstructor.apply(
        polarizedConcepts);
    List<SentimentSet> kSets = new ArrayList<>();
    while (kSets.size() < k) {
      PolarizedConcept nextPolarizedConcept = nextConceptPicker.pickNextPolarizedConcept();
      insertRepresentativeSet(nextPolarizedConcept, selector.pickSingleSet, kSets);
    }
    return kSets;
  }

  private abstract static class NextPolarizedConceptPicker {
    private List<PolarizedConcept> polarizedConcepts;

    private NextPolarizedConceptPicker(List<PolarizedConcept> polarizedConcepts) {
      this.polarizedConcepts = polarizedConcepts;
    }

    abstract protected PolarizedConcept pickNextPolarizedConcept();
  }

  /**
   * This class helps selecting most popular polarized concepts based on the number of the concept's
   * containing sentences/reviews.
   */
  private static class MostPopularPicker extends NextPolarizedConceptPicker {
    private PriorityQueue<PolarizedConcept> sortedPolarConcepts;

    private MostPopularPicker(List<PolarizedConcept> polarizedConcepts) {
      super(polarizedConcepts);
      this.sortedPolarConcepts = new PriorityQueue<>(polarizedConcepts);
    }

    @Override
    protected PolarizedConcept pickNextPolarizedConcept() {
      if (sortedPolarConcepts.isEmpty())
        sortedPolarConcepts.addAll(super.polarizedConcepts);

      return sortedPolarConcepts.poll();
    }
  }

  /**
   * This class helps selecting a polarized concepts proportionally to the number of the concept's
   * containing sentences/reviews.
   */
  private static class ProportionalToCountPicker extends NextPolarizedConceptPicker {
    private List<Integer> counts;
    private Random random;

    private ProportionalToCountPicker(List<PolarizedConcept> polarizedConcepts) {
      super(polarizedConcepts);

      counts = polarizedConcepts.stream().map(PolarizedConcept::getContainingSetCount)
          .collect(Collectors.toList());
      random = new Random();
    }

    @Override
    protected PolarizedConcept pickNextPolarizedConcept() {
      return super.polarizedConcepts.get(enumerateDistribution(counts, random));
    }

    /**
     * Pick an index in the weighted random manner, i.e. proportional to the value in counts
     *
     * @param counts weights to sample randomly
     * @param random random generator
     * @return index in the list of counts
     */
    private int enumerateDistribution(List<Integer> counts, Random random) {
      int total = counts.stream().reduce(0, Integer::sum);
      int rCount = random.nextInt(total);
      int index = 0;
      while (rCount >= 0) {
        rCount -= counts.get(index);
        ++index;
      }
      return index - 1;
    }
  }

  /**
   * Find and insert a new sentence/review into current kSets
   * Note: kSets will be modified (inserted new item)
   *
   * @param polarizedConceptCandidate the PolarizedConcept need to represent
   * @param selectSetFunc             function to pick a single SentimentSet from a candidates map
   * @param kSets                     Inserted set for modifying in this method
   */
  private static void insertRepresentativeSet(
      PolarizedConcept polarizedConceptCandidate,
      Function<Map<SentimentSet, List<Double>>, SentimentSet> selectSetFunc,
      List<SentimentSet> kSets) {

    Map<SentimentSet, List<Double>> brandNewSentimentSets = polarizedConceptCandidate
        .getDistinctSetsFrom(kSets);
    if (!brandNewSentimentSets.isEmpty())
      kSets.add(selectSetFunc.apply(brandNewSentimentSets));
  }

  private static SentimentSet randomPick(Map<SentimentSet, List<Double>> brandNewSentimentSets) {
    Random random = new Random();
    List<SentimentSet> sets = new ArrayList<>(brandNewSentimentSets.keySet());
    return sets.get(random.nextInt(sets.size()));
  }

  private static SentimentSet maxPick(Map<SentimentSet, List<Double>> brandNewSentimentSets) {
    double currentMax = -1.0;
    SentimentSet currentSet = null;
    for (SentimentSet set : brandNewSentimentSets.keySet()) {
      for (Double sentiment : brandNewSentimentSets.get(set)) {
        if (Math.abs(sentiment) > currentMax) {
          currentMax = Math.abs(sentiment);
          currentSet = set;
        }
      }
    }
    return currentSet;
  }

  private static Map<String, List<SentimentSet>> importSentimentSets(
      String inputPath, TopPairsProgram.SetOption setOption) {
    Map<String, List<SentimentSet>> docToSentimentSets;
    switch (setOption) {
      case REVIEW:
        docToSentimentSets = TopPairsProgram.importDocToSentimentReviews(
            inputPath, false, -1);
        break;
      case SENTENCE:
        docToSentimentSets = TopPairsProgram.importDocToSentimentSentences(
            inputPath, false, -1);
        break;
      default:
        docToSentimentSets = TopPairsProgram.importDocToSentimentReviews(
            inputPath, false, -1);
        break;
    }
    return docToSentimentSets;
  }

  /**
   * This class specify two methods:
   * 1) select the next polarized concept candidate,
   * 2) select a SentimentSet representing that polarized concept candidate.
   */
  private static class Selector {
    private Function<List<PolarizedConcept>, NextPolarizedConceptPicker> nextConPickerConstructor;
    private Function<Map<SentimentSet, List<Double>>, SentimentSet> pickSingleSet;

    private Selector(
        Function<List<PolarizedConcept>, NextPolarizedConceptPicker> nextConPickerConstructor,
        Function<Map<SentimentSet, List<Double>>, SentimentSet> pickSingleSet) {
      this.nextConPickerConstructor = nextConPickerConstructor;
      this.pickSingleSet = pickSingleSet;
    }
  }

  /**
   * This class represent a concept with polarity (positive or negative).
   * E.g., concept "headache" is divided to 2 polarized concept: "headache_pos" and "headache_neg"
   */
  private static class PolarizedConcept implements Comparable<PolarizedConcept> {
    String concept;
    Map<SentimentSet, List<Double>> containingSetAndSentiment;

    private PolarizedConcept(String concept,
                             Map<SentimentSet, List<Double>> containingSetAndSentiment) {
      super();
      this.concept = concept;
      this.containingSetAndSentiment = containingSetAndSentiment;
    }

    private Set<SentimentSet> getContainingSets() {
      return containingSetAndSentiment.keySet();
    }

    private int getContainingSetCount() {
      return containingSetAndSentiment.keySet().size();
    }

    private Map<SentimentSet, List<Double>> getDistinctSetsFrom(Collection<SentimentSet> other) {
      return containingSetAndSentiment.entrySet().stream().filter(e -> !other.contains(e.getKey()))
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Separate concepts into polarized version: positive vs. negative ones.
     * E.g., concept "headache" is divided into "headache_pos" and "headache_neg".
     * The idea is to keep track the positive, negative set of sentences, reviews of each concept.
     *
     * @param sentimentSets list of sentences, reviews
     * @return list of PolarizedConcept that embedded its containing sentences, reviews
     */
    private static List<PolarizedConcept> polarizeConcept(Collection<SentimentSet> sentimentSets) {
      Map<String, Map<SentimentSet, List<Double>>> polarizedConceptToParent = new HashMap<>();
      for (SentimentSet sentimentSet : sentimentSets) {
        for (ConceptSentimentPair pair : sentimentSet.getPairs()) {
          double sentiment = pair.getSentiment();
          String polarizedId = sentiment > 0 ? pair.getId() + "_pos" : pair.getId() + "_neg";
          if (!polarizedConceptToParent.containsKey(polarizedId))
            polarizedConceptToParent.put(polarizedId, new HashMap<>());

          if (!polarizedConceptToParent.get(polarizedId).containsKey(sentimentSet))
            polarizedConceptToParent.get(polarizedId).put(sentimentSet, new ArrayList<>());
          polarizedConceptToParent.get(polarizedId).get(sentimentSet).add(sentiment);
        }
      }
      return polarizedConceptToParent.entrySet().stream()
          .map(e -> new PolarizedConcept(e.getKey(), e.getValue()))
          .collect(Collectors.toList());
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result
          + ((concept == null) ? 0 : concept.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      PolarizedConcept other = (PolarizedConcept) obj;
      if (concept == null) {
        return other.concept == null;
      } else return concept.equals(other.concept);
    }

    @Override
    public int compareTo(PolarizedConcept o) {
      return o.getContainingSets().size() - this.getContainingSets().size();
    }

  }
}