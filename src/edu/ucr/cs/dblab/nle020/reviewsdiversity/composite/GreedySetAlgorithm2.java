package edu.ucr.cs.dblab.nle020.reviewsdiversity.composite;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.TopPairsResult;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;
import edu.ucr.cs.dblab.nle020.utilities.Utils;

public class GreedySetAlgorithm2 extends GreedySet2 implements Runnable{
	
	private int index;			// Thread Index
	private int numThreadsAlgorithm;
	
	Map<Integer, List<SentimentSet>> docToSentimentSets;	

	public GreedySetAlgorithm2(int k, float threshold, ConcurrentMap<Integer, TopPairsResult> docToTopPairsResult, 
			int index, int numThreadsAlgorithm, 
			Map<Integer, List<SentimentSet>> docToSentimentSets) {
		super(k, threshold, docToTopPairsResult);
		
		this.index = index;
		this.numThreadsAlgorithm = numThreadsAlgorithm;	
		this.docToSentimentSets = docToSentimentSets;
	}
	
	@Override
	public void run() {
		long startTime = System.currentTimeMillis();
		Integer[] docIDs = docToSentimentSets.keySet().toArray(new Integer[docToSentimentSets.size()]); 
		int numDocs = Constants.NUM_DOCS < docIDs.length ? Constants.NUM_DOCS : docIDs.length;
		
		for (int i = index; i < numDocs; i += numThreadsAlgorithm) {
			Integer docId = docIDs[i];
			docToTopPairsResult.put(docId, runGreedyPerDoc(docId, docToSentimentSets.get(docId)));
			
			Utils.printRunningTime(startTime, "Finished GreedySet #" + i + " of docId " + docId);
			startTime = System.currentTimeMillis();
		}
	}
}
