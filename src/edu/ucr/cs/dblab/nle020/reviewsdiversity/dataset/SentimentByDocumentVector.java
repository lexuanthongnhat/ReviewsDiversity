package edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentReview;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSentence;

public class SentimentByDocumentVector {
	
	public static void main(String[] args) {
		Map<Integer, List<SentimentReview>> docToSentimentReviews = PreprocessingForDocumentVector.importDocToSentimentReviews(
				PreprocessingForDocumentVector.DOC_TO_REVIEWS_PATH, 
				Constants.NUM_DOCTORS_TO_EXPERIMENT);
		
		Map<Integer, SentimentSentence> orderToSentence = 
				importOrderToSentence(PreprocessingForDocumentVector.PYTHON_WORKSPACE + "order-to-sentence.txt");
		
		Map<Integer, Float> orderToSentiment = importSentiment(PreprocessingForDocumentVector.PYTHON_WORKSPACE + "prediction.txt");
		Map<SentimentSentence, Integer> sentenceToOrder = new HashMap<SentimentSentence, Integer>();
		for (Integer order : orderToSentence.keySet()) {
			sentenceToOrder.put(orderToSentence.get(order), order);
		}
		
		Workbook wb = new XSSFWorkbook();
		Sheet sheet = wb.createSheet("Sentiment");
		addHeader(sheet);
		int rowCount = 1;
		for (Integer docId : docToSentimentReviews.keySet()) {
			for (SentimentReview review : docToSentimentReviews.get(docId)) {
				String reviewId = review.getId();
				int rating = review.getRawReview().getRate();
				
				String normalSentiment = "";
				String vectorSentiment = "";
				
				boolean isValid = false;
				if (review.getSentences().size() > 0) {
					for (SentimentSentence sentence : review.getSentences()) {
						if (sentenceToOrder.keySet().contains(sentence)) {
							isValid = true;
							
							normalSentiment = normalSentiment + sentence.getSentence() + sentence.getPairs().toString() + "\n";
							vectorSentiment = vectorSentiment + sentence.getSentence() + "    " + orderToSentiment.get(sentenceToOrder.get(sentence)) + "\n";
						}
					}
					
					if (isValid) {
						addRow(sheet, rowCount, docId, reviewId, rating, review.getRawReview().getBody(), normalSentiment, vectorSentiment);
						++rowCount;
					}
				}
			}
		}
		
		String outputPath = "D:\\merged_sentiment.xlsx";
//		String outputPath = PreprocessingForDocumentVector.PYTHON_WORKSPACE + "merged_sentiment.xlsx";
		try {
			wb.write(Files.newOutputStream(Paths.get(outputPath), 
					StandardOpenOption.CREATE));
			wb.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		System.out.println("Outputed to \"" + outputPath + "\"");
	}

	private static void addRow(Sheet sheet, int rowCount, Integer docId,
			String reviewId, int rating, String reviewBody, String normalSentiment, String vectorSentiment) {
		Row row = sheet.createRow(rowCount);
		
		Cell docCell = row.createCell(0);
		Cell reviewIdCell = row.createCell(1);
		Cell reviewRateCell = row.createCell(2);
		Cell reviewCell = row.createCell(3);
		Cell sentimentNormalCell = row.createCell(4);
		Cell sentimentVectorCell = row.createCell(5);	
		
		docCell.setCellValue(docId);
		reviewIdCell.setCellValue(reviewId);
		reviewRateCell.setCellValue(rating);
		reviewCell.setCellValue(reviewBody);
		sentimentNormalCell.setCellValue(normalSentiment);
		sentimentVectorCell.setCellValue(vectorSentiment);
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
				
		sheet.createFreezePane(6, 1);
		
		Cell docCell = row.createCell(0);
		Cell reviewIdCell = row.createCell(1);
		Cell reviewRateCell = row.createCell(2);
		Cell reviewCell = row.createCell(3);
		Cell sentimentNormalCell = row.createCell(4);
		Cell sentimentVectorCell = row.createCell(5);	
		
		docCell.setCellValue("doc Id");
		reviewIdCell.setCellValue("review Id");
		reviewRateCell.setCellValue("rating");
		reviewCell.setCellValue("review");
		sentimentNormalCell.setCellValue("normal sentiment");
		sentimentVectorCell.setCellValue("sentiment by vector");
		
		docCell.setCellStyle(cs);
		reviewIdCell.setCellStyle(cs);
		reviewRateCell.setCellStyle(cs);
		reviewCell.setCellStyle(cs);
		sentimentNormalCell.setCellStyle(cs);
		sentimentVectorCell.setCellStyle(cs);
	}

	private static Map<Integer, Float> importSentiment(String filePath) {
		Map<Integer, Float> orderToSentiment = new HashMap<Integer, Float>();
		
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath))) {
			String line = null;
			int count = 0;
			while ((line = reader.readLine()) != null) {
				float sentiment = Float.parseFloat(line.split(" ")[0]);
				orderToSentiment.put(count,(sentiment - 0.5f) * 2.0f);
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
}
