package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants.LPMethod;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.utils.Utils;

public class RandomizedRoundingThreadImpl extends RandomizedRounding implements Runnable {

	private int index;			// To specify the thread order
	private int numThreadsAlgorithm;
	
	Map<Integer, List<ConceptSentimentPair>> docToConceptSentimentPairs;	
	
	public RandomizedRoundingThreadImpl(int k, float threshold, 
			ConcurrentMap<Integer, StatisticalResult> docToStatisticalResult,
			ConcurrentMap<Integer, List<ConceptSentimentPair>> docToTopKPairsResult,
			int index, int numThreadsAlgorithm, 
			Map<Integer, List<ConceptSentimentPair>> docToConceptSentimentPairs, 
			LPMethod method) {
		super(k, threshold, docToStatisticalResult, docToTopKPairsResult, method);
		
		this.index = index;
		this.numThreadsAlgorithm = numThreadsAlgorithm;		
		this.docToConceptSentimentPairs = docToConceptSentimentPairs;
	}

	@Override
	public void run() {
		long startTime = System.currentTimeMillis();
		Integer[] docIDs = docToConceptSentimentPairs.keySet().toArray(new Integer[docToConceptSentimentPairs.size()]); 
		
		for (int i = index; i < docIDs.length; i += numThreadsAlgorithm) {
			Integer docId = docIDs[i];
			
			runRandomizedRoundingPerDoc(docId, docToConceptSentimentPairs.get(docId));
			
			Utils.printRunningTime(startTime, "RR finished " + i + ", final cost: " + docToStatisticalResult.get(docId).getFinalCost());
			startTime = System.currentTimeMillis();
		}		
	}

}
