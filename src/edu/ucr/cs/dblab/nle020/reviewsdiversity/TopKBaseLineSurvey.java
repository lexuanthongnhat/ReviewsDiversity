package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import edu.ucr.cs.dblab.nle020.metamap.MetaMapParser;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.composite.GreedySetThreadImpl;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.composite.TopSetsBaseline;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset.DoctorSentimentReview;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset.SentimentCalculator;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentReview;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSentence;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;
import edu.ucr.cs.dblab.nle020.utils.Utils;
import gov.nih.nlm.nls.metamap.Ev;
import gov.nih.nlm.nls.metamap.Position;

public class TopKBaseLineSurvey {

	private static final int K = 3;
	private static final float THRESHOLD = 0.3f;
	
	private static int NUM_DOCS_TO_SURVEY = 20;
	private static int NUM_REVIEWS_PER_DOC = 6;
	private static int THRESHOLD_ON_NUM_SENTENCE_PER_REVIEW = 30;
	private static final int SENTENCE_COLUMN_WIDTH = 36;
	private static XSSFWorkbook wb = new XSSFWorkbook();
	private static Font highlightFont = wb.createFont();
	private static Font headingFont = wb.createFont();
	private static MetaMapParser mmParser = new MetaMapParser();
	
	//private static Integer[] indices = new Integer[] {106, 109, 110, 111, 120, 130, 140, 150, 160, 170};
	private static Set<Integer> indices = Utils.randomIndices(1000, NUM_DOCS_TO_SURVEY);
	// Discard "dr" concept - C0031831
	private static Set<String> ignoreCuis = new HashSet<String>(Arrays.asList(
			new String[]{"C0031831"}
			));
	
	static {
		highlightFont.setColor(HSSFColor.GREEN.index);
		highlightFont.setUnderline((byte) 1);
		highlightFont.setBold(true);
		
		//headingFont.setBold(true);
		headingFont.setFontName("Calibri");
		headingFont.setFontHeightInPoints((short) 13);
		headingFont.setColor(IndexedColors.BLUE_GREY.index);
	}
	
	public static void main(String[] args) {
		String surveyFolder = "src/main/resources/survey/topsentences/";
		prepareSurvey(surveyFolder);
		
		collectSurveys(surveyFolder + "collectedsurvey/");
	}
	
	private static void collectSurveys(String surveyFolder) {
		List<Path> surveys = new ArrayList<Path>();
		
		try {
			Files.walkFileTree(Paths.get(surveyFolder), new SimpleFileVisitor<Path>(){
				
				@Override
				public FileVisitResult visitFile(Path file,
						BasicFileAttributes attrs) throws IOException {
					
					if  (file.toString().endsWith(".xlsx"))
						surveys.add(file);
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
					
		Map<Integer, List<Integer>> docToCoveragesOurMethod = new HashMap<>();
		Map<Integer, List<Integer>> docToCoveragesBaseline = new HashMap<>();
		Map<Integer, Integer> docToRowUnitNum = new HashMap<>();
		surveys.stream().forEach(file -> collectSurvey(file, 
				docToCoveragesOurMethod,
				docToCoveragesBaseline,
				docToRowUnitNum));
		
		surveyStatistics(docToRowUnitNum, docToCoveragesOurMethod, docToCoveragesBaseline,
		    surveyFolder + "survey-result.csv");
		surveySummary(docToRowUnitNum, docToCoveragesOurMethod, docToCoveragesBaseline,
		    surveyFolder + "survey-summary.csv");
	}
	
	private static void surveyStatistics(
			Map<Integer, Integer> docToRowUnitNum,
			Map<Integer, List<Integer>> docToCoveragesOurMethod, 
			Map<Integer, List<Integer>> docToCoveragesBaseline,
			String outputPath) {		
		
		Map<Integer, Double> docToAverageOurMethod = new HashMap<Integer, Double>(); 
		Map<Integer, Double> docToAverageBaseline  = new HashMap<Integer, Double>();
		
		
		for (Integer docId : docToRowUnitNum.keySet()) {
			double averageOurMethod = docToCoveragesOurMethod.get(docId).stream()
					.collect(Collectors.averagingDouble(coverage -> (double) coverage));
			double averageBaseline = docToCoveragesBaseline.get(docId).stream()
					.collect(Collectors.averagingDouble(coverage -> (double) coverage));
			docToAverageOurMethod.put(docId, averageOurMethod);
			docToAverageBaseline.put(docId, averageBaseline);			
			

		}
		
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath), 
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

			writer.write("docId, #sentences, our method, baseline"); 
			writer.newLine();
			
			for (Integer docId : docToRowUnitNum.keySet()) {
				writer.write(docId + ", " + docToRowUnitNum.get(docId) + ", " 
						+ docToAverageOurMethod.get(docId) + ", " + docToAverageBaseline.get(docId));
				writer.newLine();
			}
			
			writer.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("Find the result in \"" + outputPath + "\"");
	}

	private static void surveySummary(
	    Map<Integer, Integer> docToRowUnitNum,
			Map<Integer, List<Integer>> docToCoveragesOurMethod,
			Map<Integer, List<Integer>> docToCoveragesBaseline,
			String outputPath) {
		
		Map<Integer, List<Integer>> docToDiffs = new HashMap<Integer, List<Integer>>();
		int numParticipants = 0;			
		for (Integer docId : docToCoveragesBaseline.keySet()){
			numParticipants = docToCoveragesBaseline.get(docId).size();
			break;
		}
		
		for (Integer docId : docToRowUnitNum.keySet()) {			
			List<Integer> ourMethod = docToCoveragesOurMethod.get(docId);
			List<Integer> baseline = docToCoveragesBaseline.get(docId);
			if (!docToDiffs.containsKey(docId))
				docToDiffs.put(docId, new ArrayList<Integer>());
			for (int i = 0; i < numParticipants; ++i) {
				int diff = ourMethod.get(i) - baseline.get(i);
				docToDiffs.get(docId).add(diff);
			}
		}
		
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath), 
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			writer.write("Summary"); 
			writer.newLine();			
			
			writer.write("participants, our method is better, equal, worse"); writer.newLine();
			for (int i = 0; i < numParticipants; ++i) {
				writer.write("participant (" + i + "), ");
			
				int numBetter = 0, numEqual = 0, numWorse = 0;
				for (List<Integer> diffs : docToDiffs.values()) {
					if (diffs.get(i) > 0)
						++numBetter;
					else if (diffs.get(i) == 0)
						++numEqual;
					else 
						++numWorse;
				}
				writer.write(numBetter + ", " + numEqual + ", " + numWorse); writer.newLine();
			}
			writer.newLine();			
			writer.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("Find the survey summary in \"" + outputPath + "\"");
	}

	private static void collectSurvey(Path file,
			Map<Integer, List<Integer>> docToCoveragesOurMethod,
			Map<Integer, List<Integer>> docToCoveragesBaseline,
			Map<Integer, Integer> docToRowUnitNum) {
		
		try {
			Workbook wb = new XSSFWorkbook(file.toString());			
			Iterator<Sheet> sheetIterator = wb.iterator();
			while (sheetIterator.hasNext()) {
				Sheet sheet = sheetIterator.next(); 
				Integer docId = Integer.parseInt(sheet.getSheetName());
											
				List<Integer> cellIndicesOurMethod = new ArrayList<Integer>();
				List<Integer> cellIndicesBaseline = new ArrayList<Integer>();
				
				Row row = sheet.getRow(0);				
				for (int i = row.getFirstCellNum() + 1; i < row.getFirstCellNum() + 4; ++i)			
					cellIndicesOurMethod.add(i);					
				for (int i = row.getLastCellNum() - 3; i < row.getLastCellNum(); ++i)	
					cellIndicesBaseline.add(i);				
				
				int count = 0;
				int countOurMethod = 0;
				int countBaseline = 0;				
				for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex < sheet.getLastRowNum();
				    ++rowIndex) {
				  
					row = sheet.getRow(rowIndex);
					boolean coveredByOurMethod = false;
					boolean coveredBaseline = false;
					
					for (int cellIndex = row.getFirstCellNum() + 1; cellIndex < row.getLastCellNum();
					    ++cellIndex) {
					  
						String cellString = "";
						if (row.getCell(cellIndex) != null)
							cellString = row.getCell(cellIndex).getStringCellValue();
												
						if (cellString.equalsIgnoreCase("x")) {
							if (cellIndicesOurMethod.contains(cellIndex))
								coveredByOurMethod = true;
							if (cellIndicesBaseline.contains(cellIndex))
								coveredBaseline = true;
						}
					}
					
					if (coveredByOurMethod)
						++countOurMethod;
					if (coveredBaseline)
						++countBaseline;
					++count;
				}
				
				if (count != 0) {
					if (!docToRowUnitNum.containsKey(docId)) {
						docToRowUnitNum.put(docId, count);
						docToCoveragesOurMethod.put(docId, new ArrayList<Integer>());
						docToCoveragesBaseline.put(docId, new ArrayList<Integer>());
					} 
					
					docToCoveragesOurMethod.get(docId).add(countOurMethod);					
					docToCoveragesBaseline.get(docId).add(countBaseline);								
				}
			}
			
			wb.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings("unused")
	private static void prepareSurvey(String surveyFolder) {
		long startTime = System.currentTimeMillis();
		
		List<Integer> preDefinedDoctors = Arrays.asList(784091, 303476, 1088737, 796942, 1052723,
		    250230, 378031, 149560, 1106190, 893234);
		Map<Integer, List<SentimentReview>> docToSentimentReviews = chooseDoctorToSentimentReviews(
		    preDefinedDoctors);
    //Map<Integer, List<SentimentReview>> docToSentimentReviews = chooseDoctorToSentimentReviews();
				
		Map<Integer, List<SentimentSentence>> docToSentimentSentences =
		    convertToDocToSentimentSentences(docToSentimentReviews);
		Map<Integer, List<SentimentSentence>> docToTopSentencesOurMethod = getKSentencesOurMethod(
		    docToSentimentSentences);
		Map<Integer, List<SentimentSentence>> docToTopSentencesBaseline = getKSentencesBaseline(
		    docToSentimentSentences);
		Map<Integer, List<SentimentSentence>> docToTopSentences = new HashMap<>();
		for (Integer docId : docToTopSentencesOurMethod.keySet()) {
			docToTopSentences.put(docId, reorderTopSentences(docToTopSentencesOurMethod.get(docId),
			                                                 docToTopSentencesBaseline.get(docId)));
		}
		

		String outputExcelPath = surveyFolder + "survey.xlsx";
		outputToExcel(docToSentimentSentences, docToTopSentences, outputExcelPath);
//		Utils.writeToJson(docToTopSentencesOurMethod, surveyFolder + "our-method.txt");
//		Utils.writeToJson(docToTopSentencesBaseline, surveyFolder + "baseline.txt");
		
		Utils.printRunningTime(startTime, "Finished outputing survey to \"" + outputExcelPath + "\"");
	}
		
	// our method sentences only first, then shared sentences of our method and baseline,
	// end by baseline only
	private static List<SentimentSentence> reorderTopSentences(
			List<SentimentSentence> ourMethodSentences, List<SentimentSentence> baselineSentences) {
		List<SentimentSentence> reorderedSentences = new ArrayList<SentimentSentence>();
		
		ourMethodSentences.stream().forEach(sentence -> {		
				if (!baselineSentences.contains(sentence))
					reorderedSentences.add(sentence);
				});
		ourMethodSentences.stream().forEach(sentence -> {		
			if (baselineSentences.contains(sentence))
				reorderedSentences.add(sentence);
			});
		
		baselineSentences.stream().forEach(sentence -> {		
			if (!ourMethodSentences.contains(sentence))
				reorderedSentences.add(sentence);
			});
		if (reorderedSentences.size() > 2 * K)
			System.err.println("Wrong reorder of top sentences");
		
		return reorderedSentences;
	}

	private static Map<Integer, List<SentimentSentence>> convertToDocToSentimentSentences(
			Map<Integer, List<SentimentReview>> docToSentimentReviews) {
		Map<Integer, List<SentimentSentence>> docToSentimentSentences = new HashMap<>();
		for (Integer docId : docToSentimentReviews.keySet()) {
			List<SentimentSentence> sentences = new ArrayList<>();
			docToSentimentReviews.get(docId).stream().forEach(
			    review -> sentences.addAll(review.getSentences()));
			docToSentimentSentences.put(docId, sentences);
		}
		
		return docToSentimentSentences;
	}

	private static Map<Integer, List<SentimentSentence>> getKSentencesOurMethod(
	    Map<Integer, List<SentimentSentence>> docToSentimentSentences) {
	  
		Map<Integer, List<SentimentSet>> docToSentimentSets = new HashMap<>();
		for (Integer docId : docToSentimentSentences.keySet()) {
			List<SentimentSet> sentimentSets = new ArrayList<SentimentSet>();
			docToSentimentSentences.get(docId).stream().forEach(sentence -> sentimentSets.add(sentence));						
			docToSentimentSets.put(docId, sentimentSets);
		}
				
		Map<Integer, List<SentimentSentence>> docToTopKSentences = new HashMap<>();
		
		ConcurrentMap<Integer, StatisticalResult> docToStatisticalResult = new ConcurrentHashMap<>();
		ConcurrentMap<Integer, List<SentimentSet>> docToTopKSets = new ConcurrentHashMap<>();
		
		GreedySetThreadImpl ourMethod = 
				new GreedySetThreadImpl(K, THRESHOLD, docToStatisticalResult, docToTopKSets, 0, 1, docToSentimentSets);
		ourMethod.run();
		
		
		for (Integer docId : docToTopKSets.keySet()) {
			List<SentimentSentence> sentences = new ArrayList<>();
			docToTopKSets.get(docId).stream().forEach(set -> {
			  // To avoid error when writing to Json (recursive FullPair)
				set.setFullPairs(new ArrayList<FullPair>());  	
				sentences.add((SentimentSentence) set);
			});
			docToTopKSentences.put(docId, sentences);
		}		
		
		return docToTopKSentences;
	}
	
	private static Map<Integer, List<SentimentSentence>> getKSentencesBaseline(
	    Map<Integer, List<SentimentSentence>> docToSentimentSentences) {
	  
		Map<Integer, List<SentimentSet>> docToSentimentSets = new HashMap<>();
		for (Integer docId : docToSentimentSentences.keySet()) {
			List<SentimentSet> sentimentSets = new ArrayList<SentimentSet>();
			docToSentimentSentences.get(docId).stream().forEach(sentence -> sentimentSets.add(sentence));						
			docToSentimentSets.put(docId, sentimentSets);
		}
		
		Map<Integer, List<SentimentSentence>> docToTopKSentences = new HashMap<>();
		TopSetsBaseline.setK(K);
		for (Integer docId : docToSentimentSets.keySet()) {
			List<SentimentSet> sets = TopSetsBaseline.extractTopKFromList(docToSentimentSets.get(docId));
			List<SentimentSentence> sentences = new ArrayList<SentimentSentence>();
			sets.stream().forEach(set -> {
			  // To avoid error when writing to Json (recursive FullPair)
				set.setFullPairs(new ArrayList<FullPair>());  			
				sentences.add((SentimentSentence) set);
			});
			docToTopKSentences.put(docId, sentences);
		}
		
		return docToTopKSentences;
	}
	
	private static void outputToExcel(
			Map<Integer, List<SentimentSentence>> docToSentimentSentences,
			Map<Integer, List<SentimentSentence>> docToTopSentences,			 
			String outputExcelPath) {
				
		Map<SentimentSentence, XSSFRichTextString> sentenceToRichText = new HashMap<>();
		for (Integer docId : docToSentimentSentences.keySet()) {
			docToSentimentSentences.get(docId).stream().forEach( sentence ->
				sentenceToRichText.put(sentence, prepareColoredRichTextSentence(sentence.getSentence()))
				);
		}
	
		outputToExcel(docToSentimentSentences, docToTopSentences, outputExcelPath, sentenceToRichText);
	}

	private static void outputToExcel(
			Map<Integer, List<SentimentSentence>> docToSentimentSentences, 
			Map<Integer, List<SentimentSentence>> docToTopSentences, 
			String outputExcelPath,
			Map<SentimentSentence, XSSFRichTextString> sentenceToRichText) {
		
		for (Integer docId : docToSentimentSentences.keySet()) {
			XSSFSheet sheet = wb.createSheet(docId + "");
			fillSheet(sheet, docToSentimentSentences.get(docId), docToTopSentences.get(docId),
			    sentenceToRichText);			
		}
		
		try {
			wb.write(Files.newOutputStream(Paths.get(outputExcelPath)));
			wb.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void fillSheet(XSSFSheet sheet, 
			List<SentimentSentence> sentimentSentences, 
			List<SentimentSentence> topSentences, 
			Map<SentimentSentence, XSSFRichTextString> sentenceToRichText) {
		
		int numColumns = topSentences.size() + 1;
		sheet.createFreezePane(numColumns + 1, 1);
		for (int i = 0; i < numColumns + 1; ++i){
			sheet.setColumnWidth(i, SENTENCE_COLUMN_WIDTH * 256);
		}
		
		fillHeader(sheet, topSentences, sentenceToRichText);
		fillBodyRows(sheet, sentimentSentences, sentenceToRichText);
		
	}
	
	private static void fillBodyRows(XSSFSheet sheet,
			List<SentimentSentence> sentimentSentences,
			Map<SentimentSentence, XSSFRichTextString> sentenceToRichText) {
		
		CellStyle normalCs = sheet.getWorkbook().createCellStyle();		
		normalCs.setWrapText(true);
		
		for (int rowId = 1; rowId <= sentimentSentences.size(); ++rowId) {
			Row row = sheet.createRow(rowId);
			Cell cellSentence = row.createCell(0);
			cellSentence.setCellStyle(normalCs);
			cellSentence.setCellValue(sentenceToRichText.get(sentimentSentences.get(rowId - 1)));
		}
	}

	private static void fillHeader(
			Sheet sheet, 
			List<SentimentSentence> topSentences, 
			Map<SentimentSentence, XSSFRichTextString> sentenceToRichText) {
		
		Row row = sheet.createRow(0);
		
		CellStyle headingCs = sheet.getWorkbook().createCellStyle();		
		headingCs.setWrapText(true);
		headingCs.setFont(headingFont);	
		
		CellStyle wrapTextCs = sheet.getWorkbook().createCellStyle();
		wrapTextCs.setWrapText(true);
		
		Cell cellSentence = row.createCell(0);
		cellSentence.setCellValue("Type \"x\" for covering sentences");
		cellSentence.setCellStyle(headingCs);
		
		Cell[] cellTopSentences = new Cell[topSentences.size()];
		for (int i = 0; i < topSentences.size(); ++i) {
			cellTopSentences[i] = row.createCell(i + 1);
			cellTopSentences[i].setCellStyle(wrapTextCs);
			cellTopSentences[i].setCellValue(sentenceToRichText.get(topSentences.get(i)));
		}
	}

	private static XSSFRichTextString prepareColoredRichTextSentence(String sentence) {
		
		XSSFRichTextString richText = new XSSFRichTextString(sentence);
		List<Ev> mappings = mmParser.getValidMappings(sentence);
		for (Ev ev : mappings) {
			
			List<Position> positions;
			try {
				positions = ev.getPositionalInfo();					
				for (int i = 0; i < positions.size(); i++) {
					Position pos = positions.get(i);
				
					richText.applyFont(pos.getX(), pos.getX() + pos.getY(), highlightFont);
				}
			} catch (Exception e) {					
				e.printStackTrace();
			}
		}
		return richText;
	}

	private static Map<Integer, List<SentimentReview>> chooseDoctorToSentimentReviews() {
		Map<Integer, List<SentimentReview>> completeDocToReviews = importRawCompleteDocToReviews(
		    TopPairsProgram.DOC_TO_REVIEWS_PATH);
		Map<Integer, List<SentimentReview>> docToReviews = new HashMap<>();
		
		//Set<Integer> indices = Utils.randomIndices(completeDocToReviews.size(), NUM_DOCS_TO_SURVEY);
		
		Integer[] allDocIds = new Integer[completeDocToReviews.size()];
		completeDocToReviews.keySet().toArray(allDocIds);
		
		for (Integer index : indices) 
			docToReviews.put(allDocIds[index], completeDocToReviews.get(allDocIds[index]));
		
		Map<Integer, List<SentimentReview>> choosenDocToReviews = new HashMap<>();
		for (Integer docId : docToReviews.keySet()) {
			choosenDocToReviews.put(docId, pickUpReviewsForSurvey(docToReviews.get(docId)));
		}
		
		return choosenDocToReviews;
	}
	
	private static Map<Integer, List<SentimentReview>> chooseDoctorToSentimentReviews(
	    List<Integer> preDefinedDoctors) {
	  
    Map<Integer, List<SentimentReview>> completeDocToReviews = importRawCompleteDocToReviews(
        TopPairsProgram.DOC_TO_REVIEWS_PATH);
    Map<Integer, List<SentimentReview>> docToReviews = new HashMap<>();
        
    for (Integer docId : preDefinedDoctors) 
      docToReviews.put(docId, completeDocToReviews.get(docId));
    
    Map<Integer, List<SentimentReview>> choosenDocToReviews = new HashMap<>();
    for (Integer docId : docToReviews.keySet()) {
      choosenDocToReviews.put(docId, pickUpReviewsForSurvey(docToReviews.get(docId)));
    }
    
    return choosenDocToReviews;
  }	

	private static List<SentimentReview> pickUpReviewsForSurvey(
	    List<SentimentReview> completeSetOfReviews) {
		
		List<SentimentReview> pickUpReviews = new ArrayList<>();
		
		completeSetOfReviews.removeIf(review -> 
			SentimentCalculator.breakingIntoSentences(
			    review.getRawReview().getBody(),
			    Constants.USE_ADVANCED_SENTENCE_BREAKING).size() > THRESHOLD_ON_NUM_SENTENCE_PER_REVIEW);				
		
		updatePairsOfSentimentReviews(completeSetOfReviews);
		List<String> choosenCuis = new ArrayList<String>();
		
		if (completeSetOfReviews.size() <= NUM_REVIEWS_PER_DOC)
			pickUpReviews = completeSetOfReviews;
		else
			while (pickUpReviews.size() < NUM_REVIEWS_PER_DOC) {
				pickUpReviews.add(pickNextReview(completeSetOfReviews, choosenCuis));
			}
		
		return pickUpReviews;
	}

	private static SentimentReview pickNextReview(
			List<SentimentReview> completeSetOfReviews,
			List<String> choosenCuis) {
		
		SentimentReview nextReview = completeSetOfReviews.get(0);
		int maxUnion = 0;
		for (SentimentReview review : completeSetOfReviews) {
			int unionSize = unionSize(review.getPairs(), choosenCuis); 
			if ( unionSize > maxUnion) {
				maxUnion = unionSize;
				nextReview = review;
			}
		}
		
		completeSetOfReviews.remove(nextReview);
		for (ConceptSentimentPair pair : nextReview.getPairs()) 
			choosenCuis.add(pair.getCui());
		
		return nextReview;
	}

	private static int unionSize(List<ConceptSentimentPair> pairs,
			List<String> choosenCuis) {
		int unionSize = 0;
		for (ConceptSentimentPair pair : pairs) {
			if (choosenCuis.contains(pair.getCui()))
				++unionSize;
		}
		
		return unionSize;
	}

	private static void updatePairsOfSentimentReviews(
			List<SentimentReview> pickUpReviews) {
		for (SentimentReview review : pickUpReviews) {
			List<ConceptSentimentPair> pairs = new ArrayList<ConceptSentimentPair>();
			for (SentimentSentence sentence : review.getSentences()) {
				pairs.addAll(sentence.getPairs());
			}
			
			review.setPairs(pairs);
		}
		
	}

	private static Map<Integer, List<SentimentReview>> importRawCompleteDocToReviews(
			String docToReviewsPath) {
		Map<Integer, List<SentimentReview>> rawDocToReviews = new HashMap<>();		
		List<DoctorSentimentReview> doctorSentimentReviews =
		    TopPairsProgram.importDoctorSentimentReviewsDataset(docToReviewsPath);
		
		for (DoctorSentimentReview doctorSentimentReview : doctorSentimentReviews) {		
			List<SentimentReview> reviews = doctorSentimentReview.getSentimentReviews();
			for (SentimentReview review : reviews) 
				for (SentimentSentence sentence : review.getSentences())
					sentence.getPairs().removeIf(pair -> isIgnoreCui(pair.getCui().toUpperCase()));
			
			rawDocToReviews.put(doctorSentimentReview.getDocId(),
			    doctorSentimentReview.getSentimentReviews());			
		}
				
		return rawDocToReviews;
	}
	
	private static boolean isIgnoreCui(String cui) {
		return ignoreCuis.contains(cui);
	}
}
