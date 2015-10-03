package edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset;

import edu.ucr.cs.dblab.nle020.metamap.MetaMapParser;
import edu.ucr.cs.dblab.nle020.metamap.SemanticTypes;
import edu.ucr.cs.dblab.nle020.metamap.Sentence;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentReview;
import edu.ucr.cs.dblab.nle020.utils.Utils;
import gov.nih.nlm.nls.metamap.Ev;
import gov.nih.nlm.nls.metamap.Position;

import java.io.BufferedReader;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.DoubleStream;

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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
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
		
	public void outputExcelFileMultiThread() {
		long startTime = System.currentTimeMillis();
		Sheet sheet = wb.createSheet("concept_sentiment");		
		addHeader(sheet);
		String sourcePath = DESKTOP_FOLDER;
		String outputPath = sourcePath + "reviews_diversity_" + Constants.INTERVAL_TO_SAMPLE_REVIEW + ".xlsx";
		
		FileOutputStream fileOut;
		try {			
			fileOut = new FileOutputStream(outputPath);
			
			CopyOnWriteArrayList<Row> rows = new CopyOnWriteArrayList<Row>();
			for (int i = 0; i < rawReviews.size()/Constants.INTERVAL_TO_SAMPLE_REVIEW + 1; i++) {
				rows.add(sheet.createRow(i + 1));
			}
				
			CopyOnWriteArrayList<RawReview> concurrentReviews = new CopyOnWriteArrayList<RawReview>();
			concurrentReviews.addAllAbsent(rawReviews);
			
			CellStyle cs = wb.createCellStyle();		
			cs.setWrapText(true);
			cs.setShrinkToFit(true);
			
			ConcurrentMap<RawReview, List<ConceptSentimentPair>> docToReviewsValid = new ConcurrentHashMap<RawReview, List<ConceptSentimentPair>>();
			ConcurrentMap<RawReview, List<ConceptSentimentPair>> docToReviewsInvalid = new ConcurrentHashMap<RawReview, List<ConceptSentimentPair>>();		
			
			ExecutorService fixedPool = Executors.newFixedThreadPool(Constants.NUM_THREADS);
			for (int i = 0; i< Constants.NUM_THREADS; i++) {
				fixedPool.submit(new AddRowThread(i, rows, concurrentReviews, cs, greenFont, redFont, docToReviewsValid, docToReviewsInvalid));
			}
			
			fixedPool.shutdown();
			try {
				if (fixedPool.awaitTermination(1, TimeUnit.DAYS)) {
					CorrelationCalculator.calculatePairCorrelation(docToReviewsValid);
					CorrelationCalculator.calculatePairCorrelation(docToReviewsInvalid);
				
					wb.write(fileOut);
					Utils.printRunningTime(startTime, "Outputted excel file to \"" + outputPath + "\"");
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
				Utils.printRunningTime(startTime, "Finished output excel file");
			}
						
			outputDoctorPairsToJson(sourcePath + "valid_" + Constants.INTERVAL_TO_SAMPLE_REVIEW + ".txt", docToReviewsValid);
			outputDoctorPairsToJson(sourcePath + "invalid_" + Constants.INTERVAL_TO_SAMPLE_REVIEW + ".txt", docToReviewsInvalid);	
			
			fileOut.close();
			wb.close();
		} catch (IOException e) {
			e.printStackTrace();
		}		
		
		updateExcelFileWithCorrelation(outputPath, 
				sourcePath + "valid_" + Constants.INTERVAL_TO_SAMPLE_REVIEW + ".txt", 
				sourcePath + "invalid_" + Constants.INTERVAL_TO_SAMPLE_REVIEW + ".txt");
	}
	
	public void updateExcelFileWithCorrelation() {
		String outputPath = DESKTOP_FOLDER + "reviews_diversity_" + Constants.INTERVAL_TO_SAMPLE_REVIEW + ".xlsx";
		
		updateExcelFileWithCorrelation(outputPath, 
				DESKTOP_FOLDER + "valid_" + Constants.INTERVAL_TO_SAMPLE_REVIEW + ".txt", 
				DESKTOP_FOLDER + "invalid_" + Constants.INTERVAL_TO_SAMPLE_REVIEW + ".txt");
	}
	
	private void updateExcelFileWithCorrelation(String excelFilePath, String validConceptPath, String invalidConceptPath) {
		
		Map<RawReview, List<ConceptSentimentPair>> reviewToValidCSPairs = readRawReviewToConceptSentimentPairs(validConceptPath);
		Map<RawReview, List<ConceptSentimentPair>> reviewToInvalidCSPairs = readRawReviewToConceptSentimentPairs(invalidConceptPath);
		
		try {
			Workbook wb = new XSSFWorkbook(excelFilePath);			
			Sheet sheet = wb.getSheetAt(0);
			for (Row row : sheet) {
				if (row.getRowNum() == 0)
					continue;
				updateSentimentCellsOfRow(row, reviewToValidCSPairs, reviewToInvalidCSPairs);
			}
			
			String updatedExcelFilePath = excelFilePath.substring(0, excelFilePath.length() - 5) + "_updated.xlsx";
			wb.write(new FileOutputStream(updatedExcelFilePath));
			wb.close();
			
			System.out.println("Outputted updated excel file to \"" + updatedExcelFilePath + "\"");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @param reviewToValidCSPairs
	 * @param reviewToInvalidCSPairs
	 * @param row
	 */
	private void updateSentimentCellsOfRow(Row row,
			Map<RawReview, List<ConceptSentimentPair>> reviewToValidCSPairs,
			Map<RawReview, List<ConceptSentimentPair>> reviewToInvalidCSPairs) {
		
		Cell cellReviewId = row.getCell(1);
		if (cellReviewId == null) {
			System.err.println("Empty row!!!");
			return;
		} else {
			Integer reviewId= (int) cellReviewId.getNumericCellValue();
			if (reviewId == null) {
				System.err.println("Empty reviewId");
				return;
			} else {
			
				RawReview review = new RawReview(reviewId);
				
				if (reviewToValidCSPairs.containsKey(review)) {
					Cell cellConceptSentiment = row.getCell(10);
					cellConceptSentiment.setCellValue(prepareConceptSentimentCorrelationRichText(reviewToValidCSPairs.get(review)));
				}
				
				if (reviewToInvalidCSPairs.containsKey(review)) {
					Cell cellConceptSentimentInvalid = row.getCell(7);
					cellConceptSentimentInvalid.setCellValue(prepareConceptSentimentCorrelationRichText(reviewToInvalidCSPairs.get(review)));
				}		
			}
		}
	}
	
	private XSSFRichTextString prepareConceptSentimentCorrelationRichText(List<ConceptSentimentPair> pairs) {
		
		XSSFRichTextString conceptSentimentString = new XSSFRichTextString();
		
		List<Integer> naNPositions = new ArrayList<Integer>();
		List<Integer> positions = new ArrayList<Integer>();
		List<Integer> lengthes = new ArrayList<Integer>();
		final int NEGATIVE_LENGTH = 10;
		final int POSITIVE_LENGTH = 9;
		int offset = 0;
		
		StringBuilder conceptSentimentBuilder = new StringBuilder();
		for (ConceptSentimentPair pair : pairs) {
			String appendedLine = pair.getName() + " :  " + 
					String.format("%1$." + Constants.NUM_DIGIT_PRECISION_OF_SENTIMENT + "f", pair.getSentiment()) + 
					" | corr " + String.format("%1$." + Constants.NUM_DIGIT_PRECISION_OF_SENTIMENT + "f", pair.getCorrelation()) + "\n";			
			conceptSentimentBuilder.append(appendedLine);
			
			if (!Float.isNaN(pair.getCorrelation()) && Math.abs(pair.getCorrelation()) < Constants.CORRELATION_THRESHOLD) {
				positions.add(offset + appendedLine.indexOf("corr"));
				if (pair.getCorrelation() < 0)
					lengthes.add(NEGATIVE_LENGTH);
				else
					lengthes.add(POSITIVE_LENGTH);
			}			
			
			if (Float.isNaN(pair.getCorrelation()))
				naNPositions.add(offset + appendedLine.indexOf("corr"));
			
			offset += appendedLine.length();
		}
		
		if (conceptSentimentBuilder.length() > 0) {
			conceptSentimentBuilder.deleteCharAt(conceptSentimentBuilder.length() - 1);
			conceptSentimentString.append(conceptSentimentBuilder.toString());			
		}

		for (int i = 0; i < positions.size(); ++i) {
			conceptSentimentString.applyFont(positions.get(i), positions.get(i) + lengthes.get(i), redFont);
		}
		
		for (Integer naN : naNPositions) {
			if (naN + POSITIVE_LENGTH < conceptSentimentString.length())
				conceptSentimentString.applyFont(naN, naN + POSITIVE_LENGTH, yellowFont);
			else
				conceptSentimentString.applyFont(naN, naN + POSITIVE_LENGTH - 1, yellowFont);
		}
		
		return conceptSentimentString;
	}

	/**
	 * Each line of input file is the Json of type DoctorPairs 
	 * @param doctorPairsPath
	 * @return map: RawReview --> list of ConceptSentimentPairs
	 */
	public static Map<RawReview, List<ConceptSentimentPair>> readRawReviewToConceptSentimentPairs(String doctorPairsPath) {
		
		Map<RawReview, List<ConceptSentimentPair>> rawReviewToConceptSentimentPairs = new HashMap<RawReview, List<ConceptSentimentPair>>();
		
		ObjectMapper mapper = new ObjectMapper();
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(doctorPairsPath))) {
			String line;
			while ((line = reader.readLine()) != null) {
				DoctorPairs docPair	= mapper.readValue(line, DoctorPairs.class);
				rawReviewToConceptSentimentPairs.put(docPair.getRawReview(), docPair.getPairs());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return rawReviewToConceptSentimentPairs;
	}
	

	
	
	public void buildDatasetMultiThreads() {
		long startTime = System.currentTimeMillis();
		
		ConcurrentMap<Integer, List<RawReview>> docToReviews = new ConcurrentHashMap<Integer, List<RawReview>>();

//		for (int i = 0; i < 3000; i += Constants.INTERVAL_TO_SAMPLE_REVIEW) {
		for (int i = 0; i < rawReviews.size(); i += Constants.INTERVAL_TO_SAMPLE_REVIEW) {
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
				outputDoctorSentimentReviewsToJson();
				Utils.printRunningTime(startTime, "Built " + (100.0f / (float) Constants.INTERVAL_TO_SAMPLE_REVIEW) + "% dataset");
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
			outputDoctorSentimentReviewsToJson();
			Utils.printRunningTime(startTime, "Built " + (100.0f / (float) Constants.INTERVAL_TO_SAMPLE_REVIEW) + "% dataset");
		}
	}
	
	public void outputDoctorSentimentReviewsToJson() {
		long startTime = System.currentTimeMillis();
		
		String fileOut = DESKTOP_FOLDER + "doc_pairs_" + Constants.INTERVAL_TO_SAMPLE_REVIEW + ".txt";
		ObjectMapper mapper = new ObjectMapper();

		try {
			Files.deleteIfExists(Paths.get(fileOut));
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		int count = 1;
		for (Integer docId : docToSentimentReviews.keySet()) {
			try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(fileOut), 
				StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
				if (count > 1)						
					writer.newLine();		
				
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
	
	public void outputDoctorPairsToJson(String fileOut, ConcurrentMap<RawReview, List<ConceptSentimentPair>> docToConceptSentimentPairs) {
		long startTime = System.currentTimeMillis();

		ObjectMapper mapper = new ObjectMapper();

		try {
			Files.deleteIfExists(Paths.get(fileOut));
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		int count = 1;
		for (RawReview rawReview : docToConceptSentimentPairs.keySet()) {
			Integer docID = rawReview.getDocID();
			
			try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(fileOut), 
				StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
				if (count > 1)						
					writer.newLine();
				
				DoctorPairs docPair = new DoctorPairs(docID, docToConceptSentimentPairs.get(rawReview));
				docPair.setRawReview(rawReview);
				
				mapper.writeValue(writer, docPair);				
				
//				System.out.println(count + ". Output docID " + docID);
				count++;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		System.out.println("Num of Outputed docIDs " + count);
		Utils.printRunningTime(startTime, "Outputted JSON text file to \"" + fileOut + "\"");
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
	
	private void addHeader(Sheet sheet) {
		Row row = sheet.createRow(0);
		sheet.createFreezePane(11, 1);
		CellStyle cs = wb.createCellStyle();		
		cs.setWrapText(true);
		cs.setFont(headingFont);
		
		Cell cellDoc = row.createCell(0);
		Cell cellReviewId = row.createCell(1);
		Cell cellRate = row.createCell(2);
		Cell cellTitle = row.createCell(3);
		Cell cellBody = row.createCell(4);
		
		cellDoc.setCellValue("DocID");
		cellReviewId.setCellValue("ReviewID");
		cellRate.setCellValue("Rate");
		cellTitle.setCellValue("Title");
		cellBody.setCellValue("RawReview");
		
		
		cellDoc.setCellStyle(cs);
		cellReviewId.setCellStyle(cs);
		cellRate.setCellStyle(cs);
		cellTitle.setCellStyle(cs);
		cellBody.setCellStyle(cs);
		
		Cell cellInvalidConcepts = row.createCell(5);
		Cell cellInvalidConceptTypes = row.createCell(6);
		Cell cellInvalidConceptSentiment = row.createCell(7);
		Cell cellValidConcepts = row.createCell(8);
		Cell cellConceptTypes = row.createCell(9);
		Cell cellConceptSentiment = row.createCell(10);

		cellInvalidConcepts.setCellValue("Invalid Concepts");
		cellInvalidConceptTypes.setCellValue("Invalid Types");
		cellInvalidConceptSentiment.setCellValue("Invalid Concept-Sentiment");
		cellValidConcepts.setCellValue("Valid Concepts");
		cellConceptTypes.setCellValue("Valid Types");
		cellConceptSentiment.setCellValue("Concept-Sentiment Pairs");
		
		
		cellInvalidConcepts.setCellStyle(cs);
		cellInvalidConceptTypes.setCellStyle(cs);
		cellInvalidConceptSentiment.setCellStyle(cs);
		cellValidConcepts.setCellStyle(cs);
		cellConceptTypes.setCellStyle(cs);
		cellConceptSentiment.setCellStyle(cs);

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
