package edu.ucr.cs.dblab.nle020.reviewsdiversity.composite;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.StatisticalResult;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;
import edu.ucr.cs.dblab.nle020.utilities.Utils;

public class ILPSetAlgorithm2 extends ILPSetAlgorithm implements Runnable {
	
	private int index;			// To specify the thread order
	private int numThreadsAlgorithm;
	
	Map<Integer, List<SentimentSet>> docToSentimentSets;	
	public ILPSetAlgorithm2(int k, float threshold, 
			ConcurrentMap<Integer, StatisticalResult> docToStatisticalResult, 
			ConcurrentMap<Integer, List<SentimentSet>> docToTopKSetsResult,
			int index, int numThreadsAlgorithm, 
			Map<Integer, List<SentimentSet>> docToSentimentSets) {
		super(k, threshold, docToStatisticalResult, docToTopKSetsResult);
		
		this.index = index;
		this.numThreadsAlgorithm = numThreadsAlgorithm;		
		this.docToSentimentSets = docToSentimentSets;
	}

	@Override
	public void run() {
		long startTime = System.currentTimeMillis();
		Integer[] docIDs = docToSentimentSets.keySet().toArray(new Integer[docToSentimentSets.size()]); 

		for (int i = index; i < docIDs.length; i += numThreadsAlgorithm) {
			Integer docId = docIDs[i];
			runILPSetPerDoc(docId, docToSentimentSets.get(docId));
			
			Utils.printRunningTime(startTime, "ILPSet Finished " + i + ", final cost: " + docToStatisticalResult.get(docId).getFinalCost());
		}
	}
}
