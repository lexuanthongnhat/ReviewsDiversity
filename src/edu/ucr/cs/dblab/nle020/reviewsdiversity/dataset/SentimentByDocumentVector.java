package edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset.sentexp.SentimentExperiment;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentReview;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSentence;

public class SentimentByDocumentVector {


	public static void main(String[] args) {
		//prepareSurvey();
		
		String originalDatasetPath = "resources/doc_pairs_1_prunned.txt";
    String updatedDatasetPath = "resources/doc_pairs_1_prunned_vector.txt";
		updateSentimentWithVectorMethod(originalDatasetPath, updatedDatasetPath);
	}
	
	public static void updateSentimentWithVectorMethod(
	    String originalDatasetPath,
	    String updatedDatasetPath) {
		long startTime = System.currentTimeMillis();
		Map<Integer, SentimentSentence> orderToSentence = importOrderToSentence(
		    "src/edu/ucr/cs/dblab/nle020/reviewsdiversity/dataset/doc2vecsent/reviews/all-order-to-sentence.txt");
		Map<Integer, Double> orderToSentiment = SentimentExperiment.importSentimentFromRegression(
				SentimentExperiment.PREDICTED_SENTIMENT_DIR + "full_prediction_ridge.txt");
		
		Map<SentimentSentence, Double> sentenceToSentiment = new HashMap<>();
		for (Integer order : orderToSentence.keySet()) {
			sentenceToSentiment.put(orderToSentence.get(order), orderToSentiment.get(order));
		}
		
		
		Map<Integer, List<SentimentReview>> docToSentimentReviews = importDocToSentimentReviews(originalDatasetPath);
		
		// Re-assign sentiment of concept in sentence
		for (Integer docId : docToSentimentReviews.keySet()) {
			for (SentimentReview review : docToSentimentReviews.get(docId)) {
				for (SentimentSentence sentence : review.getSentences()) {
					if (sentenceToSentiment.containsKey(sentence)) {
						double sentiment = sentenceToSentiment.get(sentence);
						for (ConceptSentimentPair pair : sentence.getPairs()) {
							pair.setSentiment((float) sentiment);
						}
					}
				}
			}
		}
		

		outputDoctorSentimentReviewsToJson(docToSentimentReviews, updatedDatasetPath);
		System.out.println("Finish outputing JSON text file to \"" + updatedDatasetPath + "\" in " 
				+ (System.currentTimeMillis() - startTime) + " ms");
	}
	
	private static void outputDoctorSentimentReviewsToJson(
			Map<Integer, List<SentimentReview>> docToSentimentReviews,
			String updatedDatasetPath) {
		
		ObjectMapper mapper = new ObjectMapper();
		try {
			Files.deleteIfExists(Paths.get(updatedDatasetPath));
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		int count = 1;
		for (Integer docId : docToSentimentReviews.keySet()) {
			try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(updatedDatasetPath), 
				StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
				if (count > 1)						
					writer.newLine();				
				DoctorSentimentReview doctorReview = new DoctorSentimentReview(
				    docId, docToSentimentReviews.get(docId));
				mapper.writeValue(writer, doctorReview);
				
				count++;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		System.out.println("Num of Outputed docIDs " + count);	
	}

	private static Map<Integer, List<SentimentReview>> importDocToSentimentReviews(String path) {
		Map<Integer, List<SentimentReview>> result = new HashMap<Integer, List<SentimentReview>>();

		ObjectMapper mapper = new ObjectMapper();		
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(path))) {	
			String line;
			while ((line = reader.readLine()) != null) {
				DoctorSentimentReview doctorSentimentReview = mapper.readValue(
				    line, DoctorSentimentReview.class);
				result.put(doctorSentimentReview.getDocId(), doctorSentimentReview.getSentimentReviews());
			}
		} catch (IOException e) {
			e.printStackTrace();			
		}	

		return result;
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
}
