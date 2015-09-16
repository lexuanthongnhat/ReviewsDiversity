package edu.ucr.cs.dblab.nle020.reviewsdiversity;

/**
 * Treat it as non-metric uncapacitated facility location problem
 * Use Integer Linear Programming (Gurubi) to solve
 * @author Nhat XT Le
 *
 */

import java.util.List;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;

public class ILPAlgorithm1 extends ILPAlgorithm implements Runnable {

	private int docId;
	private List<ConceptSentimentPair> conceptSentimentPairs;	
	
	public ILPAlgorithm1(int k, float threshold, 
			ConcurrentMap<Integer, StatisticalResult> docToStatisticalResult, 
			ConcurrentMap<Integer, List<ConceptSentimentPair>> docToTopKPairsResult, 
			int docId, List<ConceptSentimentPair> conceptSentimentPairs) {
		super(k, threshold, docToStatisticalResult, docToTopKPairsResult);
		this.docId = docId;
		this.conceptSentimentPairs = conceptSentimentPairs;	
	}

	@Override
	public void run() {

		runILPPerDoc(docId, conceptSentimentPairs);
	}
}
