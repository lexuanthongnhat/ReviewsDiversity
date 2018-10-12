package edu.ucr.cs.dblab.nle020.reviewsdiversity.baseline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.TopPairsProgram;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSentence;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;
import org.apache.commons.cli.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Communicate with Text Summarizer that are implemented in Python.
 */
public class TextSummarizer {
  private static final int[] K_LIST = BaselineComparison.K_LIST;

  /**
   * Prepare review dataset in a form that Python summarizers (textsum.py) can easily pick up.
   *
   * @param sentencePath path to export the prepared dataset
   */
  private static void exportDataset(String inputPath, String sentencePath) {
    Map<String, List<SentimentSet>> docToSentences = TopPairsProgram
              .importDocToSentimentSentences(inputPath, false, -1);

    Map<String, Map<String, String>> docToTrimSentences = new HashMap<>();
    for (String doc : docToSentences.keySet()) {
      docToTrimSentences.put(doc, new HashMap<>());
      for (SentimentSet sentence : docToSentences.get(doc)) {
        String str = ((SentimentSentence) sentence).getSentence();
        String clean_str = str.replace(".", " ").trim() + "!";
        docToTrimSentences.get(doc).put(sentence.getId(), clean_str);
      }
    }

    ObjectMapper mapper = new ObjectMapper();
    try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(sentencePath),
        StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
      mapper.writeValue(writer, docToTrimSentences);
      System.out.println("Successfully prepared dataset for Python summarizers at \"" +
          sentencePath + "\".");
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Convert the summarises composed by Python summarizers into the main project Java format,
   * i.e. in the form of ConceptSentimentPair, SentimentSentence objects.
   *
   * @param inputDir        directory storing summaries composed by Python summarizers
   * @param summarizers     summarizer names, embedded in summary file names
   * @param docParsedFile   File contains parsed document with aspects and sentiments extracted.
   * @param outputDir       directory to export inferred summaries
   */
  private static void digestPythonSummaries(String inputDir,
                                            Collection<String> summarizers,
                                            String docParsedFile,
                                            String outputDir) {
    for (String summarizer : summarizers) {
      final Map<Integer, Map<String, List<String>>> kToDocSummaries = importSummaries(inputDir,
          summarizer);
      final Map<Integer, Map<String, List<SentimentSet>>> kToDocSentences = inferSummaries(
          docParsedFile, kToDocSummaries);
      exportSummaries(kToDocSentences, outputDir, summarizer);
    }
  }

  /**
   * Import summaries composed by Python summarizers in the form of sentence id list.
   *
   * @param inputDir   directory storing summaries composed by Python summarizers
   * @param summarizer summarizer name, embedded in summary file names
   * @return summaries in form of sentence id list
   */
  private static Map<Integer, Map<String, List<String>>> importSummaries(
      String inputDir, String summarizer) {
    ObjectMapper mapper = new ObjectMapper();
    Map<Integer, Map<String, List<String>>> kToDocSummaries = new HashMap<>();
    for (int k : K_LIST) {
      Path summaryPath = Paths.get(inputDir, summarizer + "_" + String.valueOf(k) + ".json");
      try (BufferedReader reader = Files.newBufferedReader(summaryPath)) {
        Map<String, List<String>> docToSummary = mapper.readValue(
            reader, new TypeReference<HashMap<String, List<String>>>() {
            });
        docToSummary.entrySet().removeIf(e -> e.getValue().size() < k);
        kToDocSummaries.put(k, docToSummary);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return kToDocSummaries;
  }

  /**
   * Transform summaries in form of sentence id list into the form of SentimentSentence list
   *
   * @param docParsedFile   File contains parsed document with aspects and sentiments extracted.
   * @param kToDocSummaries summaries in form of sentence id list
   * @return summaries in form of SentimentSentence list
   */
  private static Map<Integer, Map<String, List<SentimentSet>>> inferSummaries(
      String docParsedFile, Map<Integer, Map<String, List<String>>> kToDocSummaries) {
    Map<String, List<SentimentSet>> docToAllSentences = TopPairsProgram
        .importDocToSentimentSentences(docParsedFile, false, 0);

    Map<Integer, Map<String, List<SentimentSet>>> kToDocSentences = new HashMap<>();
    for (Integer k : kToDocSummaries.keySet()) {
      Map<String, List<String>> docToSummary = kToDocSummaries.get(k);
      Map<String, List<SentimentSet>> docToSentences = new HashMap<>();
      for (String doc : docToSummary.keySet()) {
        Set<String> sentences = new HashSet<>(docToSummary.get(doc));
        List<SentimentSet> allSentences = docToAllSentences.get(doc);
        docToSentences.put(doc, new ArrayList<>());
        for (SentimentSet s : allSentences) {
          if (sentences.contains(s.getId())) {
            docToSentences.get(doc).add(s);
          }
        }
      }
      kToDocSentences.put(k, docToSentences);
    }
    return kToDocSentences;
  }

  private static void exportSummaries(
      Map<Integer, Map<String, List<SentimentSet>>> kToDocSentences,
      String outputDir, String summarizer) {
    for (Integer k : kToDocSentences.keySet()) {
      Map<String, List<SentimentSet>> docToTopKSentences = kToDocSentences.get(k);
      String outputPath = Paths.get(outputDir, summaryFileName(summarizer, k)).toString();
      TopPairsProgram.outputTopKToJson(outputPath, docToTopKSentences);
    }
  }

  public static String summaryFileName(String summarizer, int topK) {
    return "top_sentence_" + summarizer + "_k" + topK + ".txt";
  }

  public static void main(String[] args) {
    CommandLineParser parser = new DefaultParser();
    Options options = new Options();
    options.addOption("h", "help", false, "print this message");
    options.addOption("p", "pre-process-dataset-for-python", false,
        "Pre-process raw dataset for Python text summarizer only.");
    options.addOption(Option.builder().longOpt("doc-parsed-file").hasArg().argName("FILE")
        .desc("File contains parsed document with aspects and sentiments extracted.").build());
    options.addOption(Option.builder().longOpt("python-summary-dir").hasArg().argName("DIR")
        .desc("Directory contains summaries created by Python summarizers").build());
    options.addOption(Option.builder().longOpt("output").hasArg().argName("FILE")
        .desc("Output file path/directory.").build());
    try {
      CommandLine commandLine = parser.parse(options, args);
      String docParsedFile = commandLine.getOptionValue(
          "doc-parsed-file", TopPairsProgram.DOC_TO_REVIEWS_PATH);

      if (commandLine.hasOption("h")) {
        HelpFormatter formatter = new HelpFormatter();
        String header = "This utility can perform 2 tasks: 1-prepare the dataset for further " +
            "text summarization done in Python (e.g., via Gensim, Sumy in textsum.py)" +
            ", 2-Convert the summarises composed by Python summarizers into the main " +
            "project Java format, i.e. in the form of ConceptSentimentPair, " +
            "SentimentSentence objects.";
        String footer = "Task 2 is the main usage; task 1 is enabled by flag \"-p\".";
        formatter.printHelp("TextSummarizer", header, options, footer, true);
      } else if (commandLine.hasOption("p")) {
        String outputFile = commandLine.getOptionValue("output",
            "src/edu/ucr/cs/dblab/nle020/reviewsdiversity/baseline/sentence_data.json");
        exportDataset(docParsedFile, outputFile);
      } else {
        String pythonSummaryDir = commandLine.getOptionValue("python-summary-dir",
            "src/edu/ucr/cs/dblab/nle020/reviewsdiversity/baseline/summary/");
        String outputDir = commandLine.getOptionValue("output",
            BaselineComparison.BASELINE_SUMMARY_DIR);

        List<String> summarizers = Arrays.asList("textrank", "lexrank", "lsa");
        digestPythonSummaries(pythonSummaryDir, summarizers, docParsedFile, outputDir);
      }
    } catch (ParseException e) {
      System.out.println("Unexpected exception:" + e.getMessage());
    }
  }

}