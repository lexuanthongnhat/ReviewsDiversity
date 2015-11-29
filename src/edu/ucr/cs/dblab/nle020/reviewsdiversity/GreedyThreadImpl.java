package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.utils.Utils;

public class GreedyThreadImpl extends Greedy implements Runnable{
	
	private int index;			// Thread Index
	private int numThreadsAlgorithm;
	
	Map<Integer, List<ConceptSentimentPair>> docToConceptSentimentPairs;	

	public GreedyThreadImpl(int k, float threshold, 
			ConcurrentMap<Integer, StatisticalResult> docToStatisticalResult,
			ConcurrentMap<Integer, List<ConceptSentimentPair>> docToTopKPairsResult,
			int index, int numThreadsAlgorithm,
			Map<Integer, List<ConceptSentimentPair>> docToConceptSentimentPairs) {
		super(k, threshold, docToStatisticalResult, docToTopKPairsResult);
		
		this.index = index;
		this.numThreadsAlgorithm = numThreadsAlgorithm;	
		this.docToConceptSentimentPairs = docToConceptSentimentPairs;
	}
	
	@Override
	public void run() {		
		Integer[] docIDs = docToConceptSentimentPairs.keySet().toArray(new Integer[docToConceptSentimentPairs.size()]); 
		
		for (int i = index; i < docIDs.length; i += numThreadsAlgorithm) {
			Integer docId = docIDs[i];
//			Utils.printTotalHeapSize("Heapsize before docID " + docId);
			runGreedyPerDoc(docId, docToConceptSentimentPairs.get(docId));
//			Utils.printTotalHeapSize("Heapsize after docID " + docId);
		}
	}
}
