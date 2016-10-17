package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
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
import edu.ucr.cs.dblab.nle020.utils.Utils;

public class TopKBaselineComparation {
	private static final int DISABLE_CONCEPT_DISTANCE = 1000;
	
	private static String sourceInputFolder = TopPairsProgram.OUTPUT_FOLDER; 
		
	public static void main(String[] args) {
		long startTime = System.currentTimeMillis();
		Map<Integer, List<ConceptSentimentPair>> docToConceptSentimentPairDataset = 
				importConceptSentimentPairDataset(TopPairsProgram.DOC_TO_REVIEWS_PATH);
		
		int[] conceptDistanceThresholds = new int[] {2};
		float[] sentimentDistanceThresholds = new float[] {0.1f};
		
		for (int conceptDistanceThreshold : conceptDistanceThresholds) {
			for (float sentimentDistanceThreshold : sentimentDistanceThresholds) {
				String outputString = "k,ilp,rr,greedy,baseline\n";		
				int[] ks = new int[] {3, 5, 10, 15, 20};		
				for (int k : ks) {
					String topSentencesGreedyPath =  sourceInputFolder + "top_sentence/k" 
							+ k + "_threshold0.2/top_SENTENCE_result_1000_greedy_set.txt";
					String topSentencesILPPath =  sourceInputFolder + "top_sentence/k" 
							+ k + "_threshold0.2/top_SENTENCE_result_1000_ilp_set.txt";
					String topSentencesRRPath =  sourceInputFolder + "top_sentence/k" 
							+ k + "_threshold0.2/top_SENTENCE_result_1000_rr_set.txt";
					
					String topSentencesBaselinePath = sourceInputFolder
							+ "baseline/top_sentence_baseline_k" + k + ".txt";
					
					Map<Integer, List<SentimentSentence>> docToTopSentencesGreedy = 
							importDocToSentimentSentencesFromJson(topSentencesGreedyPath);
					Map<Integer, List<SentimentSentence>> docToTopSentencesILP = 
							importDocToSentimentSentencesFromJson(topSentencesILPPath);
					Map<Integer, List<SentimentSentence>> docToTopSentencesRR = 
							importDocToSentimentSentencesFromJson(topSentencesRRPath);
					
					Map<Integer, List<SentimentSentence>> docToTopSentencesBaseline = 
							importDocToSentimentSentencesFromJson(topSentencesBaselinePath);
					
					Set<Integer> docIds = docToTopSentencesGreedy.keySet();
					double greedyCoverage =	evaluating(docToConceptSentimentPairDataset, docIds, 
							conceptDistanceThreshold, sentimentDistanceThreshold, docToTopSentencesGreedy);
					double ilpCoverage =	evaluating(docToConceptSentimentPairDataset, docIds, 
							conceptDistanceThreshold, sentimentDistanceThreshold, docToTopSentencesILP);
					double rrCoverage =	evaluating(docToConceptSentimentPairDataset, docIds, 
							conceptDistanceThreshold, sentimentDistanceThreshold, docToTopSentencesRR);
					
					double baselineCoverage = evaluating(docToConceptSentimentPairDataset, docIds, 
							conceptDistanceThreshold, sentimentDistanceThreshold, docToTopSentencesBaseline);
					
					outputString = outputString + k + "," + ilpCoverage + ","
					               + rrCoverage + "," + greedyCoverage + "," + baselineCoverage + "\n";
				}

				String outputPath = sourceInputFolder
				    + "baseline/comparison_distance" 
						+ conceptDistanceThreshold + "_sentiment" + (int) Math.round(10*sentimentDistanceThreshold)
						+ ".csv";
				try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath), 
						StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
					writer.write(outputString);
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				System.out.println("Finished with distanceThreshold " + conceptDistanceThreshold 
						+ ", sentimentThreshold " + sentimentDistanceThreshold);
			}
		}
		
		Utils.printRunningTime(startTime, "Finished comparing to baseline", true);
	}
	

	private static double evaluating(
			Map<Integer, List<ConceptSentimentPair>> docToConceptSentimentPairDataset,
			Set<Integer> docIds,
			int conceptDistanceThreshold, float sentimentDistanceThreshold,
			Map<Integer, List<SentimentSentence>> docToTopSentences) {

		List<Double> coverages = new ArrayList<>();
		for (Integer docId : docIds) {
			double datasetSize = docToConceptSentimentPairDataset.get(docId).size();
			
			List<ConceptSentimentPair> uncoveredPairs = new ArrayList<>();
			uncoveredPairs.addAll(docToConceptSentimentPairDataset.get(docId));
			
			Set<ConceptSentimentPair> hostingPairs = new HashSet<>();
			docToTopSentences.get(docId).stream().forEach(
			    sentence -> hostingPairs.addAll(sentence.getPairs()));
			for (ConceptSentimentPair hostingPair : hostingPairs) {
				uncoveredPairs.removeIf(pair -> isCoverable(hostingPair, pair, conceptDistanceThreshold,
				                                                               sentimentDistanceThreshold));
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


	private static Map<Integer, List<ConceptSentimentPair>> importConceptSentimentPairDataset(
	    String docToReviewsPath) {
	  
		Map<Integer, List<ConceptSentimentPair>> docToConceptSentimentPairDataset = new HashMap<>();
		Map<Integer, List<SentimentSet>> docToSentences =
		    TopPairsProgram.importDocToSentimentSentences(docToReviewsPath, false);
		
		for (Integer docId : docToSentences.keySet()) {
			docToConceptSentimentPairDataset.put(docId, new ArrayList<>());
			docToSentences.get(docId).stream().forEach(
			    sentence -> docToConceptSentimentPairDataset.get(docId).addAll(sentence.getPairs()));
		}
		
		return docToConceptSentimentPairDataset;
	}


	private static Map<Integer, List<SentimentSentence>> importDocToSentimentSentencesFromJson(
	    String inputPath) {
	  
		Map<Integer, List<SentimentSentence>> result = new HashMap<>();		
		ObjectMapper mapper = new ObjectMapper();
		
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(inputPath))) {
			result = mapper.readValue(reader,
			    new TypeReference<Map<Integer, List<SentimentSentence>>>() {	});
		} catch (IOException e) {
			e.printStackTrace();
		}
		return result;
	}
}
