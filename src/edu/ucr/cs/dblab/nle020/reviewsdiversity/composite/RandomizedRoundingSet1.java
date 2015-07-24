package edu.ucr.cs.dblab.nle020.reviewsdiversity.composite;

import java.util.List;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants.LPMethod;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.TopPairsResult;
import edu.ucr.cs.dblab.nle020.utilities.Utils;

public class RandomizedRoundingSet1 extends RandomizedRoundingSet implements Runnable {

	private int docId;
	private List<SentimentSet> sentimentSets;	
	
	public RandomizedRoundingSet1(int k, float threshold,
			ConcurrentMap<Integer, TopPairsResult> docToTopPairsResult,
			int docId, List<SentimentSet> sentimentSets) {
		super(k, threshold, docToTopPairsResult);
		this.docId = docId;
		this.sentimentSets = sentimentSets;	
	}
	
	public RandomizedRoundingSet1(int k, float threshold,
			ConcurrentMap<Integer, TopPairsResult> docToTopPairsResult,
			LPMethod method,
			int docId, List<SentimentSet> sentimentSets) {
		super(k, threshold, docToTopPairsResult, method);
		this.docId = docId;
		this.sentimentSets = sentimentSets;	
	}

	@Override
	public void run() {
		long startTime = System.currentTimeMillis();

		docToTopPairsResult.put(docId, runRandomizedRoundingSetPerDoc(docId, sentimentSets));	
		
		Utils.printRunningTime(startTime, "RR finished " + docId + ", final cost: " + docToTopPairsResult.get(docId).getFinalCost());

	}

}
