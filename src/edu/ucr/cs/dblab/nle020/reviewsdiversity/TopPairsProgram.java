package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import edu.ucr.cs.dblab.nle020.ontology.SnomedGraphBuilder;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants.LPMethod;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants.PartialTimeIndex;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.composite.GreedySetThreadImpl;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.composite.ILPSetThreadImpl;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.composite.RandomizedRoundingSetThreadImpl;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset.DoctorSentimentReview;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset.PairExtractor;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset.RawReview;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset.SentimentCalculator;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentReview;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSentence;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;
import edu.ucr.cs.dblab.nle020.utils.TriFunction;
import edu.ucr.cs.dblab.nle020.utils.Utils;
import org.apache.commons.cli.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class TopPairsProgram {

    private static int k = Constants.K;
    private static float threshold = Constants.THRESHOLD;

    static boolean RANDOMIZE_DOCS = false;
    private static int NUM_TRIALS = 20;
    private static Set<Integer> randomIndices = Utils.randomIndices(1000,
            Constants.NUM_DOCTORS_TO_EXPERIMENT);
    private final static int NUM_DOCTORS_TO_EXPERIMENT = Constants.NUM_DOCTORS_TO_EXPERIMENT;
    private final static String EXPECTED_JOB_DONE = "DONE!";

    private static final int ILP_INDEX = 0;
    private static final int RR_INDEX = 1;
    private static final int GREEDY_INDEX = 2;

    public final static String DOC_TO_REVIEWS_PATH =
            "src/main/resources/doc_pairs_1_prunned_vector.txt";
    public final static String OUTPUT_FOLDER = "src/main/resources/performance/";

    private enum Algorithm {GREEDY, ILP, RANDOMIZED_ROUNDING}
    public enum SetAlgorithm {GREEDY_SET, ILP_SET, RANDOMIZED_ROUNDING_SET}
    public enum SetOption {REVIEW, SENTENCE}
    private enum NumItem {NUM_PAIRS, NUM_PAIRS_EDGES}

    private static final Map<SetOption,
        TriFunction<String, Boolean, Integer, Map<String, List<SentimentSet>>>> SET_TO_IMPORTER =
            ImmutableMap.of(
                    SetOption.SENTENCE, TopPairsProgram::importDocToSentimentSentences,
                    SetOption.REVIEW, TopPairsProgram::importDocToSentimentReviews
            );

    public static void main(String[] args) throws IOException {
        CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        options.addOption("h", "help", false, "print this message");
        options.addOption("p", "profile-dataset", false,
            "Explore dataset statistics");
        options.addOption("s", "synthetic-dataset", false,
            "Experiment synthetic dataset");
        options.addOption("b", "best-lp", false,
            "Find the best Linear Programming method. Not popular.");

        options.addOption(Option.builder("t").longOpt("type").hasArg().argName("TYPE")
            .type(String.class)
            .desc("Experiment type, one of 3: pair, sentence or review").build());

        options.addOption(Option.builder().longOpt("input-file").hasArg().argName("FILE")
            .desc("Input file path").build());
        options.addOption(Option.builder().longOpt("output-dir").hasArg().argName("DIR")
            .desc("Output directory").build());
        options.addOption(Option.builder().longOpt("doc-count").hasArg().argName("COUNT")
            .type(Integer.class)
            .desc("Number of doctors. Default 0, use as much as the dataset has").build());
        options.addOption(Option.builder().longOpt("thread-count").hasArg().argName("COUNT")
            .type(Integer.class)
            .desc("Number of threads being used simultaneously.").build());
        options.addOption(Option.builder().longOpt("summarize-only").hasArg().argName("SUMMARIZER")
            .desc("Summarize sets only, i.e. no experiment. Possible values: " +
                SetAlgorithm.GREEDY_SET.toString().toLowerCase() + ", " +
                SetAlgorithm.ILP_SET.toString().toLowerCase() + ", " +
                SetAlgorithm.RANDOMIZED_ROUNDING_SET.toString().toLowerCase() + ".").build());

        try {
            CommandLine commandLine = parser.parse(options, args);
            int threadCount = Integer.parseInt(commandLine.getOptionValue(
                "thread-count", String.valueOf(Constants.NUM_THREADS_ALGORITHM)));
            String inputFile = commandLine.getOptionValue("input-file", DOC_TO_REVIEWS_PATH);
            int docCount = Integer.parseInt(commandLine.getOptionValue("doc-count", "0"));

            if (commandLine.hasOption("h")) {
                printHelp(options);
            } else if (commandLine.hasOption("profile-dataset")) {
                String outputDir = commandLine.getOptionValue(
                    "output-dir", "src/main/resources/");
                getDatasetStatistics(inputFile,"src/main/resources/dataset-statistics.txt");
		            examineProblemSizes(outputDir);
            } else if (commandLine.hasOption("synthetic-dataset")) {
                String outputDir = commandLine.getOptionValue("output-dir", OUTPUT_FOLDER);
                topPairsSyntheticExperiment(threadCount, inputFile, docCount, outputDir);
            } else if (commandLine.hasOption("type")) {
                long startTime = System.currentTimeMillis();
                String type = commandLine.getOptionValue("type").toLowerCase();
                String outputDir = commandLine.getOptionValue("output-dir", OUTPUT_FOLDER);
                boolean findBestLP = commandLine.hasOption("best-lp");

                List<Integer> kSet = ImmutableList.of(3, 5, 10, 15, 20);
                List<Float> thresholdSet = ImmutableList.of(0.5f);
//		            List<Float> thresholdSet = ImmutableList.of(0.3f, 0.4f, 0.5f, 0.6f);

                if (commandLine.hasOption("summarize-only")) {
                    SetAlgorithm setAlgorithm = SetAlgorithm.valueOf(
                        commandLine.getOptionValue("summarize-only").toUpperCase());
                    summarizeSets(kSet, thresholdSet, threadCount, SetOption.SENTENCE, setAlgorithm,
                        inputFile, docCount, outputDir);
                } else {
                    switch (type) {
                        case "pair":
                            topPairsExperiment(kSet, thresholdSet, threadCount, findBestLP,
                                inputFile, docCount, outputDir);
                            break;
                        case "sentence":
                            topSetsExperiment(kSet, thresholdSet, threadCount, SetOption.SENTENCE,
                                inputFile, docCount, outputDir);
                            break;
                        case "review":
                            topSetsExperiment(kSet, thresholdSet, threadCount, SetOption.REVIEW,
                                inputFile, docCount, outputDir);
                            break;
                        default:
                            printHelp(options);
                            break;
                    }
                    Utils.printRunningTime(startTime, "Finished evaluation");
                }
            } else {
                printHelp(options);
            }
        } catch (ParseException e) {
            System.out.println("Unexpected exception:" + e.getMessage());
        }
    }

    private static void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        String header = "Main summarization module with our methods.";
        formatter.printHelp("TopPairsProgram", header, options, "", true);
    }

    private static void topPairsExperiment(
            List<Integer> kSet, List<Float> thresholdSet, int threadCount, boolean findBestLP,
            String inputFile, int docCount, String outputDir
    ) throws IOException {
        List<StatisticalResult[]> statisticalResults = new ArrayList<>();
        for (int numChosen : kSet) {
            k = numChosen;
            for (float thres : thresholdSet) {
                threshold = thres;
                String subFolder = "top_pair/k" + k + "_threshold" + threshold + "/";
                String subOutputDir = outputDir + subFolder;
                if (!Files.exists(Paths.get(subOutputDir)))
                    Files.createDirectories(Paths.get(subOutputDir));
                statisticalResults.add(topPairsExperiment(
                    threadCount, inputFile, docCount, findBestLP, subOutputDir));
            }
        }

        outputSummaryStatisticsToCSV(
            statisticalResults, outputDir + "/top_pair/", "top_pair");
        outputThresholdAndCostToCSV(
            statisticalResults, outputDir + "cost_threshold/", "top_pair");
    }

    private static void outputThresholdAndCostToCSV(
            List<StatisticalResult[]> statisticalResults,
            String outputFolder, String fileNamePrefix
    ) throws IOException {
        if (!Files.exists(Paths.get(outputFolder))) {
            Files.createDirectories(Paths.get(outputFolder));
        }

        List<Map<Integer, List<StatisticalResult>>> kToResultsOfMethods = new ArrayList<>();
        for (int i = 0; i < 3; ++i)
            kToResultsOfMethods.add(new HashMap<>());
        for (StatisticalResult[] results : statisticalResults) {
            for (int i = 0; i < 3; ++i) {
                Map<Integer, List<StatisticalResult>> kToResults = kToResultsOfMethods.get(i);
                StatisticalResult result = results[i];

                Integer numSelected = result.getK();
                if (!kToResults.containsKey(numSelected))
                    kToResults.put(numSelected, new ArrayList<>());
                kToResults.get(numSelected).add(result);
            }
        }

        for (int i = 0; i < 3; ++i) {
            String methodPrefix = "";
            switch (i) {
                case ILP_INDEX:
                    methodPrefix = "ilp_";
                    break;
                case RR_INDEX:
                    methodPrefix = "rr_";
                    break;
                case GREEDY_INDEX:
                    methodPrefix = "greedy_";
                    break;
            }

            Map<Integer, List<StatisticalResult>> kToResults = kToResultsOfMethods.get(i);
            for (Integer num : kToResults.keySet()) {
                StringBuilder sb = new StringBuilder();
                for (StatisticalResult result : kToResults.get(num)) {
                    sb.append(result.getThreshold()).append(", ")
                            .append(result.getFinalCost()).append("\n");
                }

                BufferedWriter writer = Files.newBufferedWriter(
                        Paths.get(outputFolder, fileNamePrefix + "_" + methodPrefix + "k" + num + ".csv"),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
                writer.write(sb.toString());
                writer.flush();
                writer.close();
            }
        }
    }

    /**
     * @return array of size 3 of average result for 3 algorithms, also output csv file
     */
    private static StatisticalResult[] topPairsExperiment(
        int threadCount, String inputFile, int docCount, boolean findBestLP, String outputFolder) {

        long startTime = System.currentTimeMillis();
        Map<String, List<ConceptSentimentPair>> docToConceptSentimentPairs =
                importDocToConceptSentimentPairs(inputFile, RANDOMIZE_DOCS);
        printInitialization(docToConceptSentimentPairs);

        ConcurrentMap<String, StatisticalResult> docToStatisticalResultGreedyFinal = new ConcurrentHashMap<>();
        ConcurrentMap<String, StatisticalResult> docToStatisticalResultILPFinal = new ConcurrentHashMap<>();
        ConcurrentMap<String, StatisticalResult> docToStatisticalResultRRFinal = new ConcurrentHashMap<>();

        ConcurrentMap<String, StatisticalResult> docToStatisticalResultGreedy = new ConcurrentHashMap<>();
        ConcurrentMap<String, StatisticalResult> docToStatisticalResultILP = new ConcurrentHashMap<>();
        ConcurrentMap<String, StatisticalResult> docToStatisticalResultRR = new ConcurrentHashMap<>();

        ConcurrentMap<String, List<ConceptSentimentPair>> docToTopKPairsResultGreedy = new ConcurrentHashMap<>();
        ConcurrentMap<String, List<ConceptSentimentPair>> docToTopKPairsResultILP = new ConcurrentHashMap<>();
        ConcurrentMap<String, List<ConceptSentimentPair>> docToTopKPairsResultRR = new ConcurrentHashMap<>();

        String outputPrefix = outputFolder + "top_pairs_result_" + docCount;
//		importResultFromJson(outputPrefix + "_ilp.txt", docToTopPairsResultILP);
//		importResultFromJson(outputPrefix + "_greedy.txt", docToTopPairsResultGreedy);
//		importResultFromJson(outputPrefix + "_rr.txt", docToTopPairsResultRR);
        List<LPMethod> methods = ImmutableList.of(LPMethod.AUTOMATIC, LPMethod.BARRIER,
                LPMethod.CONCURRENT, LPMethod.DETERMINISTIC_CONCURRENT,
                LPMethod.DUAL_SIMPLEX, LPMethod.PRIMAL_SIMPLEX);
        for (int trial = 0; trial < NUM_TRIALS; ++trial) {
            runTopPairsAlgoMultiThreads(Algorithm.GREEDY, threadCount,
                    docToConceptSentimentPairs, docToStatisticalResultGreedy, docToTopKPairsResultGreedy);
            outputStatisticalResultToJson(outputPrefix + "_greedy.txt", docToStatisticalResultGreedy);
            outputTopKToJson(outputPrefix + "_greedy_pair.txt", docToTopKPairsResultGreedy);

            runTopPairsAlgoMultiThreads(Algorithm.ILP, threadCount,
                    docToConceptSentimentPairs, docToStatisticalResultILP, docToTopKPairsResultILP);
            outputStatisticalResultToJson(outputPrefix + "_ilp.txt", docToStatisticalResultILP);
            outputTopKToJson(outputPrefix + "_ilp_pair.txt", docToTopKPairsResultILP);

            if (findBestLP) {
                Map<LPMethod, Long> methodToTime = new HashMap<>();
                for (LPMethod method : methods) {
                    long startTime2 = System.currentTimeMillis();
                    runTopPairsAlgoMultiThreads(Algorithm.RANDOMIZED_ROUNDING, threadCount,
                            docToConceptSentimentPairs, docToStatisticalResultRR, docToTopKPairsResultRR, method);
                    outputStatisticalResultToJson(outputPrefix + "_rr.txt", docToStatisticalResultRR);

                    methodToTime.put(method, System.currentTimeMillis() - startTime2);
                }
                LPMethod bestMethod = LPMethod.AUTOMATIC;
                long min = Long.MAX_VALUE;
                for (LPMethod method : methodToTime.keySet()) {
                    if (methodToTime.get(method) < min) {
                        min = methodToTime.get(method);
                        bestMethod = method;
                    }
                }
                System.err.println(
                    "Best LP Method: " + bestMethod + ", number " + bestMethod.method());
            } else {
                runTopPairsAlgoMultiThreads(Algorithm.RANDOMIZED_ROUNDING, threadCount,
                        docToConceptSentimentPairs, docToStatisticalResultRR, docToTopKPairsResultRR);
                outputStatisticalResultToJson(outputPrefix + "_rr.txt", docToStatisticalResultRR);
                outputTopKToJson(outputPrefix + "_rr_pair.txt", docToTopKPairsResultRR);
            }

            if (docToStatisticalResultGreedyFinal.isEmpty()) {
                docToStatisticalResultGreedyFinal.putAll(docToStatisticalResultGreedy);
                docToStatisticalResultILPFinal.putAll(docToStatisticalResultILP);
                docToStatisticalResultRRFinal.putAll(docToStatisticalResultRR);
            } else {
                for (String docId : docToStatisticalResultGreedy.keySet()) {
                    docToStatisticalResultGreedyFinal.get(docId).switchToMin(
                            docToStatisticalResultGreedy.get(docId));
                    docToStatisticalResultILPFinal.get(docId).switchToMin(
                            docToStatisticalResultILP.get(docId));
                    docToStatisticalResultRRFinal.get(docId).switchToMin(
                            docToStatisticalResultRR.get(docId));
                }
            }
        }

        Path outputPath = Paths.get(outputFolder,
            "review_diversity_k" + k + "_threshold" + threshold + "_" + docCount + ".xlsx");
        outputStatisticalResultToExcel(
                outputPath, false,
                docToStatisticalResultGreedyFinal, docToStatisticalResultILPFinal, docToStatisticalResultRRFinal);
        outputTimeToCsv(
                Paths.get(outputFolder, "time_k" + k + "_s" + ((int) (threshold * 10)) + ".csv"),
                NumItem.NUM_PAIRS,
                docToStatisticalResultGreedyFinal, docToStatisticalResultILPFinal, docToStatisticalResultRRFinal);
        outputTimeToCsv(
                Paths.get(outputFolder, "time_pair_edge_k" + k + "_s" + ((int) (threshold * 10)) + ".csv"),
                NumItem.NUM_PAIRS_EDGES,
                docToStatisticalResultGreedyFinal, docToStatisticalResultILPFinal, docToStatisticalResultRRFinal);


        Utils.printRunningTime(startTime, "Finished Top Pairs", true);

        return summaryStatisticalResultsOfDifferentMethods(
                docToStatisticalResultGreedyFinal,
                docToStatisticalResultILPFinal,
                docToStatisticalResultRRFinal);
    }

    private static void topPairsSyntheticExperiment(
            int threadCount, String inputFile, int docCount, String outputDir) {
        long startTime = System.currentTimeMillis();

        Map<String, List<ConceptSentimentPair>> docToConceptSentimentPairs;
        ConcurrentMap<String, StatisticalResult> docToStatisticalResultGreedy = new ConcurrentHashMap<>();
        ConcurrentMap<String, StatisticalResult> docToStatisticalResultILP = new ConcurrentHashMap<>();
        ConcurrentMap<String, StatisticalResult> docToStatisticalResultRR = new ConcurrentHashMap<>();

        ConcurrentMap<String, List<ConceptSentimentPair>> docToTopKPairsResultGreedy = new ConcurrentHashMap<>();
        ConcurrentMap<String, List<ConceptSentimentPair>> docToTopKPairsResultILP = new ConcurrentHashMap<>();
        ConcurrentMap<String, List<ConceptSentimentPair>> docToTopKPairsResultRR = new ConcurrentHashMap<>();


        int numDecimals = 1;

        docToConceptSentimentPairs = createSyntheticDataset(
                importDocToConceptSentimentPairs(inputFile, RANDOMIZE_DOCS), numDecimals);

//		printInitialization(docToConceptSentimentPairs);

        String outputPrefix = Paths.get(outputDir, "top_pairs_synthetic_" + docCount).toString();
//		importResultFromJson(outputPrefix + "_ilp.txt", docToTopPairsResultILP);
//		importResultFromJson(outputPrefix + "_greedy.txt", docToTopPairsResultGreedy);
//		importResultFromJson(outputPrefix + "_rr.txt", docToTopPairsResultRR);

        runTopPairsAlgoMultiThreads(Algorithm.GREEDY, threadCount,
                docToConceptSentimentPairs, docToStatisticalResultGreedy, docToTopKPairsResultGreedy);
        outputStatisticalResultToJson(outputPrefix + "greedy.txt", docToStatisticalResultGreedy);

        runTopPairsAlgoMultiThreads(Algorithm.ILP, threadCount,
        docToConceptSentimentPairs, docToStatisticalResultILP, docToTopKPairsResultILP);
        outputStatisticalResultToJson(outputPrefix + "_ilp.txt", docToStatisticalResultILP);

        runTopPairsAlgoMultiThreads(Algorithm.RANDOMIZED_ROUNDING, threadCount,
            docToConceptSentimentPairs, docToStatisticalResultRR, docToTopKPairsResultRR);
        outputStatisticalResultToJson(outputPrefix + "_rr.txt", docToStatisticalResultRR);

        Path outputPath = Paths.get(outputDir,
                "synthetic_k" + k + "_threshold" + Math.round(threshold) / 10f + "_" + docCount + ".xlsx");
        outputStatisticalResultToExcel(outputPath, false,
                docToStatisticalResultGreedy, docToStatisticalResultILP, docToStatisticalResultRR);

        Utils.printRunningTime(startTime, "Finished Top Pairs Synthetic", true);
    }

    private static void topSetsExperiment(
            List<Integer> kSet, List<Float> thresholdSet, int threadCount, SetOption setOption,
            String inputPath,
            int doctorCount,
            String outputDir
    ) throws IOException {
        List<StatisticalResult[]> statisticalResults = new ArrayList<>();
        String setName = setOption.toString().toLowerCase();

        for (int numChosen : kSet) {
            k = numChosen;
            for (float thres : thresholdSet) {
                threshold = thres;
                String subOutputDir = Paths.get(outputDir, "top_" + setName,
                        "k" + k + "_threshold" + Math.round(threshold * 10) / 10.0f).toString();
                if (!Files.exists(Paths.get(subOutputDir)))
                    Files.createDirectories(Paths.get(subOutputDir));
                statisticalResults.add(topSetsExperiment(
                    threadCount, setOption, inputPath, doctorCount, subOutputDir));
            }
        }

        outputSummaryStatisticsToCSV(statisticalResults,
                Paths.get(outputDir, "top_" + setName, "/").toString(),
                "top_" + setName);
        outputThresholdAndCostToCSV(statisticalResults,
                Paths.get(outputDir, "cost_threshold").toString(), "top_" + setName);
    }

    private static StatisticalResult[] topSetsExperiment(
            int threadCount,
            SetOption setOption,
            String inputPath,
            int doctorCount,
            String outputFolder
    ) {
        long startTime = System.currentTimeMillis();
        Map<String, List<SentimentSet>> docToSentimentSets = SET_TO_IMPORTER.get(setOption)
            .apply(inputPath, RANDOMIZE_DOCS, doctorCount);

        ConcurrentMap<String, StatisticalResult> docToStatisticalResultGreedyFinal = new ConcurrentHashMap<>();
        ConcurrentMap<String, StatisticalResult> docToStatisticalResultILPFinal = new ConcurrentHashMap<>();
        ConcurrentMap<String, StatisticalResult> docToStatisticalResultRRFinal = new ConcurrentHashMap<>();

        ConcurrentMap<String, StatisticalResult> docToStatisticalResultGreedy = new ConcurrentHashMap<>();
        ConcurrentMap<String, StatisticalResult> docToStatisticalResultILP = new ConcurrentHashMap<>();
        ConcurrentMap<String, StatisticalResult> docToStatisticalResultRR = new ConcurrentHashMap<>();

        ConcurrentMap<String, List<SentimentSet>> docToTopKSetsGreedy = new ConcurrentHashMap<>();
        ConcurrentMap<String, List<SentimentSet>> docToTopKSetsILP = new ConcurrentHashMap<>();
        ConcurrentMap<String, List<SentimentSet>> docToTopKSetsRR = new ConcurrentHashMap<>();

        String outputPrefix = Paths.get(
            outputFolder, "top_" + setOption + "_result_" + doctorCount).toString();
//		importResultFromJson(outputPrefix + "_ilp.txt", docToStatisticalResultILP);
//		importResultFromJson(outputPrefix + "_greedy.txt", docToStatisticalResultGreedy);
//		importResultFromJson(outputPrefix + "_rr.txt", docToStatisticalResultRR);

        for (int trial = 0; trial < NUM_TRIALS; ++trial) {
            runTopSetsAlgoMultiThreads(SetAlgorithm.GREEDY_SET, threadCount,
                    docToSentimentSets, docToStatisticalResultGreedy, docToTopKSetsGreedy);
            outputStatisticalResultToJson(outputPrefix + "_greedy.txt", docToStatisticalResultGreedy);
            outputTopKToJson(outputPrefix + "_greedy_set.txt",
                    convertTopKSetsMapToSetResultMap(docToTopKSetsGreedy));

            runTopSetsAlgoMultiThreads(SetAlgorithm.ILP_SET, threadCount,
                    docToSentimentSets, docToStatisticalResultILP, docToTopKSetsILP);
            outputStatisticalResultToJson(outputPrefix + "_ilp.txt", docToStatisticalResultILP);
            outputTopKToJson(outputPrefix + "_ilp_set.txt",
                    convertTopKSetsMapToSetResultMap(docToTopKSetsILP));

            runTopSetsAlgoMultiThreads(SetAlgorithm.RANDOMIZED_ROUNDING_SET, threadCount,
                    docToSentimentSets, docToStatisticalResultRR, docToTopKSetsRR);
            outputStatisticalResultToJson(outputPrefix + "_rr.txt", docToStatisticalResultRR);
            outputTopKToJson(outputPrefix + "_rr_set.txt",
                    convertTopKSetsMapToSetResultMap(docToTopKSetsRR));

            if (docToStatisticalResultGreedyFinal.isEmpty()) {
                docToStatisticalResultGreedyFinal.putAll(docToStatisticalResultGreedy);
                docToStatisticalResultILPFinal.putAll(docToStatisticalResultILP);
                docToStatisticalResultRRFinal.putAll(docToStatisticalResultRR);
            } else {
                for (String docId : docToStatisticalResultGreedy.keySet()) {
                    docToStatisticalResultGreedyFinal.get(docId).switchToMin(
                            docToStatisticalResultGreedy.get(docId));
                    docToStatisticalResultILPFinal.get(docId).switchToMin(
                            docToStatisticalResultILP.get(docId));
                    docToStatisticalResultRRFinal.get(docId).switchToMin(
                            docToStatisticalResultRR.get(docId));
                }
            }
        }

        Path outputPath = Paths.get(outputFolder,
                "review_diversity_" + setOption + "_k" + k + "_threshold" +
                Math.round(threshold) / 10f + "_" + doctorCount + ".xlsx");
        outputStatisticalResultToExcel(outputPath, true,
                docToStatisticalResultGreedyFinal, docToStatisticalResultILPFinal, docToStatisticalResultRRFinal);

        outputTimeToCsv(
                Paths.get(outputFolder, "time_k" + k + "_s" + ((int) (threshold * 10)) + ".csv"),
                NumItem.NUM_PAIRS,
                docToStatisticalResultGreedyFinal, docToStatisticalResultILPFinal, docToStatisticalResultRRFinal);
        outputTimeToCsv(
                Paths.get(outputFolder, "time_pair_edge_k" + k + "_s" + ((int) (threshold * 10)) + ".csv"),
                NumItem.NUM_PAIRS_EDGES,
                docToStatisticalResultGreedyFinal, docToStatisticalResultILPFinal, docToStatisticalResultRRFinal);

        Utils.printRunningTime(startTime, "Finished Top " + setOption, true);
        return summaryStatisticalResultsOfDifferentMethods(docToStatisticalResultGreedyFinal,
                docToStatisticalResultILPFinal, docToStatisticalResultRRFinal);
    }

    private static void summarizeSets(
        List<Integer> kSet, List<Float> thresholdSet, int threadCount,
        SetOption setOption, SetAlgorithm algorithm,
        String inputPath,
        int doctorCount,
        String outputDir
    ) throws IOException {
        String setName = setOption.toString().toLowerCase();

        for (int numChosen : kSet) {
            k = numChosen;
            for (float thres : thresholdSet) {
                threshold = thres;
                long startTime = System.currentTimeMillis();

                String[] filePaths = setMethodFileName(
                    setOption, algorithm, doctorCount, k, threshold);
                Path subOutputDir = Paths.get(outputDir, filePaths[0], filePaths[1]);
                if (!Files.exists(subOutputDir))
                    Files.createDirectories(subOutputDir);

                Map<String, List<SentimentSet>> docToSentimentSets = SET_TO_IMPORTER.get(setOption)
                    .apply(inputPath, false, doctorCount);
                ConcurrentMap<String, List<SentimentSet>> docToTopKSetsGreedy =
                    new ConcurrentHashMap<>();

                String outputPath = Paths.get(outputDir, filePaths).toString();
                for (int trial = 0; trial < NUM_TRIALS; ++trial) {
                    runTopSetsAlgoMultiThreads(algorithm, threadCount,
                            docToSentimentSets, new ConcurrentHashMap<>(), docToTopKSetsGreedy);
                    outputTopKToJson(outputPath,
                            convertTopKSetsMapToSetResultMap(docToTopKSetsGreedy));
                }
                Utils.printRunningTime(startTime,
                    "Finished k=" + k + " and threshold=" + threshold);
            }
        }
    }

    public static String[] setMethodFileName(
        SetOption setOption, SetAlgorithm setAlgorithm, int doctorCount, int k, double threshold) {
        String setName = setOption.toString().toLowerCase();
        String setSubDir = "top_" + setName;
        String kThresholdSubDir = "k" + k + "_threshold" + threshold;
        String fileName = "top_" + setName + "_" + doctorCount + "_" +
                setAlgorithm.toString().toLowerCase() + ".txt";
        return new String[]{setSubDir, kThresholdSubDir, fileName};
    }

    private static void examineProblemSizes(String outputFolder) {
        Map<String, List<ConceptSentimentPair>> docToConceptSentimentPairs =
                importDocToConceptSentimentPairs(DOC_TO_REVIEWS_PATH, RANDOMIZE_DOCS);

        float[] thresholds = new float[]{0.1f, 0.3f};
        for (float threshold : thresholds) {
            Map<Integer, List<Integer>> numPairToNumEdges = new HashMap<>();
            Map<Integer, Double> numPairToAverageNumEdge = new HashMap<>();

            for (String docId : docToConceptSentimentPairs.keySet()) {
                List<ConceptSentimentPair> conceptSentimentPairs = docToConceptSentimentPairs.get(docId);

                List<ConceptSentimentPair> pairs = new ArrayList<>();
                ConceptSentimentPair root = new ConceptSentimentPair(Constants.ROOT_CUI, 0.0f);
                root.addDewey(SnomedGraphBuilder.ROOT_DEWEY);
                pairs.add(root);
                pairs.addAll(conceptSentimentPairs);

                Map<Integer, Map<Integer, Integer>> ancestorToSuccessorAndDistance =
                        FiniteDistanceInitializer.initFiniteDistancesFromPairIndexToPairIndex(pairs, threshold);

                int numPairs = pairs.size();
                int numEdges = 0;
                for (Integer ancestor : ancestorToSuccessorAndDistance.keySet()) {
                    numEdges += ancestorToSuccessorAndDistance.get(ancestor).size();
                }

                if (!numPairToNumEdges.containsKey(numPairs)) {
                    numPairToNumEdges.put(numPairs, new ArrayList<>());
                    numPairToAverageNumEdge.put(numPairs, 0.0);
                }

                numPairToNumEdges.get(numPairs).add(numEdges);
                numPairToAverageNumEdge.put(numPairs, numPairToAverageNumEdge.get(numPairs) + numEdges);
            }


            for (Integer numPairs : numPairToAverageNumEdge.keySet()) {
                numPairToAverageNumEdge.put(numPairs,
                        numPairToAverageNumEdge.get(numPairs) / (double) numPairToNumEdges.get(numPairs).size());
            }

            StringBuilder output = new StringBuilder();
            output.append("#number of pairs, number of edges, detail\n");
            List<Integer> sortedNumPairs = new ArrayList<>(numPairToAverageNumEdge.keySet());
            Collections.sort(sortedNumPairs);
            for (int numPair : sortedNumPairs) {
                output.append(numPair).append(", ")
                        .append(numPairToAverageNumEdge.get(numPair)).append(", ");
                for (Integer numEdges : numPairToNumEdges.get(numPair)) {
                    output.append(numEdges).append(" ");
                }
                output.append("\n");
            }

            try (BufferedWriter writer = Files.newBufferedWriter(
                    Paths.get(outputFolder + "problem_size_" + threshold + ".csv"),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                writer.write(output.toString());
                writer.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void getDatasetStatistics(String dataPath, String outputPath) {
        String output = "";

        Map<String, List<SentimentSet>> docToSentimentSets = importDocToSentimentReviews(
            dataPath, false, 0);
        List<Integer> counts = new ArrayList<>();
        for (List<SentimentSet> reviews : docToSentimentSets.values())
            counts.add(reviews.size());
        output = output + updateStatistics(counts, "#reviews");

        docToSentimentSets = importDocToSentimentSentences(
            dataPath, false, 0);
        counts = new ArrayList<>();
        for (List<SentimentSet> sentences : docToSentimentSets.values())
            counts.add(sentences.size());
        output = output + updateStatistics(counts, "#sentences");

        Map<String, List<ConceptSentimentPair>> docToPairs = importDocToConceptSentimentPairs(
            dataPath, false);
        counts = new ArrayList<>();
        for (List<ConceptSentimentPair> pairs : docToPairs.values())
            counts.add(pairs.size());
        output = output + updateStatistics(counts, "#pairs");

        // Raw data
        List<RawReview> rawReviews = PairExtractor.getReviews(Constants.REVIEWS_PATH);
        Map<String, List<RawReview>> docToRawReviews = new HashMap<>();
        for (RawReview rawReview : rawReviews) {
            if (!docToRawReviews.containsKey(rawReview.getDocID()))
                docToRawReviews.put(rawReview.getDocID(), new ArrayList<>());
            docToRawReviews.get(rawReview.getDocID()).add(rawReview);
        }
        counts = new ArrayList<>();
        for (List<RawReview> reviews : docToRawReviews.values())
            counts.add(reviews.size());
        output = output + updateStatistics(counts, "#raw reviews");

        Map<RawReview, Integer> rawReviewToSentenceCount = new HashMap<>();
        for (RawReview rawReview : rawReviews) {
            int sentenceCount = SentimentCalculator.breakingIntoSentences(
                rawReview.getBody(), true).size();
            rawReviewToSentenceCount.put(rawReview, sentenceCount);
        }
        counts = new ArrayList<>();
        for (RawReview rawReview : rawReviews)
            counts.add(rawReviewToSentenceCount.get(rawReview));
        output = output + updateStatistics(counts, "#raw setences/raw review");

        Map<String, Integer> docToSentenceCount = new HashMap<>();
        for (RawReview rawReview : rawReviews) {
            String docId = rawReview.getDocID();
            if (!docToSentenceCount.containsKey(docId))
                docToSentenceCount.put(docId, 0);
            docToSentenceCount.put(docId,
                    docToSentenceCount.get(docId) + rawReviewToSentenceCount.get(rawReview));
        }
        counts = new ArrayList<>(docToSentenceCount.values());
        output = output + updateStatistics(counts, "#raw sentences");

        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.write(output);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Find the output at \"" + outputPath + "\"");
    }

    private static String updateStatistics(List<Integer> counts, String heading) {
        double average = counts.stream().collect(
                Collectors.averagingDouble(count -> (double) count));
        Collections.sort(counts);
        double min = counts.get(0);
        double max = counts.get(counts.size() - 1);
        return heading + ": min " + min + ", max " + max + ", average " + average + "\n";
    }

    private static ConcurrentMap<String, List<SetResult>> convertTopKSetsMapToSetResultMap(
            ConcurrentMap<String, List<SentimentSet>> docToTopSentimentSets) {

        ConcurrentMap<String, List<SetResult>> docToSetResults = new ConcurrentHashMap<>();
        for (String docId : docToTopSentimentSets.keySet()) {
            List<SetResult> setResults = new ArrayList<>();
            for (SentimentSet set : docToTopSentimentSets.get(docId)) {
                if (set.getClass() == SentimentSentence.class) {
                    SentimentSentence sentence = (SentimentSentence) set;
                    setResults.add(new SetResult(
                            sentence.getId(), sentence.getSentence(), sentence.getPairs()));
                } else if (set.getClass() == SentimentReview.class) {
                    SentimentReview sentence = (SentimentReview) set;
                    setResults.add(new SetResult(sentence.getId(),
                            sentence.getRawReview().getBody(),
                            sentence.getPairs()));
                }
            }
            docToSetResults.put(docId, setResults);
        }

        return docToSetResults;
    }

    private static void printInitialization(
            Map<String, List<ConceptSentimentPair>> docToConceptSentimentPairs) {

        int maxNumPairs = 0;
        String maxDocID = "";
        for (Map.Entry<String, List<ConceptSentimentPair>> entry : docToConceptSentimentPairs.entrySet()) {
            if (entry.getValue().size() > maxNumPairs) {
                maxNumPairs = entry.getValue().size();
                maxDocID = entry.getKey();
            }
        }
        System.err.println("There are " + docToConceptSentimentPairs.size() +
                " doctors, with Max Num of Pairs is " + maxNumPairs + " of DocID: " + maxDocID);
    }

    /**
     * TopPairs algorithms for ConceptSentimentPair
     *
     * @param algorithm                  - GREEDY, ILP, RANDOMIZED ROUNDING
     * @param numThreadsAlgorithm        - # threads
     * @param docToConceptSentimentPairs - map from doctor's id to ConceptSentimentPairs
     * @param method                     - choosing LP solver. Note that this doesn't affect GREEDY, ILP, only affect Randomized Rounding
     */
    private static void runTopPairsAlgoMultiThreads(
            Algorithm algorithm,
            int numThreadsAlgorithm,
            Map<String, List<ConceptSentimentPair>> docToConceptSentimentPairs,
            ConcurrentMap<String, StatisticalResult> docToStatisticalResult,
            ConcurrentMap<String, List<ConceptSentimentPair>> docToTopKPairsResult,
            LPMethod method) {

        long startTime = System.currentTimeMillis();

        ExecutorService fixedPool = Executors.newFixedThreadPool(numThreadsAlgorithm);
        List<Future<String>> futures = new ArrayList<>();

        for (int index = 0; index < numThreadsAlgorithm; ++index) {
            Future<String> future;
            switch (algorithm) {
                case GREEDY:
                    future = fixedPool.submit(new GreedyThreadImpl(k, threshold, docToStatisticalResult, docToTopKPairsResult,
                            index, numThreadsAlgorithm, docToConceptSentimentPairs), "DONE!");
                    break;
                case ILP:
                    future = fixedPool.submit(new ILPThreadImpl(k, threshold, docToStatisticalResult, docToTopKPairsResult,
                            index, numThreadsAlgorithm, docToConceptSentimentPairs), "DONE!");
                    break;
                case RANDOMIZED_ROUNDING:
                    future = fixedPool.submit(new RandomizedRoundingThreadImpl(k, threshold, docToStatisticalResult, docToTopKPairsResult,
                            index, numThreadsAlgorithm, docToConceptSentimentPairs, method), "DONE!");
                    break;
                default:
                    future = fixedPool.submit(new GreedyThreadImpl(k, threshold, docToStatisticalResult, docToTopKPairsResult,
                            index, numThreadsAlgorithm, docToConceptSentimentPairs), "DONE!");
                    break;
            }
            futures.add(future);
        }

        fixedPool.shutdown();
        try {
            fixedPool.awaitTermination(2, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        taskProgress(startTime, futures, algorithm.toString(), docToStatisticalResult.size());
    }

    private static void taskProgress(
        long startTime, List<Future<String>> futures, String algorithm, int docCount) {
        Utils.printRunningTime(startTime,
            algorithm + " " + countUnfinishedJob(futures) + " unfinished jobs", true);
        Utils.printRunningTime(startTime,
            algorithm + " finished " + docCount + " doctors");
    }

    // @param method - choosing LP solver. Note that this doesn't affect GREEDY, ILP, only affect Randomized Rounding
    private static void runTopPairsAlgoMultiThreads(
            Algorithm algorithm,
            int numThreadsAlgorithm,
            Map<String, List<ConceptSentimentPair>> docToConceptSentimentPairs,
            ConcurrentMap<String, StatisticalResult> docToStatisticalResult,
            ConcurrentMap<String, List<ConceptSentimentPair>> docToTopKPairsResult) {
        runTopPairsAlgoMultiThreads(algorithm, numThreadsAlgorithm, docToConceptSentimentPairs, docToStatisticalResult, docToTopKPairsResult,
                Constants.MY_DEFAULT_LP_METHOD);
    }

    /**
     * SentimentSet such as SentimentReview, SentimentSentence
     *
     * @param setAlgorithm           - greedy or ilp or randomized rounding
     * @param numThreadsAlgorithm    - # threads
     * @param docToSentimentSets     - map from doctor's id to sentiment sets
     */
    private static void runTopSetsAlgoMultiThreads(
            SetAlgorithm setAlgorithm,
            int numThreadsAlgorithm,
            Map<String, List<SentimentSet>> docToSentimentSets,
            ConcurrentMap<String, StatisticalResult> docToStatisticalResult,
            ConcurrentMap<String, List<SentimentSet>> docToTopKSetsResult) {
        long startTime = System.currentTimeMillis();

        ExecutorService fixedPool = Executors.newFixedThreadPool(numThreadsAlgorithm);
        List<Future<String>> futures = new ArrayList<>();

        for (int index = 0; index < numThreadsAlgorithm; ++index) {
            Future<String> future;
            switch (setAlgorithm) {
                case GREEDY_SET:
                    future = fixedPool.submit(new GreedySetThreadImpl(k, threshold, docToStatisticalResult, docToTopKSetsResult,
                            index, numThreadsAlgorithm, docToSentimentSets), "DONE!");
                    break;
                case ILP_SET:
                    future = fixedPool.submit(new ILPSetThreadImpl(k, threshold, docToStatisticalResult, docToTopKSetsResult,
                            index, numThreadsAlgorithm, docToSentimentSets), "DONE!");
                    break;
                case RANDOMIZED_ROUNDING_SET:
                    future = fixedPool.submit(new RandomizedRoundingSetThreadImpl(k, threshold, docToStatisticalResult, docToTopKSetsResult,
                            index, numThreadsAlgorithm, docToSentimentSets), "DONE!");
                    break;
                default:
                    future = fixedPool.submit(new GreedySetThreadImpl(k, threshold, docToStatisticalResult, docToTopKSetsResult,
                            index, numThreadsAlgorithm, docToSentimentSets), "DONE!");
                    break;
            }
            futures.add(future);
        }

        fixedPool.shutdown();
        try {
            fixedPool.awaitTermination(2, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        taskProgress(startTime, futures, setAlgorithm.toString(), docToStatisticalResult.size());
    }

    private static int countUnfinishedJob(List<Future<String>> futures) {
        int unfinished = 0;
        for (Future<String> job : futures) {
            try {
                if (job.get() == null || !job.get().equals(EXPECTED_JOB_DONE)) {
                    ++unfinished;
                }
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
        return unfinished;
    }

    private static void outputStatisticalResultToJson(
            String outputPath,
            Map<String, StatisticalResult> docToStatisticalResult) {

        long startTime = System.currentTimeMillis();
        ObjectMapper mapper = new ObjectMapper();

        try {
            Files.deleteIfExists(Paths.get(outputPath));
        } catch (IOException e) {
            e.printStackTrace();
        }

        boolean isFirstLine = true;
        for (String docID : docToStatisticalResult.keySet()) {
            try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {

                if (!isFirstLine) {
                    writer.newLine();
                }
                mapper.writeValue(writer, docToStatisticalResult.get(docID));

                isFirstLine = false;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Utils.printRunningTime(startTime, "Outputed " + docToStatisticalResult.size() + " results to " + outputPath);
    }

    public static <T> void outputTopKToJson(
            String outputPathString,
            Map<String, List<T>> docToTopKResult) {
        long startTime = System.currentTimeMillis();
        ObjectMapper mapper = new ObjectMapper();

        try {
            Path outputPath = Paths.get(outputPathString);
            if (!Files.exists(outputPath.getParent()))
                Files.createDirectories(outputPath.getParent());

            Files.deleteIfExists(outputPath);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPathString),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            mapper.writeValue(writer, docToTopKResult);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Utils.printRunningTime(startTime,
            "Exported " + docToTopKResult.size() + " topK to " + outputPathString);
    }

    @SuppressWarnings("unused")
    private static void importStatisticalResultFromJson(String path, Map<String, StatisticalResult> docToStatisticalResult) {
        ObjectMapper mapper = new ObjectMapper();

        try (BufferedReader reader = Files.newBufferedReader(Paths.get(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                StatisticalResult statisticalResult = mapper.readValue(line, StatisticalResult.class);
                docToStatisticalResult.put(statisticalResult.getDocID(), statisticalResult);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void outputStatisticalResultToExcel(
            Path outputPath, boolean isSet,
            ConcurrentMap<String, StatisticalResult> docToStatisticalResultGreedy,
            ConcurrentMap<String, StatisticalResult> docToStatisticalResultILP,
            ConcurrentMap<String, StatisticalResult> docToStatisticalResultRR) {

        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Top Pairs Results");
        addHeader(sheet, isSet, "ILP", "Greedy", "RR");

        int count = 1;
        for (String docID : docToStatisticalResultGreedy.keySet()) {
            if (!docToStatisticalResultILP.containsKey(docID) || !docToStatisticalResultRR.containsKey(docID))
                continue;

            Row row = sheet.createRow(count);

            // TODO
            if (docToStatisticalResultILP.containsKey(docID))
                addRow(row, isSet, docToStatisticalResultILP.get(docID),
                        docToStatisticalResultGreedy.get(docID),
                        docToStatisticalResultRR.get(docID));

            ++count;
        }

        FileOutputStream fileOut = null;
        try {
            fileOut = new FileOutputStream(outputPath.toString());

            wb.write(fileOut);
            fileOut.close();
            wb.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fileOut != null)
                try {
                    fileOut.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
        System.err.println("Exported top pairs to \"" + outputPath + "\"");
    }

    private static void addRow(Row row, boolean isSet, StatisticalResult ilpResult,
                               StatisticalResult greedyResult, StatisticalResult rrResult) {

        Font regularFont = row.getSheet().getWorkbook().createFont();
        regularFont.setFontName("Calibri");
        regularFont.setFontHeightInPoints((short) 13);

        CellStyle cs = row.getSheet().getWorkbook().createCellStyle();
        cs.setWrapText(true);
        cs.setFont(regularFont);


        CellStyle csRed = row.getSheet().getWorkbook().createCellStyle();
        csRed.setWrapText(true);
//		csRed.setFillBackgroundColor(IndexedColors.RED.getIndex());
        csRed.setFillForegroundColor(IndexedColors.RED.getIndex());
        csRed.setFillPattern(CellStyle.SOLID_FOREGROUND);
        csRed.setFont(regularFont);

        CellStyle csOrange = row.getSheet().getWorkbook().createCellStyle();
        csOrange.setWrapText(true);
        csOrange.setFillForegroundColor(IndexedColors.ORANGE.getIndex());
        csOrange.setFillPattern(CellStyle.SOLID_FOREGROUND);
        csOrange.setFont(regularFont);

        Cell docCell = row.createCell(0);
        Cell numPairsCell = row.createCell(1);
        Cell initialCostCell;
        Cell numSetsCell = null;
        Cell numEdgesCell;
        if (isSet) {
            numSetsCell = row.createCell(2);
            initialCostCell = row.createCell(3);
            numEdgesCell = row.createCell(4);
        } else {
            initialCostCell = row.createCell(2);
            numEdgesCell = row.createCell(3);
        }

        docCell.setCellStyle(cs);
        numPairsCell.setCellStyle(cs);
        initialCostCell.setCellStyle(cs);
        numEdgesCell.setCellStyle(cs);

        docCell.setCellValue(greedyResult.getDocID());
        numPairsCell.setCellValue(greedyResult.getNumPairs());
        initialCostCell.setCellValue(greedyResult.getInitialCost());
        numEdgesCell.setCellValue(greedyResult.getNumPotentialUsefulCoverWithThreshold());

        if (isSet) {
            numSetsCell.setCellStyle(cs);
            numSetsCell.setCellValue(greedyResult.getNumSets());
        }

        int indexOffset = 4;
        if (isSet)
            indexOffset = 5;

        Cell numUncoveredILPCell = row.createCell(indexOffset);
        Cell numUncoveredGreedyCell = row.createCell(indexOffset + 1);
        Cell numUncoveredRRCell = row.createCell(indexOffset + 2);

        Cell ilpTimeCell = row.createCell(indexOffset + 3);
        Cell greedyTimeCell = row.createCell(indexOffset + 4);
        Cell rrTimeCell = row.createCell(indexOffset + 5);

        Cell ilpCostCell = row.createCell(indexOffset + 6);
        Cell greedyCostCell = row.createCell(indexOffset + 7);
        Cell rrCostCell = row.createCell(indexOffset + 8);
        Cell greedyRatioCell = row.createCell(indexOffset + 9);
        Cell rrRatioCell = row.createCell(indexOffset + 10);


        numUncoveredILPCell.setCellStyle(cs);
        numUncoveredGreedyCell.setCellStyle(cs);
        numUncoveredRRCell.setCellStyle(cs);
        ilpTimeCell.setCellStyle(cs);
        greedyTimeCell.setCellStyle(cs);
        rrTimeCell.setCellStyle(cs);
        ilpCostCell.setCellStyle(cs);
        greedyCostCell.setCellStyle(cs);
        rrCostCell.setCellStyle(cs);
        greedyRatioCell.setCellStyle(cs);
        rrRatioCell.setCellStyle(cs);

        numUncoveredILPCell.setCellValue(ilpResult.getNumUncovered());
        numUncoveredGreedyCell.setCellValue(greedyResult.getNumUncovered());
        if (ilpResult.getNumUncovered() > greedyResult.getNumUncovered())
            numUncoveredGreedyCell.setCellStyle(csRed);

        if (rrResult != null) {
            numUncoveredRRCell.setCellValue(rrResult.getNumUncovered());
            if (ilpResult.getNumUncovered() > rrResult.getNumUncovered())
                numUncoveredRRCell.setCellStyle(csOrange);
        }

        ilpTimeCell.setCellValue(ilpResult.getRunningTime());
        greedyTimeCell.setCellValue(greedyResult.getRunningTime());
        if (ilpResult.getRunningTime() < greedyResult.getRunningTime())
            greedyTimeCell.setCellStyle(csRed);

        if (rrResult != null) {
            rrTimeCell.setCellValue(rrResult.getRunningTime());
            if (ilpResult.getRunningTime() < rrResult.getRunningTime())
                rrTimeCell.setCellStyle(csOrange);
        }

        ilpCostCell.setCellValue(ilpResult.getFinalCost());
        greedyCostCell.setCellValue(greedyResult.getFinalCost());
        if (ilpResult.getFinalCost() > 0) {
            double greedyRatio = greedyResult.getFinalCost() / ilpResult.getFinalCost() - 1;

            greedyRatioCell.setCellValue(String.format("%1$.2f", greedyRatio * 100) + "%");
            if (greedyRatio < 0) {
                greedyRatioCell.setCellStyle(csRed);
            }
        } else if (ilpResult.getFinalCost() == 0 && greedyResult.getFinalCost() == 0) {
            greedyRatioCell.setCellValue("0%");
        }

        if (rrResult != null) {
            rrCostCell.setCellValue(rrResult.getFinalCost());
            if (ilpResult.getFinalCost() > 0) {
                double rrRatio = rrResult.getFinalCost() / ilpResult.getFinalCost() - 1;

                rrRatioCell.setCellValue(String.format("%1$.2f", rrRatio * 100) + "%");
                if (rrRatio < 0) {
                    rrRatioCell.setCellStyle(csRed);
                } else if (rrRatio > 0) {
                    rrRatioCell.setCellStyle(csOrange);
                }

            } else if (ilpResult.getFinalCost() == 0 && rrResult.getFinalCost() == 0) {
                rrRatioCell.setCellValue("0%");
            }
        }
    }

    private static void addHeader(Sheet sheet, boolean isSet, String... methods) {
        Font headingFont = sheet.getWorkbook().createFont();
        headingFont.setBold(true);
        headingFont.setFontName("Calibri");
        headingFont.setFontHeightInPoints((short) 14);
        headingFont.setColor(IndexedColors.BLUE_GREY.index);

        CellStyle cs = sheet.getWorkbook().createCellStyle();
        cs.setWrapText(true);
        cs.setFont(headingFont);
        cs.setAlignment(CellStyle.ALIGN_CENTER);

        int numMethods = methods.length;

        Row row = sheet.createRow(0);

        int lastColumn = 4 + numMethods * 4 - 1;
        if (isSet)
            ++lastColumn;
        sheet.createFreezePane(lastColumn + 2, 1);

        Cell docCell = row.createCell(0);
        Cell numPairsCell = row.createCell(1);
        Cell initialCostCell;
        Cell numSetsCell = null;
        Cell numEdgesCell;
        if (isSet) {
            numSetsCell = row.createCell(2);
            numEdgesCell = row.createCell(3);
            initialCostCell = row.createCell(4);
        } else {
            initialCostCell = row.createCell(2);
            numEdgesCell = row.createCell(3);
        }

        int offset = 4;
        if (isSet)
            offset = 5;

        Cell[] uncovereds = new Cell[numMethods];
        Cell[] times = new Cell[numMethods];
        Cell[] costs = new Cell[numMethods];
        Cell[] ratios = new Cell[numMethods - 1];

        for (int method = 0; method < numMethods; ++method) {
            uncovereds[method] = row.createCell(offset + method);
        }

        offset += numMethods;
        for (int method = 0; method < numMethods; ++method) {
            times[method] = row.createCell(offset + method);
        }

        offset += numMethods;
        for (int method = 0; method < numMethods; ++method) {
            costs[method] = row.createCell(offset + method);
        }

        offset += numMethods;
        for (int method = 0; method < numMethods - 1; ++method) {
            ratios[method] = row.createCell(offset + method);
        }
        offset += numMethods - 1;

        // Set Cell Style
        docCell.setCellStyle(cs);
        numPairsCell.setCellStyle(cs);
        initialCostCell.setCellStyle(cs);
        numEdgesCell.setCellStyle(cs);
        for (int method = 0; method < numMethods; ++method) {
            uncovereds[method].setCellStyle(cs);
            times[method].setCellStyle(cs);
            costs[method].setCellStyle(cs);
        }
        for (int method = 0; method < numMethods - 1; ++method) {
            ratios[method].setCellStyle(cs);
        }

        // Set heading title
        docCell.setCellValue("DocID");
        numPairsCell.setCellValue("# Pairs");
        initialCostCell.setCellValue("Initial Cost");
        numEdgesCell.setCellValue("# Edges");
        if (isSet) {
            numSetsCell.setCellStyle(cs);
            numSetsCell.setCellValue("# Sets");
        }


        for (int method = 0; method < numMethods; ++method) {
            uncovereds[method].setCellValue("# Uncovered " + methods[method]);
            times[method].setCellValue(methods[method] + " times (ms)");
            costs[method].setCellValue(methods[method] + " Cost");
        }

        for (int method = 0; method < numMethods - 1; ++method) {
            ratios[method].setCellValue(methods[method + 1] + " Increased Ratios");
        }

        Cell infoCell = row.createCell(lastColumn);
        infoCell.setCellStyle(cs);
        infoCell.setCellValue("K = " + k + ", Threshold = " + threshold);
    }

    // Make sure: each conceptSentimentPair of a doctor has an unique hashcode
    static Map<String, List<ConceptSentimentPair>> importDocToConceptSentimentPairs(
            String path, boolean getSomeRandomItems) {
        Map<String, List<ConceptSentimentPair>> result = new HashMap<>();

        List<DoctorSentimentReview> doctorSentimentReviews = importDoctorSentimentReviewsDataset(path);

        Set<Integer> indices = new HashSet<>();
        if (getSomeRandomItems)
            indices = randomIndices;
        else {
            for (int index = 0; index < NUM_DOCTORS_TO_EXPERIMENT; ++index) {
                indices.add(index);
            }
        }

        for (Integer index : indices) {
            DoctorSentimentReview doctorSentimentReview = doctorSentimentReviews.get(index);
            String docId = doctorSentimentReview.getDocId();
            List<ConceptSentimentPair> pairs = new ArrayList<>();

            for (SentimentReview sentimentReview : doctorSentimentReview.getSentimentReviews()) {
                for (SentimentSentence sentimentSentence : sentimentReview.getSentences()) {
                    for (ConceptSentimentPair csPair : sentimentSentence.getPairs()) {

                        if (!pairs.contains(csPair))
                            pairs.add(csPair);
                        else {
                            ConceptSentimentPair currentPair = pairs.get(pairs.indexOf(csPair));
                            currentPair.incrementCount(csPair.getCount());
                        }
                    }
                }
            }

            result.put(docId, pairs);
        }

        return result;
    }

    // Make sure: each conceptSentimentPair of a SentimentReview has an unique hashcode
    public static Map<String, List<SentimentSet>> importDocToSentimentReviews(
            String path, boolean getSomeRandomItems, int docCount) {
        Map<String, List<SentimentSet>> result = new HashMap<>();
        List<DoctorSentimentReview> doctorSentimentReviews = importDoctorSentimentReviewsDataset(path);
        docCount = docCount <= 0 ? doctorSentimentReviews.size() : docCount;
        Set<Integer> indices = new HashSet<>();
        if (getSomeRandomItems)
            indices = randomIndices;
        else {
            for (int index = 0; index < docCount; ++index) {
                indices.add(index);
            }
        }

        for (Integer index : indices) {
            DoctorSentimentReview doctorSentimentReview = doctorSentimentReviews.get(index);
            String docId = doctorSentimentReview.getDocId();
            List<SentimentSet> sentimentReviews = new ArrayList<>();

            for (SentimentReview sentimentReview : doctorSentimentReview.getSentimentReviews()) {
                List<ConceptSentimentPair> pairs = new ArrayList<>();
                for (SentimentSentence sentimentSentence : sentimentReview.getSentences()) {
                    for (ConceptSentimentPair csPair : sentimentSentence.getPairs()) {

                        if (!pairs.contains(csPair))
                            pairs.add(csPair);
                        else {
                            ConceptSentimentPair currentPair = pairs.get(pairs.indexOf(csPair));
                            currentPair.incrementCount(csPair.getCount());
                        }
                    }
                }

                if (pairs.size() > 0)
                    sentimentReviews.add(new SentimentReview(sentimentReview.getId(), pairs, sentimentReview.getRawReview()));
            }

            result.put(docId, sentimentReviews);
        }

        return result;
    }

    // Make sure: each conceptSentimentPair of a SentimentSentence has an unique hashcode
    public static Map<String, List<SentimentSet>> importDocToSentimentSentences(
            String inputPath, boolean getSomeRandomItems, int docCount) {
        Map<String, List<SentimentSet>> result = new HashMap<>();

        List<DoctorSentimentReview> doctorSentimentReviews = importDoctorSentimentReviewsDataset(
                inputPath);
        docCount = docCount <= 0 ? doctorSentimentReviews.size() : docCount;
        Set<Integer> indices = new HashSet<>();
        if (getSomeRandomItems)
            indices = randomIndices;
        else {
            for (int index = 0; index < docCount; ++index) {
                indices.add(index);
            }
        }

        for (Integer index : indices) {
            DoctorSentimentReview doctorSentimentReview = doctorSentimentReviews.get(index);

            String docId = doctorSentimentReview.getDocId();
            List<SentimentSet> sentimentSentences = new ArrayList<>();

            for (SentimentReview sentimentReview : doctorSentimentReview.getSentimentReviews()) {
                for (SentimentSentence sentimentSentence : sentimentReview.getSentences()) {
                    if (sentimentSentence.getPairs().size() > 0)
                        sentimentSentences.add(sentimentSentence);
                }
            }

            result.put(docId, sentimentSentences);
        }

        return result;
    }

    static List<DoctorSentimentReview> importDoctorSentimentReviewsDataset(String path) {
        List<DoctorSentimentReview> doctorSentimentReviews = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                DoctorSentimentReview doctorSentimentReview = mapper.readValue(line,
                        DoctorSentimentReview.class);
                doctorSentimentReviews.add(doctorSentimentReview);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return doctorSentimentReviews;
    }

    private static Map<String, List<ConceptSentimentPair>> createSyntheticDataset(
            Map<String, List<ConceptSentimentPair>> docToConceptSentimentPairs,
            int numDecimals) {
        int forRounding = (int) Math.pow(10, numDecimals);

        Map<String, List<ConceptSentimentPair>> result = new HashMap<>();

        for (int i = 0; i < Constants.NUM_SYNTHETIC_DOCTORS; ++i) {
            result.put(String.valueOf(i), new ArrayList<>());
        }

        int count = 0;
        for (String docID : docToConceptSentimentPairs.keySet()) {
            String index = String.valueOf(count % Constants.NUM_SYNTHETIC_DOCTORS);
            /*temp.get(index).addAll(docToConceptSentimentPairs.get(docID));*/

            for (ConceptSentimentPair pair : docToConceptSentimentPairs.get(docID)) {
                pair.setSentiment((float) Math.round(pair.getSentiment() * forRounding) / forRounding);
                if (!result.get(index).contains(pair))
                    result.get(index).add(pair);
            }
            count++;
        }

        return result;
    }

    private static void outputTimeToCsv(
            Path outputFilePath,
            NumItem numItem,
            Map<String, StatisticalResult> docToStatisticalResultGreedy,
            Map<String, StatisticalResult> docToStatisticalResultILP,
            Map<String, StatisticalResult> docToStatisticalResultRR) {

        for (String docId : docToStatisticalResultGreedy.keySet()) {
            docToStatisticalResultILP.get(docId).setNumEdges(
                    docToStatisticalResultGreedy.get(docId).getNumEdges());
            docToStatisticalResultRR.get(docId).setNumEdges(
                    docToStatisticalResultGreedy.get(docId).getNumEdges());
        }

        Map<Integer, StatisticalResult> numToAverageGreedy =
                extractNumToAverageTime(numItem, docToStatisticalResultGreedy);
        Map<Integer, StatisticalResult> numToAverageILP =
                extractNumToAverageTime(numItem, docToStatisticalResultILP);
        Map<Integer, StatisticalResult> numToAverageRR =
                extractNumToAverageTime(numItem, docToStatisticalResultRR);

        List<Integer> nums = new ArrayList<>(numToAverageGreedy.keySet());
        Collections.sort(nums);

        try (BufferedWriter writer = Files.newBufferedWriter(outputFilePath,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            int lowerBoundToOutput = 0;
            if (numItem == NumItem.NUM_PAIRS) {
                writer.write("numPairs, ");
                lowerBoundToOutput = 30;
            } else if (numItem == NumItem.NUM_PAIRS_EDGES) {
                lowerBoundToOutput = 200;
                writer.write("numPairs+numEdges, ");
            }
            writer.write("ilp, rr, greedy, ilp setup, rr setup, greedy setup, "
                    + "ilp main, rr main, greedy main, ilp, rr's lp");
            writer.newLine();
            for (int num : nums) {
                if (num <= lowerBoundToOutput)
                    continue;

                StatisticalResult ilp = numToAverageILP.get(num);
                StatisticalResult rr = numToAverageRR.get(num);
                StatisticalResult greedy = numToAverageGreedy.get(num);
                writer.write(num + ", " + ilp.getRunningTime() + ", " + rr.getRunningTime() + ", " + greedy.getRunningTime() + ", "
                        + ilp.getPartialTime(PartialTimeIndex.SETUP) + ", " + rr.getPartialTime(PartialTimeIndex.SETUP) + ", "
                        + greedy.getPartialTime(PartialTimeIndex.SETUP) + ", " + ilp.getPartialTime(PartialTimeIndex.MAIN) + ", "
                        + rr.getPartialTime(PartialTimeIndex.MAIN) + ", " + greedy.getPartialTime(PartialTimeIndex.MAIN) + ", "
                        + ilp.getPartialTime(PartialTimeIndex.LP) + ", " + rr.getPartialTime(PartialTimeIndex.LP));
                writer.newLine();

                writer.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Map<Integer, StatisticalResult> extractNumToAverageTime(
            NumItem numItem,
            Map<String, StatisticalResult> docToStatisticalResult) {
        Map<Integer, StatisticalResult> numToAverageStat = new HashMap<>();
        Map<Integer, List<StatisticalResult>> numToStats = new HashMap<>();
        for (String docId : docToStatisticalResult.keySet()) {
            int num = 0;
            if (numItem == NumItem.NUM_PAIRS)
                num = docToStatisticalResult.get(docId).getNumPairs();
            else if (numItem == NumItem.NUM_PAIRS_EDGES)
                num = docToStatisticalResult.get(docId).getNumPairs()
                        + docToStatisticalResult.get(docId).getNumEdges();

            if (!numToStats.containsKey(num))
                numToStats.put(num, new ArrayList<>());

            numToStats.get(num).add(docToStatisticalResult.get(docId));
        }

        for (Integer num : numToStats.keySet()) {
            List<StatisticalResult> stats = numToStats.get(num);
            double numStats = (double) stats.size();
            StatisticalResult averageStat = new StatisticalResult();
            stats.forEach(stat -> {
                averageStat.increaseRunningTime(stat.getRunningTime());
                averageStat.increasePartialTime(stat);
            });

            averageStat.setRunningTime(
                    Utils.rounding(averageStat.getRunningTime() / numStats,
                            Constants.NUM_DIGITS_IN_TIME));
            for (PartialTimeIndex index : averageStat.getPartialTimes().keySet()) {
                averageStat.getPartialTimes().put(
                        index,
                        Utils.rounding(
                                averageStat.getPartialTimes().get(index) / numStats,
                                Constants.NUM_DIGITS_IN_TIME));
            }

            numToAverageStat.put(num, averageStat);
        }

        return numToAverageStat;
    }

    /**
     * Averaging results into array of size 3
     *
     * @return array of average result for 3 algorithms
     */
    private static StatisticalResult[] summaryStatisticalResultsOfDifferentMethods(
            Map<String, StatisticalResult> docToStatisticalResultGreedy,
            Map<String, StatisticalResult> docToStatisticalResultILP,
            Map<String, StatisticalResult> docToStatisticalResultRR) {

        double count = 0.0f;
        double rrCount = 0.0f;
        double[] costSum = new double[]{0.0f, 0.0f, 0.0f};
        double[] runningTimeSum = new double[]{0, 0, 0};
        double[] setupTimeSum = new double[]{0, 0, 0};
        double[] mainTimeSum = new double[]{0, 0, 0};
        double[] getKTimeSum = new double[]{0, 0, 0};
        double rrTime = 0;
        for (String docId : docToStatisticalResultGreedy.keySet()) {
            if (docToStatisticalResultILP.containsKey(docId)
                    && docToStatisticalResultRR.containsKey(docId)) {

                ++count;
                costSum[GREEDY_INDEX] += docToStatisticalResultGreedy.get(docId).getFinalCost();
                costSum[ILP_INDEX] += docToStatisticalResultILP.get(docId).getFinalCost();
                costSum[RR_INDEX] += docToStatisticalResultRR.get(docId).getFinalCost();

                runningTimeSum[GREEDY_INDEX] += docToStatisticalResultGreedy.get(docId).getRunningTime();
                runningTimeSum[ILP_INDEX] += docToStatisticalResultILP.get(docId).getRunningTime();
                runningTimeSum[RR_INDEX] += docToStatisticalResultRR.get(docId).getRunningTime();

                setupTimeSum[GREEDY_INDEX]
                        += docToStatisticalResultGreedy.get(docId).getPartialTime(PartialTimeIndex.SETUP);
                setupTimeSum[ILP_INDEX]
                        += docToStatisticalResultILP.get(docId).getPartialTime(PartialTimeIndex.SETUP);
                setupTimeSum[RR_INDEX]
                        += docToStatisticalResultRR.get(docId).getPartialTime(PartialTimeIndex.SETUP);

                mainTimeSum[GREEDY_INDEX]
                        += docToStatisticalResultGreedy.get(docId).getPartialTime(PartialTimeIndex.MAIN);
                mainTimeSum[ILP_INDEX]
                        += docToStatisticalResultILP.get(docId).getPartialTime(PartialTimeIndex.MAIN);
                mainTimeSum[RR_INDEX]
                        += docToStatisticalResultRR.get(docId).getPartialTime(PartialTimeIndex.MAIN);

                getKTimeSum[GREEDY_INDEX]
                        += docToStatisticalResultGreedy.get(docId).getPartialTime(PartialTimeIndex.GET_TOPK);
                getKTimeSum[ILP_INDEX]
                        += docToStatisticalResultILP.get(docId).getPartialTime(PartialTimeIndex.GET_TOPK);
                getKTimeSum[RR_INDEX]
                        += docToStatisticalResultRR.get(docId).getPartialTime(PartialTimeIndex.GET_TOPK);

                if (docToStatisticalResultRR.get(docId).getPartialTime(PartialTimeIndex.RR) > 0) {
                    rrCount++;
                    rrTime += docToStatisticalResultRR.get(docId).getPartialTime(PartialTimeIndex.RR);
                }
            }
        }

        StatisticalResult[] statisticalResults = new StatisticalResult[3];
        statisticalResults[GREEDY_INDEX] = new StatisticalResult(
                k, threshold,
                costSum[GREEDY_INDEX] / count,
                Utils.rounding(runningTimeSum[GREEDY_INDEX] / count, Constants.NUM_DIGITS_IN_TIME));
        statisticalResults[GREEDY_INDEX].addPartialTime(PartialTimeIndex.SETUP,
                Utils.rounding(setupTimeSum[GREEDY_INDEX] / count, Constants.NUM_DIGITS_IN_TIME));
        statisticalResults[GREEDY_INDEX].addPartialTime(PartialTimeIndex.MAIN,
                Utils.rounding(mainTimeSum[GREEDY_INDEX] / count, Constants.NUM_DIGITS_IN_TIME));
        statisticalResults[GREEDY_INDEX].addPartialTime(PartialTimeIndex.GET_TOPK,
                Utils.rounding(getKTimeSum[GREEDY_INDEX] / count, Constants.NUM_DIGITS_IN_TIME));

        statisticalResults[ILP_INDEX] = new StatisticalResult(k, threshold, costSum[ILP_INDEX] / count,
                Utils.rounding(runningTimeSum[ILP_INDEX] / count, Constants.NUM_DIGITS_IN_TIME));
        statisticalResults[ILP_INDEX].addPartialTime(PartialTimeIndex.SETUP,
                Utils.rounding(setupTimeSum[ILP_INDEX] / count, Constants.NUM_DIGITS_IN_TIME));
        statisticalResults[ILP_INDEX].addPartialTime(PartialTimeIndex.MAIN,
                Utils.rounding(mainTimeSum[ILP_INDEX] / count, Constants.NUM_DIGITS_IN_TIME));
        statisticalResults[ILP_INDEX].addPartialTime(PartialTimeIndex.GET_TOPK,
                Utils.rounding(getKTimeSum[ILP_INDEX] / count, Constants.NUM_DIGITS_IN_TIME));

        statisticalResults[RR_INDEX] = new StatisticalResult(k, threshold, costSum[RR_INDEX] / count,
                Utils.rounding(runningTimeSum[RR_INDEX] / count, Constants.NUM_DIGITS_IN_TIME));
        statisticalResults[RR_INDEX].addPartialTime(PartialTimeIndex.SETUP,
                Utils.rounding(setupTimeSum[RR_INDEX] / count, Constants.NUM_DIGITS_IN_TIME));
        statisticalResults[RR_INDEX].addPartialTime(PartialTimeIndex.MAIN,
                Utils.rounding(mainTimeSum[RR_INDEX] / count, Constants.NUM_DIGITS_IN_TIME));
        statisticalResults[RR_INDEX].addPartialTime(PartialTimeIndex.GET_TOPK,
                Utils.rounding(getKTimeSum[RR_INDEX] / count, Constants.NUM_DIGITS_IN_TIME));
        if (rrCount >= 1)
            statisticalResults[RR_INDEX].addPartialTime(PartialTimeIndex.RR,
                    Utils.rounding(rrTime / count, Constants.NUM_DIGITS_IN_TIME));

        return statisticalResults;
    }

    private static void outputSummaryStatisticsToCSV(
            List<StatisticalResult[]> statisticalResults,
            String finalOutputFolder, String fileNamePrefix
    ) throws IOException {
        if (!Files.exists(Paths.get(finalOutputFolder))) {
            Files.createDirectory(Paths.get(finalOutputFolder));
        }

        Map<Float, List<StatisticalResult[]>> thresholdToStatisticalResults = new HashMap<>();
        statisticalResults.forEach(statistics -> {
            Float threshold = statistics[GREEDY_INDEX].getThreshold();
            if (!thresholdToStatisticalResults.containsKey(threshold))
                thresholdToStatisticalResults.put(threshold, new ArrayList<>());
            thresholdToStatisticalResults.get(threshold).add(statistics);
        });

        for (Float threshold : thresholdToStatisticalResults.keySet()) {
            int modifiedThresholdForLatexName = (int) (threshold * 10);
            Path file = Paths.get(finalOutputFolder,
                fileNamePrefix + "_" + modifiedThresholdForLatexName + ".csv");
            String content = prepareCSVSummary(thresholdToStatisticalResults.get(threshold));

            BufferedWriter writer = Files.newBufferedWriter(file,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            writer.write(content);
            writer.flush();
        }

        System.err.println("Summaries was exported to \"" + finalOutputFolder + "\"");
    }

    private static String prepareCSVSummary(List<StatisticalResult[]> resultsList) {
        StringBuilder content = new StringBuilder(
                "k, ILP, RR, Greedy, RR Time Diff, Greedy Time Diff,"
                + "ILP, RR, Greedy, RR Cost Diff, Greedy Cost Diff, "
                + "ILP Setup, ILP Main, ILP GetK, RR Setup, RR Main, RR GetK, RR rr, "
                + "Greedy Setup, Greedy Main, Greedy GetK\n");
        int i = 0;
        if (resultsList.size() >= 2
                && resultsList.get(0)[GREEDY_INDEX].getK() == resultsList.get(1)[GREEDY_INDEX].getK())
            i = 1;
        for (; i < resultsList.size(); ++i) {
            StatisticalResult[] stats = resultsList.get(i);
            double ilpTime = stats[ILP_INDEX].getRunningTime();
            double rrTime = stats[RR_INDEX].getRunningTime();
            double greedyTime = stats[GREEDY_INDEX].getRunningTime();
            double rrChangeTime = -Utils.rounding((rrTime - ilpTime) / ilpTime * 100, Constants.NUM_DIGITS_IN_TIME);
            double greedyChangeTime = -Utils.rounding((greedyTime - ilpTime) / ilpTime * 100, Constants.NUM_DIGITS_IN_TIME);

            Map<PartialTimeIndex, Double> ilpPartialTimes = stats[ILP_INDEX].getPartialTimes();
            Map<PartialTimeIndex, Double> rrPartialTimes = stats[RR_INDEX].getPartialTimes();
            Map<PartialTimeIndex, Double> greedyPartialTimes = stats[GREEDY_INDEX].getPartialTimes();

            double ilpCost = stats[ILP_INDEX].getFinalCost();
            double rrCost = stats[RR_INDEX].getFinalCost();
            double greedyCost = stats[GREEDY_INDEX].getFinalCost();
            double rrChangeCost = Utils.rounding((rrCost - ilpCost) / ilpCost * 100, Constants.NUM_DIGITS_IN_TIME);
            double greedyChangeCost = Utils.rounding((greedyCost - ilpCost) / ilpCost * 100, Constants.NUM_DIGITS_IN_TIME);

            String line = resultsList.get(i)[GREEDY_INDEX].getK() + ", "
                    + ilpTime + ", " + rrTime + ", " + greedyTime + ", " + rrChangeTime + " %, " + greedyChangeTime + " %, "
                    + ilpCost + ", " + rrCost + ", " + greedyCost + ", " + rrChangeCost + " %, " + greedyChangeCost + " %, "
                    + ilpPartialTimes.get(PartialTimeIndex.SETUP) + ", " + ilpPartialTimes.get(PartialTimeIndex.MAIN)
                    + ", " + ilpPartialTimes.get(PartialTimeIndex.GET_TOPK) + ", "
                    + rrPartialTimes.get(PartialTimeIndex.SETUP) + ", " + rrPartialTimes.get(PartialTimeIndex.MAIN)
                    + ", " + rrPartialTimes.get(PartialTimeIndex.GET_TOPK) + ", " + rrPartialTimes.get(PartialTimeIndex.RR) + ", "
                    + greedyPartialTimes.get(PartialTimeIndex.SETUP) + ", " + greedyPartialTimes.get(PartialTimeIndex.MAIN)
                    + ", " + greedyPartialTimes.get(PartialTimeIndex.GET_TOPK)
                    + "\n";
            content.append(line);
        }

        return content.toString();
    }
}
