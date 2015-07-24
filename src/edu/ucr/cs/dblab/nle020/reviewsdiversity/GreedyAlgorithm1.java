package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import java.util.List;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;

public class GreedyAlgorithm1 extends Greedy implements Runnable {
	
	private int docId;
	private List<ConceptSentimentPair> conceptSentimentPairs;

	public GreedyAlgorithm1(int k, float threshold, ConcurrentMap<Integer, TopPairsResult> docToTopPairsResult, 
			int docId, List<ConceptSentimentPair> conceptSentimentPairs) {
		super(k, threshold, docToTopPairsResult);
		
		this.docId = docId;		
		this.conceptSentimentPairs = conceptSentimentPairs;		
	}
	
	@Override
	public void run() {
		docToTopPairsResult.put(docId, runGreedyPerDoc(docId, conceptSentimentPairs));
	}	
}
