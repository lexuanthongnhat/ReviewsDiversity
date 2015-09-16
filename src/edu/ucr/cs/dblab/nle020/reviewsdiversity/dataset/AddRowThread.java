package edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset;

import edu.ucr.cs.dblab.nle020.metamap.MetaMapParser;
import edu.ucr.cs.dblab.nle020.metamap.SemanticTypes;
import edu.ucr.cs.dblab.nle020.metamap.Sentence;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.utilities.Utils;
import gov.nih.nlm.nls.metamap.Ev;
import gov.nih.nlm.nls.metamap.Position;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;

public class AddRowThread implements Runnable {
	private SentimentCalculator sentimentCalculator = new SentimentCalculator();
	
	private int index;
	private List<Row> rows;
	private List<RawReview> rawReviews;
	
	private CellStyle cs;
	private Font validFont;
	private Font invalidFont;
	
	ConcurrentMap<RawReview, List<ConceptSentimentPair>> docToReviewsValid; 
	ConcurrentMap<RawReview, List<ConceptSentimentPair>> docToReviewsInvalid;
	
	private MetaMapParser mmParser = new MetaMapParser();
		
	public AddRowThread(int index, List<Row> rows, List<RawReview> rawReviews,
			CellStyle cs, Font validFont, Font invalidFont, 
			ConcurrentMap<RawReview, List<ConceptSentimentPair>> docToReviewsValid, 
			ConcurrentMap<RawReview, List<ConceptSentimentPair>> docToReviewsInvalid) {
		super();
		this.index = index;
		this.rows = rows;
		this.rawReviews = rawReviews;
		this.cs = cs;
		this.validFont = validFont;
		this.invalidFont = invalidFont;
		
		this.docToReviewsValid = docToReviewsValid;
		this.docToReviewsInvalid = docToReviewsInvalid;
	}

	@Override
	public void run() {
		
		long startTime = System.currentTimeMillis();
		
		// Ex. 4 threads, interval = 100 (1% dataset):
		//			index = 0:  0  , 400, 800
		//			index = 1:	100, 500, 900
		//          index = 2:  200, 600, 1000
		// 			index = 3:  300, 700, 1100
		for (int i = 0 + index * Constants.INTERVAL_TO_SAMPLE_REVIEW; i < rawReviews.size(); i += Constants.INTERVAL_TO_SAMPLE_REVIEW * Constants.NUM_THREADS) {
			RawReview rawReview = rawReviews.get(i);
			Row row = rows.get(i / Constants.INTERVAL_TO_SAMPLE_REVIEW + i % Constants.INTERVAL_TO_SAMPLE_REVIEW);
			
			docToReviewsValid.putIfAbsent(rawReview, new ArrayList<ConceptSentimentPair>());
			docToReviewsInvalid.putIfAbsent(rawReview, new ArrayList<ConceptSentimentPair>());
			
			addRow(row, rawReview, docToReviewsValid.get(rawReview), docToReviewsInvalid.get(rawReview));
		}
		
		mmParser.disconnect();
		
		Utils.printRunningTime(startTime, Thread.currentThread().getName()	+ " finished");
	}	
	
	private void addRow(Row row, RawReview rawReview, 
			List<ConceptSentimentPair> validConceptSentiment, 
			List<ConceptSentimentPair> invalidConceptSentiment) {			
		
		addDefaultCells(row, cs, rawReview);				

		Cell cellInvalidConcepts = row.createCell(5);
		Cell cellInvalidConceptTypes = row.createCell(6);
		Cell cellInvalidConceptSentiment = row.createCell(7);
		Cell cellValidConcepts = row.createCell(8);
		Cell cellConceptTypes = row.createCell(9);
		Cell cellConceptSentiment = row.createCell(10);

		cellInvalidConcepts.setCellStyle(cs);
		cellValidConcepts.setCellStyle(cs);
		cellInvalidConceptSentiment.setCellStyle(cs);
		cellConceptTypes.setCellStyle(cs);
		cellConceptSentiment.setCellStyle(cs);
		cellInvalidConceptTypes.setCellStyle(cs);
		
		String body = rawReview.getBody();		
		
		Map<Sentence, List<Ev>> sentenceToValid = new HashMap<Sentence, List<Ev>>();
		Map<Sentence, List<Ev>> sentenceToInvalid = new HashMap<Sentence, List<Ev>>();
		mmParser.parseToSentenceMap(body, sentenceToValid, sentenceToInvalid);
		
		cellConceptTypes.setCellValue(prepareConceptTypesRichText(sentenceToValid, validFont));
		cellInvalidConceptTypes.setCellValue(prepareConceptTypesRichText(sentenceToInvalid, invalidFont));
			
		cellValidConcepts.setCellValue(prepareHightlightedConcept(body, sentenceToValid, validFont));
		cellInvalidConcepts.setCellValue(prepareHightlightedConcept(body, sentenceToInvalid, invalidFont));
		
		cellConceptSentiment.setCellValue(prepareConceptSentimentRichText(sentenceToValid, validConceptSentiment));
		cellInvalidConceptSentiment.setCellValue(prepareConceptSentimentRichText(sentenceToInvalid, invalidConceptSentiment));
	}

	/**
	 * @param conceptTypes
	 */
	private void hightlightConceptTypes(XSSFRichTextString conceptTypes, Font font) {

		String content = conceptTypes.getString();
		int startIndex = 0;
		int endIndex = -1;
		while (endIndex < content.length()) {
			startIndex = content.indexOf("\"", endIndex + 1);
			endIndex = content.indexOf("\"", startIndex + 1);
			
			if (startIndex >= 0 && startIndex < content.length() && endIndex > 0 && endIndex < content.length())
				conceptTypes.applyFont(startIndex, endIndex, font);
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
	}

	private XSSFRichTextString prepareHightlightedConcept(String body, Map<Sentence, List<Ev>> sentenceMap, Font font) {
		XSSFRichTextString conceptsRichText = new XSSFRichTextString(body);
		
		for (Map.Entry<Sentence, List<Ev>> entry : sentenceMap.entrySet()) {

			// Building the value for columns: cellInvalidConcepts, cellValidConcepts, cellConceptTypes
			for (Ev ev : entry.getValue()) {
				// For each mapping
				
				List<Position> positions;
				try {
					positions = ev.getPositionalInfo();					
					for (int i = 0; i < positions.size(); i++) {
						Position pos = positions.get(i);
					
						conceptsRichText.applyFont(pos.getX(), pos.getX() + pos.getY(), font);
					}
				} catch (Exception e) {					
					e.printStackTrace();
				}
			}
		}
		
		return conceptsRichText;
	}
	
	private XSSFRichTextString prepareConceptTypesRichText(Map<Sentence, List<Ev>> sentenceMap, Font font) {
		XSSFRichTextString typesRichText = new XSSFRichTextString();
		StringBuilder typesBuilder = new StringBuilder();
		
		for (Map.Entry<Sentence, List<Ev>> entry : sentenceMap.entrySet()) {

			// Building the value for columns: cellInvalidConcepts, cellValidConcepts, cellConceptTypes
			for (Ev ev : entry.getValue()) {
		
				List<Position> positions;
				try {
					positions = ev.getPositionalInfo();

					List<String> matchedWords = ev.getMatchedWords();
					List<String> types = ev.getSemanticTypes();
					
					for (int i = 0; i < positions.size(); i++) {
						// TODO - out of bound exception here		
						if (i < matchedWords.size())
							typesBuilder.append("\"" + matchedWords.get(i) + "\"-" +  ev.getConceptId() + "-" + ev.getConceptName() + ": ");
						else 
							typesBuilder.append("\"NO_MATCHED_WORD\"-" +  ev.getConceptId() + "-" + ev.getConceptName() + ": ");

						for (String type : types) {
							typesBuilder.append(type + " - ");
							typesBuilder.append(SemanticTypes.getInstance().getFullTypeNameByType(type) + " - ");						
							typesBuilder.append(SemanticTypes.getInstance().getGroupNameByType(type) + "; ");

							if (ev.getNegationStatus() == 1)
								typesBuilder.append(". NEGATED!");

						}
						typesBuilder.append("\n");
					}
				} catch (Exception e) {					
					e.printStackTrace();
				}
			}
		}
		
		if (typesBuilder.length() > 0) {
			typesBuilder.deleteCharAt(typesBuilder.length() - 1);			
			typesRichText.append(typesBuilder.toString());
			
			hightlightConceptTypes(typesRichText, font);
		}
		
		return typesRichText;
	}
	
	/**
	 * @param sentenceMap
	 * @param conceptSentimentPairs 
	 */
	private XSSFRichTextString prepareConceptSentimentRichText(Map<Sentence, List<Ev>> sentenceMap, 
			List<ConceptSentimentPair> conceptSentimentPairs) {
		
		XSSFRichTextString conceptSentimentString = new XSSFRichTextString();
		List<ConceptSentimentPair> pairs = sentimentCalculator.calculateSentiment(sentenceMap);
		StringBuilder conceptSentimentBuilder = new StringBuilder();
		for (ConceptSentimentPair pair : pairs) {
			conceptSentimentBuilder.append(pair.getName() + " :  " + 
						String.format("%1$." + Constants.NUM_DIGIT_PRECISION_OF_SENTIMENT + "f", pair.getSentiment()) + "\n");
		}
		
		if (conceptSentimentBuilder.length() > 0) {
			conceptSentimentBuilder.deleteCharAt(conceptSentimentBuilder.length() - 1);
			conceptSentimentString.append(conceptSentimentBuilder.toString());			
		}
		
		
		// XXX - update the ConceptSentimentPair
		conceptSentimentPairs.addAll(pairs);
		
		return conceptSentimentString;
	}	
	
	
	
	// Add 4 default cells: doctor ID, review's rate, title, original body
	private void addDefaultCells(Row row, CellStyle cs, RawReview rawReview) {
		Cell cellDoc = row.createCell(0);
		Cell cellReviewId = row.createCell(1);
		Cell cellRate = row.createCell(2);
		Cell cellTitle = row.createCell(3);
		Cell cellBody = row.createCell(4);
		
		cellDoc.setCellStyle(cs);
		cellRate.setCellStyle(cs);
		cellTitle.setCellStyle(cs);
		cellBody.setCellStyle(cs);
		cellReviewId.setCellStyle(cs);
		
		cellDoc.setCellValue(rawReview.getDocID());
		cellReviewId.setCellValue(rawReview.getId());
		cellRate.setCellValue(rawReview.getRate());
		cellTitle.setCellValue(rawReview.getTitle());
		cellBody.setCellValue(rawReview.getBody());
	}
}
