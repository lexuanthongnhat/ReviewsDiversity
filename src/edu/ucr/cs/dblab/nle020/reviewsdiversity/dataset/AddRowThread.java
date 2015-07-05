package edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset;

import edu.ucr.cs.dblab.nle020.metamap.MetaMapParser;
import edu.ucr.cs.dblab.nle020.metamap.SemanticTypes;
import edu.ucr.cs.dblab.nle020.metamap.Sentence;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;
import edu.ucr.cs.dblab.nle020.utilities.Utils;
import gov.nih.nlm.nls.metamap.Ev;
import gov.nih.nlm.nls.metamap.Position;

import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;

public class AddRowThread implements Runnable {

	private int index;
	private List<Row> rows;
	private List<Review> reviews;
	
	private CellStyle cs;
	private Font validFont;
	private Font invalidFont;
	
	private MetaMapParser mmParser = new MetaMapParser();
		
	public AddRowThread(int index, List<Row> rows, List<Review> reviews,
			CellStyle cs, Font validFont, Font invalidFont) {
		super();
		this.index = index;
		this.rows = rows;
		this.reviews = reviews;
		this.cs = cs;
		this.validFont = validFont;
		this.invalidFont = invalidFont;
	}

	@Override
	public void run() {
		
		long startTime = System.currentTimeMillis();
		
		// Ex. 4 threads, interval = 100 (1% dataset):
		//			index = 0:  0  , 400, 800
		//			index = 1:	100, 500, 900
		//          index = 2:  200, 600, 1000
		// 			index = 3:  300, 700, 1100
		for (int i = 0 + index * Constants.INTERVAL; i < reviews.size(); i += Constants.INTERVAL * Constants.NUM_THREADS) {
			Review review = reviews.get(i);
			Row row = rows.get(i / Constants.INTERVAL + i % Constants.INTERVAL);
			addRow(row, review);
		}
		
		mmParser.disconnect();
		
		Utils.printRunningTime(startTime, Thread.currentThread().getName()	+ " finished");
	}	
	
	private void addRow(Row row, Review review) {			
		addDefaultCells(row, cs, review);				

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
		
		String body = review.getBody();		
		
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
							validConcepts.applyFont(pos.getX(), pos.getX() + pos.getY(), validFont);
					// TODO - out of bound exception here		
							if (i < matchedWords.size())
								conceptTypesBuilder.append(ev.getConceptId() + "-" + ev.getConceptName() + "-\"" + matchedWords.get(i) + "\": ");
							else 
								conceptTypesBuilder.append(ev.getConceptId() + "-" + ev.getConceptName() + "-\"NO_MATCHED_WORD\": ");
							
							for (String type : types) {
								conceptTypesBuilder.append(type + " - ");
								conceptTypesBuilder.append(SemanticTypes.getInstance().getFullTypeNameByType(type) + " - ");						
								conceptTypesBuilder.append(SemanticTypes.getInstance().getGroupNameByType(type) + "; ");
/*								for (String source : ev.getSources()) {
									if (source.startsWith("SNOMED"))
										conceptTypesBuilder.append(" " + source);
								}*/
								
								if (ev.getNegationStatus() == 1)
									conceptTypesBuilder.append(". NEGATED!");
								
							}
							conceptTypesBuilder.append("\n");
						} else {
							invalidConcepts.applyFont(pos.getX(), pos.getX() + pos.getY(), invalidFont);
							
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
			
		// Building the value for columns: conceptSentiment TODO
//		List<ConceptSentimentPair> pairs = calculateSentiment(sentenceMap);
		List<ConceptSentimentPair> pairs = SentimentCalculator.calculateSentiment(sentenceMap);
		
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
					invalidConceptTypes.applyFont(startIndex, endIndex, invalidFont);
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
					conceptTypes.applyFont(startIndex, endIndex, validFont);
				else
					break;
			}
			
			// NEGATED
			startIndex = 0;
			endIndex = -1;
			while (endIndex < content.length()) {
				startIndex = content.indexOf("NEGATED", endIndex + 1);
				endIndex = content.indexOf("!", startIndex + 1);
				
				if (startIndex >= 0 && startIndex < content.length() && endIndex > 0 && endIndex < content.length())
					conceptTypes.applyFont(startIndex, endIndex, invalidFont);
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
	
	// Add 4 default cells: doctor ID, review's rate, title, original body
	private void addDefaultCells(Row row, CellStyle cs, Review review) {
		Cell cellDoc = row.createCell(0);
		Cell cellRate = row.createCell(1);
		Cell cellTitle = row.createCell(2);
		Cell cellBody = row.createCell(3);
		
		cellDoc.setCellStyle(cs);
		cellRate.setCellStyle(cs);
		cellTitle.setCellStyle(cs);
		cellBody.setCellStyle(cs);
		
		cellDoc.setCellValue(review.getDocID());
		cellRate.setCellValue(review.getRate());
		cellTitle.setCellValue(review.getTitle());
		cellBody.setCellValue(review.getBody());
	}
}
