package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import java.util.List;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;

public class RandomizedRounding1 extends RandomizedRounding implements Runnable {

	private int docId;
	private List<ConceptSentimentPair> conceptSentimentPairs;	
	
	public RandomizedRounding1(int k, float threshold, ConcurrentMap<Integer, TopPairsResult> docToTopPairsResult, 
			int docId, List<ConceptSentimentPair> conceptSentimentPairs) {
		super(k, threshold, docToTopPairsResult);
		this.docId = docId;
		this.conceptSentimentPairs = conceptSentimentPairs;	
	}
	
	public RandomizedRounding1(int k, float threshold, ConcurrentMap<Integer, TopPairsResult> docToTopPairsResult,
			int docId, List<ConceptSentimentPair> conceptSentimentPairs,
			Constants.LPMethod method) {
		super(k, threshold, docToTopPairsResult, method);
		this.docId = docId;
		this.conceptSentimentPairs = conceptSentimentPairs;	
	}

	@Override
	public void run() {

		docToTopPairsResult.put(docId, runRandomizedRoundingPerDoc(docId, conceptSentimentPairs));
	}

}
