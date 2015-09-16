package edu.ucr.cs.dblab.nle020.reviewsdiversity.composite;

/**
 * Treat it as non-metric uncapacitated facility location problem
 * Use Integer Linear Programming (Gurubi) to solve
 * @author Nhat XT Le
 *
 */

import java.util.List;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.StatisticalResult;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;

public class ILPSetAlgorithm1 extends ILPSetAlgorithm implements Runnable {

	private int docId;
	private List<SentimentSet> sentimentSets;	
	
	public ILPSetAlgorithm1(int k, float threshold, 
			ConcurrentMap<Integer, StatisticalResult> docToStatisticalResult,
			ConcurrentMap<Integer, List<SentimentSet>> docToTopKSetsResult,
			int docId, List<SentimentSet> sentimentSets) {
		super(k, threshold, docToStatisticalResult, docToTopKSetsResult);
		this.docId = docId;
		this.sentimentSets = sentimentSets;	
	}

	@Override
	public void run() {

		runILPSetPerDoc(docId, sentimentSets);
	}
}
