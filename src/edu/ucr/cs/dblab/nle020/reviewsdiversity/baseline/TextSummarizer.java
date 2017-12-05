package edu.ucr.cs.dblab.nle020.reviewsdiversity.baseline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.BaselineComparison;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.TopPairsProgram;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSentence;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;
import edu.ucr.cs.dblab.nle020.utils.Utils;
import org.apache.commons.cli.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
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
  private static void exportDataset(String sentencePath) {
    Map<Integer, List<SentimentSet>> docToSentences = TopPairsProgram
        .importDocToSentimentSentences(TopPairsProgram.DOC_TO_REVIEWS_PATH, false);

    Map<Integer, Map<String, String>> docToTrimSentences = new HashMap<>();
    for (Integer doc : docToSentences.keySet()) {
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
   * @param inputDir    directory storing summaries composed by Python summarizers
   * @param summarizers summarizer names, embedded in summary file names
   * @param outputDir   directory to export inferred summaries
   */
  private static void digestPythonSummaries(String inputDir,
                                            Collection<String> summarizers,
                                            String outputDir) {
    for (String summarizer : summarizers) {
      final Map<Integer, Map<String, List<String>>> kToDocSummaries = importSummaries(inputDir,
          summarizer);
      final Map<Integer, Map<Integer, List<SentimentSet>>> kToDocSentences = inferSummaries(
          kToDocSummaries);
      exportSummaries(kToDocSentences, outputDir, summarizer);
    }
  }

  /**
   * Import summaries composed by Python summarizers in the form of sentence id list.
   *
   * @param dir        directory storing summaries composed by Python summarizers
   * @param summarizer summarizer name, embedded in summary file names
   * @return summaries in form of sentence id list
   */
  private static Map<Integer, Map<String, List<String>>> importSummaries(String dir, String
      summarizer) {
    ObjectMapper mapper = new ObjectMapper();
    Map<Integer, Map<String, List<String>>> kToDocSummaries = new HashMap<>();
    for (int k : K_LIST) {
      String summaryPath = dir + summarizer + "_" + String.valueOf(k) + ".json";
      try (BufferedReader reader = Files.newBufferedReader(Paths.get(summaryPath))) {
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
   * @param kToDocSummaries summaries in form of sentence id list
   * @return summaries in form of SentimentSentence list
   */
  private static Map<Integer, Map<Integer, List<SentimentSet>>> inferSummaries(
      Map<Integer, Map<String, List<String>>> kToDocSummaries) {
    Map<Integer, List<SentimentSet>> docToAllSentences = TopPairsProgram
        .importDocToSentimentSentences(TopPairsProgram.DOC_TO_REVIEWS_PATH, false);

    Map<Integer, Map<Integer, List<SentimentSet>>> kToDocSentences = new HashMap<>();
    for (Integer k : kToDocSummaries.keySet()) {
      Map<String, List<String>> docToSummary = kToDocSummaries.get(k);
      Map<Integer, List<SentimentSet>> docToSentences = new HashMap<>();
      for (String doc : docToSummary.keySet()) {
        Integer docId = Integer.valueOf(doc);
        Set<String> sentences = new HashSet<>(docToSummary.get(doc));
        List<SentimentSet> allSentences = docToAllSentences.get(docId);
        docToSentences.put(docId, new ArrayList<>());
        for (SentimentSet s : allSentences) {
          if (sentences.contains(s.getId())) {
            docToSentences.get(docId).add(s);
          }
        }
      }
      kToDocSentences.put(k, docToSentences);
    }
    return kToDocSentences;
  }

  private static void exportSummaries(
      Map<Integer, Map<Integer, List<SentimentSet>>> kToDocSentences,
      String dir, String summarizer) {
    for (Integer k : kToDocSentences.keySet()) {
      Map<Integer, List<SentimentSet>> docToTopKSentences = kToDocSentences.get(k);
      String outputPath = dir + "top_sentence_" + summarizer + "_k" + k.toString() + ".txt";
      TopPairsProgram.outputTopKToJson(outputPath, docToTopKSentences);
    }
  }

  public static void main(String[] args) {
    CommandLineParser parser = new DefaultParser();
    Options options = new Options();
    options.addOption("h", "help", false, "print this message");
    options.addOption("p", "preprocess-dataset-for-python", false,
        "Preprocess raw dataset for Python text summarizer only.");
    try {
      CommandLine line = parser.parse(options, args);

      if (line.hasOption("h")) {
        HelpFormatter formatter = new HelpFormatter();
        String header = "This utility can perform 2 tasks: 1-prepare the dataset for further " +
            "text summarization done in Python (e.g., via Gensim, Sumy in textsum.py)" +
            ", 2-Convert the summarises composed by Python summarizers into the main " +
            "project Java format, i.e. in the form of ConceptSentimentPair, " +
            "SentimentSentence objects.";
        String footer = "Task 2 is the main usage; task 1 is enabled by flag \"-p\".";
        formatter.printHelp("TextSummarizer", header, options, footer, true);
      } else if (line.hasOption("p")) {
        String senPath = "src/edu/ucr/cs/dblab/nle020/reviewsdiversity/baseline/sentence_data.json";
        exportDataset(senPath);
      } else {
        long startTime = System.currentTimeMillis();
        String inputDir = "src/edu/ucr/cs/dblab/nle020/reviewsdiversity/baseline/summary/";
        List<String> summarizers = Arrays.asList("textrank", "lexrank", "lsa");
        String exportDir = BaselineComparison.BASELINE_SUMMARY_DIR;
        digestPythonSummaries(inputDir, summarizers, exportDir);

        Utils.printRunningTime(startTime, "Finished digesting Python summaries",
            true);
      }
    } catch (ParseException e) {
      System.out.println("Unexpected exception:" + e.getMessage());
    }
  }

}