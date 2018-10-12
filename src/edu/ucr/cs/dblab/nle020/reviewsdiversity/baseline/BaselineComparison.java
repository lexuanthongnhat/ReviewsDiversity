package edu.ucr.cs.dblab.nle020.reviewsdiversity.baseline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSentence;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;
import edu.ucr.cs.dblab.nle020.utils.Utils;
import org.apache.commons.cli.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

import static edu.ucr.cs.dblab.nle020.reviewsdiversity.TopPairsProgram.*;

/**
 * @author nhat
 * Compare different Top-k sentence summarization methods on various automatic metrics
 * (contrast to survey based metrics)
 */
public class BaselineComparison {
  private static final String SUMMARY_DIR = OUTPUT_FOLDER;
  static final String BASELINE_SUMMARY_DIR = SUMMARY_DIR + "baseline/summary/";
  private static final int ROUNDING_DIGIT = 2;
  static final int[] K_LIST = {3, 5, 10, 15, 20};
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

  enum MethodType {OUR, BASELINE}

  public BaselineComparison() {
  }

  public static void main(String[] args) {
    CommandLineParser parser = new DefaultParser();
    Options options = new Options();
    options.addOption("h", "help", false, "print this message");
    options.addOption(Option.builder().longOpt("doc-parsed-file").hasArg().argName("FILE")
        .desc("File contains parsed document with aspects and sentiments extracted.").build());
    options.addOption(Option.builder().longOpt("output").hasArg().argName("FILE")
        .desc("Output file path/directory.").build());
    options.addOption(Option.builder().longOpt("our-summary-dir").hasArg().argName("DIR")
        .desc("Directory contains summaries created by our summarizers").build());
    options.addOption(Option.builder().longOpt("baseline-summary-dir").hasArg().argName("DIR")
        .desc("Directory contains summaries created by baseline summarizers").build());
    options.addOption(Option.builder().longOpt("sample").hasArg().argName("SAMPLE")
        .type(Double.class)
        .desc("Sample ratio of all doc to evaluate, default=1 (all)").build());
    try {
      CommandLine commandLine = parser.parse(options, args);
      String docParsedFile = commandLine.getOptionValue(
          "doc-parsed-file", DOC_TO_REVIEWS_PATH);
      String outputDir = commandLine.getOptionValue("output", SUMMARY_DIR + "baseline/");
      String ourSummaryDir = commandLine.getOptionValue("our-summary-dir",
          "src/edu/ucr/cs/dblab/nle020/reviewsdiversity/baseline/summary/");
      String baselineSummaryDir = commandLine.getOptionValue("baseline-summary-dir",
          "src/edu/ucr/cs/dblab/nle020/reviewsdiversity/baseline/summary/");
      double sample = Double.valueOf(commandLine.getOptionValue("sample", "1"));

      startEvaluation(docParsedFile, sample, ourSummaryDir, baselineSummaryDir, outputDir);
    } catch (ParseException e) {
      System.out.println("Unexpected exception:" + e.getMessage());
    }
  }

  private static void startEvaluation(String docParsedFile, double sample,
                                      String ourSummaryDir,
                                      String baselineSummaryDir,
                                      String outputDir) {
    // Threshold parameters of COVERAGE measures
    int[] conceptDiffThresholds = {2, 3, 4};
    double[] sentimentDiffThresholds = {0.3};
    List<Measure> measures = Measure.initMeasures(
        conceptDiffThresholds, sentimentDiffThresholds, docParsedFile);

    double appliedSentThreshold = 0.5;  // Sentiment threshold used when generating summaries
    // Evaluate with various measures

    Map<Measure, Map<Integer, Map<String, Double>>> measureToKResults = new HashMap<>();
    for (int k : K_LIST) {
      Map<Measure, Map<String, Double>> measureToResults = evaluate(
          k, appliedSentThreshold, measures, sample, ourSummaryDir, baselineSummaryDir);
      for (Measure measure : measureToResults.keySet()) {
        if (!measureToKResults.containsKey(measure))
          measureToKResults.put(measure, new HashMap<>());
        measureToKResults.get(measure).put(k, measureToResults.get(measure));
      }
    }

    // Save results to files, one measure per file
    for (Measure measure : measures) {
      exportResultInCsv(Paths.get(outputDir, measure.pathForm() + ".csv"),
                        measureToKResults.get(measure));
    }
    System.out.println("Exported evaluation result to \"" + outputDir + "\"");
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
  private static Map<Measure, Map<String, Double>> evaluate(
      int k, double appliedSentThreshold, List<Measure> measures, double sample,
      String ourSummaryDir,
      String baselineSummaryDir
  ) {
    Map<String, Map<String, List<SentimentSentence>>> methodToSummaries = importMethodSummaries(
        k, appliedSentThreshold, ourSummaryDir, baselineSummaryDir);
    final List<String> sampleDocs = sampleDoctors(methodToSummaries, sample);
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
                                             List<String> docIds, Measure measure,
                                             int roundingDigit) {
    List<Double> errors = new ArrayList<>();
    for (int i = 0; i < RE_SAMPLE_RANDOM_METHOD; ++i) {
      Map<String, List<SentimentSet>> docToSummaries = FreqBasedTopSets.summarizeDoctorReviews(
          DOC_TO_REVIEWS_PATH, SetOption.SENTENCE, k, method);
      Map<String, List<SentimentSentence>> docToSentimentSets = new HashMap<>();
      for (String doc : docToSummaries.keySet()) {
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
  private static List<String> sampleDoctors(
      Map<String, Map<String, List<SentimentSentence>>> methodToSummaries, double sample) {
    List<String> minimalDocs = new ArrayList<>();
    for (Map<String, List<SentimentSentence>> docToSentences : methodToSummaries.values()) {
      Set<String> currentMethodDocs = docToSentences.keySet();
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
  private static Map<String, Map<String, List<SentimentSentence>>> importMethodSummaries(
      int k, double threshold, String ourSummaryDir, String baselineSummaryDir) {
    final Map<String, Map<String, List<SentimentSentence>>> mthToSummaries = new HashMap<>();
    for (String method : methods) {
      String summaryPath;
      if (methodToType.get(method) == MethodType.OUR) {
        String[] filePaths = setMethodFileName(
            SetOption.SENTENCE, SetAlgorithm.GREEDY_SET, 0, k, threshold);
        summaryPath = Paths.get(ourSummaryDir, filePaths).toString();
      } else
        summaryPath = Paths.get(baselineSummaryDir, TextSummarizer.summaryFileName(method, k))
            .toString();
      final Map<String, List<SentimentSentence>> docToTopSentences =
          importSummaryFromJson(summaryPath);
      mthToSummaries.put(method, docToTopSentences);
    }

    return mthToSummaries;
  }

  private static void exportResultInCsv(Path outputPath,
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

    try (BufferedWriter writer = Files.newBufferedWriter(outputPath,
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
      writer.write(outputString.toString());
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

  private static Map<String, List<SentimentSentence>> importSummaryFromJson(String inputPath) {
    Map<String, List<SentimentSentence>> result = new HashMap<>();
    ObjectMapper mapper = new ObjectMapper();
    try (BufferedReader reader = Files.newBufferedReader(Paths.get(inputPath))) {
      result = mapper.readValue(reader,
          new TypeReference<Map<String, List<SentimentSentence>>>() {
          });
    } catch (IOException e) {
      e.printStackTrace();
    }
    return result;
  }

}