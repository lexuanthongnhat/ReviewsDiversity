package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;

public class GreedyThreadImpl extends Greedy implements Runnable{
	
	private int index;			// Thread Index
	private int numThreadsAlgorithm;
	
	private Map<String, List<ConceptSentimentPair>> docToConceptSentimentPairs;

	GreedyThreadImpl(int k, float threshold,
			ConcurrentMap<String, StatisticalResult> docToStatisticalResult,
			ConcurrentMap<String, List<ConceptSentimentPair>> docToTopKPairsResult,
			int index, int numThreadsAlgorithm,
			Map<String, List<ConceptSentimentPair>> docToConceptSentimentPairs) {
		super(k, threshold, docToStatisticalResult, docToTopKPairsResult);
		
		this.index = index;
		this.numThreadsAlgorithm = numThreadsAlgorithm;	
		this.docToConceptSentimentPairs = docToConceptSentimentPairs;
	}
	
	@Override
	public void run() {
		String[] docIDs = docToConceptSentimentPairs.keySet().toArray(new String[docToConceptSentimentPairs.size()]);
		
		for (int i = index; i < docIDs.length; i += numThreadsAlgorithm) {
			String docId = docIDs[i];
//			Utils.printTotalHeapSize("Heapsize before docID " + docId);
			runGreedyPerDoc(docId, docToConceptSentimentPairs.get(docId));
//			Utils.printTotalHeapSize("Heapsize after docID " + docId);
		}
	}
}
