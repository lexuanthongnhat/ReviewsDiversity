package edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset;

import java.io.BufferedReader;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentReview;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSentence;
import edu.ucr.cs.dblab.nle020.stats.KrippendorffAlpha;
import edu.ucr.cs.dblab.nle020.stats.KrippendorffAlpha.DataValueType;

public class SentimentByDocumentVector {
	static int forRounding = (int) Math.pow(10, Constants.NUM_DIGIT_PRECISION_OF_SENTIMENT);


	public static void main(String[] args) {
		//comparingDifferentMethods();
		//prepareSurvey();
		evaluateSurvey();
	}
	
	public static void evaluateSurvey() {
		Map<Integer, Float> orderToSentimentSurvey = accumulateSentiments();
		
		Map<Integer, Float> orderToSentimentMean = new HashMap<Integer, Float>();
		Map<Integer, Float> orderToSentimentMedian = new HashMap<Integer, Float>();
		Map<Integer, Float> orderToSentimentMode = new HashMap<Integer, Float>();
		List<Float> surveySentiments = new ArrayList<Float>(orderToSentimentSurvey.values());
		Collections.sort(surveySentiments);
		float sentimentMean = 0.0f;
		float sentimentMode = 0.0f;
		float sentimentMedian = surveySentiments.get(surveySentiments.size() / 2);
		float sentimentSum = 0.0f;
		Map<Float, Integer> sentimentToNum = new HashMap<Float, Integer>();
		for (Float sentiment : surveySentiments) {
			sentimentSum += sentiment;
			if (!sentimentToNum.containsKey(sentiment))
				sentimentToNum.put(sentiment, 1);
			else
				sentimentToNum.put(sentiment, sentimentToNum.get(sentiment) + 1);
		}
		sentimentMean = sentimentSum / (float) surveySentiments.size();
		int mostFreq = 0;
		for (Float sentiment : sentimentToNum.keySet()) {
			if (sentimentToNum.get(sentiment) > mostFreq) {
				mostFreq = sentimentToNum.get(sentiment);
				sentimentMode = sentiment;
			}
		}
		
		for (Integer order : orderToSentimentSurvey.keySet()) {
			orderToSentimentMean.put(order, sentimentMean);
			orderToSentimentMedian.put(order, sentimentMedian);
			orderToSentimentMode.put(order, sentimentMode);
		}
		
		
		
		
		Map<Integer, SentimentSentence> orderToSentence = 
				importOrderToSentence(PreprocessingForDocumentVector.PYTHON_WORKSPACE + "order-to-sentence.txt");
		

		Map<SentimentSentence, Integer> sentenceToOrder = new HashMap<SentimentSentence, Integer>();
		for (Integer order : orderToSentence.keySet()) {
			sentenceToOrder.put(orderToSentence.get(order), order);
		}
		
		Map<Integer, Float> orderToSentimentBinaryLogistic = importSentiment(
				PreprocessingForDocumentVector.PYTHON_WORKSPACE + "prediction.txt");
		Map<Integer, Float> orderToSentimentRidge = importSentimentFromRidge(
				PreprocessingForDocumentVector.PYTHON_WORKSPACE + "prediction_ridge.txt");
		
		Map<Integer, Float> orderToSentimentNormal = new HashMap<Integer, Float>();
		for (Integer order : orderToSentence.keySet()) {
			float sum = 0.0f;
			for (ConceptSentimentPair pair : orderToSentence.get(order).getPairs()) {
				sum += pair.getSentiment();
			}
			orderToSentimentNormal.put(order, shortenSentiment(sum / (float) orderToSentence.get(order).getPairs().size()));
		}
		
		Map<Integer, Integer> orderToLabelLogistic = importSentimentLabelFromLogisticRegression(
				PreprocessingForDocumentVector.PYTHON_WORKSPACE + "prediction_logistic.txt");
		Map<Integer, Float> orderToSentimentMultiLogistic = new HashMap<Integer, Float>();
		for (Integer order : orderToLabelLogistic.keySet()) {
			int label = orderToLabelLogistic.get(order);
			float normalized = (label - 2.5f) / 1.5f;
			orderToSentimentMultiLogistic.put(order, shortenSentiment(normalized));
		}
		
		Map<Integer, Float> orderToSentimentCombination = new HashMap<Integer, Float>();
		for (Integer order : orderToSentimentNormal.keySet()) {
			float combination = (orderToSentimentBinaryLogistic.get(order) + orderToSentimentNormal.get(order)) / 2.0f;
			orderToSentimentCombination.put(order, combination);
		}
		
		
		List<Float> errorOfNormalList = calculateError(orderToSentimentSurvey, orderToSentimentNormal); 
		List<Float> errorOfCombinationList = calculateError(orderToSentimentSurvey, orderToSentimentCombination);
		List<Float> errorOfBinaryLogList = calculateError(orderToSentimentSurvey, orderToSentimentBinaryLogistic);
		List<Float> errorOfMultiLogList = calculateError(orderToSentimentSurvey, orderToSentimentMultiLogistic);
		List<Float> errorOfRidgeList = calculateError(orderToSentimentSurvey, orderToSentimentRidge);
		
		ErrorStatistic errorOfNormal = new ErrorStatistic(errorOfNormalList);
		ErrorStatistic errorOfCombination = new ErrorStatistic(errorOfCombinationList);
		ErrorStatistic errorOfBinaryLog = new ErrorStatistic(errorOfBinaryLogList);
		ErrorStatistic errorOfMultiLog = new ErrorStatistic(errorOfMultiLogList);
		ErrorStatistic errorOfRidge = new ErrorStatistic(errorOfRidgeList);
		
		System.err.println("Error Rate:\n");
		System.out.println("Normal      \t--->\tmean: " + errorOfNormal.getMean() + ", \t\tsd: " + errorOfNormal.getSd());
		System.out.println("Combination \t--->\tmean: " + errorOfCombination.getMean() + ", \t\tsd: " + errorOfCombination.getSd());
		System.out.println("BinaryLog   \t--->\tmean: " + errorOfBinaryLog.getMean() + ", \t\tsd: " + errorOfBinaryLog.getSd());
		System.out.println("MultiLog    \t--->\tmean: " + errorOfMultiLog.getMean() + ", \t\tsd: " + errorOfMultiLog.getSd());
		System.out.println("Ridge       \t--->\tmean: " + errorOfRidge.getMean() + ", \t\tsd: " + errorOfRidge.getSd());
		
		
		List<Float> errorOfMeanList = calculateError(orderToSentimentSurvey, orderToSentimentMean);
		List<Float> errorOfMedianList = calculateError(orderToSentimentSurvey, orderToSentimentMedian);
		List<Float> errorOfModeList = calculateError(orderToSentimentSurvey, orderToSentimentMode);
		ErrorStatistic errorOfMean = new ErrorStatistic(errorOfMeanList);
		ErrorStatistic errorOfMedian = new ErrorStatistic(errorOfMedianList);
		ErrorStatistic errorOfMode = new ErrorStatistic(errorOfModeList);
		System.out.println();
		System.out.println("Mean " + shortenSentiment(sentimentMean) + "  \t--->\tmean: " + errorOfMean.getMean() + ", \t\tsd: " + errorOfMean.getSd());
		System.out.println("Median " + sentimentMedian + "  \t--->\tmean: " + errorOfMedian.getMean() + ", \t\tsd: " + errorOfMedian.getSd());
		System.out.println("Mode " + sentimentMode + "   \t--->\tmean: " + errorOfMode.getMean() + ", \t\tsd: " + errorOfMode.getSd());
		System.out.println();
		
		float optimalSentiment = 0.0f;
		ErrorStatistic optimalError = new ErrorStatistic(10.0f, 10.0f);
		for (float staticSentiment = -1.0f; staticSentiment <= 1.0f; staticSentiment += 0.1) {
			Map<Integer, Float> orderToSentimentOptimal = new HashMap<Integer, Float>();
			for (Integer order : orderToSentimentSurvey.keySet()) {
				orderToSentimentOptimal.put(order, staticSentiment);
			}
			
			List<Float> errorOfOptimalList = calculateError(orderToSentimentSurvey, orderToSentimentOptimal);
			ErrorStatistic errorOfOptimal = new ErrorStatistic(errorOfOptimalList);
			
			if (errorOfOptimal.getMean() < optimalError.getMean()) {
				optimalError = errorOfOptimal;
				optimalSentiment = staticSentiment;
			}
		}
		System.out.println();
		System.out.println("Optimal " + shortenSentiment(optimalSentiment) + "   \t--->\tmean: " + optimalError.getMean() + ", \t\tsd: " + optimalError.getSd());
	}
	
	private static double krippendorffAlpha(Map<Integer, List<Float>> orderToSentiments) {
		double alpha = Double.NaN;
		
		int numUnits = orderToSentiments.size();
		int numCoders = orderToSentiments.get(0).size();
		// value that codes give to units 
		double[][] v = new double[numCoders][numUnits];
		for (Integer unit : orderToSentiments.keySet()) {
			for (int coder = 0; coder < numCoders; ++coder) {
				v[coder][unit] = (double) orderToSentiments.get(unit).get(coder);
			}
		}
		
		alpha = KrippendorffAlpha.krippendorffAlpha(v, DataValueType.POLAR);
		return alpha;
	}
	
	private static class ErrorStatistic{
		float mean;
		float sd;		// standard deviation
		public float getMean() {
			return mean;
		}
		public float getSd() {
			return sd;
		}
		
		public ErrorStatistic(float mean, float sd) {
			this.mean = mean;
			this.sd = sd;
		}
		public ErrorStatistic(List<Float> errors) {
			mean = averaging(errors);
			List<Float> differences = new ArrayList<Float>();
			for (Float error : errors) {
				float difference = error - mean;
				differences.add(difference * difference);				
			}
			
			sd = (float) Math.sqrt((double) averaging(differences));
		}
	}
	
	private static float averaging(List<Float> list) {
		float sum = 0.0f;
		for (Float item : list) {
			sum += item;
		}
		
		return sum / (float) list.size();
	}
	
	private static List<Float> calculateError(
			Map<Integer, Float> orderToSentimentSurvey,
			Map<Integer, Float> orderToSentiment) {
		
		List<Float> error = new ArrayList<Float>();
		for (Integer order : orderToSentiment.keySet()) {
			error.add(shortenSentiment(Math.abs(orderToSentiment.get(order) - orderToSentimentSurvey.get(order))));
		}
		return error;
	}

	public static Map<Integer, Float> accumulateSentiments() {
		String surveyFolder = "D:\\UCR Google Drive\\GD - Review Diversity\\sentiment survey";
		
		Map<Integer, List<Float>> orderToSentiments = new HashMap<Integer, List<Float>>();		
		
		List<Path> sentimentFilePaths = new ArrayList<Path>(); 
		try {
			Files.walkFileTree(Paths.get(surveyFolder), new SimpleFileVisitor<Path>(){
				
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					sentimentFilePaths.add(file);
					return FileVisitResult.CONTINUE;
				}			      
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		sentimentFilePaths.stream().forEach(file ->	mergeIntoSentenceToSentimentsMap(file, orderToSentiments));
		
		
		double krippendorffAlpha = krippendorffAlpha(orderToSentiments);
		System.out.println("Krippendorf Alpha Coefficient is " + krippendorffAlpha);
		
		Map<Integer, Float> orderToSentiment = new HashMap<Integer, Float>();		
		for (Integer order : orderToSentiments.keySet()) {
			
			float sum = 0.0f;
			for (Float sentiment : orderToSentiments.get(order)) { 
				sum += sentiment;
			}
			
			if (orderToSentiments.get(order).size() != 4)
				System.err.println("Wrong size!");
			
			orderToSentiment.put(order, sum / (float) orderToSentiments.get(order).size());
		}
		
		return orderToSentiment;
	}
	
	private static void mergeIntoSentenceToSentimentsMap(Path file,
			Map<Integer, List<Float>> orderToSentiments) {
		
		try {
			Workbook wb = new XSSFWorkbook(Files.newInputStream(file));
			Sheet sheet = wb.getSheetAt(0);
			Iterator<Row> rowIterator = sheet.rowIterator();
			int count = 0;
			while(rowIterator.hasNext()) {
				if (count == 0) {
					++count;
					rowIterator.next();
					continue;
				}
					
				Row row = rowIterator.next();
								 
				float sentiment = normalizeRating(row.getCell(3).getStringCellValue().trim());
				
				if (!orderToSentiments.containsKey(count - 1))
					orderToSentiments.put(count - 1, new ArrayList<Float>());
				
				orderToSentiments.get(count - 1).add(sentiment);
				++count;
			}
			
			wb.close();
		} catch (IOException e) {
			e.printStackTrace();
		}		
	}

	private static float normalizeRating(String ratingString) {
		Map<String, Float> ratingToSentiment = new HashMap<String, Float>();
		ratingToSentiment.put("very negative", -1.0f);
		ratingToSentiment.put("negative", -0.5f);
		ratingToSentiment.put("neutral", 0.0f);
		ratingToSentiment.put("positive", 0.5f);
		ratingToSentiment.put("very positive", 1.0f);
				
		float sentiment = -2;
		if (!ratingToSentiment.containsKey(ratingString))
			System.err.println("Unknow rating string");
		else
			sentiment = ratingToSentiment.get(ratingString);
		
		return sentiment;
	}

	public static void prepareSurvey() {
		Map<Integer, SentimentSentence> orderToSentence = 
				importOrderToSentence(PreprocessingForDocumentVector.PYTHON_WORKSPACE + "order-to-sentence.txt");
		
		Workbook wb = new XSSFWorkbook();
		Sheet sheet = wb.createSheet("survey");
		int count = 0;
		
		while (count <= orderToSentence.size()) {
			Row row = sheet.createRow(count);
			
			Cell cellReviewId = row.createCell(0);
			Cell cellSentenceId = row.createCell(1);
			Cell cellSentence = row.createCell(2);
			Cell cellRating = row.createCell(3);
			
			if (count == 0) {
				cellReviewId.setCellValue("review id");
				cellSentenceId.setCellValue("sentence id");
				cellSentence.setCellValue("sentence");
				cellRating.setCellValue("Rating (1, 2, 3 or 4 stars)");
			} else {
				SentimentSentence sentence = orderToSentence.get(count - 1);
				cellReviewId.setCellValue(sentence.getReviewId());
				cellSentenceId.setCellValue(sentence.getId());
				cellSentence.setCellValue(sentence.getSentence());
			}
			
			++count;
		}
		
		Path outputPath = Paths.get(PreprocessingForDocumentVector.PYTHON_WORKSPACE + "sentiment_survey.xlsx");
		try {
			
			Files.deleteIfExists(outputPath);
			wb.write(Files.newOutputStream(outputPath, StandardOpenOption.CREATE));
			wb.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void comparingDifferentMethods() {
		Map<Integer, List<SentimentReview>> docToSentimentReviews = PreprocessingForDocumentVector.importDocToSentimentReviews(
				PreprocessingForDocumentVector.DOC_TO_REVIEWS_PATH, 1000);
		
		Map<Integer, SentimentSentence> orderToSentence = 
				importOrderToSentence(PreprocessingForDocumentVector.PYTHON_WORKSPACE + "order-to-sentence.txt");
		
		Map<Integer, Float> orderToSentiment = importSentiment(PreprocessingForDocumentVector.PYTHON_WORKSPACE + "prediction.txt");
		Map<SentimentSentence, Integer> sentenceToOrder = new HashMap<SentimentSentence, Integer>();
		for (Integer order : orderToSentence.keySet()) {
			sentenceToOrder.put(orderToSentence.get(order), order);
		}
		
		Map<Integer, Integer> orderToLabelLogistic = importSentimentLabelFromLogisticRegression(
				PreprocessingForDocumentVector.PYTHON_WORKSPACE + "prediction_logistic.txt");
		Map<Integer, Float> orderToSentimentRidge = importSentimentFromRidge(
				PreprocessingForDocumentVector.PYTHON_WORKSPACE + "prediction_ridge.txt");
		
		Workbook wb = new XSSFWorkbook();
		Sheet sheet = wb.createSheet("Sentiment");
		addHeader(sheet);
		int rowCount = 1;
		for (Integer docId : docToSentimentReviews.keySet()) {
			for (SentimentReview review : docToSentimentReviews.get(docId)) {
				String reviewId = review.getId();
				int rating = review.getRawReview().getRate();
				
				String sentences = "";
				String normalSentiment = "";
				String vectorSentiment = "";
				String combinationSentiment = "";
				String multiLogisticSentiment = "";
				String ridgeSentiment = "";
				
				boolean isValid = false;
				if (review.getSentences().size() > 0) {
					for (SentimentSentence sentence : review.getSentences()) {
						if (sentenceToOrder.keySet().contains(sentence)) {
							isValid = true;
							
							int sentenceOrder = sentenceToOrder.get(sentence);
							sentences = sentences + "\n * " + sentence.getSentence();
							normalSentiment = normalSentiment + "\n * " + sentence.getPairs().toString();
							vectorSentiment = vectorSentiment + "\n * " + orderToSentiment.get(sentenceOrder);
							multiLogisticSentiment = multiLogisticSentiment + "\n * " + orderToLabelLogistic.get(sentenceOrder);
							ridgeSentiment = ridgeSentiment + "\n * " + orderToSentimentRidge.get(sentenceOrder);
							combinationSentiment = combinationSentiment + "\n * " + getCombinationSentiment(
									sentence.getPairs(),  orderToSentiment.get(sentenceOrder));
						}
					}
					
					if (isValid) {
						addRow(sheet, rowCount, docId, reviewId, rating, review.getRawReview().getBody(), sentences,
								normalSentiment, vectorSentiment, combinationSentiment, multiLogisticSentiment, ridgeSentiment);
						++rowCount;
					}
				}
			}
		}
		
//		String outputPath = "D:\\merged_sentiment.xlsx";
		String outputPath = PreprocessingForDocumentVector.PYTHON_WORKSPACE + "merged_sentiment.xlsx";		
		try {
			Files.deleteIfExists(Paths.get(outputPath));
			wb.write(Files.newOutputStream(Paths.get(outputPath), 
					StandardOpenOption.CREATE));
			wb.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		System.out.println("Outputed to \"" + outputPath + "\"");
	}

	private static String getCombinationSentiment(
			List<ConceptSentimentPair> pairs, Float sentimentFromVector) {
		String combinationString = "[";
		for (ConceptSentimentPair pair : pairs) {
			float sentiment = (pair.getSentiment() + sentimentFromVector) / 2.0f; 
			combinationString = combinationString + "{\"" + pair.getCui() + "-" + pair.getName() + "\": \"" 
					+ shortenSentiment(sentiment) + "\"}"; 
		}
		return combinationString;
	}

	private static Map<Integer, Float> importSentimentFromRidge(
			String sentimentPath) {
		Map<Integer, Float> orderToLabelLogistic = new HashMap<Integer, Float>();
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(sentimentPath))) {
			String line;
			int count = 0;
			while ((line = reader.readLine()) != null) {
				float sentiment = Float.parseFloat(line.trim()); 
				float normalizedSentiment = (sentiment - 1.5f) / 1.5f;
				orderToLabelLogistic.put(count, shortenSentiment(normalizedSentiment));
				++count;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return orderToLabelLogistic;
	}


	private static Map<Integer, Integer> importSentimentLabelFromLogisticRegression(
			String sentimentPath) {
		Map<Integer, Integer> orderToLabelLogistic = new HashMap<Integer, Integer>();
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(sentimentPath))) {
			String line;
			int count = 0;
			while ((line = reader.readLine()) != null) {
				String[] probStrings = line.split(" ");
				List<Double> probs = new ArrayList<Double>();
				Arrays.stream(probStrings).forEach(probString -> probs.add(Double.parseDouble(probString)));
				
				int indexOfMax = 0;
				Double max = 0.0d;
				for (int i = 0; i < probs.size(); ++i) {
					if (probs.get(i) > max) {
						max = probs.get(i);
						indexOfMax = i;
					}
				}
				orderToLabelLogistic.put(count, indexOfMax + 1);
				++count;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return orderToLabelLogistic;
	}

	private static void addRow(Sheet sheet, int rowCount, Integer docId,
			String reviewId, int rating, String reviewBody, String sentences, 
			String normalSentiment, String vectorSentiment, String combinationSentiment, 
			String multiLogisticSentiment, String ridgeSentiment) {
		Row row = sheet.createRow(rowCount);
		
		Cell docCell = row.createCell(0);
		Cell reviewIdCell = row.createCell(1);
		Cell reviewRateCell = row.createCell(2);
		Cell reviewCell = row.createCell(3);
		Cell sentencesCell = row.createCell(4);
		
		Cell sentimentNormalCell = row.createCell(5);
		Cell sentimentVectorCell = row.createCell(6);
		Cell sentimentCombinationCell = row.createCell(7);
		Cell sentimentMultiLogisticCell = row.createCell(8);
		Cell sentimentRidgeCell = row.createCell(9);
				
		docCell.setCellValue(docId);
		reviewIdCell.setCellValue(reviewId);
		reviewRateCell.setCellValue(rating);
		reviewCell.setCellValue(reviewBody);
		sentencesCell.setCellValue(sentences);
		
		sentimentNormalCell.setCellValue(normalSentiment);
		sentimentVectorCell.setCellValue(vectorSentiment);
		sentimentCombinationCell.setCellValue(combinationSentiment);
		sentimentMultiLogisticCell.setCellValue(multiLogisticSentiment);
		sentimentRidgeCell.setCellValue(ridgeSentiment);
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
				
		sheet.createFreezePane(10, 1);
		
		Cell docCell = row.createCell(0);
		Cell reviewIdCell = row.createCell(1);
		Cell reviewRateCell = row.createCell(2);
		Cell reviewCell = row.createCell(3);
		Cell sentencesCell = row.createCell(4);
		
		Cell sentimentNormalCell = row.createCell(5);
		Cell sentimentVectorCell = row.createCell(6);
		Cell sentimentCombinationCell = row.createCell(7);
		Cell sentimentMultiLogisticCell = row.createCell(8);
		Cell sentimentRidgeCell = row.createCell(9);
		
		docCell.setCellValue("doc Id");
		reviewIdCell.setCellValue("review Id");
		reviewRateCell.setCellValue("rating");
		reviewCell.setCellValue("review");		
		sentencesCell.setCellValue("sentences");
		
		sentimentNormalCell.setCellValue("normal sentiment");
		sentimentVectorCell.setCellValue("binary logistic");
		sentimentCombinationCell.setCellValue("combination");
		sentimentMultiLogisticCell.setCellValue("multiclass logistic");
		sentimentRidgeCell.setCellValue("ridge");
		
		docCell.setCellStyle(cs);
		reviewIdCell.setCellStyle(cs);
		reviewRateCell.setCellStyle(cs);
		reviewCell.setCellStyle(cs);
		sentencesCell.setCellStyle(cs);
		
		sentimentNormalCell.setCellStyle(cs);
		sentimentVectorCell.setCellStyle(cs);
		sentimentCombinationCell.setCellStyle(cs);
		sentimentMultiLogisticCell.setCellStyle(cs);
		sentimentRidgeCell.setCellStyle(cs);
	}

	private static Map<Integer, Float> importSentiment(String filePath) {
		Map<Integer, Float> orderToSentiment = new HashMap<Integer, Float>();
		
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath))) {
			String line = null;
			int count = 0;
			while ((line = reader.readLine()) != null) {
				float sentiment = Float.parseFloat(line.split(" ")[0]);
				float normalizedSentiment = (sentiment - 0.5f) * 2.0f;
				orderToSentiment.put(count, shortenSentiment(normalizedSentiment));
				++count;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return orderToSentiment;
	}

	private static Map<Integer, SentimentSentence> importOrderToSentence(String filePath) {
		Map<Integer, SentimentSentence> result = new HashMap<Integer, SentimentSentence>();
		ObjectMapper mapper = new ObjectMapper();
		
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath))) {
			result = mapper.readValue(reader,  new TypeReference<Map<Integer, SentimentSentence>>() { });				
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return result;
	}
	
	private static float shortenSentiment(float sentiment) {
		return (float)Math.round(sentiment * forRounding) / forRounding;
	}
}
