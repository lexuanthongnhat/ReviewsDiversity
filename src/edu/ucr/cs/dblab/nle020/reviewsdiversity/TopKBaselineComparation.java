package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSentence;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;

public class TopKBaselineComparation {
	private static final int DISABLE_CONCEPT_DISTANCE = 1000;
	
	private static String sourceInputFolder = TopPairsProgram.OUTPUT_FOLDER; 
	private static String topSentencesPath =  sourceInputFolder + "Top SENTENCE\\k5_threshold0.2\\top_SENTENCE_result_200_greedy_set.txt";
	private static String topSentencesBaselinePath = sourceInputFolder + "top_SENTENCE_baseline_k5.txt";
	
	
	public static void main(String[] args) {
		Map<Integer, List<ConceptSentimentPair>> docToConceptSentimentPairDataset = 
				importConceptSentimentPairDataset(TopPairsProgram.DOC_TO_REVIEWS_PATH);
		
		Map<Integer, List<SentimentSentence>> docToTopSentences = importDocToSentimentSentencesFromJson(topSentencesPath);
		Map<Integer, List<SentimentSentence>> docToTopSentencesBaseline = importDocToSentimentSentencesFromJson(topSentencesBaselinePath);
		
		int conceptDistanceThreshold = 3;
		float sentimentDistanceThreshold = 0.3f;
		double ourCoverage = 
				evaluating(docToConceptSentimentPairDataset, conceptDistanceThreshold, sentimentDistanceThreshold, docToTopSentences);
		double baselineCoverage = 
				evaluating(docToConceptSentimentPairDataset, conceptDistanceThreshold, sentimentDistanceThreshold, docToTopSentencesBaseline);
	

		System.out.println("ourCoverage " + ourCoverage + ", \tbaselineCoverage: " + baselineCoverage);
	}
	

	private static double evaluating(
			Map<Integer, List<ConceptSentimentPair>> docToConceptSentimentPairDataset,
			int conceptDistanceThreshold, float sentimentDistanceThreshold,
			Map<Integer, List<SentimentSentence>> docToTopSentences) {

		List<Double> coverages = new ArrayList<Double>();
		for (Integer docId : docToConceptSentimentPairDataset.keySet()) {
			double datasetSize = docToConceptSentimentPairDataset.get(docId).size();
			
			List<ConceptSentimentPair> uncoveredPairs = new ArrayList<ConceptSentimentPair>();
			uncoveredPairs.addAll(docToConceptSentimentPairDataset.get(docId));
			
			Set<ConceptSentimentPair> hostingPairs = new HashSet<ConceptSentimentPair>();
			docToTopSentences.get(docId).stream().forEach(sentence -> hostingPairs.addAll(sentence.getPairs()));
			for (ConceptSentimentPair hostingPair : hostingPairs) {
				uncoveredPairs.removeIf(pair -> isCoverable(hostingPair, pair, conceptDistanceThreshold, sentimentDistanceThreshold));
			}
			
			coverages.add(1.0f - (double) uncoveredPairs.size() / datasetSize);
		}
				
		return coverages.stream().collect(Collectors.averagingDouble(coverage -> coverage));
	}


	private static boolean isCoverable(ConceptSentimentPair hostingPair,
			ConceptSentimentPair pair, 
			int conceptDistanceThreshold,
			float sentimentDistanceThreshold) {
		
		if (Math.abs(hostingPair.getSentiment() - pair.getSentiment()) > sentimentDistanceThreshold)
			return false;
		else if (conceptDistanceThreshold >= DISABLE_CONCEPT_DISTANCE) 
			return true;
		else if (conceptDistance(hostingPair, pair) > conceptDistanceThreshold)
			return false;
		else
			return true;		
	}


	private static int conceptDistance(ConceptSentimentPair hostingPair,
			ConceptSentimentPair pair) {
		
		int min =  Constants.INVALID_DISTANCE;
		for (String hostingDewey : hostingPair.getDeweys()) {
			for (String clientDewey : pair.getDeweys()) {
				int distance = Constants.INVALID_DISTANCE;
				
				if (hostingDewey.equals(clientDewey))
					distance = 0;
				else {
					String[] hostings = hostingDewey.split("\\.");
					String[] clients = clientDewey.split("\\.");
					
					int minLength = hostings.length <= clients.length ? hostings.length : clients.length;
					for (int i = 0; i < minLength; ++i) {
						if (!hostings[i].equalsIgnoreCase(clients[i])) {
							distance = (hostings.length - i) + (clients.length - i);
							break;
						}
					}
				}
				if (distance < min)
					min = distance;
			}
		}
		return min;
	}


	private static Map<Integer, List<ConceptSentimentPair>> importConceptSentimentPairDataset(String docToReviewsPath) {
		Map<Integer, List<ConceptSentimentPair>> docToConceptSentimentPairDataset = new HashMap<Integer, List<ConceptSentimentPair>>();
		Map<Integer, List<SentimentSet>> docToSentences = TopPairsProgram.importDocToSentimentSentences(docToReviewsPath);
		
		for (Integer docId : docToSentences.keySet()) {
			docToConceptSentimentPairDataset.put(docId, new ArrayList<ConceptSentimentPair>());
			docToSentences.get(docId).stream().
				forEach(sentence -> docToConceptSentimentPairDataset.get(docId).addAll(sentence.getPairs()));
		}
		
		return docToConceptSentimentPairDataset;
	}


	private static Map<Integer, List<SentimentSentence>> importDocToSentimentSentencesFromJson(String inputPath) {
		Map<Integer, List<SentimentSentence>> result = new HashMap<Integer, List<SentimentSentence>>();
		
		ObjectMapper mapper = new ObjectMapper();
		
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(inputPath))) {
			result = mapper.readValue(reader, new TypeReference<Map<Integer, List<SentimentSentence>>>() {	});
		} catch (IOException e) {
			e.printStackTrace();
		}
		return result;
	}
}
