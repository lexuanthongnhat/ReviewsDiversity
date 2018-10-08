package edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;

class CorrelationCalculator {
		
	// Update the field "correlation" of ConceptSentimentPair
	static void calculatePairCorrelation(Map<RawReview, List<ConceptSentimentPair>> reviewToPairs) {
		Map<String, Float> reviewIdToRate = calculateReviewToNormalizedRate(reviewToPairs);
		Map<String, Map<String, Float>> cuiToSentimentMap = new HashMap<>();
		
		for (RawReview rawReview : reviewToPairs.keySet()) {
			String reviewId = rawReview.getId();
						
			Map<String, List<Float>> cuiToSentimentsWithinReview = new HashMap<>();
			
			for (ConceptSentimentPair pair : reviewToPairs.get(rawReview)) {
				String cui = pair.getId();				
				if (!cuiToSentimentsWithinReview.containsKey(cui))
					cuiToSentimentsWithinReview.put(cui, new ArrayList<>());
				cuiToSentimentsWithinReview.get(cui).add(pair.getSentiment());
			}
			
			for (String cui : cuiToSentimentsWithinReview.keySet()) {			
				float cuiSentiment = 0.0f;	
				for (Float sentiment : cuiToSentimentsWithinReview.get(cui)) 
					cuiSentiment += sentiment;	
				float cuiCount = (float) cuiToSentimentsWithinReview.get(cui).size();				
				
				if (!cuiToSentimentMap.containsKey(cui))
					cuiToSentimentMap.put(cui, new HashMap<>());
				cuiToSentimentMap.get(cui).put(reviewId, cuiSentiment / cuiCount);
			}
		}
		
		Map<String, Float> cuiToCorrelation = new HashMap<>();
		for (String cui : cuiToSentimentMap.keySet()) {
			cuiToCorrelation.put(cui, calculatePairCorrelationForEachConcept(reviewIdToRate, cuiToSentimentMap.get(cui)));
		}
		
		for (List<ConceptSentimentPair> pairList : reviewToPairs.values()) {
			for (ConceptSentimentPair pair : pairList) {
				pair.setCorrelation(cuiToCorrelation.get(pair.getId()));
			}
		}
	}
	
	// Update the field "correlation" of ConceptSentimentPair, treat concept as present or absent (not by sentiment)
	static void calculatePairCorrelationWithPresence(
			Map<RawReview, List<ConceptSentimentPair>> reviewToPairs) {
		Map<String, Float> reviewIdToRate = calculateReviewToNormalizedRate(reviewToPairs);
		Map<String, Map<String, Float>> cuiToPresenceMap = new HashMap<>();
		
		for (RawReview rawReview : reviewToPairs.keySet()) {
			String reviewId = rawReview.getId();
						
			for (ConceptSentimentPair pair : reviewToPairs.get(rawReview)) {
				String cui = pair.getId();
				if (!cuiToPresenceMap.containsKey(cui))
					cuiToPresenceMap.put(cui, new HashMap<>());
				cuiToPresenceMap.get(cui).putIfAbsent(reviewId, (float) 1);
			}
		}		
		
		Map<String, Float> cuiToCorrelation = new HashMap<>();
		for (String cui : cuiToPresenceMap.keySet()) {
			cuiToCorrelation.put(cui, calculatePairCorrelationWithPresenceForEachConcept(
			        reviewIdToRate, cuiToPresenceMap.get(cui)));
		}		
		
		for (List<ConceptSentimentPair> pairList : reviewToPairs.values()) {
			for (ConceptSentimentPair pair : pairList) {
				pair.setCorrelation(cuiToCorrelation.get(pair.getId()));
			}
		}
	}

	private static Float calculatePairCorrelationForEachConcept(
			Map<String, Float> reviewIdToRate, Map<String, Float> reviewIdToConceptSentiment) {
		double correlation;
//		float size = reviewIdToConceptSentiment.size();
		
		double reviewMeanSum = 0.0f;
		double conceptMeanSum = 0.0f;
		
		double numerator = 0.0f;
		double conceptDenominator = 0.0f;
		double reviewDenominator = 0.0f;
		float count = 0; 
		for (String reviewId : reviewIdToConceptSentiment.keySet()) {
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
			Map<String, Float> reviewIdToRate, Map<String, Float> reviewIdToConceptPresence) {
		double correlation;
		float size = reviewIdToRate.size();
		
		double reviewMeanSum = 0.0f;
		double conceptMeanSum = 0.0f;
		
		double numerator = 0.0f;
		double conceptDenominator = 0.0f;
		double reviewDenominator = 0.0f;

		for (String reviewId : reviewIdToRate.keySet()) {
			
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

	private static Map<String, Float> calculateReviewToNormalizedRate(
			Map<RawReview, List<ConceptSentimentPair>> reviewToPairs) {
		Map<String, Float> reviewIdToRate = new HashMap<>();
		for (RawReview rawReview : reviewToPairs.keySet()) {
			if (reviewToPairs.get(rawReview).size() > 0) {
				reviewIdToRate.put(rawReview.getId(), normalizingRate(rawReview.getRate()));
			}
		}
		
		return reviewIdToRate;
	}

	private static float normalizingRate(int rate) {
		float mean = (100.0f + 25.0f) / 2.0f;
		return (rate - mean) / mean;
	}

}
