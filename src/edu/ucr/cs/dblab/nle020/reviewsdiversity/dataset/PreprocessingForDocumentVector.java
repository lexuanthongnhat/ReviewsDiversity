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
import java.util.Random;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentReview;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSentence;
import edu.ucr.cs.dblab.nle020.utils.Utils;

public class PreprocessingForDocumentVector {
	public final static String DOC_TO_REVIEWS_PATH = "D:\\UCR Google Drive\\GD - Review Diversity\\doc_pairs_1_prunned.txt";
	public final static String FULL_REVIEWS_PATH = "D:\\reviews.csv";
	public final static String PYTHON_WORKSPACE =
	    "src/edu/ucr/cs/dblab/nle020/reviewsdiversity/dataset/doc2vecsent/reviews/";	
	public final static int TRAINING_SIZE = 50000;
	public final static int SAMPLE_SIZE = 100;

	public static void main (String[] args) {
		Map<String, List<SentimentReview>> docToSentimentReviews =
				importDocToSentimentReviews(DOC_TO_REVIEWS_PATH, 1000);
		
		outputSampleSentenceToFile(docToSentimentReviews, PYTHON_WORKSPACE);
		
		Set<Integer> seenReviewIds = new HashSet<>();
		for (List<SentimentReview> reviews : docToSentimentReviews.values()) {
			for (SentimentReview review : reviews){
				seenReviewIds.add(Integer.parseInt(review.getId().trim()));
			}
		}
		
		List<RawReview> oneStarRawReviews = new ArrayList<>();
		List<RawReview> twoStarRawReviews = new ArrayList<>();
		List<RawReview> threeStarRawReviews = new ArrayList<>();
		List<RawReview> fourStarRawReviews = new ArrayList<>();

		List<RawReview> rawReviews = getReviews(FULL_REVIEWS_PATH);
		for (RawReview review : rawReviews) {
			if (!seenReviewIds.contains(review.getId())) {					
				if (review.getRate() == 25 && oneStarRawReviews.size() < TRAINING_SIZE)
					oneStarRawReviews.add(review);
				else if (review.getRate() == 50 && twoStarRawReviews.size() < TRAINING_SIZE)
					twoStarRawReviews.add(review);
				else if (review.getRate() == 75 && threeStarRawReviews.size() < TRAINING_SIZE)
					threeStarRawReviews.add(review);
				else if (review.getRate() == 100 && fourStarRawReviews.size() < TRAINING_SIZE)
					fourStarRawReviews.add(review);
			}
		};
		
		outputRawReviewToFile(oneStarRawReviews, PYTHON_WORKSPACE + "train-one-star-reviews.txt");
		outputRawReviewToFile(twoStarRawReviews, PYTHON_WORKSPACE + "train-two-star-reviews.txt");
		outputRawReviewToFile(threeStarRawReviews, PYTHON_WORKSPACE + "train-three-star-reviews.txt");
		outputRawReviewToFile(fourStarRawReviews, PYTHON_WORKSPACE + "train-four-star-reviews.txt");
		System.out.println("# reviews: " + rawReviews.size() + ", " 
				+ "#1-star: " + oneStarRawReviews.size() + ", #2-star: " + twoStarRawReviews.size()
				+ ", #3-star: " + threeStarRawReviews.size() + ", #4-star: " + fourStarRawReviews.size());
	}
	
	private static void outputSampleSentenceToFile(
	        Map<String, List<SentimentReview>> docToSentimentReviews,
            String outputFolder) {
		int samplePartSize = SAMPLE_SIZE / 4;
		
		List<SentimentSentence> sentences = new ArrayList<>();
		Map<Integer, SentimentSentence> orderToSentences = new HashMap<>();
		int sentenceCount = 0;
		
		List<SentimentReview> allReviews = new ArrayList<>();
		docToSentimentReviews.values().forEach(reviews -> allReviews.addAll(reviews));
		
		List<SentimentReview> sampleReviews = new ArrayList<>();
		int oneStarCount = 0;
		int twoStarCount = 0;
		int threeStarCount = 0;
		int fourStarCount = 0;
		Random random = new Random();
		Set<Integer> seenIndices = new HashSet<>();
		while ((oneStarCount + twoStarCount + threeStarCount + fourStarCount) < SAMPLE_SIZE) {
			int randomIndex = random.nextInt(allReviews.size());
			while (seenIndices.contains(randomIndex)) {
				randomIndex = random.nextInt(allReviews.size());
			}
			
			SentimentReview review = allReviews.get(randomIndex);
			if (review.getSentences().size() == 0)
				continue;
			
			if (review.getRawReview().getRate() == 25 && oneStarCount < samplePartSize) {
				sampleReviews.add(review);
				++oneStarCount;
			} else if (review.getRawReview().getRate() == 50 && twoStarCount < samplePartSize) {
				sampleReviews.add(review);
				++twoStarCount;
			} else if (review.getRawReview().getRate() == 75 && threeStarCount < samplePartSize) {
				sampleReviews.add(review);
				++threeStarCount;
			} else if (review.getRawReview().getRate() == 100 && fourStarCount < samplePartSize) {
				sampleReviews.add(review);
				++fourStarCount;
			}
		}
		
	
		for (SentimentReview review : sampleReviews) {
			for (SentimentSentence sentence : review.getSentences()) {
				if (sentence.getPairs().size() > 0) {
					sentences.add(sentence);
					orderToSentences.put(sentenceCount, sentence);
					++sentenceCount;
				}
			}
		}
		
		outputSentencesToFile(sentences, outputFolder + "test-sentences.txt");
		outputOrderToSentence(orderToSentences, outputFolder + "order-to-sentence.txt");		
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

	// Make sure: each conceptSentimentPair of a SentimentReview has an unique hashcode
	private static Map<String, List<SentimentReview>> importDocToSentimentReviews(
			String path, int numDoctorsToExperiment) {
		Map<String, List<SentimentReview>> result = new HashMap<>();
		
		ObjectMapper mapper = new ObjectMapper();		
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(path))) {	
			String line;
			int count = 0;
			while ((line = reader.readLine()) != null) {
				DoctorSentimentReview doctorSentimentReview = mapper.readValue(line, DoctorSentimentReview.class);

				String docId = doctorSentimentReview.getDocId();
				List<SentimentReview> sentimentReviews = new ArrayList<>();
				
				for (SentimentReview sentimentReview : doctorSentimentReview.getSentimentReviews()) {
					List<ConceptSentimentPair> pairs = new ArrayList<>();
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

	private static String normalizeText(String text) {
		String normalized = text.toLowerCase();
	
		// Erroneous codes
		normalized = normalized.replace("�", "").replace("�", "");
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

		List<RawReview> results = new ArrayList<>();
		try {
			Reader in = new FileReader(file);
			Iterable<CSVRecord> records = CSVFormat.EXCEL.parse(in);
			
			boolean isFirst = true;
			for (CSVRecord record : records) {
				if (isFirst) {
					isFirst = false;
					continue;
				}
				
				String id = record.get(0).trim();
                String docID = record.get(1).trim();
				String tittle = record.get(5).trim();
				String body = record.get(6).trim();
				body = body.replace("DR.", "DR").replace("dr.", "dr").replace("Dr.", "Dr").replace("dR.", "dR");
				body = body.replace("Drs.", "Drs").replace("DRs.", "DRs");
				
				body = body.replace("Mr.", "Mr").replace("mr.", "mr").replace("MR.", "MR");
				body = body.replace("Mrs.", "Mrs").replace("mrs.", "mrs").replace("MRS.", "MRS");
				body = body.replace("Miss.", "Miss");
				
				// The review "� " cause MetaMap server to fail				
				body = body.replace(" ", "");
				body = body.replace("�", "");
				body = body.replaceAll("…", "").replaceAll("�", "").replaceAll("�", "").replace("ï��", "");
				
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
