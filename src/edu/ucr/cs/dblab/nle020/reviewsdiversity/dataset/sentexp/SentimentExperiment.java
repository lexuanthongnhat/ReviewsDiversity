package edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset;

import java.io.BufferedReader;
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
import java.util.Collections;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSentence;
import edu.ucr.cs.dblab.nle020.stats.KrippendorffAlpha;
import edu.ucr.cs.dblab.nle020.stats.KrippendorffAlpha.DataValueType;

public class SentimentExperiment {

	public static void main(String[] args) {
    String predictedSentimentDir =
        "src/edu/ucr/cs/dblab/nle020/reviewsdiversity/dataset/sentexp/predict/";
    String groundTruthSentimentDir =
    		"src/edu/ucr/cs/dblab/nle020/reviewsdiversity/dataset/sentexp/survey/";
		evaluateSurvey(groundTruthSentimentDir, predictedSentimentDir);
	}
	
	private static class ErrorStatistic{
		double mean;
		double sd;		// standard deviation
		
		public double getMean() {
			return mean;
		}
				
		public ErrorStatistic(double mean, double sd) {
			this.mean = mean;
			this.sd = sd;
		}
		
		public ErrorStatistic(List<Double> errors) {
			
			DoubleSummaryStatistics squareErrorStatistics = errors.stream().collect(
					Collectors.summarizingDouble(error -> error * error));
			// mean = Math.sqrt(squareErrorStatistics.getSum()) / errors.size();
			mean = errors.stream().collect(Collectors.averagingDouble(error -> error));
			
			sd = errors.stream().collect(Collectors
					.summingDouble(error -> (error - mean) * (error - mean)));
			sd = Math.sqrt(sd / (double) errors.size());	
		}
	}
	
	public static double median(List<Double> numbers) {
		double median = 0.0d;
		Collections.sort(numbers);
		median = numbers.get(numbers.size() / 2);
		return median;
	}
	
	public static double mode(List<Double> numbers) {
		double mode = 0.0d;
		Map<Double, Integer> numToFreg = new HashMap<Double, Integer>();
		for (Double number : numbers) {
			if (!numToFreg.containsKey(number))
				numToFreg.put(number, 1);
			else
				numToFreg.put(number, numToFreg.get(number) + 1);
		}
		
		int mostFreq = 0;
		for (Double number : numToFreg.keySet()) {
			if (numToFreg.get(number) > mostFreq) {
				mostFreq = numToFreg.get(number);
				mode = number;
			}
		}
		
		return mode;
	}
	
	public static void evaluateSurvey(String groundTruthSentimentDir, String predictedSentimentDir) {
		Map<Integer, List<Double>> sentenceNumToSentiments = collectSurvey(groundTruthSentimentDir);
		double krippendorffAlpha = krippendorffAlpha(sentenceNumToSentiments);
		System.out.println("Krippendorf Alpha Coefficient is " + krippendorffAlpha);
		
		Map<Integer, Double> orderToSurveySentiment = collectAverageSurveySentiment(
				sentenceNumToSentiments);
		
		Map<Integer, Double> orderToSentimentMean = new HashMap<Integer, Double>();
		Map<Integer, Double> orderToSentimentMedian = new HashMap<Integer, Double>();
		Map<Integer, Double> orderToSentimentMode = new HashMap<Integer, Double>();

		List<Double> surveySentiments = new ArrayList<Double>(orderToSurveySentiment.values());
		DoubleSummaryStatistics sentiStatistics = orderToSurveySentiment.values().stream().collect(
				Collectors.summarizingDouble(sentiment -> sentiment));
		double sentimentMean = sentiStatistics.getAverage();
		double sentimentMode = mode(surveySentiments);
		double sentimentMedian = median(surveySentiments);		
		
		for (Integer order : orderToSurveySentiment.keySet()) {
			orderToSentimentMean.put(order, sentimentMean);
			orderToSentimentMedian.put(order, sentimentMedian);
			orderToSentimentMode.put(order, sentimentMode);
		}	
		
		// Import dictionary-based method
		Map<Integer, SentimentSentence> orderToSentence = 
				importOrderToSentence(predictedSentimentDir + "order-to-sentence.txt");		
		Map<Integer, Double> orderToSentimentDictionary = new HashMap<>();		
		for (Integer order : orderToSentence.keySet()) {
			double average = orderToSentence.get(order).getPairs()
					.stream().collect(Collectors.averagingDouble(pair -> pair.getSentiment()));			
			orderToSentimentDictionary.put(order,	shortenSentiment(average));
		}
		
		// Doc2Vec-based methods
		Map<Integer, Double> orderToSentimentRidge = importSentimentFromRegression(
				predictedSentimentDir + "prediction_ridge.txt");
		Map<Integer, Double> orderToSentimentLasso = importSentimentFromRegression(
				predictedSentimentDir + "prediction_lasso.txt");
		Map<Integer, Double> orderToSentimentTest = importSentimentFromRegression(
				predictedSentimentDir + "prediction_bayesian_ridge.txt");
				
		Map<Integer, Double> orderToSentimentCombinationWithRidge = new HashMap<Integer, Double>();
		Map<Integer, Double> orderToSentimentCombinationWithLasso = new HashMap<Integer, Double>();
		for (Integer order : orderToSentimentDictionary.keySet()) {
			double combination = (orderToSentimentRidge.get(order)
					+ orderToSentimentDictionary.get(order)) / 2.0d;
			orderToSentimentCombinationWithRidge.put(order, combination);
			
			combination = (orderToSentimentLasso.get(order)
					+ orderToSentimentDictionary.get(order)) / 2.0d;
			orderToSentimentCombinationWithLasso.put(order, combination);
		}

		// Predict by a fix number
		double optimalSentiment = 0.0d;
		Map<Integer, Double> orderToOptimalSentiment = new HashMap<>();
		ErrorStatistic optimalError = new ErrorStatistic(10.0d, 10.0d);		
		for (double staticSentiment = -1.0d; staticSentiment <= 1.0d; staticSentiment += 0.1) {
			Map<Integer, Double> orderToLocalSentiment = new HashMap<>();
			for (Integer order : orderToSurveySentiment.keySet()) {
				orderToLocalSentiment.put(order, staticSentiment);
			}			
			ErrorStatistic localError = new ErrorStatistic(
					calculateError(orderToSurveySentiment, orderToLocalSentiment));
			
			if (localError.getMean() < optimalError.getMean()) {
				optimalError = localError;
				optimalSentiment = staticSentiment;
				orderToOptimalSentiment = orderToLocalSentiment;
			}
		}
		
		Map<String, Map<Integer, Double>> methodToSentiments = new HashMap<>();
		methodToSentiments.put("Dictionary", orderToSentimentDictionary);
		methodToSentiments.put("Ridge", orderToSentimentRidge);
		methodToSentiments.put("Lasso", orderToSentimentLasso);
		methodToSentiments.put("Combination With Ridge", orderToSentimentCombinationWithRidge);
		methodToSentiments.put("Combination With Lasso", orderToSentimentCombinationWithLasso);
		methodToSentiments.put("Predict As Mode", orderToSentimentMode);
		methodToSentiments.put("Predict As Mean", orderToSentimentMean);
		methodToSentiments.put("Predict As Media", orderToSentimentMedian);
		methodToSentiments.put("Predict As " + Math.round(optimalSentiment), orderToOptimalSentiment);
		methodToSentiments.put("Bayesian Ridge", orderToSentimentTest);
		
		Map<String, ErrorStatistic> methodToErrorStats = methodToSentiments.entrySet().stream()
				.collect(Collectors.toMap(
						e -> e.getKey(),
						e -> new ErrorStatistic(calculateError(orderToSurveySentiment, e.getValue()))));
		
		StringBuilder outputCsvBuilder = new StringBuilder(
				"Method, Absolute Error Mean, Absolute Error Standard Deviation\n");		
		methodToErrorStats.forEach((method, stats) -> outputCsvBuilder
				.append(method + ", " + stats.mean + ", " + stats.sd + "\n"));		
		
		System.out.println(outputCsvBuilder.toString());
		try (BufferedWriter writer = Files.newBufferedWriter(
				Paths.get(predictedSentimentDir + "comparison.csv"), StandardOpenOption.CREATE)) {
			writer.write(outputCsvBuilder.toString());
			System.out.println("Outputed comparison to: \"" + predictedSentimentDir + "comparison.csv\"");			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Collect sentences' sentiments from surveys
	 * @param surveyFolder: where do surveys reside
	 * @return sentenceNumToSentiments: map from order of sentence to survey sentiments
	 */
	public static Map<Integer, List<Double>> collectSurvey(String surveyDir) {	
		Map<Integer, List<Double>> sentenceNumToSentiments = new HashMap<Integer, List<Double>>();		
		
		List<Path> sentimentFilePaths = new ArrayList<Path>(); 
		try {
			Files.walkFileTree(Paths.get(surveyDir), new SimpleFileVisitor<Path>(){
				
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					sentimentFilePaths.add(file);
					return FileVisitResult.CONTINUE;
				}			      
			});
		} catch (IOException e) {
			e.printStackTrace();
		}		
		sentimentFilePaths.stream().forEach(file ->	aggregateSurveyIntoSentenceToSentimentsMap(file, 
				sentenceNumToSentiments));		
		
		return sentenceNumToSentiments;
	}	
	
	/**Take the sentiment's average from multiple survey
	 * @param sentenceNumToSentiments: map from order of sentence to survey sentiments
	 * @return orderToAvgSentiment: map from the order of sentence to survey sentiments' average
	 */
	private static Map<Integer, Double> collectAverageSurveySentiment(
			Map<Integer, List<Double>> sentenceNumToSentiments){
		Map<Integer, Double> orderToAvgSentiment = new HashMap<Integer, Double>();		
		for (Integer order : sentenceNumToSentiments.keySet()) {			
			double average = sentenceNumToSentiments.get(order)
					.stream().collect(Collectors.averagingDouble(sentiment -> sentiment));			
			orderToAvgSentiment.put(order, average);
		}
		
		return orderToAvgSentiment;
	}
	
	private static double krippendorffAlpha(Map<Integer, List<Double>> orderToSentiments) {
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
	
	/**
	 * Collect a single survey
	 * @param surveyFile
	 * @param orderToSentiments: sentence order -> list of sentiments
	 */
	private static void aggregateSurveyIntoSentenceToSentimentsMap(Path surveyFile,
			Map<Integer, List<Double>> orderToSentiments) {
		
		try {
			Workbook wb = new XSSFWorkbook(Files.newInputStream(surveyFile));
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
								 
				double sentiment = normalizeRating(row.getCell(3).getStringCellValue().trim());				
				if (!orderToSentiments.containsKey(count - 1))
					orderToSentiments.put(count - 1, new ArrayList<Double>());				
				orderToSentiments.get(count - 1).add(sentiment);
				++count;
			}
			
			wb.close();
		} catch (IOException e) {
			e.printStackTrace();
		}	
	}
	
	/**
	 * Get the absolute error
	 * @param orderToSurveySentiment
	 * @param orderToSentiment
	 * @return errors: list of absolute errors
	 */
	private static List<Double> calculateError(
			Map<Integer, Double> orderToSurveySentiment,
			Map<Integer, Double> orderToSentiment) {
		
		List<Double> errors = new ArrayList<Double>();
		for (Integer order : orderToSentiment.keySet()) {
			errors.add(shortenSentiment(Math.abs(orderToSentiment.get(order)
																 - orderToSurveySentiment.get(order))));
		}
		return errors;
	}
	
	/**
	 * Get the absolute error for each concept in sentence.
	 * It means, an sentence (order) can have multiple words, each has a sentiment 
	 * @param orderToSurveySentiments
	 * @param orderToSentiment
	 * @return errors: list of absolute errors
	 */
	private static List<Double> calculateErrorAtConceptLevel(
			Map<Integer, List<Double>> orderToSurveySentiments,
			Map<Integer, Double> orderToSentiment) {
		
		List<Double> errors = new ArrayList<Double>();
		for (Integer order : orderToSentiment.keySet()) {
			double sentencePrediction = orderToSentiment.get(order);
			List<Double> sentenceErrors = orderToSurveySentiments.get(order).stream()
					.map(error -> Math.abs(sentencePrediction - error))
					.collect(Collectors.toList());
			
			errors.addAll(sentenceErrors);
		}
		return errors;
	}
	
	private static Map<Integer, Double> importSentimentFromRegression(String sentimentPath) {
		Map<Integer, Double> orderToPredictedSentiment = new HashMap<Integer, Double>();
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(sentimentPath))) {
			String line;
			int count = 0;
			while ((line = reader.readLine()) != null) {
				double sentiment = Double.parseDouble(line.trim()); 
				double normalizedSentiment = (sentiment - 1.5d) / 1.5d;
				orderToPredictedSentiment.put(count, shortenSentiment(normalizedSentiment));
				++count;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return orderToPredictedSentiment;
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
	
	private static double shortenSentiment(double sentiment) {
		int forRounding = (int) Math.pow(10, Constants.NUM_DIGIT_PRECISION_OF_SENTIMENT);
		return (double) Math.round(sentiment * forRounding) / forRounding;
	}
	
	/**
	 * Normalize ordinal sentiment to the range (-1, +1)
	 * @param ratingString
	 * @return sentiment: continuous number from -1 to 1
	 */
	private static double normalizeRating(String ratingString) {
		Map<String, Double> ratingToSentiment = new HashMap<String, Double>();
		ratingToSentiment.put("very negative", -1.0d);
		ratingToSentiment.put("negative", -0.5d);
		ratingToSentiment.put("neutral", 0.0d);
		ratingToSentiment.put("positive", 0.5d);
		ratingToSentiment.put("very positive", 1.0d);
				
		double sentiment = -2;
		if (!ratingToSentiment.containsKey(ratingString))
			System.err.println("Unknow rating string");
		else
			sentiment = ratingToSentiment.get(ratingString);
		
		return sentiment;
	}
}