package edu.ucr.cs.dblab.nle020.reviewsdiversity.composite;

import java.util.List;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.TopPairsResult;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;

public class GreedySetAlgorithm1 extends GreedySet implements Runnable {
	
	private int docId;
	private List<SentimentSet> sentimentSets;

	public GreedySetAlgorithm1(int k, float threshold, ConcurrentMap<Integer, TopPairsResult> docToTopPairsResult, 
			int docId, List<SentimentSet> sentimentSets) {
		super(k, threshold, docToTopPairsResult);
		
		this.docId = docId;		
		this.sentimentSets = sentimentSets;		
	}
	
	@Override
	public void run() {
		docToTopPairsResult.put(docId, runGreedyPerDoc(docId, sentimentSets));
	}	
}
