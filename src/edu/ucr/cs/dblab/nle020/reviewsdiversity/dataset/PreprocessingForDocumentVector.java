package edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentReview;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSentence;
import edu.ucr.cs.dblab.nle020.utilities.Utils;

public class PreprocessingForDocumentVector {
	public final static String DOC_TO_REVIEWS_PATH = "D:\\UCR Google Drive\\GD - Review Diversity\\doc_pairs_1_prunned.txt";
	public final static String FULL_REVIEWS_PATH = "D:\\reviews.csv";
	public final static String PYTHON_WORKSPACE = "D:\\UCR Google Drive\\python_workspace\\";
	public final static int TRAINING_SIZE = 200000;

	public static void main (String[] args) {
		Map<Integer, List<SentimentReview>> docToSentimentReviews = 
				importDocToSentimentReviews(DOC_TO_REVIEWS_PATH, Constants.NUM_DOCTORS_TO_EXPERIMENT);
		Set<Integer> seenReviewIds = new HashSet<Integer>();
		for (List<SentimentReview> reviews : docToSentimentReviews.values()) {
			for (SentimentReview review : reviews){
				seenReviewIds.add(Integer.parseInt(review.getId().trim()));
			}
		}
		
		List<RawReview> posRawReviews = new ArrayList<RawReview>();
		List<RawReview> negRawReviews = new ArrayList<RawReview>();
		int posCount = 0;
		int negCount = 0;
		List<RawReview> rawReviews = getReviews(FULL_REVIEWS_PATH);
		for (RawReview review : rawReviews) {
			if (!seenReviewIds.contains(review.getId())) {
				if (review.getRate() > 50 && posCount < TRAINING_SIZE) {
					posRawReviews.add(review);
					++posCount;
				} else if (review.getRate() <= 50 && negCount < TRAINING_SIZE) {
					negRawReviews.add(review);
					++negCount;
				}
			}
		};
		
		outputRawReviewToFile(posRawReviews, PYTHON_WORKSPACE + "train-pos-reviews.txt");
		outputRawReviewToFile(negRawReviews, PYTHON_WORKSPACE + "train-neg-reviews.txt");
		System.out.println("# reviews: " + rawReviews.size() + ", #pos: " + posCount + ", #neg: " + negCount);
	}
	
	private static void outputRawReviewToFile(List<RawReview> rawReviews, String outputPath) {
		
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath),
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			for (RawReview review : rawReviews) {
				writer.append(normalizeText(review.getBody()));
				writer.newLine();
			}
		} catch (IOException e) {			
			e.printStackTrace();
		}		
	}

	public static void outputingStandardReviews() {
		Map<Integer, List<SentimentReview>> docToSentimentReviews = new HashMap<Integer, List<SentimentReview>>();		
		docToSentimentReviews = importDocToSentimentReviews(DOC_TO_REVIEWS_PATH, Constants.NUM_DOCTORS_TO_EXPERIMENT);
		
		List<SentimentReview> positiveReviews = new ArrayList<SentimentReview>();
		List<SentimentReview> negativeReviews = new ArrayList<SentimentReview>();
		
		List<SentimentSentence> sentences = new ArrayList<SentimentSentence>();
		Map<Integer, SentimentSentence> orderToSentences = new HashMap<Integer, SentimentSentence>();
		int sentenceCount = 0;
		
		for (List<SentimentReview> sentimentReviews : docToSentimentReviews.values()) {
			for (SentimentReview review : sentimentReviews) {
				if (review.getRawReview().getRate() > 50)
					positiveReviews.add(review);
				else
					negativeReviews.add(review);
				
				for (SentimentSentence sentence : review.getSentences()) {
					if (sentence.getPairs().size() > 0) {
						sentences.add(sentence);
						orderToSentences.put(sentenceCount, sentence);
						++sentenceCount;
					}
				}
			}
		}
		
		
		outputReviewsToFile(positiveReviews, PYTHON_WORKSPACE + "train-pos-reviews.txt");
		outputReviewsToFile(negativeReviews, PYTHON_WORKSPACE + "train-neg-reviews.txt");
		
		outputSentencesToFile(sentences, PYTHON_WORKSPACE + "test-sentences.txt");
		outputOrderToSentence(orderToSentences, PYTHON_WORKSPACE + "order-to-sentence.txt");		
		
		System.out.println("# pos_reviews: " + positiveReviews.size() + ", # neg_reviews: " + negativeReviews.size() +
				", # sentences: " + sentences.size());
	}
	
	// Make sure: each conceptSentimentPair of a SentimentReview has an unique hashcode
	public static Map<Integer, List<SentimentReview>> importDocToSentimentReviews(String path, int numDoctorsToExperiment) {
		Map<Integer, List<SentimentReview>> result = new HashMap<Integer, List<SentimentReview>>();
		
		ObjectMapper mapper = new ObjectMapper();		
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(path))) {	
			String line;
			int count = 0;
			while ((line = reader.readLine()) != null) {
				DoctorSentimentReview doctorSentimentReview = mapper.readValue(line, DoctorSentimentReview.class);
				
				Integer docId = doctorSentimentReview.getDocId();
				List<SentimentReview> sentimentReviews = new ArrayList<SentimentReview>();
				
				for (SentimentReview sentimentReview : doctorSentimentReview.getSentimentReviews()) {
					List<ConceptSentimentPair> pairs = new ArrayList<ConceptSentimentPair>();
					for (SentimentSentence sentimentSentence : sentimentReview.getSentences()) {
						for (ConceptSentimentPair csPair : sentimentSentence.getPairs()) {
						
							if (!pairs.contains(csPair))
								pairs.add(csPair);
							else {
								ConceptSentimentPair currentPair = pairs.get(pairs.indexOf(csPair));
								currentPair.incrementCount(csPair.getCount());
							}
						}
					}
					
					int rating = sentimentReview.getRawReview().getRate();
					if (pairs.size() > 0 && rating > 0)
						sentimentReviews.add(new SentimentReview(sentimentReview.getId(), pairs, 
								sentimentReview.getRawReview(),
								sentimentReview.getSentences()));
				}
				
				
				result.put(docId, sentimentReviews);
				
				++count;
				if (count >= numDoctorsToExperiment)
					break;
			}
		} catch (IOException e) {
			e.printStackTrace();			
		}	
		
		return result;
	}

	private static void outputOrderToSentence(
			Map<Integer, SentimentSentence> orderToSentences, String outputPath) {
		ObjectMapper mapper = new ObjectMapper();
		
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath),
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			mapper.writeValue(writer, orderToSentences);
		} catch (IOException e) {			
			e.printStackTrace();
		}		
	}

	private static void outputSentencesToFile(
			List<SentimentSentence> sentences, String outputPath) {
		
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath),
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			for (SentimentSentence sentence : sentences) {
				writer.append(normalizeText(sentence.getSentence()));
				writer.newLine();
			}
		} catch (IOException e) {			
			e.printStackTrace();
		}		
	}

	private static void outputReviewsToFile(
			List<SentimentReview> reviews, String outputPath) {
/*		try {
			Files.deleteIfExists(Paths.get(outputPath));
		} catch (IOException e) {
			e.printStackTrace();
		}	*/	
		
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath),
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			for (SentimentReview review : reviews) {
				writer.append(normalizeText(review.getRawReview().getBody()));
				writer.newLine();
			}
		} catch (IOException e) {			
			e.printStackTrace();
		}
		
	}
	
	private static String normalizeText(String text) {
		String normalized = text.toLowerCase();
	
		// Erroneous codes
		normalized = normalized.replace("…", "").replace("—", "");
		normalized = normalized.replaceAll("\\x85", "").replaceAll("\\x97", "")
				.replaceAll("\\xb4", "").replaceAll("\\x96", "");
		
		// Disconnecting the punctuation from words
		normalized = normalized.replace(".", " . ").replace("?", " ? ").replace("!", " ! ")
				.replace(";", " ; ").replace(":", " : ").replace("\"", " \" ")
				.replace("(", " ( ").replace(")", " ) ")
				.replace("[", " [ ").replace("]", " ] ")
				.replace("<br />", "");
		
		return normalized;
	}
	
	private static List<RawReview> getReviews(String file) {
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
