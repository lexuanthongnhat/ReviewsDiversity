package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
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
import edu.ucr.cs.dblab.nle020.reviewsdiversity.composite.GreedySetAlgorithm1;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.composite.GreedySetAlgorithm2;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.composite.ILPSetAlgorithm1;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.composite.ILPSetAlgorithm2;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.composite.RandomizedRoundingSet1;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.composite.RandomizedRoundingSet2;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset.DoctorSentimentReview;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentReview;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSentence;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;
import edu.ucr.cs.dblab.nle020.utilities.Utils;

public class TopPairsProgram {

	private static int k = Constants.K;
	private static float threshold = Constants.THRESHOLD;
		
	private final static String DOC_TO_REVIEWS_PATH = "D:\\UCR Google Drive\\GD - Review Diversity\\doc_pairs_1_latest.txt";
	private final static String OUTPUT_FOLDER = "D:\\UCR Google Drive\\GD - Review Diversity\\Experiment Output\\";
	private final static String DESKTOP_FOLDER;	
	static {
		if (Files.isDirectory(Paths.get("C:\\Users\\Thong Nhat\\Desktop")))
			DESKTOP_FOLDER = "C:\\Users\\Thong Nhat\\Desktop\\";
		 else 
			DESKTOP_FOLDER = "C:\\Users\\Nhat XT Le\\Desktop\\";
	}
	
	private enum Algorithm 		{GREEDY, ILP, RANDOMIZED_ROUDNING};
	private enum SetAlgorithm 	{GREEDY_SET, ILP_SET, RANDOMIZED_ROUNDING_SET};
	private enum SetOption 		{REVIEW, SENTENCE }; 
	
	public static void main(String[] args) throws InterruptedException, ExecutionException {
//		topPairsExperiment();
//		topPairsSyntheticExperiment();
			
//		topSetsExperiment(SetOption.REVIEW);
		topSetsExperiment(SetOption.SENTENCE);
	}

	@SuppressWarnings("unused")
	private static void topPairsExperiment() throws InterruptedException, ExecutionException {
		
//		int[] ks = new int[] {3, 5, 10, 15, 20};
		int[] ks = new int[] {30};
		float[] thresholds = new float[] {0.1f, 0.2f, 0.3f};
//		float[] thresholds = new float[] {0.3f};
		for (int numChoosen : ks) {
			k = numChoosen;
			for (int thresh = 0; thresh < thresholds.length; ++thresh) {
				threshold = thresholds[thresh];
				
				String subFolder = "Top Pairs\\k" + k + "_threshold" + threshold + "\\";
				
				if (!Files.exists(Paths.get(OUTPUT_FOLDER + subFolder))) {
					try {
						Files.createDirectories(Paths.get(OUTPUT_FOLDER + subFolder));
					} catch (IOException e) {				
						e.printStackTrace();
					}
				}
				topPairsExperiment(OUTPUT_FOLDER + subFolder);
//				topPairsExperiment(DESKTOP_FOLDER);	
			}
		}
	}
	
	private static void topPairsExperiment(String outputFolder) throws InterruptedException, ExecutionException {
		long startTime = System.currentTimeMillis();
		
		Map<Integer, List<ConceptSentimentPair>> docToConceptSentimentPairs = new HashMap<Integer, List<ConceptSentimentPair>>();		
		ConcurrentMap<Integer, TopPairsResult> docToTopPairsResultGreedy = new ConcurrentHashMap<Integer, TopPairsResult>();
		ConcurrentMap<Integer, TopPairsResult> docToTopPairsResultILP = new ConcurrentHashMap<Integer, TopPairsResult>();
		ConcurrentMap<Integer, TopPairsResult> docToTopPairsResultRR = new ConcurrentHashMap<Integer, TopPairsResult>();
				
		docToConceptSentimentPairs = importDocToConceptSentimentPairs(DOC_TO_REVIEWS_PATH);
		printInitialization(docToConceptSentimentPairs);
		
//		importResultFromJson(outputFolder + "top_pairs_result_" + Constants.NUM_DOCS + "_ilp.txt", docToTopPairsResultILP);
//		importResultFromJson(outputFolder + "top_pairs_result_" + Constants.NUM_DOCS + "_greedy.txt", docToTopPairsResultGreedy);
//		importResultFromJson(outputFolder + "top_pairs_result_" + Constants.NUM_DOCS + "_rr.txt", docToTopPairsResultRR);
		
		runTopPairsAlgoMultiThreads(Algorithm.ILP, Constants.NUM_THREADS_ALGORITHM, docToConceptSentimentPairs, docToTopPairsResultILP);
		outputResultToJson(outputFolder + "top_pairs_result_" + Constants.NUM_DOCS + "_ilp.txt", docToTopPairsResultILP);
		
		runTopPairsAlgoMultiThreads(Algorithm.GREEDY, Constants.NUM_THREADS_ALGORITHM, docToConceptSentimentPairs, docToTopPairsResultGreedy);
		outputResultToJson(outputFolder + "top_pairs_result_" + Constants.NUM_DOCS + "_greedy.txt", docToTopPairsResultGreedy);
		
		if (Constants.FIND_BEST_LP_METHOD) {
			LPMethod[] methods = new LPMethod[] {LPMethod.AUTOMATIC, LPMethod.BARRIER, 
					LPMethod.CONCURRENT, LPMethod.DETERMINISTIC_CONCURRENT,
					LPMethod.DUAL_SIMPLEX, LPMethod.PRIMAL_SIMPLEX};
			Map<LPMethod, Long> methodToTime = new HashMap<LPMethod, Long>();
			for (LPMethod method : methods) {		
				long startTime2 = System.currentTimeMillis();
				runTopPairsAlgoMultiThreads(Algorithm.RANDOMIZED_ROUDNING, Constants.NUM_THREADS_ALGORITHM, 
						docToConceptSentimentPairs, docToTopPairsResultRR, method);
				outputResultToJson(outputFolder + "top_pairs_result_" + Constants.NUM_DOCS + "_rr.txt", docToTopPairsResultRR);

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
					docToConceptSentimentPairs, docToTopPairsResultRR);
			outputResultToJson(outputFolder + "top_pairs_result_" + Constants.NUM_DOCS + "_rr.txt", docToTopPairsResultRR);
		}
		
		String outputPath = outputFolder + "review_diversity_k" + k + "_threshold" + threshold + "_" + Constants.NUM_DOCS + ".xlsx";
		boolean isSet = false;
		outputResultToExcel(outputPath, isSet, docToTopPairsResultGreedy, docToTopPairsResultILP, docToTopPairsResultRR);
		
		Utils.printRunningTime(startTime, "Finished Top Pairs", true);
	}
	
	@SuppressWarnings("unused")
	private static void topPairsSyntheticExperiment() throws InterruptedException, ExecutionException {
		long startTime = System.currentTimeMillis();
		
		Map<Integer, List<ConceptSentimentPair>> docToConceptSentimentPairs = new HashMap<Integer, List<ConceptSentimentPair>>();		
		ConcurrentMap<Integer, TopPairsResult> docToTopPairsResultGreedy = new ConcurrentHashMap<Integer, TopPairsResult>();
		ConcurrentMap<Integer, TopPairsResult> docToTopPairsResultILP = new ConcurrentHashMap<Integer, TopPairsResult>();
		ConcurrentMap<Integer, TopPairsResult> docToTopPairsResultRR = new ConcurrentHashMap<Integer, TopPairsResult>();
		
		int numDecimals = 1;

		docToConceptSentimentPairs = createSyntheticDataset(
				importDocToConceptSentimentPairs(DOC_TO_REVIEWS_PATH), 
				Constants.NUM_SYNTHETIC_DOCTORS, numDecimals);

//		printInitialization(docToConceptSentimentPairs);
		
//		importResultFromJson(DESKTOP_FOLDER + "top_pairs_synthetic_result_" + Constants.NUM_DOCS + "_ilp.txt", docToTopPairsResultILP);
//		importResultFromJson(DESKTOP_FOLDER + "top_pairs_synthetic_result_" + Constants.NUM_DOCS + "_greedy.txt", docToTopPairsResultGreedy);
//		importResultFromJson(DESKTOP_FOLDER + "top_pairs_synthetic_result_" + Constants.NUM_DOCS + "_rr.txt", docToTopPairsResultRR);
		
		runTopPairsAlgoMultiThreads(Algorithm.GREEDY, Constants.NUM_THREADS_ALGORITHM, docToConceptSentimentPairs, docToTopPairsResultGreedy);
		outputResultToJson(DESKTOP_FOLDER + "top_pairs_synthetic_result_" + Constants.NUM_DOCS + "_greedy.txt", docToTopPairsResultGreedy);
		
/*		runTopPairsAlgoMultiThreads(Algorithm.ILP, Constants.NUM_THREADS_ALGORITHM, docToConceptSentimentPairs, docToTopPairsResultILP);
		outputResultToJson(DESKTOP_FOLDER + "top_pairs_synthetic_result_" + Constants.NUM_DOCS + "_ilp.txt", docToTopPairsResultILP);*/		

/*		runRandomizedRoundingMultiThreads(Constants.NUM_THREADS_ALGORITHM, docToConceptSentimentPairs, docToTopPairsResultRR);
		outputResultToJson(DESKTOP_FOLDER + "top_pairs_synthetic_result_" + Constants.NUM_DOCS + "_rr.txt", docToTopPairsResultRR);*/
		
//		printResult();
		
		String outputPath = DESKTOP_FOLDER + "review_diversity_synthetic_k" + k + "_threshold" + threshold + "_" + Constants.NUM_DOCS + ".xlsx";
		boolean isSet = false;
		outputResultToExcel(outputPath, isSet, docToTopPairsResultGreedy, docToTopPairsResultILP, docToTopPairsResultRR);
		
		Utils.printRunningTime(startTime, "Finished Top Pairs Synthetic", true);
	}
	
	
	@SuppressWarnings("unused")
	private static void topSetsExperiment(SetOption setOption) {
//		int[] ks = new int[] {3, 5, 10, 15, 20};
		int[] ks = new int[] {20};
		float[] thresholds = new float[] {0.1f, 0.2f, 0.3f};
//		float[] thresholds = new float[] {0.2f};
		for (int numChoosen : ks) {
			k = numChoosen;
			for (int thresh = 0; thresh < thresholds.length; ++thresh) {
				threshold = thresholds[thresh];
				
				String subFolder = "Top " + setOption + "\\k" + k + "_threshold" + threshold + "\\";
				
				if (!Files.exists(Paths.get(OUTPUT_FOLDER + subFolder))) {
					try {
						Files.createDirectories(Paths.get(OUTPUT_FOLDER + subFolder));
					} catch (IOException e) {				
						e.printStackTrace();
					}
				}
				topSetsExperiment(setOption, OUTPUT_FOLDER + subFolder);
//				topPairsExperiment(DESKTOP_FOLDER);	
			}
		}
	}
	
	private static void topSetsExperiment(SetOption setOption, String outputFolder) {
		long startTime = System.currentTimeMillis();
		
		Map<Integer, List<SentimentSet>> docToSentimentSets = new HashMap<Integer, List<SentimentSet>>();		
		ConcurrentMap<Integer, TopPairsResult> docToTopPairsResultGreedy = new ConcurrentHashMap<Integer, TopPairsResult>();
		ConcurrentMap<Integer, TopPairsResult> docToTopPairsResultILP = new ConcurrentHashMap<Integer, TopPairsResult>();
		ConcurrentMap<Integer, TopPairsResult> docToTopPairsResultRR = new ConcurrentHashMap<Integer, TopPairsResult>();
		
		switch (setOption) {
		case REVIEW: 	
			docToSentimentSets = importDocToSentimentReviews(DOC_TO_REVIEWS_PATH);
			break;
		case SENTENCE:  
			docToSentimentSets = importDocToSentimentSentences(DOC_TO_REVIEWS_PATH);
			break;
		default: 		
			docToSentimentSets = importDocToSentimentReviews(DOC_TO_REVIEWS_PATH);
			break;
		}			
				
//		importResultFromJson(outputFolder + "top_" + setOption + "_result_" + Constants.NUM_DOCS + "_ilp.txt", docToTopPairsResultILP);
//		importResultFromJson(outputFolder + "top_" + setOption + "_result_" + Constants.NUM_DOCS + "_greedy.txt", docToTopPairsResultGreedy);
//		importResultFromJson(outputFolder + "top_" + setOption + "_result_" + Constants.NUM_DOCS + "_rr.txt", docToTopPairsResultRR);
		
		runTopSetsAlgoMultiThreads(SetAlgorithm.RANDOMIZED_ROUNDING_SET, Constants.NUM_THREADS_ALGORITHM, docToSentimentSets, docToTopPairsResultRR);
		outputResultToJson(outputFolder + "top_" +setOption + "_result_" + Constants.NUM_DOCS + "_rr.txt", docToTopPairsResultRR);
		
		runTopSetsAlgoMultiThreads(SetAlgorithm.GREEDY_SET, Constants.NUM_THREADS_ALGORITHM, docToSentimentSets, docToTopPairsResultGreedy);
		outputResultToJson(outputFolder + "top_" +setOption + "_result_" + Constants.NUM_DOCS + "_greedy.txt", docToTopPairsResultGreedy);
		
		runTopSetsAlgoMultiThreads(SetAlgorithm.ILP_SET, Constants.NUM_THREADS_ALGORITHM, docToSentimentSets, docToTopPairsResultILP);
		outputResultToJson(outputFolder + "top_" +setOption + "_result_" + Constants.NUM_DOCS + "_ilp.txt", docToTopPairsResultILP);
		

				
		String outputPath = outputFolder + "review_diversity_" + setOption + 
										"_k" + k + "_threshold" + threshold + "_" + Constants.NUM_DOCS + ".xlsx";
		boolean isSet = true;
		outputResultToExcel(outputPath, isSet, docToTopPairsResultGreedy, docToTopPairsResultILP, docToTopPairsResultRR);
		
		Utils.printRunningTime(startTime, "Finished Top " + setOption, true);		

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
			ConcurrentMap<Integer, TopPairsResult> docToTopPairsResult,
			LPMethod method) {
		
		long startTime = System.currentTimeMillis();
		
		ExecutorService fixedPool = Executors.newFixedThreadPool(numThreadsAlgorithm);
		List<Future<String>> futures = new ArrayList<Future<String>>();
		
		int docCount = 0;	
		
		if (!Constants.USE_SECOND_MULTI_THREAD) {
			for (Integer docID : docToConceptSentimentPairs.keySet()) {
				Future<String> future;				
				switch (algorithm) {
				case GREEDY:
					future = fixedPool.submit(new GreedyAlgorithm1(k, threshold, docToTopPairsResult, 
							docID, docToConceptSentimentPairs.get(docID)), "DONE!");
					break;
				case ILP:
					future = fixedPool.submit(new ILPAlgorithm1(k, threshold, docToTopPairsResult, 
							docID, docToConceptSentimentPairs.get(docID)), "DONE!");
					break;					
				case RANDOMIZED_ROUDNING:
					future = fixedPool.submit(new RandomizedRounding1(k, threshold, docToTopPairsResult, 
							docID, docToConceptSentimentPairs.get(docID), method), "DONE!");
					break;
				default:
					future = fixedPool.submit(new GreedyAlgorithm1(k, threshold, docToTopPairsResult, 
							docID, docToConceptSentimentPairs.get(docID)), "DONE!");
					break;
				}				
				futures.add(future);

				docCount++;
				if (docCount >= Constants.NUM_DOCS)
					break;
			}
		} else {
			for (int index = 0; index < numThreadsAlgorithm; ++index) {				
				Future<String> future;
				switch (algorithm) {
				case GREEDY:
					future = fixedPool.submit(new GreedyAlgorithm2(k, threshold, docToTopPairsResult, 
							index, numThreadsAlgorithm, docToConceptSentimentPairs), "DONE!");
					break;
				case ILP:
					future = fixedPool.submit(new ILPAlgorithm2(k, threshold, docToTopPairsResult, 
							index, numThreadsAlgorithm, docToConceptSentimentPairs), "DONE!");
					break;
				case RANDOMIZED_ROUDNING:
					future = fixedPool.submit(new RandomizedRounding2(k, threshold, docToTopPairsResult, 
							index, numThreadsAlgorithm, docToConceptSentimentPairs, method), "DONE!");
					break;
				default:
					future = fixedPool.submit(new GreedyAlgorithm2(k, threshold, docToTopPairsResult, 
							index, numThreadsAlgorithm, docToConceptSentimentPairs), "DONE!");
					break;
				}
				futures.add(future);
			}
		}

		fixedPool.shutdown();		
		try {
			fixedPool.awaitTermination(2, TimeUnit.DAYS);

			Utils.printRunningTime(startTime, algorithm + " there are " + getUnfinishedJobNum(futures, "DONE!") + " unfinished jobs", true);			
			Utils.printRunningTime(startTime, algorithm + " finished " + docToTopPairsResult.size() + " doctors");
		} catch (InterruptedException e) {
			e.printStackTrace();

			Utils.printRunningTime(startTime, algorithm + " there are " + getUnfinishedJobNum(futures, "DONE!") + " unfinished jobs", true);			
			Utils.printRunningTime(startTime, algorithm + " finished " + docToTopPairsResult.size() + " doctors");
		}
	}
	
	// @param method - choosing LP solver. Note that this doesn't affect GREEDY, ILP, only affect Randomized Rounding
	private static void runTopPairsAlgoMultiThreads(
			Algorithm algorithm,
			int numThreadsAlgorithm,
			Map<Integer, List<ConceptSentimentPair>> docToConceptSentimentPairs,
			ConcurrentMap<Integer, TopPairsResult> docToTopPairsResult) {
		runTopPairsAlgoMultiThreads(algorithm, numThreadsAlgorithm, docToConceptSentimentPairs, docToTopPairsResult, 
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
			ConcurrentMap<Integer, TopPairsResult> docToTopPairsResult) {
		long startTime = System.currentTimeMillis();

		ExecutorService fixedPool = Executors.newFixedThreadPool(numThreadsAlgorithm);
		List<Future<String>> futures = new ArrayList<Future<String>>();

		int docCount = 0;	

		if (!Constants.USE_SECOND_MULTI_THREAD) {
			for (Integer docID : docToSentimentSets.keySet()) {
				Future<String> future;				
				switch (setAlgorithm) {
				case GREEDY_SET: 	
					future = fixedPool.submit(new GreedySetAlgorithm1(k, threshold, docToTopPairsResult, 
							docID, docToSentimentSets.get(docID)), "DONE!");
					break;
				case ILP_SET: 		
					future = fixedPool.submit(new ILPSetAlgorithm1(k, threshold, docToTopPairsResult, 
							docID, docToSentimentSets.get(docID)), "DONE!");
					break;
				case RANDOMIZED_ROUNDING_SET: 
					future = fixedPool.submit(new RandomizedRoundingSet1(k, threshold, docToTopPairsResult, 
							docID, docToSentimentSets.get(docID)), "DONE!");
					break;
				default:		
					future = fixedPool.submit(new GreedySetAlgorithm1(k, threshold, docToTopPairsResult, 
							docID, docToSentimentSets.get(docID)), "DONE!");
					break;
				}				
				futures.add(future);

				docCount++;
				if (docCount >= Constants.NUM_DOCS)
					break;
			}
		} else {
			for (int index = 0; index < numThreadsAlgorithm; ++index) {
				Future<String> future;				
				switch (setAlgorithm) {
				case GREEDY_SET: 	
					future = fixedPool.submit(new GreedySetAlgorithm2(k, threshold, docToTopPairsResult, 
						index, numThreadsAlgorithm, docToSentimentSets), "DONE!");
					break;
				case ILP_SET: 		
					future = fixedPool.submit(new ILPSetAlgorithm2(k, threshold, docToTopPairsResult, 
						index, numThreadsAlgorithm, docToSentimentSets), "DONE!");
					break;
				case RANDOMIZED_ROUNDING_SET: 
					future = fixedPool.submit(new RandomizedRoundingSet2(k, threshold, docToTopPairsResult, 
						index, numThreadsAlgorithm, docToSentimentSets), "DONE!");
					break;
				default:
					future = fixedPool.submit(new GreedySetAlgorithm2(k, threshold, docToTopPairsResult, 
						index, numThreadsAlgorithm, docToSentimentSets), "DONE!");
					break;
				}				
				futures.add(future);
			}
		}

		fixedPool.shutdown();		
		try {
			fixedPool.awaitTermination(2, TimeUnit.DAYS);

			Utils.printRunningTime(startTime, setAlgorithm + " there are " + getUnfinishedJobNum(futures, "DONE!") + " unfinished jobs", true);			
			Utils.printRunningTime(startTime, setAlgorithm + " finished " + docToTopPairsResult.size() + " doctors");
		} catch (InterruptedException e) {
			e.printStackTrace();

			Utils.printRunningTime(startTime, setAlgorithm + " there are " + getUnfinishedJobNum(futures, "DONE!") + " unfinished jobs", true);			
			Utils.printRunningTime(startTime, setAlgorithm + " finished " + docToTopPairsResult.size() + " doctors");
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
	
	private static void outputResultToJson(String path, Map<Integer, TopPairsResult> docToTopPairsResult) {
		long startTime = System.currentTimeMillis();
		ObjectMapper mapper = new ObjectMapper();
		
		try {
			Files.deleteIfExists(Paths.get(path));
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		boolean isFirstLine = true;
		for (Integer docID : docToTopPairsResult.keySet()) {
			try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(path), 
					StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
				
					if (!isFirstLine) {
						writer.newLine();						
					}
					mapper.writeValue(writer, docToTopPairsResult.get(docID));
					
					isFirstLine = false;					
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		Utils.printRunningTime(startTime, "Outputed " + docToTopPairsResult.size() + " results to " + path);
	}
	
	@SuppressWarnings("unused")
	private static void importResultFromJson(String path, Map<Integer, TopPairsResult> docToTopPairsResult) {
		ObjectMapper mapper = new ObjectMapper();
		
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(path))) {
			String line;
			while ((line = reader.readLine()) != null) {
				TopPairsResult result = mapper.readValue(line, TopPairsResult.class);
				docToTopPairsResult.put(result.getDocID(), result);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static void outputResultToExcel(
			String outputPath, boolean isSet,
			ConcurrentMap<Integer, TopPairsResult> docToTopPairsResultGreedy,
			ConcurrentMap<Integer, TopPairsResult> docToTopPairsResultILP,
			ConcurrentMap<Integer, TopPairsResult> docToTopPairsResultRR){		

		Workbook wb = new XSSFWorkbook();
		Sheet sheet = wb.createSheet("Top Pairs Results");
		addHeader(sheet, isSet, "ILP", "Greedy", "RR");
		
		int count = 1;
		for (Integer docID : docToTopPairsResultGreedy.keySet()) {
			if (!docToTopPairsResultILP.containsKey(docID) || !docToTopPairsResultRR.containsKey(docID))
				continue;
			
			Row row = sheet.createRow(count);
			
			// TODO
			if (docToTopPairsResultILP.containsKey(docID))
				addRow(row, isSet, docToTopPairsResultILP.get(docID),
						docToTopPairsResultGreedy.get(docID),
						docToTopPairsResultRR.get(docID));
			
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
	
	private static void addRow(Row row, TopPairsResult ilpResult, TopPairsResult greedyResult) {
		
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
		
		Cell docCell = row.createCell(0);
		Cell numPairsCell = row.createCell(1);
		Cell numPotentialUsefulCoverCell = row.createCell(2);
		Cell numPotentialUsefulCoverWithThresholdCell = row.createCell(3);
		
		Cell numUsefulCoverILPCell = row.createCell(4);
		Cell numUsefulCoverGreedyCell = row.createCell(5);
		
		Cell numUncoveredILPCell = row.createCell(6);
		Cell numUncoveredGreedyCell = row.createCell(7);
		
		Cell ilpTimeCell = row.createCell(8);
		Cell greedyTimeCell = row.createCell(9);

		Cell initialCostCell = row.createCell(10);
		Cell ilpCostCell = row.createCell(11);
		Cell greedyCostCell = row.createCell(12);
		Cell greedyRatioCell = row.createCell(13);
		
		docCell.setCellStyle(cs);
		numPairsCell.setCellStyle(cs);
		
		numPotentialUsefulCoverCell.setCellStyle(cs);
		numPotentialUsefulCoverWithThresholdCell.setCellStyle(cs);
		numUsefulCoverGreedyCell.setCellStyle(cs);
		numUsefulCoverILPCell.setCellStyle(cs);
		
		numUncoveredILPCell.setCellStyle(cs);
		numUncoveredGreedyCell.setCellStyle(cs);
		ilpTimeCell.setCellStyle(cs);
		greedyTimeCell.setCellStyle(cs);
		initialCostCell.setCellStyle(cs);
		ilpCostCell.setCellStyle(cs);
		greedyCostCell.setCellStyle(cs);
		greedyRatioCell.setCellStyle(cs);		
		
		docCell.setCellValue(ilpResult.getDocID());
		numPairsCell.setCellValue(ilpResult.getNumPairs());
		
		numPotentialUsefulCoverCell.setCellValue(greedyResult.getNumPotentialUsefulCover());
		numPotentialUsefulCoverWithThresholdCell.setCellValue(greedyResult.getNumPotentialUsefulCoverWithThreshold());
		numUsefulCoverGreedyCell.setCellValue(greedyResult.getNumUsefulCover());
		numUsefulCoverILPCell.setCellValue(ilpResult.getNumUsefulCover());
		
		numUncoveredILPCell.setCellValue(ilpResult.getNumUncovered());
		numUncoveredGreedyCell.setCellValue(greedyResult.getNumUncovered());
		if (ilpResult.getNumUncovered() > greedyResult.getNumUncovered())
			numUncoveredGreedyCell.setCellStyle(csRed);
		
		ilpTimeCell.setCellValue(ilpResult.getRunningTime());
		greedyTimeCell.setCellValue(greedyResult.getRunningTime());
		if (ilpResult.getRunningTime() < greedyResult.getRunningTime())
			greedyTimeCell.setCellStyle(csRed);
		
		initialCostCell.setCellValue(greedyResult.getInitialCost());
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
		
	}

	private static void addRow(Row row, boolean isSet, TopPairsResult ilpResult,
			TopPairsResult greedyResult, TopPairsResult rrResult) {
		
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
		
		Cell rrChosenCell = row.createCell(indexOffset + 11);

	
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
		rrChosenCell.setCellStyle(cs);
		
		
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


			rrChosenCell.setCellValue(rrResult.getNumFacilities());
			if (rrResult.getNumFacilities() > k)
				rrChosenCell.setCellStyle(csOrange);
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
		
		
		Cell rrNumChoosenCell = row.createCell(lastColumn);
		rrNumChoosenCell.setCellStyle(cs);
		rrNumChoosenCell.setCellValue("# Choosen Rounding");
		
		Cell infoCell = row.createCell(lastColumn + 1);
		infoCell.setCellStyle(cs);
		infoCell.setCellValue("K = " + k + ", Threshold = " + threshold);
	}
	
	private static void addHeader(Sheet sheet) {
		Font headingFont = sheet.getWorkbook().createFont();
		headingFont.setBold(true);
		headingFont.setFontName("Calibri");
		headingFont.setFontHeightInPoints((short) 14);
		headingFont.setColor(IndexedColors.BLUE_GREY.index);
		
		CellStyle cs = sheet.getWorkbook().createCellStyle();
		cs.setWrapText(true);
		cs.setFont(headingFont);
		cs.setAlignment(CellStyle.ALIGN_CENTER);
		
		Row row = sheet.createRow(0);
		sheet.createFreezePane(14, 1);
		
		Cell docCell = row.createCell(0);
		Cell numPairsCell = row.createCell(1);
		Cell numPotentialUsefulCoverCell = row.createCell(2);
		Cell numPotentialUsefulCoverWithThresholdCell = row.createCell(3);
		
		Cell numUsefulCoverILPCell = row.createCell(4);
		Cell numUsefulCoverGreedyCell = row.createCell(5);
		
		Cell numUncoveredILPCell = row.createCell(6);
		Cell numUncoveredGreedyCell = row.createCell(7);
		
		Cell ilpTimeCell = row.createCell(8);
		Cell greedyTimeCell = row.createCell(9);

		Cell initialCostCell = row.createCell(10);
		Cell ilpCostCell = row.createCell(11);
		Cell greedyCostCell = row.createCell(12);
		Cell greedyRatioCell = row.createCell(13);
		
		docCell.setCellStyle(cs);
		numPairsCell.setCellStyle(cs);
		
		numPotentialUsefulCoverCell.setCellStyle(cs);
		numPotentialUsefulCoverWithThresholdCell.setCellStyle(cs);
		numUsefulCoverGreedyCell.setCellStyle(cs);
		numUsefulCoverILPCell.setCellStyle(cs);
		
		numUncoveredILPCell.setCellStyle(cs);
		numUncoveredGreedyCell.setCellStyle(cs);
		ilpTimeCell.setCellStyle(cs);
		greedyTimeCell.setCellStyle(cs);
		initialCostCell.setCellStyle(cs);
		ilpCostCell.setCellStyle(cs);
		greedyCostCell.setCellStyle(cs);
		greedyRatioCell.setCellStyle(cs);	
		
		
		docCell.setCellValue("DocID");
		numPairsCell.setCellValue("# Pairs");
		
		numPotentialUsefulCoverCell.setCellValue("# Potential Useful Cover");
		numPotentialUsefulCoverWithThresholdCell.setCellValue("# Potential Useful Cover With Threshold");
		numUsefulCoverILPCell.setCellValue("# Useful Cover ILP");
		numUsefulCoverGreedyCell.setCellValue("# Useful Cover Greedy");
		
		numUncoveredILPCell.setCellValue("# Uncovered ILP");
		numUncoveredGreedyCell.setCellValue("# Uncovered Greedy");
		
		ilpTimeCell.setCellValue("ILP Time (ms)");
		greedyTimeCell.setCellValue("Greedy Time (ms)");
		
		initialCostCell.setCellValue("Initial Cost");
		ilpCostCell.setCellValue("ILP Cost");
		greedyCostCell.setCellValue("Greedy Cost");
		greedyRatioCell.setCellValue("Greedy Increased Ratio");		

		
		Cell infoCell = row.createCell(14);
		infoCell.setCellStyle(cs);
		infoCell.setCellValue("K = " + k + ", Threshold = " + threshold);
	}
		
	// Make sure: each conceptSentimentPair of a doctor has an unique hashcode
	private static Map<Integer, List<ConceptSentimentPair>> importDocToConceptSentimentPairs(String path) {
		Map<Integer, List<ConceptSentimentPair>> result = new HashMap<Integer, List<ConceptSentimentPair>>();
		
		ObjectMapper mapper = new ObjectMapper();
		
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(path))) {	
			String line;
			while ((line = reader.readLine()) != null) {
				DoctorSentimentReview doctorSentimentReview = mapper.readValue(line, DoctorSentimentReview.class);
				
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
		} catch (IOException e) {
			e.printStackTrace();			
		}	
		
		return result;
	}	

	// Make sure: each conceptSentimentPair of a SentimentReview has an unique hashcode
	private static Map<Integer, List<SentimentSet>> importDocToSentimentReviews(String path) {
		Map<Integer, List<SentimentSet>> result = new HashMap<Integer, List<SentimentSet>>();
		
		ObjectMapper mapper = new ObjectMapper();
		
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(path))) {	
			String line;
			while ((line = reader.readLine()) != null) {
				DoctorSentimentReview doctorSentimentReview = mapper.readValue(line, DoctorSentimentReview.class);
				
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
		} catch (IOException e) {
			e.printStackTrace();			
		}	
		
		return result;
	}
	
	// Make sure: each conceptSentimentPair of a SentimentSentence has an unique hashcode
	private static Map<Integer, List<SentimentSet>> importDocToSentimentSentences(String path) {
		Map<Integer, List<SentimentSet>> result = new HashMap<Integer, List<SentimentSet>>();

		ObjectMapper mapper = new ObjectMapper();

		try (BufferedReader reader = Files.newBufferedReader(Paths.get(path))) {	
			String line;
			while ((line = reader.readLine()) != null) {
				DoctorSentimentReview doctorSentimentReview = mapper.readValue(line, DoctorSentimentReview.class);

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
		} catch (IOException e) {
			e.printStackTrace();			
		}	

		return result;
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
}
