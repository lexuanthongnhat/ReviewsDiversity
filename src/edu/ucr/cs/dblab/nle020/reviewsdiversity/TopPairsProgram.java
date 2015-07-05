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
import java.util.List;
import java.util.Map;
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

import edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset.DoctorPairs;
import edu.ucr.cs.dblab.nle020.utilities.Utils;

public class TopPairsProgram {

	private static int k = Constants.K;
	private static float threshold = Constants.THRESHOLD;
	
	private static Map<Integer, List<ConceptSentimentPair>> docToReviews = new HashMap<Integer, List<ConceptSentimentPair>>();
	private static ConcurrentMap<Integer, List<FullPair>> docToTopPairs = new ConcurrentHashMap<Integer, List<FullPair>>();
	
	private static ConcurrentMap<Integer, TopPairsResult> docToTopPairsResultGreedy = new ConcurrentHashMap<Integer, TopPairsResult>();
	private static ConcurrentMap<Integer, TopPairsResult> docToTopPairsResultILP = new ConcurrentHashMap<Integer, TopPairsResult>();
	
	private final static String DOC_TO_REVIEWS_PATH = "D:\\UCR Google Drive\\GD - Review Diversity\\doc_pairs_1.txt"; 
	private final static String DESKTOP_FOLDER;	
	static {
		if (Files.isDirectory(Paths.get("C:\\Users\\Thong Nhat\\Desktop")))
			DESKTOP_FOLDER = "C:\\Users\\Thong Nhat\\Desktop\\";
		 else 
			DESKTOP_FOLDER = "C:\\Users\\Nhat XT Le\\Desktop\\";
	}
	
	public static void main(String[] args) throws InterruptedException, ExecutionException {
		long startTime = System.currentTimeMillis();
		
		importDocToReviews(DOC_TO_REVIEWS_PATH);

		int maxNumPairs = 0;
		int maxDocID = 0;
		for (Map.Entry<Integer, List<ConceptSentimentPair>> entry : docToReviews.entrySet()) {
			if (entry.getValue().size() > maxNumPairs) {
				maxNumPairs = entry.getValue().size();
				maxDocID = entry.getKey();
			}
		}
		System.out.println("Max Num of Pairs is " + maxNumPairs + " , DocID: " + maxDocID);
		
		importResultFromJson(DESKTOP_FOLDER + "top_pairs_result_" + Constants.NUM_DOCS + "_ilp.txt", docToTopPairsResultILP);
		importResultFromJson(DESKTOP_FOLDER + "top_pairs_result_" + Constants.NUM_DOCS + "_greedy.txt", docToTopPairsResultGreedy);
		
//		runILPMultiThreads(Constants.NUM_THREADS_ALGORITHM);
//		outputToJson(DESKTOP_FOLDER + "top_pairs_result_" + Constants.NUM_DOCS + "_ilp.txt", docToTopPairsResultILP);
		
//		runGreedyMultiThreads(Constants.NUM_THREADS_ALGORITHM);
//		outputToJson(DESKTOP_FOLDER + "top_pairs_result_" + Constants.NUM_DOCS + "_greedy.txt", docToTopPairsResultGreedy);
		
//		runAbsoluteSolutionMultiThreads(Constants.NUM_THREADS_ALGORITHM);


//		printResult();
//		outputResultToFile();
		
		try {
			outputResultToExcel();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		Utils.printRunningTime(startTime, "Finished Top Pairs", true);
	}

	@SuppressWarnings("unused")
	private static void runGreedyMultiThreads(int numThreadsAlgorithm) throws InterruptedException, ExecutionException {
		long startTime = System.currentTimeMillis();
		
		ExecutorService fixedPool = Executors.newFixedThreadPool(numThreadsAlgorithm);
		List<Future<String>> futures = new ArrayList<Future<String>>();
		int docCount = 0;		
		
		if (!Constants.USE_SECOND_MULTI_THREAD) {
			for (Integer docID : docToReviews.keySet()) {
				Utils.printTotalHeapSize("Before Greedy " + docCount);
				
				Future<String> future = fixedPool.submit(
						new GreedyAlgorithm(k, threshold, docID, docToReviews.get(docID), docToTopPairsResultGreedy),
						"DONE!");
				futures.add(future);
				
				Utils.printTotalHeapSize("After Greedy " + docCount, true);
				
				docCount++;
				if (docCount >= Constants.NUM_DOCS)
					break;
			}
		} else {		
			for (int index = 0; index < numThreadsAlgorithm; ++index) {
				Future<String> future = fixedPool.submit(
						new GreedyAlgorithm2(index, numThreadsAlgorithm, k, threshold, docToReviews, docToTopPairsResultGreedy),
						"DONE!");
				futures.add(future);
			}
		}
/*		int docID = 158732;
		fixedPool.submit(new GreedyAlgorithm(k, threshold, docID, docToReviews.get(docID), docToTopPairsResult));*/
		
		fixedPool.shutdown();		
		try {
			fixedPool.awaitTermination(2, TimeUnit.DAYS);
					
			Utils.printRunningTime(startTime, "Greedy there are " + getUnfinishedJobNum(futures, "DONE!") + " unfinished jobs", true);
			
			Utils.printRunningTime(startTime, "Finished Greedy for " + docToTopPairsResultGreedy.size() + " doctors");
		} catch (InterruptedException e) {
			e.printStackTrace();
			
			Utils.printRunningTime(startTime, "Greedy there are " + getUnfinishedJobNum(futures, "DONE!") + " unfinished jobs", true);
			
			Utils.printRunningTime(startTime, "Finished Greedy for " + docToTopPairsResultGreedy.size() + " doctors");
		}
	}
	
	private static void runILPMultiThreads(int numThreadsAlgorithm) throws InterruptedException, ExecutionException {
		long startTime = System.currentTimeMillis();
		
		ExecutorService fixedPool = Executors.newFixedThreadPool(numThreadsAlgorithm);
		List<Future<String>> futures = new ArrayList<Future<String>>();
		
		int docCount = 0;	
		
		if (!Constants.USE_SECOND_MULTI_THREAD) {
			for (Integer docID : docToReviews.keySet()) {
				Future<String> future = fixedPool.submit(
						new IntegerLinearProgramming(k, threshold, docID, docToReviews.get(docID),  docToTopPairsResultILP),
						"DONE!");
				futures.add(future);

				docCount++;
				if (docCount >= Constants.NUM_DOCS)
					break;
			}
		} else {
			for (int index = 0; index < numThreadsAlgorithm; ++index) {
				Future<String> future = fixedPool.submit(
						new IntegerLinearProgramming2(index, numThreadsAlgorithm, k, threshold, docToReviews, docToTopPairsResultILP),
						"DONE!");
				futures.add(future);
			}
		}
			
/*		int docID = 158732;
		fixedPool.submit(new IntegerLinearProgramming(k, threshold, docID, docToReviews.get(docID),  docToTopPairsResult));*/
		
		fixedPool.shutdown();		
		try {
			fixedPool.awaitTermination(2, TimeUnit.DAYS);
			
			Utils.printRunningTime(startTime, "ILP there are " + getUnfinishedJobNum(futures, "DONE!") + " unfinished jobs", true);
			
			Utils.printRunningTime(startTime, "Finished ILP for " + docToTopPairsResultILP.size() + " doctors");
		} catch (InterruptedException e) {
			e.printStackTrace();
			
			Utils.printRunningTime(startTime, "ILP there are " + getUnfinishedJobNum(futures, "DONE!") + " unfinished jobs", true);
			
			Utils.printRunningTime(startTime, "Finished ILP for " + docToTopPairsResultILP.size() + " doctors");
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
	
/*	private static void runAbsoluteSolutionMultiThreads(int numThreadsAlgorithm) {
		long startTime = System.currentTimeMillis();
		
		ExecutorService fixedPool = Executors.newFixedThreadPool(numThreadsAlgorithm);
		int docCount = 0;		
		for (Integer docID : docToReviews.keySet()) {
			fixedPool.submit(new AbsoluteSolution(k, threshold, docID, docToReviews.get(docID), docToTopPairsResult));
			
			docCount++;
			if (docCount >= Constants.NUM_DOCS)
				break;
		}
		int docID = 1204253;
		fixedPool.submit(new AbsoluteSolution(k, threshold, docID, docToReviews.get(docID), docToTopPairsResult));
		
		fixedPool.shutdown();		
		try {
			fixedPool.awaitTermination(2, TimeUnit.DAYS);
		
			long finishTime = System.currentTimeMillis();			
			System.out.println("Finished Absolute Solution for " + docCount + " in " + (finishTime - startTime) + " ms");
		} catch (InterruptedException e) {
			e.printStackTrace();
			
			long finishTime = System.currentTimeMillis();
			System.out.println("Finished Absolute Solution for " + docCount + " in " + (finishTime - startTime) + " ms");
		}
	}*/
	
	@SuppressWarnings("unused")
	private static void printResult() {
		System.err.println("FINAL RESULTS: ");
		System.err.println("GREEDY: ");
		for (Integer docID : docToTopPairsResultGreedy.keySet()) {
			System.out.println(docToTopPairsResultGreedy.get(docID).toString());
			System.out.println();
		}
		System.out.println();
		
		System.err.println("ILP: ");
		for (Integer docID : docToTopPairsResultILP.keySet()) {
			System.out.println(docToTopPairsResultILP.get(docID).toString());
			System.out.println();
		}
	}
	
	private static void outputToJson(String path, Map<Integer, TopPairsResult> docToTopPairsResult) {
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
	
	@SuppressWarnings("unused")
	private static void outputResultToFile() {	
		
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(DESKTOP_FOLDER + "output.txt")
				, StandardOpenOption.TRUNCATE_EXISTING)) {
			writer.append("FINAL RESULTS: ");
			writer.append("\nGREEDY:");
			for (Integer docID : docToTopPairsResultGreedy.keySet()) {
				writer.append(docToTopPairsResultGreedy.get(docID).toString() + "\n");
			}
			writer.append("\n");
			for (Integer docID : docToTopPairsResultILP.keySet()) {
				writer.append(docToTopPairsResultILP.get(docID).toString() + "\n");
			}
			writer.append("ILP:");
			
			writer.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	private static void outputResultToExcel() throws IOException {
		
		String outputPath = DESKTOP_FOLDER + "review_diversity_" + Constants.NUM_DOCS + ".xlsx";
		Workbook wb = new XSSFWorkbook();
		Sheet sheet = wb.createSheet("Top Pairs Results");
		addHeader(sheet);
		
		int count = 1;
		for (Integer docID : docToTopPairsResultGreedy.keySet()) {
			Row row = sheet.createRow(count);
			
			// TODO
			if (docToTopPairsResultILP.containsKey(docID))
				addRow(row, docToTopPairsResultILP.get(docID),
						docToTopPairsResultGreedy.get(docID));
			
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
				fileOut.close();
		}
		System.err.println("Outputed top pairs to \"" + outputPath + "\"");
	}
	
	private static void addRow(Row row, TopPairsResult ilpResult,
			TopPairsResult greedyResult) {
		
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
		infoCell.setCellValue("K = " + Constants.K + ", Threshold = " + Constants.THRESHOLD);
	}
	
	private static void importDocToReviews(String docToReviewsPath) {
		ObjectMapper mapper = new ObjectMapper();
		
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(docToReviewsPath))) {	
			String line;
			while ((line = reader.readLine()) != null) {
				DoctorPairs doctorPairs = mapper.readValue(line, DoctorPairs.class);
				
				docToReviews.put(doctorPairs.getDocID(), doctorPairs.getPairs());
			}
		} catch (IOException e) {
			e.printStackTrace();			
		}		
	}
}
