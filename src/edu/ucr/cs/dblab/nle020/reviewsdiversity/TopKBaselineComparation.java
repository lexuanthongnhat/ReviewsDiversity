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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSentence;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;

public class TopKBaselineComparation {
	private static String sourceInputFolder = TopPairsProgram.OUTPUT_FOLDER; 
	private static String topSentencesPath =  sourceInputFolder + "Top SENTENCE\\k5_threshold0.2\\top_SENTENCE_result_200_greedy_set.txt";
	private static String topSentencesBaselinePath = sourceInputFolder + "top_SENTENCE_baseline_k10.txt";
	
	
	public static void main(String[] args) {
		Map<Integer, List<ConceptSentimentPair>> docToConceptSentimentPairDataset = 
				importConceptSentimentPairDataset(TopPairsProgram.DOC_TO_REVIEWS_PATH);
		
		Map<Integer, List<SentimentSentence>> docToTopSentences = importDocToSentimentSentencesFromJson(topSentencesPath);
		Map<Integer, List<SentimentSentence>> docToTopSentencesBaseline = importDocToSentimentSentencesFromJson(topSentencesBaselinePath);
		
		int conceptDistanceThreshold = 3;
		float sentimentDistanceThreshold = 0.3f;
		float ourCoverage = 
				evaluating(docToConceptSentimentPairDataset, conceptDistanceThreshold, sentimentDistanceThreshold, docToTopSentences);
		
	}
	

	private static float evaluating(
			Map<Integer, List<ConceptSentimentPair>> docToConceptSentimentPairDataset,
			int conceptDistanceThreshold, float sentimentDistanceThreshold,
			Map<Integer, List<SentimentSentence>> docToTopSentences) {

		List<Float> coverages = new ArrayList<Float>();
		for (Integer docId : docToTopSentences.keySet()) {
			int datasetSize = docToConceptSentimentPairDataset.get(docId).size();
			
			List<ConceptSentimentPair> uncoveredPairs = new ArrayList<ConceptSentimentPair>();
			uncoveredPairs.addAll(docToConceptSentimentPairDataset.get(docId));
			
			Set<ConceptSentimentPair> hostingPairs = new HashSet<ConceptSentimentPair>();
			docToTopSentences.get(docId).stream().forEach(sentence -> hostingPairs.addAll(sentence.getPairs()));
			for (ConceptSentimentPair hostingPair : hostingPairs) {
				uncoveredPairs.removeIf(pair -> coverable(hostingPair, pair));
			}
		}
		
		
		return 0;
	}


	private static boolean coverable(ConceptSentimentPair hostingPair,
			ConceptSentimentPair pair) {
		// TODO Auto-generated method stub
		return null;
	}


	private static Map<Integer, List<ConceptSentimentPair>> importConceptSentimentPairDataset(String docToReviewsPath) {
		Map<Integer, List<ConceptSentimentPair>> docToConceptSentimentPairDataset = new HashMap<Integer, List<ConceptSentimentPair>>();
		Map<Integer, List<SentimentSet>> docToSentences = 
				TopPairsProgram.importDocToSentimentSentences(docToReviewsPath, Constants.NUM_DOCTORS_TO_EXPERIMENT);
		
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
