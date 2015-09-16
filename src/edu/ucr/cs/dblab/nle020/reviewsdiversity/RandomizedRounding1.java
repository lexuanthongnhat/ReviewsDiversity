package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import java.util.List;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;

public class RandomizedRounding1 extends RandomizedRounding implements Runnable {

	private int docId;
	private List<ConceptSentimentPair> conceptSentimentPairs;	
	
	public RandomizedRounding1(int k, float threshold, ConcurrentMap<Integer, StatisticalResult> docToStatisticalResult, 
			int docId, List<ConceptSentimentPair> conceptSentimentPairs) {
		super(k, threshold, docToStatisticalResult);
		this.docId = docId;
		this.conceptSentimentPairs = conceptSentimentPairs;	
	}
	
	public RandomizedRounding1(int k, float threshold, 
			ConcurrentMap<Integer, StatisticalResult> docToStatisticalResult,
			ConcurrentMap<Integer, List<ConceptSentimentPair>> docToTopKPairsResult,
			int docId, List<ConceptSentimentPair> conceptSentimentPairs,
			Constants.LPMethod method) {
		super(k, threshold, docToStatisticalResult, docToTopKPairsResult, method);
		this.docId = docId;
		this.conceptSentimentPairs = conceptSentimentPairs;	
	}

	@Override
	public void run() {

		runRandomizedRoundingPerDoc(docId, conceptSentimentPairs);
	}

}
