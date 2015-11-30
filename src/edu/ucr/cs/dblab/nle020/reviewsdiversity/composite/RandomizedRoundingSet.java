package edu.ucr.cs.dblab.nle020.reviewsdiversity.composite;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.ILP.StatisticalResultAndTopKByOriginalOrder;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.RandomizedRounding;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.StatisticalResult;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;
import edu.ucr.cs.dblab.nle020.utils.Utils;

public class RandomizedRoundingSet extends RandomizedRounding {
	ConcurrentMap<Integer, List<SentimentSet>> docToTopKSetsResult = new ConcurrentHashMap<Integer, List<SentimentSet>>();

	public RandomizedRoundingSet(int k, float threshold,
			ConcurrentMap<Integer, StatisticalResult> docToStatisticalResult,
			ConcurrentMap<Integer, List<SentimentSet>> docToTopKSetsResult) {
		super(k, threshold, docToStatisticalResult);
		this.docToTopKSetsResult = docToTopKSetsResult;
	}
	
	/**
	 * Run Integer Linear Programming for a doctor's data set
	 * @param docId - doctor ID
	 * @param docToSentimentSets - list of sentiment units/nodes in K-medians
	 * @return Result's statistics
	 */
	protected void runRandomizedRoundingSetPerDoc(int docId, List<SentimentSet> sentimentSets) {
		long startTime = System.nanoTime();
		
		StatisticalResult statisticalResult = new StatisticalResult(docId, k, threshold);		
		List<SentimentSet> topKSets = new ArrayList<SentimentSet>();
		
		List<ConceptSentimentPair> pairs = new ArrayList<ConceptSentimentPair>();
		for (SentimentSet set : sentimentSets) {
			if (set.getPairs().size() > 0) {
				for (ConceptSentimentPair pair : set.getPairs()) {
					if (!pairs.contains(pair))
						pairs.add(pair);
				}				
			}
		}
		
		if (sentimentSets.size() <= k) {
			topKSets = sentimentSets;
		} else {
			int[][] distances = ILPSet.initDistances(threshold, sentimentSets, pairs, statisticalResult);
			
			StatisticalResultAndTopKByOriginalOrder statisticalResultAndTopKByOriginalOrder = 
					doRandomizedRounding(distances, statisticalResult, method);
			
			statisticalResult = statisticalResultAndTopKByOriginalOrder.getStatisticalResult();
			for (Integer order : statisticalResultAndTopKByOriginalOrder.getTopKByOriginalOrders()) {
				topKSets.add(sentimentSets.get(order));
			}	
		}		
		
		docToStatisticalResult.put(docId, statisticalResult);
		docToTopKSetsResult.put(docId, topKSets);
		double runningTime = (double) (System.nanoTime() - startTime) / Constants.TIME_MS_TO_NS;
		runningTime = Utils.rounding(runningTime, Constants.NUM_DIGITS_IN_TIME);
		gatherFinalResult(runningTime, sentimentSets.size() + 1, statisticalResult);		
	}
}
