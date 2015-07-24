package edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset;

import edu.ucr.cs.dblab.nle020.metamap.MetaMapParser;
import edu.ucr.cs.dblab.nle020.metamap.SemanticTypes;
import edu.ucr.cs.dblab.nle020.metamap.Sentence;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentReview;
import edu.ucr.cs.dblab.nle020.utilities.Utils;
import gov.nih.nlm.nls.metamap.Ev;
import gov.nih.nlm.nls.metamap.Position;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 
 * @author Thong Nhat
 *
 */
public class PairExtractor {
	private SentimentCalculator sentimentCalculator = new SentimentCalculator();
	
	private final static String REVIEWS_PATH = "D:\\Dropbox\\Reviews Diversity\\most_reviewed_providers.csv";
	private final static String DESKTOP_FOLDER;	
	static {
		if (Files.isDirectory(Paths.get("C:\\Users\\Thong Nhat\\Desktop")))
			DESKTOP_FOLDER = "C:\\Users\\Thong Nhat\\Desktop\\";
		 else 
			DESKTOP_FOLDER = "C:\\Users\\Nhat XT Le\\Desktop\\";
	}
	
	private List<RawReview> rawReviews = null;
//	private Map<Integer, List<ConceptSentimentPair>> docToPairs = new HashMap<Integer, List<ConceptSentimentPair>>();
//	private ConcurrentMap<Integer, List<ConceptSentimentPair>> docToPairs = new ConcurrentHashMap<Integer, List<ConceptSentimentPair>>();
	private ConcurrentMap<Integer, List<SentimentReview>> docToSentimentReviews = new ConcurrentHashMap<Integer, List<SentimentReview>>();
		
	private Workbook wb = new XSSFWorkbook();
	private XSSFFont redFont = (XSSFFont) wb.createFont();
	private Font headingFont = wb.createFont();
	private Font greenFont = wb.createFont();
	private Font greyFont = wb.createFont();
	private Font blueFont = wb.createFont();
	private Font yellowFont = wb.createFont();	

	private MetaMapParser mmParser = new MetaMapParser();

	private static final PairExtractor INSTANCE = new PairExtractor();
	public static PairExtractor getInstance() {
		return INSTANCE;
	}
	
	private PairExtractor() { 
		init();
	}
	
	private void init() {

		rawReviews = new CopyOnWriteArrayList<RawReview>();
		rawReviews.addAll(getReviews(REVIEWS_PATH));
		//rawReviews = getReviews(REVIEWS_PATH);
		
		initExcelColor();
	}


	private void initExcelColor() {
		redFont.setColor(HSSFColor.RED.index);
		redFont.setUnderline((byte) 1);
		redFont.setBold(true);	

		greenFont.setColor(HSSFColor.GREEN.index);
		greenFont.setUnderline((byte) 1);
		greenFont.setBold(true);
		
		greyFont.setColor(HSSFColor.BLUE_GREY.index);
		greyFont.setUnderline((byte) 1);
		greyFont.setBold(true);
		
		blueFont.setColor(HSSFColor.BLUE.index);
		blueFont.setUnderline((byte) 1);
		blueFont.setBold(true);
		
		yellowFont.setColor(HSSFColor.DARK_YELLOW.index);
		yellowFont.setUnderline((byte) 1);
		yellowFont.setBold(true);
		
		headingFont.setBold(true);
		headingFont.setFontName("Calibri");
		headingFont.setFontHeightInPoints((short) 13);
		headingFont.setColor(IndexedColors.BLUE_GREY.index);
	}
	
	public void outputExcelFile() {
		Date start = new Date();	
		
		Sheet sheet = wb.createSheet("concept_sentiment");		
		addHeader(sheet);
		
		FileOutputStream fileOut;
		try {			
			fileOut = new FileOutputStream(DESKTOP_FOLDER + "reviews_diversity_" + Constants.INTERVAL + ".xlsx");
			
//			for (RawReview review : rawReviews) {
			for (int i = 0; i < rawReviews.size(); i += Constants.INTERVAL ) {
//			for (int i = 0; i <= 100; i += Constants.INTERVAL ) {
				RawReview rawReview = rawReviews.get(i);	
				
				//addRow(sheet, i/interval + 1, review);
				Row row = sheet.createRow(i/Constants.INTERVAL + 1);
				addRow(row, rawReview);				
			}
						
			wb.write(fileOut);
			fileOut.close();
			wb.close();
		} catch (IOException e) {
			e.printStackTrace();
		}		

		Date finish = new Date();
		System.out.println("Running time: " + (finish.getTime() - start.getTime()) + " ms");
	}
		
		
	public void outputExcelFileMultiThread() {
		long startTime = System.currentTimeMillis();
		Sheet sheet = wb.createSheet("concept_sentiment");		
		addHeader(sheet);
		
		FileOutputStream fileOut;
		try {			
			fileOut = new FileOutputStream(DESKTOP_FOLDER + "reviews_diversity_" + Constants.INTERVAL + ".xlsx");
			//fileOut = new FileOutputStream("D:\\UCR Google Drive\\GD - RawReview Diversity\\" + "reviews_diversity" + Constants.INTERVAL + ".xlsx");
			
			CopyOnWriteArrayList<Row> rows = new CopyOnWriteArrayList<Row>();
			for (int i = 0; i < rawReviews.size()/Constants.INTERVAL + 1; i++) {
				rows.add(sheet.createRow(i + 1));
			}
				
			CopyOnWriteArrayList<RawReview> concurrentReviews = new CopyOnWriteArrayList<RawReview>();
			concurrentReviews.addAllAbsent(rawReviews);
			
			CellStyle cs = wb.createCellStyle();		
			cs.setWrapText(true);
			cs.setShrinkToFit(true);
			
			ExecutorService fixedPool = Executors.newFixedThreadPool(Constants.NUM_THREADS);
			for (int i = 0; i< Constants.NUM_THREADS; i++) {
				fixedPool.submit(new AddRowThread(i, rows, concurrentReviews, cs, greenFont, redFont));
			}
			
			fixedPool.shutdown();
			try {
				if (fixedPool.awaitTermination(1, TimeUnit.DAYS)) {
					wb.write(fileOut);
					Utils.printRunningTime(startTime, "Finished output excel file");
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
				Utils.printRunningTime(startTime, "Finished output excel file");
			}
			

			fileOut.close();
			wb.close();
		} catch (IOException e) {
			e.printStackTrace();
		}		
	}
	
	private void addRow(Row row, RawReview rawReview) {		
		CellStyle cs = wb.createCellStyle();		
		cs.setWrapText(true);
		cs.setShrinkToFit(true);
		
		addDefaultCells(row, cs, rawReview);				

		Cell cellInvalidConcepts = row.createCell(4);
		Cell cellInvalidConceptTypes = row.createCell(5);
		Cell cellValidConcepts = row.createCell(6);
		Cell cellConceptTypes = row.createCell(7);
		Cell cellConceptSentiment = row.createCell(8);

		cellInvalidConcepts.setCellStyle(cs);
		cellValidConcepts.setCellStyle(cs);
		cellConceptTypes.setCellStyle(cs);
		cellConceptSentiment.setCellStyle(cs);
		cellInvalidConceptTypes.setCellStyle(cs);
		
		String body = rawReview.getBody();		
		
		XSSFRichTextString invalidConcepts = new XSSFRichTextString(body);
		XSSFRichTextString validConcepts = new XSSFRichTextString(body);
		XSSFRichTextString invalidConceptTypes = new XSSFRichTextString();
		XSSFRichTextString conceptTypes = new XSSFRichTextString();
		XSSFRichTextString conceptSentimentString = new XSSFRichTextString(); 
		
		StringBuilder invalidConceptTypesBuilder = new StringBuilder();
		StringBuilder conceptTypesBuilder = new StringBuilder();
		StringBuilder conceptSentimentBuilder = new StringBuilder();
			
		Map<Sentence, List<Ev>> sentenceMap = mmParser.parseToSentenceMap(body);
		for (Map.Entry<Sentence, List<Ev>> entry : sentenceMap.entrySet()) {

			// Building the value for columns: cellInvalidConcepts, cellValidConcepts, cellConceptTypes
			for (Ev ev : entry.getValue()) {
				// For each mapping
				
				List<Position> positions;
				try {
					positions = ev.getPositionalInfo();

					List<String> matchedWords = ev.getMatchedWords();
					List<String> types = ev.getSemanticTypes();
					
					for (int i = 0; i < positions.size(); i++) {
						Position pos = positions.get(i);
					
						if (mmParser.isValidType(types)) {
							validConcepts.applyFont(pos.getX(), pos.getX() + pos.getY(), greenFont);
							
							// TODO - out of bound exception here		
							if (i < matchedWords.size())
								conceptTypesBuilder.append(ev.getConceptId() + "-" + ev.getConceptName() + "-\"" + matchedWords.get(i) + "\": ");
							else 
								conceptTypesBuilder.append(ev.getConceptId() + "-" + ev.getConceptName() + "-\"NO_MATCHED_WORD\": ");
							
							for (String type : types) {
								conceptTypesBuilder.append(type + " - ");
								conceptTypesBuilder.append(SemanticTypes.getInstance().getFullTypeNameByType(type) + " - ");						
								conceptTypesBuilder.append(SemanticTypes.getInstance().getGroupNameByType(type) + "; ");
								for (String source : ev.getSources()) {
									if (source.startsWith("SNOMED"))
										conceptTypesBuilder.append(" " + source);
								}
								
								if (ev.getNegationStatus() == 1)
									conceptTypesBuilder.append(". Negated!");
								
							}
							conceptTypesBuilder.append("\n");
						} else {
							invalidConcepts.applyFont(pos.getX(), pos.getX() + pos.getY(), redFont);
							
							invalidConceptTypesBuilder.append(ev.getConceptId() + "-" + ev.getConceptName() + "-\"" + matchedWords.get(i) + "\": ");
							
							for (String type : types) {
								invalidConceptTypesBuilder.append(type + " - ");
								invalidConceptTypesBuilder.append(SemanticTypes.getInstance().getFullTypeNameByType(type) + " - ");
								invalidConceptTypesBuilder.append(SemanticTypes.getInstance().getGroupNameByType(type) + "; ");
							}
							invalidConceptTypesBuilder.append("\n");
						}
					}
					
				} catch (Exception e) {					
					e.printStackTrace();
				}
			}	
		}
			
		// Building the value for columns: conceptSentiment
		List<ConceptSentimentPair> pairs = sentimentCalculator.calculateSentiment(sentenceMap);
		for (ConceptSentimentPair pair : pairs) {
			conceptSentimentBuilder.append(pair.getName() + " :  " + String.format("%1$.3f", pair.getSentiment()) + "\n");
		}
			
		// Finalizing	
		if (invalidConceptTypesBuilder.length() > 0) {
			invalidConceptTypesBuilder.deleteCharAt(invalidConceptTypesBuilder.length() - 1);

			String content = invalidConceptTypesBuilder.toString();
			invalidConceptTypes.append(content);
			int startIndex = 0;
			int endIndex = -1;
			while (endIndex < content.length()) {
				startIndex = content.indexOf("\"", endIndex + 1);
				endIndex = content.indexOf("\"", startIndex + 1);
				
				if (startIndex >= 0 && startIndex < content.length() && endIndex > 0 && endIndex < content.length())
					invalidConceptTypes.applyFont(startIndex, endIndex, redFont);
				else
					break;
			}			
			
			cellInvalidConceptTypes.setCellValue(invalidConceptTypes);
		}
		
		if (conceptTypesBuilder.length() > 0) {
			conceptTypesBuilder.deleteCharAt(conceptTypesBuilder.length() - 1);
			
			String content = conceptTypesBuilder.toString();
			conceptTypes.append(content);
			
			
			int startIndex = 0;
			int endIndex = -1;
			while (endIndex < content.length()) {
				startIndex = content.indexOf("\"", endIndex + 1);
				endIndex = content.indexOf("\"", startIndex + 1);
				
				if (startIndex >= 0 && startIndex < content.length() && endIndex > 0 && endIndex < content.length())
					conceptTypes.applyFont(startIndex, endIndex, greenFont);
				else
					break;
			}			
			
			cellConceptTypes.setCellValue(conceptTypes);
		}
		
		if (conceptSentimentBuilder.length() > 0) {
			conceptSentimentBuilder.deleteCharAt(conceptSentimentBuilder.length() - 1);
			conceptSentimentString.append(conceptSentimentBuilder.toString());		
			cellConceptSentiment.setCellValue(conceptSentimentString);
		}
		
		cellValidConcepts.setCellValue(validConcepts);
		cellInvalidConcepts.setCellValue(invalidConcepts);
	}
	
/*	public void buildDataset() {
		long startTime = System.currentTimeMillis();
		
//		for (RawReview review : rawReviews) {
		for (int i = 0; i < rawReviews.size(); i += Constants.INTERVAL ) {
//		for (int i = 0; i <= 500; i += Constants.INTERVAL ) {
			RawReview rawReview = rawReviews.get(i);	
			Integer docID = rawReview.getDocID();
						
			
			List<ConceptSentimentPair> pairs = getConceptSentimentPairs(rawReview);
			if (!docToPairs.containsKey(docID)) {
				docToPairs.put(docID, new ArrayList<ConceptSentimentPair>());
				docToPairs.get(docID).addAll(pairs);
			} else {
				List<ConceptSentimentPair> currentPairs = docToPairs.get(docID);
				for (ConceptSentimentPair pair : pairs) {
					if (!currentPairs.contains(pair)) {
						currentPairs.add(pair);
					} else {
						currentPairs.get(currentPairs.indexOf(pair)).incrementCount(pair.getCount());
					}
				}
			}	
		}
		
		outputPairsToJson();
		
		Utils.printRunningTime(startTime, "Built " + (100.0f / (float) Constants.INTERVAL) + "% dataset");
	}*/
	
	public void buildDatasetMultiThreads() {
		long startTime = System.currentTimeMillis();
		
		ConcurrentMap<Integer, List<RawReview>> docToReviews = new ConcurrentHashMap<Integer, List<RawReview>>();

//		for (int i = 0; i < 3000; i += Constants.INTERVAL) {
		for (int i = 0; i < rawReviews.size(); i += Constants.INTERVAL) {
			RawReview rawReview = rawReviews.get(i);	
			Integer docID = rawReview.getDocID();
			if (!docToReviews.containsKey(docID)) {
				docToReviews.put(docID, new ArrayList<RawReview>());
			}
			
			docToReviews.get(docID).add(rawReview);
		}
		
		ExecutorService fixedPool = Executors.newFixedThreadPool(Constants.NUM_THREADS);
		for (int i = 0; i< Constants.NUM_THREADS; i++) {
//			fixedPool.submit(new PairExtractorThread(docToReviews, docToPairs, i));
			fixedPool.submit(new PairExtractorThread(docToReviews, docToSentimentReviews, i));
		}
		fixedPool.shutdown();
		
		
		try {
			if (fixedPool.awaitTermination(1, TimeUnit.DAYS)) {
				outputPairsToJson();
				Utils.printRunningTime(startTime, "Built " + (100.0f / (float) Constants.INTERVAL) + "% dataset");
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
			outputPairsToJson();
			Utils.printRunningTime(startTime, "Built " + (100.0f / (float) Constants.INTERVAL) + "% dataset");
		}
	}
	
	public void outputPairsToJson() {
		long startTime = System.currentTimeMillis();
		
		String fileOut = DESKTOP_FOLDER + "doc_pairs_" + Constants.INTERVAL + ".txt";
		ObjectMapper mapper = new ObjectMapper();

		try {
			Files.deleteIfExists(Paths.get(fileOut));
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		int count = 1;
//		for (Integer docID : docToPairs.keySet()) {
		for (Integer docId : docToSentimentReviews.keySet()) {
			try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(fileOut), 
				StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
				if (count > 1)						
					writer.newLine();
				
				/*DoctorPairs docPair = new DoctorPairs(docID, docToPairs.get(docID));					
				mapper.writeValue(writer, docPair);*/				
				DoctorSentimentReview doctorReview = new DoctorSentimentReview(docId, docToSentimentReviews.get(docId));
				mapper.writeValue(writer, doctorReview);
				
//				System.out.println(count + ". Output docID " + docID);
				count++;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		System.out.println("Num of Outputed docIDs " + count);
		long finishTime = System.currentTimeMillis();
		System.out.println("Finish outputing JSON text file to \"" + fileOut + "\" in " + (finishTime - startTime) + " ms");
	}
	
	public void testSentimentCalculation() {
		for (RawReview rawReview : rawReviews){
			if (rawReview.getDocID() == 138856 && rawReview.getBody().startsWith("I would like to share my wonderful experience")) {
				getConceptSentimentPairs(rawReview);				
			}
		}
	}
	
	private List<ConceptSentimentPair> getConceptSentimentPairs(RawReview rawReview) {			
		Map<Sentence, List<Ev>> sentenceMap = mmParser.parseToSentenceMap(rawReview.getBody());		
		return sentimentCalculator.calculateSentiment(sentenceMap);
	}
	

	private void addHeader(Sheet sheet) {
		Row row = sheet.createRow(0);
		sheet.createFreezePane(9, 1);
		CellStyle cs = wb.createCellStyle();		
		cs.setWrapText(true);
		cs.setFont(headingFont);
		
		Cell cellDoc = row.createCell(0);
		Cell cellRate = row.createCell(1);
		Cell cellTitle = row.createCell(2);
		Cell cellBody = row.createCell(3);
		
		cellDoc.setCellValue("DocID");
		cellRate.setCellValue("Rate");
		cellTitle.setCellValue("Title");
		cellBody.setCellValue("RawReview");
		
		
		cellDoc.setCellStyle(cs);
		cellRate.setCellStyle(cs);
		cellTitle.setCellStyle(cs);
		cellBody.setCellStyle(cs);
		
		Cell cellInvalidConcepts = row.createCell(4);
		Cell cellInvalidConceptTypes = row.createCell(5);
		Cell cellValidConcepts = row.createCell(6);
		Cell cellConceptTypes = row.createCell(7);
		Cell cellConceptSentiment = row.createCell(8);

		cellInvalidConcepts.setCellValue("Invalid Concepts");
		cellInvalidConceptTypes.setCellValue("Invalid Types");
		cellValidConcepts.setCellValue("Valid Concepts");
		cellConceptTypes.setCellValue("Valid Types");
		cellConceptSentiment.setCellValue("Concept-Sentiment Pairs");
		
		
		cellInvalidConcepts.setCellStyle(cs);
		cellInvalidConceptTypes.setCellStyle(cs);
		cellValidConcepts.setCellStyle(cs);
		cellConceptTypes.setCellStyle(cs);
		cellConceptSentiment.setCellStyle(cs);

	}
	
	// Add 4 default cells: doctor ID, review's rate, title, original body
	private void addDefaultCells(Row row, CellStyle cs, RawReview rawReview) {
		Cell cellDoc = row.createCell(0);
		Cell cellRate = row.createCell(1);
		Cell cellTitle = row.createCell(2);
		Cell cellBody = row.createCell(3);
		
		cellDoc.setCellStyle(cs);
		cellRate.setCellStyle(cs);
		cellTitle.setCellStyle(cs);
		cellBody.setCellStyle(cs);
		
		cellDoc.setCellValue(rawReview.getDocID());
		cellRate.setCellValue(rawReview.getRate());
		cellTitle.setCellValue(rawReview.getTitle());
		cellBody.setCellValue(rawReview.getBody());
	}
	
	private List<RawReview> getReviews(String file) {
		long startTime = System.currentTimeMillis();	

		List<RawReview> results = new ArrayList<RawReview>();
		
		try {
			Reader in = new FileReader(file);
			Iterable<CSVRecord> records = CSVFormat.EXCEL.parse(in);
			
			boolean isFirst = true;
			for (CSVRecord record : records) {
				if (isFirst) {
					isFirst = false;
					continue;
				}
				
				int id = Integer.parseInt(record.get(0).trim());
				int docID = Integer.parseInt(record.get(1).trim());
				String tittle = record.get(5).trim();
				String body = record.get(6).trim();
				body = body.replace("DR.", "DR").replace("dr.", "dr").replace("Dr.", "Dr").replace("dR.", "dR");
				body = body.replace("Drs.", "Drs").replace("DRs.", "DRs");
				
				body = body.replace("Mr.", "Mr").replace("mr.", "mr").replace("MR.", "MR");
				body = body.replace("Mrs.", "Mrs").replace("mrs.", "mrs").replace("MRS.", "MRS");
				body = body.replace("Miss.", "Miss");
				
				// The review "Â " cause MetaMap server to fail				
				body = body.replace("Â ", "");
				body = body.replace("Â", "");
				body = body.replaceAll("â€¦", "").replaceAll("¨", "").replaceAll("¦", "").replace("Ã¯»¿", "");
				
				if (body.trim().length() == 0)
					continue;
				
				String temp = record.get(8).trim();
				int rate = -1;
				if (temp.length() > 0)
					rate = Integer.parseInt(temp);
				else {
					// Empty rate -> guess based on other minor rates					
					int sum = 0;
					int count = 0;
					
					for (int i = 9; i <= 15; i++) {						
						if (record.get(i).trim().length() > 0) {
							sum += Integer.parseInt(record.get(i).trim());
							count++;
						}							
					}
					if (sum > 0) {
						rate = sum/count;
				//		System.out.println("RawReview: " + body + ", rate: " + rate);
					} else {
				//		System.out.println("RawReview: " + body);
					}				
				}
				
				if (rate >= 0) {
					RawReview rawReview = new RawReview(id, docID, tittle, body, rate);
					results.add(rawReview);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		Utils.printRunningTime(startTime, "Importing " + results.size() + " rawReviews");
		
		return results;
	}
}
