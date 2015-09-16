package edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
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

import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import edu.ucr.cs.dblab.nle020.metamap.SemanticConstants;
import edu.ucr.cs.dblab.nle020.metamap.SemanticGroups;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.utilities.Utils;

public class PairExtractingExperimentation {	
	private final static int HEADING_FONT_INDEX = 0;
	private final static int RED_FONT_INDEX = 1;
	private final static int GREEN_FONT_INDEX = 2;
	private final static int YELLOW_FONT_INDEX = 3;
	private final static int BLUE_FONT_INDEX = 4;
	private final static int GREY_FONT_INDEX = 4;
	
	private final static String SOURCE_FOLDER = Constants.SOURCE_PATH;	
	private final static String DESKTOP_FOLDER;
	static {
		if (Files.isDirectory(Paths.get("C:\\Users\\Thong Nhat\\Desktop")))
			DESKTOP_FOLDER = "C:\\Users\\Thong Nhat\\Desktop\\";
		 else 
			DESKTOP_FOLDER = "C:\\Users\\Nhat XT Le\\Desktop\\";
	}
	private static int trainingSetSize = Constants.TRAINING_SIZE;
//	private static String sourceFolder = DESKTOP_FOLDER;
	private static String sourceFolder = Constants.SOURCE_PATH;
	private static float correlationThreshold = Constants.CORRELATION_THRESHOLD;
	
	private static Set<String> defaultValidTypes = new HashSet<String>();
	private static Set<String> defaultInvalidTypes = new HashSet<String>();	
	private static Set<String> invalidSemanticTypes = new HashSet<String>();
	
	private enum CorrelationMethod {SENTIMENT, PRESENCE};
	private enum SentimentCalculationMethod {COUNT_CONCEPT_SENTIMENT, NOT_COUNT_CONCEPT_SENTIMENT};
	private enum ClassifyingMethod {CORRELATION_ONLY, CORRELATION_AND_TYPE_FILTER, TYPE_FILTER_ONLY};
	
	static {
		initDefaultValidTypes();
		initDefaultInvalidTypes();
		
		initInvalidTypes();
	}
	
	private static void initDefaultValidTypes() {
		Set<String> defaultSemanticGroups = new HashSet<String>();
		defaultSemanticGroups.add(SemanticConstants.DISO);

		defaultSemanticGroups.stream().forEach(group -> defaultValidTypes.addAll(SemanticGroups.getTypesOfGroup(group)));	
	}
	
	private static void initDefaultInvalidTypes() {
		Set<String> defaultInvalidSemanticGroups = new HashSet<String>();
		defaultInvalidSemanticGroups.add(SemanticConstants.CHEM);		// Chemicals & Drugs
		defaultInvalidSemanticGroups.add(SemanticConstants.GEOG);		// Geographic Areas
		defaultInvalidSemanticGroups.add(SemanticConstants.CONC);		// Concepts & Ideas			
		defaultInvalidSemanticGroups.add(SemanticConstants.OBJC);		// Objects	
		defaultInvalidSemanticGroups.add(SemanticConstants.LIVB);		// Living Beings

		defaultInvalidSemanticGroups.stream().forEach(group -> defaultInvalidTypes.addAll(SemanticGroups.getTypesOfGroup(group)));
			
		// Some types of LIVB group
		defaultInvalidTypes.remove("prog");		// Professional or Occupational Group
		defaultInvalidTypes.remove("Virus");		// Virus
		
		defaultInvalidTypes.add("orga");			// Organism Attribute - PHYS
	}
	
	private static void initInvalidTypes() {
		Set<String> invalidSemanticGroups = new HashSet<String>();		
		invalidSemanticGroups.add(SemanticConstants.CHEM);		// Chemicals & Drugs
		invalidSemanticGroups.add(SemanticConstants.GEOG);		// Geographic Areas
		invalidSemanticGroups.add(SemanticConstants.CONC);		// Concepts & Ideas		
		invalidSemanticGroups.add(SemanticConstants.OBJC);		// Objects		
		invalidSemanticGroups.add(SemanticConstants.LIVB);		// Living Beings
		
		for (String group : invalidSemanticGroups) {
			invalidSemanticTypes.addAll(SemanticGroups.getTypesOfGroup(group));
		}

		invalidSemanticTypes.add("menp");			// Mental Process - PHYS
		
		// Some types of LIVB group
		invalidSemanticTypes.remove("prog");		// Professional or Occupational Group
		invalidSemanticTypes.remove("Virus");		// Virus
		
		invalidSemanticTypes.add("orga");			// Organism Attribute - PHYS
	}
	
	public static void runExperiment() {
		long startTime = System.currentTimeMillis();
		
		Workbook wb = new XSSFWorkbook();
		Sheet sheet = wb.createSheet("Extracting Methods");
		Font[] coloredFonts = initExcelColor(wb);

		
		String outputPath = sourceFolder + "pair_extracting_experimentation.xlsx";
		try {
			Files.deleteIfExists(Paths.get(outputPath));
		} catch (IOException e) {
			e.printStackTrace();
		}
/*		appendToOutputCSV(outputPath, "ClassifyingMethod, SentimentCalculationMethod, "
				+ "CorrelationMethod, CorrelationThreshold, found_valid(true pos)/valid, false_found_valid(false pos)/invalid");*/
		
		
		String trainingSetPath = SOURCE_FOLDER + "sample_based_on_type_final.xlsx";
		List<String> labeledValidCuis = new ArrayList<String>();
		List<String> labeledInvalidCuis = new ArrayList<String>();
		importTrainingSet(trainingSetPath, labeledValidCuis, labeledInvalidCuis);
				
		int validSize = labeledValidCuis.size();
		List<String> trainingValidCuis = labeledValidCuis.subList(0, validSize / 2);
		List<String> testingValidCuis = labeledValidCuis.subList(validSize / 2, labeledValidCuis.size());
		List<String> trainingInvalidCuis = labeledInvalidCuis.subList(0, trainingSetSize / 2 - validSize / 2);
		List<String> testingInvalidCuis = labeledInvalidCuis.subList(trainingSetSize / 2 - validSize / 2, labeledInvalidCuis.size());		
		
		addHeader(sheet, coloredFonts, testingValidCuis.size(), testingInvalidCuis.size());
		
		ClassifyingMethod[] classifyingMethods = new ClassifyingMethod[] {
				ClassifyingMethod.CORRELATION_ONLY, 
				ClassifyingMethod.CORRELATION_AND_TYPE_FILTER, 
				ClassifyingMethod.TYPE_FILTER_ONLY
				};
		
		SentimentCalculationMethod[] sentimentCalculationMethods = new SentimentCalculationMethod[] {
				SentimentCalculationMethod.COUNT_CONCEPT_SENTIMENT,
				SentimentCalculationMethod.NOT_COUNT_CONCEPT_SENTIMENT};
		
		CorrelationMethod[] correlationMethods = new CorrelationMethod[] {
				CorrelationMethod.SENTIMENT,
				CorrelationMethod.PRESENCE};
		float[] correlationThresholdCandidates = new float[] {0.01f, 0.02f, 0.04f, 0.05f, 0.07f, 0.1f, 0.15f, 0.2f, 0.25f, 0.3f}; 
		
		Map<RawReview, List<ConceptSentimentPair>> originalReviewToCSPairs = importReviewToConceptSentimentPairs();
		
		int rowIndex = 1;
		for (ClassifyingMethod classifyingMethod : classifyingMethods) {
			for (SentimentCalculationMethod sentimentCalculationMethod : sentimentCalculationMethods) {
				Map<RawReview, List<ConceptSentimentPair>> reviewToCSPairs = 
						copyOriginalReviewToCSPairs(originalReviewToCSPairs, sentimentCalculationMethod);
				
				for (CorrelationMethod correlationMethod : correlationMethods) {
					//reviewToCSPairs = calculateCorrelation(reviewToCSPairs, correlationMethod);
					Set<ConceptSentimentPair> correlatedPairs = calculateCorrelation2(reviewToCSPairs, correlationMethod); 
					
					Map<Float, Float> thresholdToFScore = new HashMap<Float, Float>();
					for (float correlationThresholdCandidate : correlationThresholdCandidates) {
						correlationThreshold = correlationThresholdCandidate;			
						
						List<ConceptSentimentPair> validPairs = new ArrayList<ConceptSentimentPair>();
						List<ConceptSentimentPair> invalidPairs = new ArrayList<ConceptSentimentPair>();
						filterValidPairs(correlatedPairs, classifyingMethod, validPairs, invalidPairs);
						
						int truePos = countMatchedConcepts(validPairs, trainingValidCuis);
						int falsePos = countMatchedConcepts(validPairs, trainingInvalidCuis);
						int trueNeg = countMatchedConcepts(invalidPairs, trainingInvalidCuis);
						int falseNeg = countMatchedConcepts(invalidPairs, trainingValidCuis);
						float precision = (float) truePos / (float) (truePos + falsePos);
						float recall = (float) truePos / (float) (truePos + falseNeg);
						float fScore = 2 * precision * recall / (precision + recall);
						
						thresholdToFScore.put(correlationThresholdCandidate, fScore);
						
/*						addRow(sheet, rowIndex, coloredFonts, 
								classifyingMethod, sentimentCalculationMethod, correlationMethod, correlationThreshold,
								truePos, falsePos, trueNeg, falseNeg, fScore);
						++rowIndex;*/
					}
					
					float optimalThreshold = 0.0f;
					float maxFScore = 0.0f;
					for (Float threshold : thresholdToFScore.keySet()) {
						if (maxFScore < thresholdToFScore.get(threshold)) {
							optimalThreshold = threshold;
							maxFScore = thresholdToFScore.get(threshold);
						}
					}
					
					correlationThreshold = optimalThreshold;
					List<ConceptSentimentPair> validPairs = new ArrayList<ConceptSentimentPair>();
					List<ConceptSentimentPair> invalidPairs = new ArrayList<ConceptSentimentPair>();
					filterValidPairs(correlatedPairs, classifyingMethod, validPairs, invalidPairs);
					
					int truePos = countMatchedConcepts(validPairs, testingValidCuis);
					int falsePos = countMatchedConcepts(validPairs, testingInvalidCuis);
					int trueNeg = countMatchedConcepts(invalidPairs, testingInvalidCuis);
					int falseNeg = countMatchedConcepts(invalidPairs, testingValidCuis);
					float precision = (float) truePos / (float) (truePos + falsePos);
					float recall = (float) truePos / (float) (truePos + falseNeg);
					float fScore = 2 * precision * recall / (precision + recall);
					

					addRow(sheet, rowIndex, coloredFonts, 
							classifyingMethod, sentimentCalculationMethod, correlationMethod, correlationThreshold,
							truePos, falsePos, trueNeg, falseNeg, fScore);
					++rowIndex;
				}
			}	

		}	

		
		try {
			wb.write(new FileOutputStream(outputPath));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		Utils.printRunningTime(startTime, "Outputed to \"" + outputPath + "\"", true);
	}

	private static void addRow(Sheet sheet, int rowIndex, Font[] coloredFonts,
			ClassifyingMethod classifyingMethod,
			SentimentCalculationMethod sentimentCalculationMethod,
			CorrelationMethod correlationMethod,
			float correlationThresholdCandidate, int truePos, int falsePos,
			int trueNeg, int falseNeg, float fScore) {

		Row row = sheet.createRow(rowIndex);
		CellStyle cs = sheet.getWorkbook().createCellStyle();		
		cs.setWrapText(true);		
		
		Cell cellClassifyingMethod = row.createCell(0);
		Cell cellSentimentMethod = row.createCell(1);		
		Cell cellCorrelationMethod = row.createCell(2);
		Cell cellCorrelationThreshold = row.createCell(3);
		Cell cellTruePos = row.createCell(4);
		Cell cellFalsePos = row.createCell(5);
		Cell cellTrueNeg = row.createCell(6);
		Cell cellFalseNeg = row.createCell(7);
		Cell cellFScore = row.createCell(8);
		
		cellClassifyingMethod.setCellValue(classifyingMethod.toString());
		cellSentimentMethod.setCellValue(sentimentCalculationMethod.toString());
		cellCorrelationMethod.setCellValue(correlationMethod.toString());
		cellCorrelationThreshold.setCellValue(correlationThresholdCandidate);
		cellTruePos.setCellValue(truePos);
		cellFalsePos.setCellValue(falsePos);
		cellTrueNeg.setCellValue(trueNeg);
		cellFalseNeg.setCellValue(falseNeg);
		cellFScore.setCellValue(fScore);
		
		cellClassifyingMethod.setCellStyle(cs);
		cellSentimentMethod.setCellStyle(cs);
		cellCorrelationMethod.setCellStyle(cs);
		cellCorrelationThreshold.setCellStyle(cs);
		cellTruePos.setCellStyle(cs);		
		cellFalsePos.setCellStyle(cs);
		cellTrueNeg.setCellStyle(cs);
		cellFalseNeg.setCellStyle(cs);
		cellFScore.setCellStyle(cs);
	}

	private static void addHeader(Sheet sheet, Font[] coloredFonts, int testingValidSize, int testingInvalidSize) {
		Row row = sheet.createRow(0);
		sheet.createFreezePane(10, 1);
		CellStyle cs = sheet.getWorkbook().createCellStyle();		
		cs.setWrapText(true);
		cs.setFont(coloredFonts[HEADING_FONT_INDEX]);
		
		Cell cellClassifyingMethod = row.createCell(0);
		Cell cellSentimentMethod = row.createCell(1);		
		Cell cellCorrelationMethod = row.createCell(2);
		Cell cellCorrelationThreshold = row.createCell(3);
		Cell cellTruePos = row.createCell(4);
		Cell cellFalsePos = row.createCell(5);
		Cell cellTrueNeg = row.createCell(6);
		Cell cellFalseNeg = row.createCell(7);
		Cell cellFScore = row.createCell(8);
		Cell cellTestSize = row.createCell(9);
		
		cellClassifyingMethod.setCellValue("Classifying Method");
		cellSentimentMethod.setCellValue("Sentiment Method");
		cellCorrelationMethod.setCellValue("Correlation Method");
		cellCorrelationThreshold.setCellValue("Correlation Threshold");
		cellTruePos.setCellValue("True Pos");
		cellFalsePos.setCellValue("False Pos");
		cellTrueNeg.setCellValue("True Neg");
		cellFalseNeg.setCellValue("False Neg");
		cellFScore.setCellValue("F-score");
		cellTestSize.setCellValue("Test Size: #" + testingValidSize+ " valid/#" + testingInvalidSize + " invalid");
		
		cellClassifyingMethod.setCellStyle(cs);
		cellSentimentMethod.setCellStyle(cs);
		cellCorrelationMethod.setCellStyle(cs);
		cellCorrelationThreshold.setCellStyle(cs);
		cellTruePos.setCellStyle(cs);		
		cellFalsePos.setCellStyle(cs);
		cellTrueNeg.setCellStyle(cs);
		cellFalseNeg.setCellStyle(cs);
		cellFScore.setCellStyle(cs);
		cellTestSize.setCellStyle(cs);	
	}

	private static Map<RawReview, List<ConceptSentimentPair>> copyOriginalReviewToCSPairs(
			Map<RawReview, List<ConceptSentimentPair>> originalReviewToCSPairs,
			SentimentCalculationMethod sentimentCalculationMethod) {
		
		Map<RawReview, List<ConceptSentimentPair>> reviewToCSPairs = new HashMap<RawReview, List<ConceptSentimentPair>>();
		for (RawReview review : originalReviewToCSPairs.keySet()) {
			if (!reviewToCSPairs.containsKey(review))
				reviewToCSPairs.put(review, new ArrayList<ConceptSentimentPair>());
			
			for (ConceptSentimentPair pair : originalReviewToCSPairs.get(review)) {
				ConceptSentimentPair newPair = new ConceptSentimentPair(pair.getId());
/*				if (sentimentCalculationMethod == SentimentCalculationMethod.COUNT_CONCEPT_SENTIMENT)
					newPair.setSentiment(pair.getSentimentWithSelfCount());
				else 
*/					newPair.setSentiment(pair.getSentiment());
				
				newPair.setCount(pair.getCount());
//				newPair.setCorrelation(pair.getCorrelation());
				newPair.setTypes(pair.getTypes());
				newPair.setName(pair.getName());
				
				reviewToCSPairs.get(review).add(newPair);
			}
		}
		return reviewToCSPairs;
	}
		
	private static int countMatchedConcepts(List<ConceptSentimentPair> pairs, List<String> desiredCuis) {	
		Set<String> matched = new HashSet<String>();
		
		for (ConceptSentimentPair pair : pairs) {
			if (desiredCuis.contains(pair.getId()))
				matched.add(pair.getId());
		}
		return matched.size();
	}

	private static void appendToOutputCSV(String outputPath, String string) {
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath), 
				StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
			
			writer.append(string);
			writer.newLine();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void importTrainingSet(String trainingSetPath,
			List<String> labeledValidCuis, List<String> labeledInvalidCuis) {

		try {
			Workbook wb = new XSSFWorkbook(trainingSetPath);
			Sheet sheet = wb.getSheetAt(0);
			
			for (int i = 1; i < trainingSetSize + 1; ++i) {
				Row row = sheet.getRow(i);
				
				String Cui = row.getCell(5).getStringCellValue().trim();
				
				Cell cellIsValid = row.getCell(4);
				if (cellIsValid != null && cellIsValid.getStringCellValue().trim().equalsIgnoreCase("x")) {
					labeledValidCuis.add(Cui);
				} else
					labeledInvalidCuis.add(Cui);
			}
			
			wb.close();
		} catch (IOException e) {
			e.printStackTrace();
		}		
	}
	
	private static void filterValidPairs(
			Set<ConceptSentimentPair> pairs, 
			ClassifyingMethod classifyingMethod,
			List<ConceptSentimentPair> validPairs,
			List<ConceptSentimentPair> invalidPairs) {			
		
		switch (classifyingMethod) {
		case CORRELATION_ONLY:
			filterValidPairsByCorrelationOnly(pairs, validPairs, invalidPairs);
			break;
		case CORRELATION_AND_TYPE_FILTER:
			filterValidPairsByCorrelationAndType(pairs, validPairs, invalidPairs);
			break;
		case TYPE_FILTER_ONLY:
			filterValidPairsByTypeOnly(pairs, validPairs, invalidPairs);
		}
	}


	private static void filterValidPairsByTypeOnly(
			Set<ConceptSentimentPair> allPairs,
			List<ConceptSentimentPair> validPairs,
			List<ConceptSentimentPair> invalidPairs) {

		for (ConceptSentimentPair pair : allPairs) {
			if (!isInInvalidSemanticTypes(pair))				
				validPairs.add(pair);
			else
				invalidPairs.add(pair);
		}
	
	}

	private static boolean isInInvalidSemanticTypes(ConceptSentimentPair pair) {
		for (String type : pair.getTypes()) {
			if (invalidSemanticTypes.contains(type))
				return true;
		}
		
		return false;
	}

	private static void filterValidPairsByCorrelationAndType(
			Set<ConceptSentimentPair> allPairs,
			List<ConceptSentimentPair> validPairs,
			List<ConceptSentimentPair> invalidPairs) {
		
		Set<ConceptSentimentPair> remainingPairs = new HashSet<ConceptSentimentPair>();
		for (ConceptSentimentPair pair : allPairs) {
			if (isInDefaultValidTypes(pair))
				validPairs.add(pair);
			else 				
				remainingPairs.add(pair);
		}
		
		Set<ConceptSentimentPair> remainingPairs2 = new HashSet<ConceptSentimentPair>();
		for (ConceptSentimentPair pair : remainingPairs) {
			if (isInDefaultInvalidTypes(pair))
				invalidPairs.add(pair);
			else 				
				remainingPairs2.add(pair);
		}
		
		filterValidPairsByCorrelationOnly(remainingPairs2, validPairs, invalidPairs);
	}

	private static boolean isInDefaultValidTypes(ConceptSentimentPair pair) {
		for (String type : pair.getTypes()) {
			if (defaultValidTypes.contains(type))
				return true;
		}
		
		return false;
	}
	
	private static boolean isInDefaultInvalidTypes(ConceptSentimentPair pair) {
		for (String type : pair.getTypes()) {
			if (defaultInvalidTypes.contains(type))
				return true;
		}
		
		return false;
	}

	private static void filterValidPairsByCorrelationOnly(
			Set<ConceptSentimentPair> allPairs,
			List<ConceptSentimentPair> validPairs,
			List<ConceptSentimentPair> invalidPairs) {
		
		for (ConceptSentimentPair pair : allPairs) {
			if (Math.abs(pair.getCorrelation()) >= correlationThreshold)
				validPairs.add(pair);
			else
				invalidPairs.add(pair);
		}
	}

	/*private static void switchToSentimentWithSelfCount(
			Map<RawReview, List<ConceptSentimentPair>> reviewToCSPairs) {
		for (RawReview review : reviewToCSPairs.keySet()) {
			for (ConceptSentimentPair pair : reviewToCSPairs.get(review)) {
				pair.setSentiment(pair.getSentimentWithSelfCount());
			}
		}
	}*/

	private static Map<RawReview, List<ConceptSentimentPair>> calculateCorrelation(
			Map<RawReview, List<ConceptSentimentPair>> reviewToCSPairs,
			CorrelationMethod correlationMethod) {
		
		switch (correlationMethod) {
		case SENTIMENT:
			CorrelationCalculator.calculatePairCorrelation(reviewToCSPairs);
			break;
		case PRESENCE:
			CorrelationCalculator.calculatePairCorrelationWithPresence(reviewToCSPairs);
			break;
		default:
			CorrelationCalculator.calculatePairCorrelation(reviewToCSPairs);
			break;	
		}		
		
		return reviewToCSPairs;
	}
	
	private static Set<ConceptSentimentPair> calculateCorrelation2(
			Map<RawReview, List<ConceptSentimentPair>> reviewToCSPairs,
			CorrelationMethod correlationMethod) {
		
		switch (correlationMethod) {
		case SENTIMENT:
			CorrelationCalculator.calculatePairCorrelation(reviewToCSPairs);
			break;
		case PRESENCE:
			CorrelationCalculator.calculatePairCorrelationWithPresence(reviewToCSPairs);
			break;
		default:
			CorrelationCalculator.calculatePairCorrelation(reviewToCSPairs);
			break;	
		}		
		
		Set<ConceptSentimentPair> computedPairs = new HashSet<ConceptSentimentPair>();
		Set<String> computedCuis = new HashSet<String>();
		for (List<ConceptSentimentPair> pairList : reviewToCSPairs.values()) {
			for (ConceptSentimentPair pair : pairList) {
				String cui = pair.getId();
				if (!computedCuis.contains(cui)) {
					computedCuis.add(cui);
					
					ConceptSentimentPair newPair = new ConceptSentimentPair(cui);
					newPair.setCorrelation(pair.getCorrelation());
					newPair.setTypes(pair.getTypes());
					computedPairs.add(newPair);
				}
			}
		}
		
		return computedPairs;
	}

	private static Map<RawReview, List<ConceptSentimentPair>> importReviewToConceptSentimentPairs() {
		
		Map<RawReview, List<ConceptSentimentPair>> reviewToCSPairs = new HashMap<RawReview, List<ConceptSentimentPair>>();
		
		String validConceptPath = sourceFolder + "valid_" + Constants.INTERVAL_TO_SAMPLE_REVIEW + ".txt";		
		String invalidConceptPath = sourceFolder + "invalid_" + Constants.INTERVAL_TO_SAMPLE_REVIEW + ".txt";
		
		Map<RawReview, List<ConceptSentimentPair>> reviewToValidCSPairs = PairExtractor.readRawReviewToConceptSentimentPairs(validConceptPath);
		Map<RawReview, List<ConceptSentimentPair>> reviewToInvalidCSPairs = PairExtractor.readRawReviewToConceptSentimentPairs(invalidConceptPath);
	
		for (RawReview review : reviewToValidCSPairs.keySet()) {
			if (!reviewToCSPairs.containsKey(review))
				reviewToCSPairs.put(review, new ArrayList<ConceptSentimentPair>());
			reviewToCSPairs.get(review).addAll(reviewToValidCSPairs.get(review));
		}
		
		for (RawReview review : reviewToInvalidCSPairs.keySet()) {
			if (!reviewToCSPairs.containsKey(review))
				reviewToCSPairs.put(review, new ArrayList<ConceptSentimentPair>());
			reviewToCSPairs.get(review).addAll(reviewToInvalidCSPairs.get(review));
		}
		
		return reviewToCSPairs;
	}
	
	private static Font[] initExcelColor(Workbook wb) {
				
		Font redFont = wb.createFont();
		Font headingFont = wb.createFont();
		Font greenFont = wb.createFont();
		Font greyFont = wb.createFont();
		Font blueFont = wb.createFont();
		Font yellowFont = wb.createFont();
		
		Font[] results = new Font[] {headingFont, redFont, greenFont, yellowFont, blueFont, greyFont};
		
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
		
		return results;
	}
	
	public static void main(String[] args) {
		PairExtractingExperimentation.runExperiment();
	}
}
