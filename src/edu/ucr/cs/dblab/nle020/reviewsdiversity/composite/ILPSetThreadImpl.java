package edu.ucr.cs.dblab.nle020.reviewsdiversity.composite;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.StatisticalResult;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;
import edu.ucr.cs.dblab.nle020.utils.Utils;

public class ILPSetThreadImpl extends ILPSet implements Runnable {
	
	private int index;			// To specify the thread order
	private int numThreadsAlgorithm;
	
	Map<String, List<SentimentSet>> docToSentimentSets;
	public ILPSetThreadImpl(int k, float threshold, 
			ConcurrentMap<String, StatisticalResult> docToStatisticalResult,
			ConcurrentMap<String, List<SentimentSet>> docToTopKSetsResult,
			int index, int numThreadsAlgorithm, 
			Map<String, List<SentimentSet>> docToSentimentSets) {
		super(k, threshold, docToStatisticalResult, docToTopKSetsResult);
		
		this.index = index;
		this.numThreadsAlgorithm = numThreadsAlgorithm;		
		this.docToSentimentSets = docToSentimentSets;
	}

	@Override
	public void run() {
		long startTime = System.currentTimeMillis();
		String[] docIDs = docToSentimentSets.keySet().toArray(new String[docToSentimentSets.size()]);

		for (int i = index; i < docIDs.length; i += numThreadsAlgorithm) {
			String docId = docIDs[i];
			runILPSetPerDoc(docId, docToSentimentSets.get(docId));
			
			Utils.printRunningTime(startTime, "ILPSet Finished " + i + ", final cost: " + docToStatisticalResult.get(docId).getFinalCost());
		}
	}
}
