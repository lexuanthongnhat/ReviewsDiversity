package edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset.sentexp;

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
import java.util.stream.Stream;

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
			mean = errors.stream().collect(Collectors.averagingDouble(error -> error));
			
			sd = errors.stream().collect(Collectors
					.summingDouble(error -> (error - mean) * (error - mean)));
			sd = Math.sqrt(sd / (double) errors.size());	
		}
	}
	
	private static class ClassAccuracyStatistic{
		double accuracy;
		
		public ClassAccuracyStatistic (
				Map<Integer, Double> orderToSurveyNormalizedSent,
				Map<Integer, Double> orderToPredictedSent) {
/*			double numMatch = orderToSurveyNormalizedSent.entrySet().stream().filter(
					e -> e.getValue() == orderToPredictedSent.get(e.getKey()))
					.count();*/
			double numMatch = 0;
			for (Integer order : orderToSurveyNormalizedSent.keySet()) {
				if (orderToSurveyNormalizedSent.get(order).compareTo(orderToPredictedSent.get(order)) == 0)
					numMatch++;
			}
			this.accuracy = numMatch / orderToSurveyNormalizedSent.size();
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
		
		String baselineName = "Dictionary";
		Map<String, String> nameToRegressors = new HashMap<>();
		nameToRegressors.put("Ridge", "prediction_ridge.txt");
		nameToRegressors.put("Lasso", "prediction_lasso.txt");
		nameToRegressors.put("Bayesian Ridge", "prediction_bayesian_ridge.txt");
		nameToRegressors.put("Linear SVR", "prediction_linear_svr.txt");
		
		evaluateAsContinuousSentiment(predictedSentimentDir, sentenceNumToSentiments, baselineName,
				nameToRegressors);
		evaluateAsNormalizedSentiment(predictedSentimentDir, sentenceNumToSentiments, baselineName,
				nameToRegressors);
	}
	
	private static void evaluateAsNormalizedSentiment(
			String predictedSentimentDir,
	    Map<Integer, List<Double>> sentenceNumToSentiments,
	    String baselineName,
	    Map<String, String> nameToRegressors) {
		
		System.out.println("Evaluating sentiment as normalizing number as class");
		Map<Integer, Double> orderToSurveySentiment = collectAverageSurveySentiment(
				sentenceNumToSentiments);
		Map<Integer, Double> orderToSurveyNormalized = mapContinuousToNormalizedSent(
				orderToSurveySentiment);
		
		Map<String, Map<Integer, Double>> methodToSentiments = collectMethodSentiments(
				predictedSentimentDir, orderToSurveySentiment, baselineName, nameToRegressors);
		Map<String, Map<Integer, Double>> methodToNormalizedSents = methodToSentiments.entrySet()
				.stream().collect(Collectors.toMap(
						e -> e.getKey(),
						e -> mapContinuousToNormalizedSent(e.getValue())));
		
		Map<String, ClassAccuracyStatistic> methodToClassAccuracyStat = methodToNormalizedSents.entrySet()
				.stream().collect(Collectors.toMap(
						entry -> entry.getKey(),
						entry -> new ClassAccuracyStatistic(orderToSurveyNormalized, entry.getValue())));
		
		StringBuilder outputCsvBuilder = new StringBuilder(
				"Method, Accuracy, Compare to Dictionary\n");
		double baselineAccuracy = methodToClassAccuracyStat.get(baselineName).accuracy;
		methodToClassAccuracyStat.forEach(
				(method, stat) -> outputCsvBuilder.append(method + ", " + stat.accuracy + ", " 
																								  + (stat.accuracy / baselineAccuracy) + "\n"));
		
    outputEvaluationResult(predictedSentimentDir, "comparison_class.csv", outputCsvBuilder);
	}

	private static void evaluateAsContinuousSentiment(
			String predictedSentimentDir,
	    Map<Integer, List<Double>> sentenceNumToSentiments,
	    String baselineName,
	    Map<String, String> nameToRegressors) {

		System.out.println("Evaluating sentiment as continuous number");
		Map<Integer, Double> orderToSurveySentiment = collectAverageSurveySentiment(
				sentenceNumToSentiments);		
		Map<String, Map<Integer, Double>> methodToSentiments = collectMethodSentiments(
				predictedSentimentDir, orderToSurveySentiment, baselineName, nameToRegressors);
		
	
		Map<String, ErrorStatistic> methodToErrorStats = methodToSentiments.entrySet().stream()
				.collect(Collectors.toMap(
						e -> e.getKey(),
						e -> new ErrorStatistic(calculateAbsoluteError(orderToSurveySentiment, e.getValue()))));
		
		StringBuilder outputCsvBuilder = new StringBuilder(
				"Method, Absolute Error Mean, Absolute Error Standard Deviation, Compare to Dictionary (Mean)\n");
		double baselineErrorMean = methodToErrorStats.get(baselineName).mean;
		methodToErrorStats.forEach(
				(method, stats) -> outputCsvBuilder.append(method + ", " + stats.mean + ", " + stats.sd 
																									+ ", " + (stats.mean / baselineErrorMean)
																									+ "\n"));		
		
		outputEvaluationResult(predictedSentimentDir, "comparison_continuous.csv", outputCsvBuilder);
	}

  private static void outputEvaluationResult(
      String predictedSentimentDir,
      String fileName,
      StringBuilder outputCsvBuilder) {
    
    System.out.println(outputCsvBuilder.toString());
		try (BufferedWriter writer = Files.newBufferedWriter(				
				Paths.get(predictedSentimentDir + fileName), StandardOpenOption.CREATE)) {
			writer.write(outputCsvBuilder.toString());
			System.out.println("Outputed comparison to: \"" + predictedSentimentDir + fileName + "\"");			
		} catch (IOException e) {
			e.printStackTrace();
		}
  }
	
	/**
	 * Collect sentiment prediction of various methods 
	 * @param predictedSentimentDir
	 * @param orderToSurveySentiment
	 * @param baselineName
	 * @param nameToRegressors
	 * @return methodToSentiments: map name of method -> method's predictions (Map<Integer, Double>) 
	 */
	private static Map<String, Map<Integer, Double>> collectMethodSentiments(
			String predictedSentimentDir,
			Map<Integer, Double> orderToSurveySentiment,
			String baselineName,
			Map<String, String> nameToRegressors) {
		
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
		Map<String, Map<Integer, Double>> regressorToSentiments = nameToRegressors.entrySet().stream()
				.collect(Collectors.toMap(
						entry -> entry.getKey(),
						entry -> importSentimentFromRegression(predictedSentimentDir + entry.getValue())));

		for (String regressor : nameToRegressors.keySet()) {
			Map<Integer, Double> orderToSentiments = regressorToSentiments.get(regressor);
			regressorToSentiments.put(
					"Combination with " + regressor,
					orderToSentiments.entrySet().stream().collect(Collectors.toMap(
							e -> e.getKey(), 
							e -> (e.getValue() + orderToSentimentDictionary.get(e.getKey())) / 2.0d)));
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
					calculateAbsoluteError(orderToSurveySentiment, orderToLocalSentiment));
			
			if (localError.getMean() < optimalError.getMean()) {
				optimalError = localError;
				optimalSentiment = staticSentiment;
				orderToOptimalSentiment = orderToLocalSentiment;
			}
		}
		
		// Predictions of every method
		Map<String, Map<Integer, Double>> methodToSentiments = new HashMap<>();
		methodToSentiments.put(baselineName, orderToSentimentDictionary);
		
		methodToSentiments.put("Predict As Mode", orderToSentimentMode);
		methodToSentiments.put("Predict As Mean", orderToSentimentMean);
		methodToSentiments.put("Predict As Median", orderToSentimentMedian);
		methodToSentiments.put("Predict As " + Math.round(optimalSentiment), orderToOptimalSentiment);
		
		regressorToSentiments.forEach(
				(name, orderToSentiments) -> methodToSentiments.put(name, orderToSentiments));		
		
		return methodToSentiments;
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
	private static List<Double> calculateAbsoluteError(
			Map<Integer, Double> orderToSurveySentiment,
			Map<Integer, Double> orderToSentiment) {
		
		List<Double> errors = new ArrayList<Double>();
		for (Integer order : orderToSentiment.keySet()) {
			errors.add(shortenSentiment(Math.abs(orderToSentiment.get(order)
																 - orderToSurveySentiment.get(order))));
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
	
	/**
	 * Ex: map continuous sentiment 0.9 -> normalized sentiment 1.0
	 * @param contSent
	 * @return normalized sentiment
	 */
	private static double mapContinuousToNormalizedSent(double contSent) {
		if (contSent >= 0.75)
			return 1.0d;
		else if (contSent >= 0.25)
			return 0.5d;
		else if (contSent >= -0.25)
			return 0.0d;
		else if (contSent >= -0.75)
			return -0.5d;
		else
			return -1.0d;
	}
	
	private static Map<Integer, Double> mapContinuousToNormalizedSent (
			Map<Integer, Double> orderToContSentiment) {
		return orderToContSentiment.entrySet().stream()
				.collect(Collectors.toMap(
						e -> e.getKey(),
						e -> mapContinuousToNormalizedSent(e.getValue()))
						);
	}
}