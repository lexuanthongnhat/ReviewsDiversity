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
	
	public ILPAlgorithm1(int k, float threshold, ConcurrentMap<Integer, TopPairsResult> docToTopPairsResult, 
			int docId, List<ConceptSentimentPair> conceptSentimentPairs) {
		super(k, threshold, docToTopPairsResult);
		this.docId = docId;
		this.conceptSentimentPairs = conceptSentimentPairs;	
	}

	@Override
	public void run() {

		docToTopPairsResult.put(docId, runILPPerDoc(docId, conceptSentimentPairs));
	}
}
