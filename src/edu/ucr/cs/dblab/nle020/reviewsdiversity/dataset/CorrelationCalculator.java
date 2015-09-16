package edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;

public class CorrelationCalculator {
		
	// Update the field "correlation" of ConceptSentimentPair
	public static void calculatePairCorrelation(Map<RawReview, List<ConceptSentimentPair>> reviewToPairs) {
		Map<Integer, Float> reviewIdToRate = calculateReviewToNormalizedRate(reviewToPairs);

		Map<String, Map<Integer, Float>> cuiToSentimentMap = new HashMap<String, Map<Integer, Float>>();
		
		for (RawReview rawReview : reviewToPairs.keySet()) {
			int reviewId = rawReview.getId();
						
			Map<String, List<Float>> cuiToSentimentsWithinReview = new HashMap<String, List<Float>>();
			
			for (ConceptSentimentPair pair : reviewToPairs.get(rawReview)) {
				String cui = pair.getId();				
				if (!cuiToSentimentsWithinReview.containsKey(cui))
					cuiToSentimentsWithinReview.put(cui, new ArrayList<Float>());
				cuiToSentimentsWithinReview.get(cui).add(pair.getSentiment());
			}
			
			for (String cui : cuiToSentimentsWithinReview.keySet()) {			
				float cuiSentiment = 0.0f;	
				for (Float sentiment : cuiToSentimentsWithinReview.get(cui)) 
					cuiSentiment += sentiment;	
				float cuiCount = (float) cuiToSentimentsWithinReview.get(cui).size();				
				
				if (!cuiToSentimentMap.containsKey(cui))
					cuiToSentimentMap.put(cui, new HashMap<Integer, Float>());
				cuiToSentimentMap.get(cui).put(reviewId, cuiSentiment / cuiCount);
			}
		}
		
		////////////////////////////
		Map<String, Float> cuiToCorrelation = new HashMap<String, Float>();
		for (String cui : cuiToSentimentMap.keySet()) {
			cuiToCorrelation.put(cui, calculatePairCorrelationForEachConcept(reviewIdToRate, cuiToSentimentMap.get(cui)));
		}
		
		////////////////////////////
		for (List<ConceptSentimentPair> pairList : reviewToPairs.values()) {
			for (ConceptSentimentPair pair : pairList) {
				pair.setCorrelation(cuiToCorrelation.get(pair.getId()));
			}
		}
	}
	
	// Update the field "correlation" of ConceptSentimentPair, treat concept as present or absent (not by sentiment)
	public static void calculatePairCorrelationWithPresence(Map<RawReview, List<ConceptSentimentPair>> reviewToPairs) {
		
		Map<Integer, Float> reviewIdToRate = calculateReviewToNormalizedRate(reviewToPairs);
		
		Map<String, Map<Integer, Float>> cuiToPresenceMap = new HashMap<String, Map<Integer, Float>>();
		
		for (RawReview rawReview : reviewToPairs.keySet()) {
			int reviewId = rawReview.getId();
						
			for (ConceptSentimentPair pair : reviewToPairs.get(rawReview)) {
				String cui = pair.getId();
				if (!cuiToPresenceMap.containsKey(cui))
					cuiToPresenceMap.put(cui, new HashMap<Integer, Float>());
				cuiToPresenceMap.get(cui).putIfAbsent(reviewId, (float) 1);
			}
		}		
		
		Map<String, Float> cuiToCorrelation = new HashMap<String, Float>();
		for (String cui : cuiToPresenceMap.keySet()) {
			cuiToCorrelation.put(cui, calculatePairCorrelationWithPresenceForEachConcept(reviewIdToRate, cuiToPresenceMap.get(cui)));
		}		
		
		for (List<ConceptSentimentPair> pairList : reviewToPairs.values()) {
			for (ConceptSentimentPair pair : pairList) {
				pair.setCorrelation(cuiToCorrelation.get(pair.getId()));
			}
		}
	}

	private static Float calculatePairCorrelationForEachConcept(
			Map<Integer, Float> reviewIdToRate, Map<Integer, Float> reviewIdToConceptSentiment) {
		double correlation = 0.0f;
//		float size = reviewIdToConceptSentiment.size();
		
		double reviewMeanSum = 0.0f;
		double conceptMeanSum = 0.0f;
		
		double numerator = 0.0f;
		double conceptDenominator = 0.0f;
		double reviewDenominator = 0.0f;
		float count = 0; 
		for (Integer reviewId : reviewIdToConceptSentiment.keySet()) {
			if (reviewIdToRate.containsKey(reviewId)) {
				++count;
				reviewMeanSum += reviewIdToRate.get(reviewId);
				conceptMeanSum += reviewIdToConceptSentiment.get(reviewId);
				
				float reviewRate = reviewIdToRate.get(reviewId);
				float conceptSentiment = reviewIdToConceptSentiment.get(reviewId);
				
				numerator += reviewRate * conceptSentiment;
				conceptDenominator += conceptSentiment * conceptSentiment;
				reviewDenominator += reviewRate * reviewRate;
			}
		}
		double reviewMean = reviewMeanSum / count;
		double conceptMean = conceptMeanSum/ count;
		
		numerator -= count * reviewMean * conceptMean;
		conceptDenominator = Math.sqrt(conceptDenominator - count * conceptMean * conceptMean);
		reviewDenominator = Math.sqrt(reviewDenominator - count *  reviewMean * reviewMean);
		
		correlation = numerator / (conceptDenominator * reviewDenominator);
		
/*		if (Math.abs(correlation) > 1.5)
			System.err.println("Wrong Correlation " + correlation);*/
		
		return (float) correlation;
	}
	
	private static Float calculatePairCorrelationWithPresenceForEachConcept(
			Map<Integer, Float> reviewIdToRate, Map<Integer, Float> reviewIdToConceptPresence) {
		double correlation = 0.0f;
		float size = reviewIdToRate.size();
		
		double reviewMeanSum = 0.0f;
		double conceptMeanSum = 0.0f;
		
		double numerator = 0.0f;
		double conceptDenominator = 0.0f;
		double reviewDenominator = 0.0f;

		for (Integer reviewId : reviewIdToRate.keySet()) {
			
			float reviewRate = reviewIdToRate.get(reviewId);	
			float conceptPresence = 0.0f;
			
			if (reviewIdToConceptPresence.containsKey(reviewId)) {
				conceptPresence = reviewIdToConceptPresence.get(reviewId);
				conceptMeanSum += conceptPresence;
			}		
			reviewMeanSum += reviewIdToRate.get(reviewId);
			
			numerator += reviewRate * conceptPresence;			
			conceptDenominator += conceptPresence * conceptPresence;
			reviewDenominator += reviewRate * reviewRate;
		}		
		
		double reviewMean = reviewMeanSum / size;
		double conceptMean = conceptMeanSum/ size;
		
		numerator -= size * reviewMean * conceptMean;
		conceptDenominator = Math.sqrt(conceptDenominator - size * conceptMean * conceptMean);
		reviewDenominator = Math.sqrt(reviewDenominator - size *  reviewMean * reviewMean);
		
		correlation = numerator / (conceptDenominator * reviewDenominator);
		
/*		if (Math.abs(correlation) > 1)
			System.err.println("Wrong Correlation");*/
		
		return (float) correlation;
	}

	private static Map<Integer, Float> calculateReviewToNormalizedRate(
			Map<RawReview, List<ConceptSentimentPair>> reviewToPairs) {
		Map<Integer, Float> reviewIdToRate = new HashMap<Integer, Float>();		
		for (RawReview rawReview : reviewToPairs.keySet()) {
			if (reviewToPairs.get(rawReview).size() > 0) {
				reviewIdToRate.put(rawReview.getId(), normalizingRate(rawReview.getRate()));
			}
		}
		
		return reviewIdToRate;
	}

	public static float normalizingRate(int rate) {
		float mean = (100.0f + 25.0f) / 2.0f;
		return (float) (rate - mean) / mean;
	}

}
