package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants.LPMethod;
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
import edu.ucr.cs.dblab.nle020.utils.Utils;

public class TopPairsProgram {
	
	private static int k = Constants.K;
	private static float threshold = Constants.THRESHOLD;
	
	private static boolean RANDOMIZE_DOCS = false;
	private static Set<Integer> randomIndices = Utils.randomIndices(1000, Constants.NUM_DOCTORS_TO_EXPERIMENT);
	private final static int NUM_DOCTORS_TO_EXPERIMENT = Constants.NUM_DOCTORS_TO_EXPERIMENT;
	
	private static final int ILP_INDEX = 0;
	private static final int RR_INDEX = 1;
	private static final int GREEDY_INDEX = 2;

	public final static String DOC_TO_REVIEWS_PATH = "src/main/resources/doc_pairs_1_prunned_vector.txt";
//	public final static String OUTPUT_FOLDER = "D:\\Experiments\\";
	public final static String OUTPUT_FOLDER = "src/main/resources/performance/";
	
	private enum Algorithm 		{GREEDY, ILP, RANDOMIZED_ROUDNING};
	private enum SetAlgorithm 	{GREEDY_SET, ILP_SET, RANDOMIZED_ROUNDING_SET};
	public static enum SetOption 		{REVIEW, SENTENCE }; 
	
	public static void main(String[] args) throws InterruptedException, ExecutionException, IOException {
		long startTime = System.currentTimeMillis();
//		getDatasetStatistics();
		
		topPairsExperiment();
		topSetsExperiment(SetOption.REVIEW);
		topSetsExperiment(SetOption.SENTENCE);
		
//		topPairsSyntheticExperiment();
		Utils.printRunningTime(startTime, "Finished evaluation");
	}
	
	public static void getDatasetStatistics() {
		String outputPath = "src/main/resources/dataset-statistics.txt";
		String output = "";
				
		Map<Integer, List<SentimentSet>> docToSentimentSets = importDocToSentimentReviews(DOC_TO_REVIEWS_PATH, false);
		List<Integer> counts = new ArrayList<Integer>();
		for (List<SentimentSet> reviews : docToSentimentSets.values()) 
			counts.add(reviews.size());
		output = output + updateStatistics(counts, "#reviews");
		
		docToSentimentSets = importDocToSentimentSentences(DOC_TO_REVIEWS_PATH, false);
		counts = new ArrayList<Integer>();
		for (List<SentimentSet> sentences : docToSentimentSets.values()) 
			counts.add(sentences.size());
		output = output + updateStatistics(counts, "#sentences");
		docToSentimentSets = null;
		
		Map<Integer, List<ConceptSentimentPair>> docToPairs = importDocToConceptSentimentPairs(DOC_TO_REVIEWS_PATH, false);
		counts = new ArrayList<Integer>();
		for (List<ConceptSentimentPair> pairs : docToPairs.values()) 
			counts.add(pairs.size());
		output = output + updateStatistics(counts, "#pairs");
		docToPairs = null;
		
		// Raw data
		List<RawReview> rawReviews = PairExtractor.getReviews(Constants.REVIEWS_PATH);
		Map<Integer, List<RawReview>> docToRawReviews = new HashMap<Integer, List<RawReview>>();
		for (RawReview rawReview : rawReviews) {
			if (!docToRawReviews.containsKey(rawReview.getDocID()))
				docToRawReviews.put(rawReview.getDocID(), new ArrayList<RawReview>());
			docToRawReviews.get(rawReview.getDocID()).add(rawReview);
		}
		counts = new ArrayList<Integer>();
		for (List<RawReview> reviews : docToRawReviews.values()) 
			counts.add(reviews.size());
		output = output + updateStatistics(counts, "#raw reviews");
		
		Map<RawReview, Integer> rawReviewToSentenceCount = new HashMap<RawReview, Integer>();
		for (RawReview rawReview : rawReviews) {
			int sentenceCount = SentimentCalculator.breakingIntoSentences(rawReview.getBody(), true).size();
			rawReviewToSentenceCount.put(rawReview, sentenceCount);
		}
		counts = new ArrayList<Integer>();
		for (RawReview rawReview : rawReviews) 
			counts.add(rawReviewToSentenceCount.get(rawReview));
		output = output + updateStatistics(counts, "#raw setences/raw review");
		
		Map<Integer, Integer> docToSentenceCount = new HashMap<Integer, Integer>();
		for (RawReview rawReview : rawReviews) {
			int docId = rawReview.getDocID();
			if (!docToSentenceCount.containsKey(docId))
				docToSentenceCount.put(docId, 0);
			docToSentenceCount.put(docId, docToSentenceCount.get(docId) + rawReviewToSentenceCount.get(rawReview));
		}
		counts = new ArrayList<Integer>();
		counts.addAll(docToSentenceCount.values());
		output = output + updateStatistics(counts, "#raw sentences");
		
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath), 
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			writer.write(output);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		System.out.println("Find the output at \"" + outputPath + "\"");
	}
	
	public static String updateStatistics(List<Integer> counts, String heading) {
		double min = 0;
		double max = 0;
		double average = 0;
		
		average = counts.stream().collect(Collectors.averagingDouble(count -> (double) count));
		Collections.sort(counts);
		min = counts.get(0);
		max = counts.get(counts.size() - 1);
		return heading + ": min " + min + ", max " + max + ", average " + average + "\n";		
	}

	private static void topPairsExperiment() throws InterruptedException, ExecutionException, IOException {
		
		List<StatisticalResult[]> statisticalResults = new ArrayList<StatisticalResult[]>();		
		
		// TODO - the first "3" is always slower than the other numbers 
		int[] ks = new int[] {3, 3, 5, 10, 15, 20};
//		int[] ks = new int[] {5};
		float[] thresholds = new float[] {0.1f, 0.3f};
//		float[] thresholds = new float[] {0.3f};
		for (int numChoosen : ks) {
			k = numChoosen;
			for (int thresh = 0; thresh < thresholds.length; ++thresh) {
				threshold = thresholds[thresh];
				
				String subFolder = "Top Pairs\\k" + k + "_threshold" + threshold + "\\";				
				if (!Files.exists(Paths.get(OUTPUT_FOLDER + subFolder)))
					Files.createDirectories(Paths.get(OUTPUT_FOLDER + subFolder));
				
				statisticalResults.add(topPairsExperiment(OUTPUT_FOLDER + subFolder));
			}
		}
		
		outputSummaryStatisticsToCSV(statisticalResults, OUTPUT_FOLDER, "top-pair");
	}

	private static StatisticalResult[] topPairsExperiment(String outputFolder) throws InterruptedException, ExecutionException {
		long startTime = System.currentTimeMillis();		
		Map<Integer, List<ConceptSentimentPair>> docToConceptSentimentPairs = importDocToConceptSentimentPairs(DOC_TO_REVIEWS_PATH, RANDOMIZE_DOCS);
		printInitialization(docToConceptSentimentPairs);
		
		ConcurrentMap<Integer, StatisticalResult> docToStatisticalResultGreedy = new ConcurrentHashMap<Integer, StatisticalResult>();
		ConcurrentMap<Integer, StatisticalResult> docToStatisticalResultILP = new ConcurrentHashMap<Integer, StatisticalResult>();
		ConcurrentMap<Integer, StatisticalResult> docToStatisticalResultRR = new ConcurrentHashMap<Integer, StatisticalResult>();
				
		ConcurrentMap<Integer, List<ConceptSentimentPair>> docToTopKPairsResultGreedy = new ConcurrentHashMap<Integer, List<ConceptSentimentPair>>();
		ConcurrentMap<Integer, List<ConceptSentimentPair>> docToTopKPairsResultILP = new ConcurrentHashMap<Integer, List<ConceptSentimentPair>>();
		ConcurrentMap<Integer, List<ConceptSentimentPair>> docToTopKPairsResultRR = new ConcurrentHashMap<Integer, List<ConceptSentimentPair>>();
						
		String outputPrefix = outputFolder + "top_pairs_result_" + NUM_DOCTORS_TO_EXPERIMENT;
//		importResultFromJson(outputPrefix + "_ilp.txt", docToTopPairsResultILP);
//		importResultFromJson(outputPrefix + "_greedy.txt", docToTopPairsResultGreedy);
//		importResultFromJson(outputPrefix + "_rr.txt", docToTopPairsResultRR);
				
		runTopPairsAlgoMultiThreads(Algorithm.GREEDY, Constants.NUM_THREADS_ALGORITHM, docToConceptSentimentPairs, 
				docToStatisticalResultGreedy, docToTopKPairsResultGreedy);
		outputStatisticalResultToJson(outputPrefix + "_greedy.txt", docToStatisticalResultGreedy);
		outputTopKToJson(outputPrefix + "_greedy_pair.txt", docToTopKPairsResultGreedy);
		
		runTopPairsAlgoMultiThreads(Algorithm.ILP, Constants.NUM_THREADS_ALGORITHM, docToConceptSentimentPairs, 
				docToStatisticalResultILP, docToTopKPairsResultILP);
		outputStatisticalResultToJson(outputPrefix + "_ilp.txt", docToStatisticalResultILP);
		outputTopKToJson(outputPrefix + "_ilp_pair.txt", docToTopKPairsResultILP);
		
		if (Constants.FIND_BEST_LP_METHOD) {
			LPMethod[] methods = new LPMethod[] {LPMethod.AUTOMATIC, LPMethod.BARRIER, 
					LPMethod.CONCURRENT, LPMethod.DETERMINISTIC_CONCURRENT,
					LPMethod.DUAL_SIMPLEX, LPMethod.PRIMAL_SIMPLEX};
			Map<LPMethod, Long> methodToTime = new HashMap<LPMethod, Long>();
			for (LPMethod method : methods) {		
				long startTime2 = System.currentTimeMillis();
				runTopPairsAlgoMultiThreads(Algorithm.RANDOMIZED_ROUDNING, Constants.NUM_THREADS_ALGORITHM, 
						docToConceptSentimentPairs, docToStatisticalResultRR, docToTopKPairsResultRR, method);
				outputStatisticalResultToJson(outputPrefix + "_rr.txt", docToStatisticalResultRR);

				methodToTime.put(method, System.currentTimeMillis() - startTime2);
			}
			LPMethod bestMethod = null;
			long min = Long.MAX_VALUE;
			for (LPMethod method : methodToTime.keySet()) {
				if (methodToTime.get(method) < min) {
					min = methodToTime.get(method);
					bestMethod = method;
				}
			}
			System.err.println("Best LP Method: " + bestMethod + ", number " + bestMethod.method());
		} else {
			runTopPairsAlgoMultiThreads(Algorithm.RANDOMIZED_ROUDNING, Constants.NUM_THREADS_ALGORITHM, 
					docToConceptSentimentPairs, docToStatisticalResultRR, docToTopKPairsResultRR);
			outputStatisticalResultToJson(outputPrefix + "_rr.txt", docToStatisticalResultRR);
			outputTopKToJson(outputPrefix + "_rr_pair.txt", docToTopKPairsResultRR);
		}
		
		String outputPath = outputFolder + "review_diversity_k" + k + "_threshold" + threshold + "_" + NUM_DOCTORS_TO_EXPERIMENT + ".xlsx";
		boolean isSet = false;
		outputStatisticalResultToExcel(outputPath, isSet, docToStatisticalResultGreedy, docToStatisticalResultILP, docToStatisticalResultRR);
						
		Utils.printRunningTime(startTime, "Finished Top Pairs", true);
		
		return summaryStatisticalResultsOfDifferentMethods(docToStatisticalResultGreedy, docToStatisticalResultILP, docToStatisticalResultRR);
	}
	


	@SuppressWarnings("unused")
	private static void topPairsSyntheticExperiment() throws InterruptedException, ExecutionException {
		long startTime = System.currentTimeMillis();
		
		Map<Integer, List<ConceptSentimentPair>> docToConceptSentimentPairs = new HashMap<Integer, List<ConceptSentimentPair>>();		
		ConcurrentMap<Integer, StatisticalResult> docToStatisticalResultGreedy = new ConcurrentHashMap<Integer, StatisticalResult>();
		ConcurrentMap<Integer, StatisticalResult> docToStatisticalResultILP = new ConcurrentHashMap<Integer, StatisticalResult>();
		ConcurrentMap<Integer, StatisticalResult> docToStatisticalResultRR = new ConcurrentHashMap<Integer, StatisticalResult>();
		
		ConcurrentMap<Integer, List<ConceptSentimentPair>> docToTopKPairsResultGreedy = new ConcurrentHashMap<Integer, List<ConceptSentimentPair>>();
		ConcurrentMap<Integer, List<ConceptSentimentPair>> docToTopKPairsResultILP = new ConcurrentHashMap<Integer, List<ConceptSentimentPair>>();
		ConcurrentMap<Integer, List<ConceptSentimentPair>> docToTopKPairsResultRR = new ConcurrentHashMap<Integer, List<ConceptSentimentPair>>();
		
		
		int numDecimals = 1;

		docToConceptSentimentPairs = createSyntheticDataset(
				importDocToConceptSentimentPairs(DOC_TO_REVIEWS_PATH, RANDOMIZE_DOCS), 
				Constants.NUM_SYNTHETIC_DOCTORS, numDecimals);

//		printInitialization(docToConceptSentimentPairs);
		
		String outputPrefix = OUTPUT_FOLDER + "top_pairs_synthetic_result_" + NUM_DOCTORS_TO_EXPERIMENT;
//		importResultFromJson(outputPrefix + "_ilp.txt", docToTopPairsResultILP);
//		importResultFromJson(outputPrefix + "_greedy.txt", docToTopPairsResultGreedy);
//		importResultFromJson(outputPrefix + "_rr.txt", docToTopPairsResultRR);
		
		runTopPairsAlgoMultiThreads(Algorithm.GREEDY, Constants.NUM_THREADS_ALGORITHM, docToConceptSentimentPairs, 
				docToStatisticalResultGreedy, docToTopKPairsResultGreedy);
		outputStatisticalResultToJson(outputPrefix + "_greedy.txt", docToStatisticalResultGreedy);
		
/*		runTopPairsAlgoMultiThreads(Algorithm.ILP, Constants.NUM_THREADS_ALGORITHM, docToConceptSentimentPairs, 
 				docToTopPairsResultILP, docToTopKPairsResultILP);
		outputResultToJson(outputPrefix + "_ilp.txt", docToTopPairsResultILP);*/		

/*		runRandomizedRoundingMultiThreads(Constants.NUM_THREADS_ALGORITHM, docToConceptSentimentPairs, 
 * 				docToTopPairsResultRR, docToTopKPairsResultRR);
		outputResultToJson(outputPrefix + "_rr.txt", docToTopPairsResultRR);*/
		
		String outputPath = OUTPUT_FOLDER + "review_diversity_synthetic_k" + k + "_threshold" + threshold 
				+ "_" + NUM_DOCTORS_TO_EXPERIMENT + ".xlsx";
		boolean isSet = false;
		outputStatisticalResultToExcel(outputPath, isSet, 
				docToStatisticalResultGreedy, docToStatisticalResultILP, docToStatisticalResultRR);
		
		Utils.printRunningTime(startTime, "Finished Top Pairs Synthetic", true);
	}
	
	private static void topSetsExperiment(SetOption setOption) throws IOException {
		List<StatisticalResult[]> statisticalResults = new ArrayList<StatisticalResult[]>();		
		
		int[] ks = new int[] {3, 3, 5, 10, 15, 20};
//		int[] ks = new int[] {5};
		float[] thresholds = new float[] {0.1f, 0.3f};
//		float[] thresholds = new float[] {0.3f};
		for (int numChoosen : ks) {
			k = numChoosen;
			for (int thresh = 0; thresh < thresholds.length; ++thresh) {
				threshold = thresholds[thresh];
				
				String subFolder = "Top " + setOption + "\\k" + k + "_threshold" + threshold + "\\";
				
				if (!Files.exists(Paths.get(OUTPUT_FOLDER + subFolder)))
					Files.createDirectories(Paths.get(OUTPUT_FOLDER + subFolder));

				statisticalResults.add(topSetsExperiment(setOption, OUTPUT_FOLDER + subFolder));
			}
		}
		
		outputSummaryStatisticsToCSV(statisticalResults, OUTPUT_FOLDER, "top-" + setOption.toString().toLowerCase());
	}
	
	private static StatisticalResult[] topSetsExperiment(SetOption setOption, String outputFolder) {
		long startTime = System.currentTimeMillis();		
		Map<Integer, List<SentimentSet>> docToSentimentSets = new HashMap<Integer, List<SentimentSet>>();		
		
		ConcurrentMap<Integer, StatisticalResult> docToStatisticalResultGreedy = new ConcurrentHashMap<Integer, StatisticalResult>();
		ConcurrentMap<Integer, StatisticalResult> docToStatisticalResultILP = new ConcurrentHashMap<Integer, StatisticalResult>();
		ConcurrentMap<Integer, StatisticalResult> docToStatisticalResultRR = new ConcurrentHashMap<Integer, StatisticalResult>();
		
		ConcurrentMap<Integer, List<SentimentSet>> docToTopKSetsGreedy = new ConcurrentHashMap<Integer, List<SentimentSet>>();
		ConcurrentMap<Integer, List<SentimentSet>> docToTopKSetsILP = new ConcurrentHashMap<Integer, List<SentimentSet>>();
		ConcurrentMap<Integer, List<SentimentSet>> docToTopKSetsRR = new ConcurrentHashMap<Integer, List<SentimentSet>>();
		
		switch (setOption) {
		case REVIEW: 	
			docToSentimentSets = importDocToSentimentReviews(DOC_TO_REVIEWS_PATH, RANDOMIZE_DOCS);
			break;
		case SENTENCE:  
			docToSentimentSets = importDocToSentimentSentences(DOC_TO_REVIEWS_PATH, RANDOMIZE_DOCS);
			break;
		default: 		
			docToSentimentSets = importDocToSentimentReviews(DOC_TO_REVIEWS_PATH, RANDOMIZE_DOCS);
			break;
		}			

		String outputPrefix = outputFolder + "top_" + setOption + "_result_" + NUM_DOCTORS_TO_EXPERIMENT;
//		importResultFromJson(outputPrefix + "_ilp.txt", docToStatisticalResultILP);
//		importResultFromJson(outputPrefix + "_greedy.txt", docToStatisticalResultGreedy);
//		importResultFromJson(outputPrefix + "_rr.txt", docToStatisticalResultRR);
				
		runTopSetsAlgoMultiThreads(SetAlgorithm.GREEDY_SET, Constants.NUM_THREADS_ALGORITHM, docToSentimentSets, 
				docToStatisticalResultGreedy, docToTopKSetsGreedy);
		outputStatisticalResultToJson(outputPrefix + "_greedy.txt",	docToStatisticalResultGreedy);
		outputTopKToJson(outputPrefix + "_greedy_set.txt", convertTopKSetsMapToSetResultMap(docToTopKSetsGreedy));
		
		runTopSetsAlgoMultiThreads(SetAlgorithm.ILP_SET, Constants.NUM_THREADS_ALGORITHM, docToSentimentSets, 
				docToStatisticalResultILP, docToTopKSetsILP);
		outputStatisticalResultToJson(outputPrefix + "_ilp.txt", docToStatisticalResultILP);
		outputTopKToJson(outputPrefix + "_ilp_set.txt", convertTopKSetsMapToSetResultMap(docToTopKSetsILP));
		
		runTopSetsAlgoMultiThreads(SetAlgorithm.RANDOMIZED_ROUNDING_SET, Constants.NUM_THREADS_ALGORITHM, docToSentimentSets, 
				docToStatisticalResultRR, docToTopKSetsRR);
		outputStatisticalResultToJson(outputPrefix + "_rr.txt",	docToStatisticalResultRR);
		outputTopKToJson(outputPrefix + "_rr_set.txt", convertTopKSetsMapToSetResultMap(docToTopKSetsRR));
				
		String outputPath = outputFolder + "review_diversity_" + setOption + 
										"_k" + k + "_threshold" + threshold + "_" + NUM_DOCTORS_TO_EXPERIMENT + ".xlsx";
		boolean isSet = true;
		outputStatisticalResultToExcel(outputPath, isSet, docToStatisticalResultGreedy, docToStatisticalResultILP, docToStatisticalResultRR);
		
		Utils.printRunningTime(startTime, "Finished Top " + setOption, true);		

		return summaryStatisticalResultsOfDifferentMethods(docToStatisticalResultGreedy, docToStatisticalResultILP, docToStatisticalResultRR);
	}
			
	private static ConcurrentMap<Integer, List<SetResult>> convertTopKSetsMapToSetResultMap(
			ConcurrentMap<Integer, List<SentimentSet>> docToTopSentimentSets) {
		ConcurrentMap<Integer, List<SetResult>> docToSetResults = new ConcurrentHashMap<Integer, List<SetResult>>();
		for (Integer docId : docToTopSentimentSets.keySet()) {
			List<SetResult> setResults = new ArrayList<SetResult>();
			for (SentimentSet set : docToTopSentimentSets.get(docId)) {				
				if (set.getClass() == SentimentSentence.class) {
					SentimentSentence sentence = (SentimentSentence) set;
					setResults.add(new SetResult(sentence.getId(), sentence.getSentence(), sentence.getPairs()));
				} else if (set.getClass() == SentimentReview.class){
					SentimentReview sentence = (SentimentReview) set;
					setResults.add(new SetResult(sentence.getId(), sentence.getRawReview().getBody(), sentence.getPairs()));
				}				
			}
			docToSetResults.put(docId, setResults);
		}
		
		return docToSetResults;
	}

	private static void printInitialization(Map<Integer, List<ConceptSentimentPair>> docToConceptSentimentPairs) {
		int maxNumPairs = 0;
		int maxDocID = 0;
		for (Map.Entry<Integer, List<ConceptSentimentPair>> entry : docToConceptSentimentPairs.entrySet()) {
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
	 * @param algorithm - GREEDY, ILP, RANDOMIZED ROUNDING
	 * @param numThreadsAlgorithm - # threads
	 * @param docToConceptSentimentPairs - map from doctor's id to ConceptSentimentPairs 
	 * @param docToTopPairsResultILP - map
	 * @param method - choosing LP solver. Note that this doesn't affect GREEDY, ILP, only affect Randomized Rounding
	 */
	private static void runTopPairsAlgoMultiThreads(
			Algorithm algorithm,
			int numThreadsAlgorithm,
			Map<Integer, List<ConceptSentimentPair>> docToConceptSentimentPairs,
			ConcurrentMap<Integer, StatisticalResult> docToStatisticalResult,
			ConcurrentMap<Integer, List<ConceptSentimentPair>> docToTopKPairsResult,
			LPMethod method) {
		
		long startTime = System.currentTimeMillis();
		
		ExecutorService fixedPool = Executors.newFixedThreadPool(numThreadsAlgorithm);
		List<Future<String>> futures = new ArrayList<Future<String>>();
		
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
			case RANDOMIZED_ROUDNING:
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

			Utils.printRunningTime(startTime, algorithm + " there are " + getUnfinishedJobNum(futures, "DONE!") + " unfinished jobs", true);			
			Utils.printRunningTime(startTime, algorithm + " finished " + docToStatisticalResult.size() + " doctors");
		} catch (InterruptedException e) {
			e.printStackTrace();

			Utils.printRunningTime(startTime, algorithm + " there are " + getUnfinishedJobNum(futures, "DONE!") + " unfinished jobs", true);			
			Utils.printRunningTime(startTime, algorithm + " finished " + docToStatisticalResult.size() + " doctors");
		}
	}
	
	// @param method - choosing LP solver. Note that this doesn't affect GREEDY, ILP, only affect Randomized Rounding
	private static void runTopPairsAlgoMultiThreads(
			Algorithm algorithm,
			int numThreadsAlgorithm,
			Map<Integer, List<ConceptSentimentPair>> docToConceptSentimentPairs,
			ConcurrentMap<Integer, StatisticalResult> docToStatisticalResult,
			ConcurrentMap<Integer, List<ConceptSentimentPair>> docToTopKPairsResult) {
		runTopPairsAlgoMultiThreads(algorithm, numThreadsAlgorithm, docToConceptSentimentPairs, docToStatisticalResult, docToTopKPairsResult, 
				Constants.MY_DEFAULT_LP_METHOD);
	}
	
	/**
	 * SentimentSet such as SentimentReview, SentimentSentence
	 * @param setAlgorithm - greedy or ilp or randomized rounding
	 * @param numThreadsAlgorithm - # threads
	 * @param docToSentimentSets - map from doctor's id to sentiment sets 
	 * @param docToTopPairsResultILP - map
	 */
	private static void runTopSetsAlgoMultiThreads(
			SetAlgorithm setAlgorithm,
			int numThreadsAlgorithm,
			Map<Integer, List<SentimentSet>> docToSentimentSets,
			ConcurrentMap<Integer, StatisticalResult> docToStatisticalResult,
			ConcurrentMap<Integer, List<SentimentSet>> docToTopKSetsResult) {
		long startTime = System.currentTimeMillis();

		ExecutorService fixedPool = Executors.newFixedThreadPool(numThreadsAlgorithm);
		List<Future<String>> futures = new ArrayList<Future<String>>();

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

			Utils.printRunningTime(startTime, setAlgorithm + " there are " + getUnfinishedJobNum(futures, "DONE!") + " unfinished jobs", true);			
			Utils.printRunningTime(startTime, setAlgorithm + " finished " + docToStatisticalResult.size() + " doctors");
		} catch (InterruptedException e) {
			e.printStackTrace();

			Utils.printRunningTime(startTime, setAlgorithm + " there are " + getUnfinishedJobNum(futures, "DONE!") + " unfinished jobs", true);			
			Utils.printRunningTime(startTime, setAlgorithm + " finished " + docToStatisticalResult.size() + " doctors");
		}
	}
	
	private static int getUnfinishedJobNum(List<Future<String>> futures, String expectedResult) {
		int unfinished = 0;
		for (Future<String> job : futures) {
			try {
				if (job.get() == null || !job.get().equals(expectedResult)) {
					++unfinished;
				}
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		}
		
		return unfinished;
	}
	
	private static void outputStatisticalResultToJson(String outputPath, Map<Integer, StatisticalResult> docToStatisticalResult) {
		long startTime = System.currentTimeMillis();
		ObjectMapper mapper = new ObjectMapper();
		
		try {
			Files.deleteIfExists(Paths.get(outputPath));
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		boolean isFirstLine = true;
		for (Integer docID : docToStatisticalResult.keySet()) {
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
	
	public static <T> void  outputTopKToJson(
			String outputPath,
			Map<Integer, List<T>> docToTopKResult) {
		long startTime = System.currentTimeMillis();
		ObjectMapper mapper = new ObjectMapper();
		
		try {
			Files.deleteIfExists(Paths.get(outputPath));
		} catch (IOException e) {
			e.printStackTrace();
		}		
		
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath),
				StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
			mapper.writeValue(writer, docToTopKResult);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		Utils.printRunningTime(startTime, "Outputed " + docToTopKResult.size() + " topK to " + outputPath);
	}

	@SuppressWarnings("unused")
	private static void importStatisticalResultFromJson(String path, Map<Integer, StatisticalResult> docToStatisticalResult) {
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
			String outputPath, boolean isSet,
			ConcurrentMap<Integer, StatisticalResult> docToStatisticalResultGreedy,
			ConcurrentMap<Integer, StatisticalResult> docToStatisticalResultILP,
			ConcurrentMap<Integer, StatisticalResult> docToStatisticalResultRR){		

		Workbook wb = new XSSFWorkbook();
		Sheet sheet = wb.createSheet("Top Pairs Results");
		addHeader(sheet, isSet, "ILP", "Greedy", "RR");
		
		int count = 1;
		for (Integer docID : docToStatisticalResultGreedy.keySet()) {
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
			fileOut = new FileOutputStream(outputPath);
			
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
		System.err.println("Outputed top pairs to \"" + outputPath + "\"");
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
			double greedyRatio = greedyResult.getFinalCost()/ilpResult.getFinalCost() - 1;
			
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
				double rrRatio = rrResult.getFinalCost()/ilpResult.getFinalCost() - 1;

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
	private static Map<Integer, List<ConceptSentimentPair>> importDocToConceptSentimentPairs(String path, boolean getSomeRandomItems) {
		Map<Integer, List<ConceptSentimentPair>> result = new HashMap<Integer, List<ConceptSentimentPair>>();
		
		List<DoctorSentimentReview> doctorSentimentReviews = importDoctorSentimentReviewsDataset(path);

		Set<Integer> indices = new HashSet<Integer>();
		if (getSomeRandomItems)
			indices = randomIndices;
		else {
			for (int index = 0; index < NUM_DOCTORS_TO_EXPERIMENT; ++index) {
				indices.add(index);
			}
		}
		
		for (Integer index : indices) {
			DoctorSentimentReview doctorSentimentReview = doctorSentimentReviews.get(index);			
			Integer docId = doctorSentimentReview.getDocId();
			List<ConceptSentimentPair> pairs = new ArrayList<ConceptSentimentPair>();
			
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
	public static Map<Integer, List<SentimentSet>> importDocToSentimentReviews(String path, boolean getSomeRandomItems) {
		Map<Integer, List<SentimentSet>> result = new HashMap<Integer, List<SentimentSet>>();
		
		List<DoctorSentimentReview> doctorSentimentReviews = importDoctorSentimentReviewsDataset(path);
		
		Set<Integer> indices = new HashSet<Integer>();
		if (getSomeRandomItems)
			indices = randomIndices;
		else {
			for (int index = 0; index < NUM_DOCTORS_TO_EXPERIMENT; ++index) {
				indices.add(index);
			}
		}
		
		for (Integer index : indices) {
			DoctorSentimentReview doctorSentimentReview = doctorSentimentReviews.get(index);
			Integer docId = doctorSentimentReview.getDocId();
			List<SentimentSet> sentimentReviews = new ArrayList<SentimentSet>();
			
			for (SentimentReview sentimentReview : doctorSentimentReview.getSentimentReviews()) {
				List<ConceptSentimentPair> pairs = new ArrayList<ConceptSentimentPair>();
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
	public static Map<Integer, List<SentimentSet>> importDocToSentimentSentences(String path, boolean getSomeRandomItems) {
		Map<Integer, List<SentimentSet>> result = new HashMap<Integer, List<SentimentSet>>();

		List<DoctorSentimentReview> doctorSentimentReviews = importDoctorSentimentReviewsDataset(path);		
		Set<Integer> indices = new HashSet<Integer>();
		if (getSomeRandomItems)
			indices = randomIndices;
		else {
			for (int index = 0; index < NUM_DOCTORS_TO_EXPERIMENT; ++index) {
				indices.add(index);
			}
		}
		
		for (Integer index : indices) {
			DoctorSentimentReview doctorSentimentReview = doctorSentimentReviews.get(index);

			Integer docId = doctorSentimentReview.getDocId();
			List<SentimentSet> sentimentSentences = new ArrayList<SentimentSet>();

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
	
	public static List<DoctorSentimentReview> importDoctorSentimentReviewsDataset(String path) {
		List<DoctorSentimentReview> doctorSentimentReviews = new ArrayList<DoctorSentimentReview>();
		ObjectMapper mapper = new ObjectMapper();
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(path))) {	
			String line;
			while ((line = reader.readLine()) != null) {
				DoctorSentimentReview doctorSentimentReview = mapper.readValue(line, DoctorSentimentReview.class);
				doctorSentimentReviews.add(doctorSentimentReview);
			}
		} catch (IOException e) {
			e.printStackTrace();			
		}
		return doctorSentimentReviews;
	}
	
	// Create synthetic dataset
	private static Map<Integer, List<ConceptSentimentPair>> createSyntheticDataset(
			Map<Integer, List<ConceptSentimentPair>> docToConceptSentimentPairs,
			int numDocs,
			int numDecimals) {
		int forRounding = (int) Math.pow(10, numDecimals);
		
		Map<Integer, List<ConceptSentimentPair>> result = new HashMap<Integer, List<ConceptSentimentPair>>();
		Map<Integer, Set<ConceptSentimentPair>> temp = new HashMap<Integer, Set<ConceptSentimentPair>>();
		
		for (int i = 0; i < numDocs; ++i) {
			temp.put(i, new HashSet<ConceptSentimentPair>());
		}
		
		int count = 0;
		for (Integer docID : docToConceptSentimentPairs.keySet()) {
			int index = count % numDocs;			
			/*temp.get(index).addAll(docToConceptSentimentPairs.get(docID));*/
			
			for (ConceptSentimentPair pair : docToConceptSentimentPairs.get(docID)) {
				pair.setSentiment((float) Math.round(pair.getSentiment() * forRounding) / forRounding);
				temp.get(index).add(pair);
			}
		}
		
		for (int i = 0; i < numDocs; ++i) {
			result.put(i, new ArrayList<ConceptSentimentPair>());
			result.get(i).addAll(temp.get(i));
		}
		temp = null;
		
		return result;
	}
	
	private static StatisticalResult[] summaryStatisticalResultsOfDifferentMethods(
			ConcurrentMap<Integer, StatisticalResult> docToStatisticalResultGreedy,
			ConcurrentMap<Integer, StatisticalResult> docToStatisticalResultILP,
			ConcurrentMap<Integer, StatisticalResult> docToStatisticalResultRR) {
				
		double count = 0.0f;
		double[] costSum = new double[] {0.0f, 0.0f, 0.0f};
		double[] runningTimeSum = new double[] {0, 0, 0};
		for (Integer docId : docToStatisticalResultGreedy.keySet()) {
			if (docToStatisticalResultILP.containsKey(docId) && docToStatisticalResultRR.containsKey(docId)) {
				++count;
				costSum[GREEDY_INDEX] 	+= docToStatisticalResultGreedy.get(docId).getFinalCost();
				costSum[ILP_INDEX] 		+= docToStatisticalResultILP.get(docId).getFinalCost();
				costSum[RR_INDEX]		+= docToStatisticalResultRR.get(docId).getFinalCost();
				
				runningTimeSum[GREEDY_INDEX] 	+= docToStatisticalResultGreedy.get(docId).getRunningTime();
				runningTimeSum[ILP_INDEX] 		+= docToStatisticalResultILP.get(docId).getRunningTime();
				runningTimeSum[RR_INDEX]		+= docToStatisticalResultRR.get(docId).getRunningTime();
			}
		}
		
		StatisticalResult[] statisticalResults = new StatisticalResult[3];
		statisticalResults[GREEDY_INDEX] 	= 
				new StatisticalResult(k, threshold, costSum[GREEDY_INDEX] / count, 	
						Utils.rounding(runningTimeSum[GREEDY_INDEX] / count, Constants.NUM_DIGITS_IN_TIME));
		statisticalResults[ILP_INDEX] 		= 
				new StatisticalResult(k, threshold, costSum[ILP_INDEX] / count, 
						Utils.rounding(runningTimeSum[ILP_INDEX] / count, Constants.NUM_DIGITS_IN_TIME));
		statisticalResults[RR_INDEX] 		= 
				new StatisticalResult(k, threshold, costSum[RR_INDEX] / count, 
						Utils.rounding(runningTimeSum[RR_INDEX] / count, Constants.NUM_DIGITS_IN_TIME));
		
		return statisticalResults;
	}
	
	private static void outputSummaryStatisticsToCSV(
			List<StatisticalResult[]> statisticalResults, String finalOutputFolder, String fileNamePrefix) {
		
		Map<Float, List<StatisticalResult[]>> thresholdToStatisticalResults = new HashMap<Float, List<StatisticalResult[]>>();
		statisticalResults.stream().forEach(statistics -> {
			Float threshold = statistics[GREEDY_INDEX].getThreshold();
			if (!thresholdToStatisticalResults.containsKey(threshold))
				thresholdToStatisticalResults.put(threshold, new ArrayList<StatisticalResult[]>());
			thresholdToStatisticalResults.get(threshold).add(statistics);
			});
		
		for (Float threshold : thresholdToStatisticalResults.keySet()) {
			int modifiedThresholdForLatexName = (int) (threshold * 10);
			String fileName = finalOutputFolder + fileNamePrefix + "-" + modifiedThresholdForLatexName + ".csv"; 
			String content = prepareCSVSummary(thresholdToStatisticalResults.get(threshold));
			
			try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(fileName), 
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
				
				writer.write(content);
				writer.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		System.err.println("Summaries was outputed to \"" + finalOutputFolder + "\"");
	}
	
	private static String prepareCSVSummary(List<StatisticalResult[]> resultsList) {
		String content = "#k, ILP Time (ms), RR Time, Greedy Time, RR Time Diff, Greedy Time Diff,"
				+ "ILP Cost, RR Cost, Greedy Cost, RR Cost Diff, Greedy Cost Diff \n";
		content = content + "k, ILP, RR, Greedy, RR Time Diff, Greedy Time Diff,"
				+ "ILP, RR, Greedy, RR Cost Diff, Greedy Cost Diff \n";
		
		int i = 0;
		if (resultsList.get(0)[GREEDY_INDEX].getK() == resultsList.get(1)[GREEDY_INDEX].getK())
			i = 1;
		for ( ; i < resultsList.size(); ++i) {
			StatisticalResult[] stats = resultsList.get(i);
			double ilpTime = stats[ILP_INDEX].getRunningTime();
			double rrTime = stats[RR_INDEX].getRunningTime();
			double greedyTime = stats[GREEDY_INDEX].getRunningTime();
			double rrChangeTime = - Utils.rounding((rrTime - ilpTime) / ilpTime * 100, Constants.NUM_DIGITS_IN_TIME);
			double greedyChangeTime = - Utils.rounding((greedyTime - ilpTime) / ilpTime * 100, Constants.NUM_DIGITS_IN_TIME);
			
			double ilpCost = stats[ILP_INDEX].getFinalCost();
			double rrCost = stats[RR_INDEX].getFinalCost();
			double greedyCost = stats[GREEDY_INDEX].getFinalCost();
			double rrChangeCost = Utils.rounding((rrCost - ilpCost) / ilpCost * 100, Constants.NUM_DIGITS_IN_TIME);
			double greedyChangeCost = Utils.rounding((greedyCost - ilpCost) / ilpCost * 100, Constants.NUM_DIGITS_IN_TIME);
			
			content = content + resultsList.get(i)[GREEDY_INDEX].getK() + ", " 
					+ ilpTime + ", " + rrTime + ", " + greedyTime + ", " + rrChangeTime + " %, " + greedyChangeTime + " %, "
					+ ilpCost + ", " + rrCost + ", " + greedyCost + ", " + rrChangeCost + " %, " + greedyChangeCost + " %\n";
		}
		
		return content;
	}
}
