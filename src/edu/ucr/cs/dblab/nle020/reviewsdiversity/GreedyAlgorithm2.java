package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.utilities.Utils;

public class GreedyAlgorithm2 extends Greedy implements Runnable{
	
	private int index;			// Thread Index
	private int numThreadsAlgorithm;
	
	Map<Integer, List<ConceptSentimentPair>> docToConceptSentimentPairs;	

	public GreedyAlgorithm2(int k, float threshold, ConcurrentMap<Integer, TopPairsResult> docToTopPairsResult,  
			int index, int numThreadsAlgorithm,
			Map<Integer, List<ConceptSentimentPair>> docToConceptSentimentPairs) {
		super(k, threshold, docToTopPairsResult);
		
		this.index = index;
		this.numThreadsAlgorithm = numThreadsAlgorithm;	
		this.docToConceptSentimentPairs = docToConceptSentimentPairs;
	}
	
	@Override
	public void run() {		
		Integer[] docIDs = docToConceptSentimentPairs.keySet().toArray(new Integer[docToConceptSentimentPairs.size()]); 
		int numDocs = Constants.NUM_DOCS < docIDs.length ? Constants.NUM_DOCS : docIDs.length;
		
		for (int i = index; i < numDocs; i += numThreadsAlgorithm) {
			Integer docId = docIDs[i];
			Utils.printTotalHeapSize("Heapsize before docID " + docId);
			docToTopPairsResult.put(docId, runGreedyPerDoc(docId, docToConceptSentimentPairs.get(docId)));
			Utils.printTotalHeapSize("Heapsize after docID " + docId);
		}
	}
}
