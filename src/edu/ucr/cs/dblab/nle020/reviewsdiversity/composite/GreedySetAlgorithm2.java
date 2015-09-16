package edu.ucr.cs.dblab.nle020.reviewsdiversity.composite;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.StatisticalResult;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;
import edu.ucr.cs.dblab.nle020.utilities.Utils;

public class GreedySetAlgorithm2 extends GreedySet implements Runnable{
	
	private int index;			// Thread Index
	private int numThreadsAlgorithm;
	
	Map<Integer, List<SentimentSet>> docToSentimentSets;	

	public GreedySetAlgorithm2(int k, float threshold, 
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
			runGreedyPerDoc(docId, docToSentimentSets.get(docId));
			
			Utils.printRunningTime(startTime, "Finished GreedySet #" + i + " of docId " + docId);
			startTime = System.currentTimeMillis();
		}
	}
}
