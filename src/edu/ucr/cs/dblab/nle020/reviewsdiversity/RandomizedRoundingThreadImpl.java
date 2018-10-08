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
	
	Map<String, List<ConceptSentimentPair>> docToConceptSentimentPairs;
	
	RandomizedRoundingThreadImpl(
			int k, float threshold,
			ConcurrentMap<String, StatisticalResult> docToStatisticalResult,
			ConcurrentMap<String, List<ConceptSentimentPair>> docToTopKPairsResult,
			int index, int numThreadsAlgorithm, 
			Map<String, List<ConceptSentimentPair>> docToConceptSentimentPairs,
			LPMethod method
    ) {
		super(k, threshold, docToStatisticalResult, docToTopKPairsResult, method);
		
		this.index = index;
		this.numThreadsAlgorithm = numThreadsAlgorithm;		
		this.docToConceptSentimentPairs = docToConceptSentimentPairs;
	}

	@Override
	public void run() {
		long startTime = System.currentTimeMillis();
        String[] docIDs = docToConceptSentimentPairs.keySet().toArray(new String[docToConceptSentimentPairs.size()]);
		
		for (int i = index; i < docIDs.length; i += numThreadsAlgorithm) {
            String docId = docIDs[i];
			
			runRandomizedRoundingPerDoc(docId, docToConceptSentimentPairs.get(docId));
			
			Utils.printRunningTime(startTime, "RR finished " + i + ", final cost: " + docToStatisticalResult.get(docId).getFinalCost());
		}		
	}

}
