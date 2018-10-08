package edu.ucr.cs.dblab.nle020.reviewsdiversity.composite;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.StatisticalResult;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;
import edu.ucr.cs.dblab.nle020.utils.Utils;

public class GreedySetThreadImpl extends GreedySet implements Runnable{
	
	private int index;			// Thread Index
	private int numThreadsAlgorithm;
	
	private Map<String, List<SentimentSet>> docToSentimentSets;

	public GreedySetThreadImpl(int k, float threshold, 
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
        String[] docIDs =
				docToSentimentSets.keySet().toArray(new String[docToSentimentSets.size()]);
		
		for (int i = index; i < docIDs.length; i += numThreadsAlgorithm) {
            String docId = docIDs[i];
			runGreedyPerDoc(docId, docToSentimentSets.get(docId));
			
			Utils.printRunningTime(startTime, "Finished GreedySet #" + i + " of docId " + docId);
			startTime = System.currentTimeMillis();
		}
	}
}
