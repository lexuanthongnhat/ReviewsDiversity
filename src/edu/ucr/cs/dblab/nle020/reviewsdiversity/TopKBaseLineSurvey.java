package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFDataValidation;
import org.apache.poi.xssf.usermodel.XSSFDataValidationConstraint;
import org.apache.poi.xssf.usermodel.XSSFDataValidationHelper;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import edu.ucr.cs.dblab.nle020.metamap.MetaMapParser;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.composite.GreedySetAlgorithm2;
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
	
	private static int NUM_DOCS_TO_SURVEY = 10;
	private static int NUM_REVIEWS_PER_DOC = 6;
	private static int THRESHOLD_ON_NUM_SENTENCE_PER_REVIEW = 30;
	private static final int SENTENCE_COLUMN_WIDTH = 36;
	private static XSSFWorkbook wb = new XSSFWorkbook();
	private static Font highlightFont = wb.createFont();
	private static Font headingFont = wb.createFont();
	private static MetaMapParser mmParser = new MetaMapParser();
	
	private static Integer[] indices = new Integer[] {106, 109, 110, 111};
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
		long startTime = System.currentTimeMillis();
		Map<Integer, List<SentimentReview>> docToSentimentReviews = chooseDoctorToSentimentReviews();
				
		Map<Integer, List<SentimentSentence>> docToSentimentSentences = convertToDocToSentimentSentences(docToSentimentReviews);
		Map<Integer, List<SentimentSentence>> docToTopSentencesOurMethod = getKSentencesOurMethod(docToSentimentSentences);
		Map<Integer, List<SentimentSentence>> docToTopSentencesBaseline = getKSentencesBaseline(docToSentimentSentences);
		Map<Integer, List<SentimentSentence>> docToTopSentences = new HashMap<Integer, List<SentimentSentence>>();
		for (Integer docId : docToTopSentencesOurMethod.keySet()) {
			docToTopSentences.put(docId, new ArrayList<SentimentSentence>());
			docToTopSentences.get(docId).addAll(docToTopSentencesOurMethod.get(docId));
			docToTopSentencesBaseline.get(docId).stream().forEach(sentence -> {
					if (!docToTopSentences.get(docId).contains(sentence))
						docToTopSentences.get(docId).add(sentence);
			});
		}
		
		String outputFolder = "D:\\";
		String outputExcelPath = outputFolder + "survey.xlsx";
		outputToExcel(docToSentimentSentences, docToTopSentences, outputExcelPath);
		outputToJson(docToTopSentencesOurMethod, outputFolder + "our-method.txt");
		outputToJson(docToTopSentencesBaseline, outputFolder + "baseline.txt");
		
		Utils.printRunningTime(startTime, "Finished outputing survey to \"" + outputExcelPath + "\"");
	}
	
	private static void outputToJson(
			Map<Integer, List<SentimentSentence>> docToTopSentencesOurMethod,
			String outputPath) {
		// TODO Auto-generated method stub
		
	}

	private static Map<Integer, List<SentimentSentence>> convertToDocToSentimentSentences(
			Map<Integer, List<SentimentReview>> docToSentimentReviews) {
		Map<Integer, List<SentimentSentence>> docToSentimentSentences = new HashMap<Integer, List<SentimentSentence>>();
		for (Integer docId : docToSentimentReviews.keySet()) {
			List<SentimentSentence> sentences = new ArrayList<SentimentSentence>();
			docToSentimentReviews.get(docId).stream().forEach(review -> sentences.addAll(review.getSentences()));
			docToSentimentSentences.put(docId, sentences);
		}
		
		return docToSentimentSentences;
	}

	private static Map<Integer, List<SentimentSentence>> getKSentencesOurMethod(Map<Integer, List<SentimentSentence>> docToSentimentSentences) {
		Map<Integer, List<SentimentSet>> docToSentimentSets = new HashMap<Integer, List<SentimentSet>>();
		for (Integer docId : docToSentimentSentences.keySet()) {
			List<SentimentSet> sentimentSets = new ArrayList<SentimentSet>();
			docToSentimentSentences.get(docId).stream().forEach(sentence -> sentimentSets.add(sentence));						
			docToSentimentSets.put(docId, sentimentSets);
		}
				
		Map<Integer, List<SentimentSentence>> docToTopKSentences = new HashMap<Integer, List<SentimentSentence>>();
		
		ConcurrentMap<Integer, StatisticalResult> docToStatisticalResult = new ConcurrentHashMap<Integer, StatisticalResult>();
		ConcurrentMap<Integer, List<SentimentSet>> docToTopKSets = new ConcurrentHashMap<Integer, List<SentimentSet>>();
		
		GreedySetAlgorithm2 ourMethod = 
				new GreedySetAlgorithm2(K, THRESHOLD, docToStatisticalResult, docToTopKSets, 0, 1, docToSentimentSets);
		ourMethod.run();
		
		
		for (Integer docId : docToTopKSets.keySet()) {
			List<SentimentSentence> sentences = new ArrayList<SentimentSentence>();
			docToTopKSets.get(docId).stream().forEach(set -> sentences.add((SentimentSentence) set));
			docToTopKSentences.put(docId, sentences);
		}		
		
		return docToTopKSentences;
	}
	
	private static Map<Integer, List<SentimentSentence>> getKSentencesBaseline(Map<Integer, List<SentimentSentence>> docToSentimentSentences) {
		Map<Integer, List<SentimentSet>> docToSentimentSets = new HashMap<Integer, List<SentimentSet>>();
		for (Integer docId : docToSentimentSentences.keySet()) {
			List<SentimentSet> sentimentSets = new ArrayList<SentimentSet>();
			docToSentimentSentences.get(docId).stream().forEach(sentence -> sentimentSets.add(sentence));						
			docToSentimentSets.put(docId, sentimentSets);
		}
		
		Map<Integer, List<SentimentSentence>> docToTopKSentences = new HashMap<Integer, List<SentimentSentence>>();
		TopSetsBaseline.setK(K);
		for (Integer docId : docToSentimentSets.keySet()) {
			List<SentimentSet> sets = TopSetsBaseline.extractTopKFromList(docToSentimentSets.get(docId));
			List<SentimentSentence> sentences = new ArrayList<SentimentSentence>();
			sets.stream().forEach(set -> sentences.add((SentimentSentence) set));
			docToTopKSentences.put(docId, sentences);
		}
		
		return docToTopKSentences;
	}
	
	private static void outputToExcel(
			Map<Integer, List<SentimentSentence>> docToSentimentSentences,
			Map<Integer, List<SentimentSentence>> docToTopSentences,			 
			String outputExcelPath) {
				
		Map<SentimentSentence, XSSFRichTextString> sentenceToRichText = new HashMap<SentimentSentence, XSSFRichTextString>();
		for (Integer docId : docToSentimentSentences.keySet()) {
			docToSentimentSentences.get(docId).stream().forEach( sentence ->
				sentenceToRichText.put(sentence, prepareColoredRichTextSentence(sentence.getSentence()))
				);
		}
		
		Map<SentimentReview, Set<String>> reviewToCuis = new HashMap<SentimentReview, Set<String>>();
		
		outputToExcel(docToSentimentSentences, docToTopSentences, outputExcelPath, sentenceToRichText);
	}

	private static void outputToExcel(
			Map<Integer, List<SentimentSentence>> docToSentimentSentences, 
			Map<Integer, List<SentimentSentence>> docToTopSentences, 
			String outputExcelPath,
			Map<SentimentSentence, XSSFRichTextString> sentenceToRichText) {
		
		for (Integer docId : docToSentimentSentences.keySet()) {
			XSSFSheet sheet = wb.createSheet(docId + "");
			fillSheet(sheet, docToSentimentSentences.get(docId), docToTopSentences.get(docId), sentenceToRichText);			
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
		Map<Integer, List<SentimentReview>> completeDocToReviews = importRawCompleteDocToReviews(TopPairsProgram.DOC_TO_REVIEWS_PATH);
		Map<Integer, List<SentimentReview>> docToReviews = new HashMap<Integer, List<SentimentReview>>();
		
		//Set<Integer> indices = Utils.randomIndices(completeDocToReviews.size(), NUM_DOCS_TO_SURVEY);
		
		Integer[] allDocIds = new Integer[completeDocToReviews.size()];
		completeDocToReviews.keySet().toArray(allDocIds);
		
		for (Integer index : indices) 
			docToReviews.put(allDocIds[index], completeDocToReviews.get(allDocIds[index]));
		
		Map<Integer, List<SentimentReview>> choosenDocToReviews = new HashMap<Integer, List<SentimentReview>>();
		for (Integer docId : docToReviews.keySet()) {
			choosenDocToReviews.put(docId, pickUpReviewsForSurvey(docToReviews.get(docId)));
		}
		
		return choosenDocToReviews;
	}

	private static List<SentimentReview> pickUpReviewsForSurvey(List<SentimentReview> completeSetOfReviews) {
		
		List<SentimentReview> pickUpReviews = new ArrayList<SentimentReview>();
		
		completeSetOfReviews.removeIf(review -> 
			SentimentCalculator.breakingIntoSentences(review.getRawReview().getBody(), Constants.USE_ADVANCED_SENTENCE_BREAKING)
				.size() > THRESHOLD_ON_NUM_SENTENCE_PER_REVIEW);				
		
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
		Map<Integer, List<SentimentReview>> rawDocToReviews = new HashMap<Integer, List<SentimentReview>>();		
		List<DoctorSentimentReview> doctorSentimentReviews = TopPairsProgram.importDoctorSentimentReviewsDataset(docToReviewsPath);
		
		for (DoctorSentimentReview doctorSentimentReview : doctorSentimentReviews) {		
			List<SentimentReview> reviews = doctorSentimentReview.getSentimentReviews();
			for (SentimentReview review : reviews) 
				for (SentimentSentence sentence : review.getSentences())
					sentence.getPairs().removeIf(pair -> isIgnoreCui(pair.getCui().toUpperCase()));
			
			rawDocToReviews.put(doctorSentimentReview.getDocId(), doctorSentimentReview.getSentimentReviews());			
		}
				
		return rawDocToReviews;
	}
	
	private static boolean isIgnoreCui(String cui) {
		return ignoreCuis.contains(cui);
	}
}
