package edu.ucr.cs.dblab.nle020.reviewsdiversity.composite;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants.PartialTimeIndex;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.ILP.StatisticalResultAndTopKByOriginalOrder;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.FiniteDistanceInitializer;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.RandomizedRounding;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.StatisticalResult;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;
import edu.ucr.cs.dblab.nle020.utils.Utils;

class RandomizedRoundingSet extends RandomizedRounding {
	private ConcurrentMap<String, List<SentimentSet>> docToTopKSetsResult;

	RandomizedRoundingSet(int k, float threshold,
			ConcurrentMap<String, StatisticalResult> docToStatisticalResult,
			ConcurrentMap<String, List<SentimentSet>> docToTopKSetsResult) {
		super(k, threshold, docToStatisticalResult);
		this.docToTopKSetsResult = docToTopKSetsResult;
	}
	
	/**
	 * Run Integer Linear Programming for a doctor's data set
	 * @param docId - doctor ID
	 * @param sentimentSets - list of sentiment units/nodes in K-medians
	 */
	void runRandomizedRoundingSetPerDoc(String docId, List<SentimentSet> sentimentSets) {
		long startTime = System.nanoTime();
		
		StatisticalResult statisticalResult = new StatisticalResult(docId, k, threshold);		
		List<SentimentSet> topKSets = new ArrayList<>();
		
		List<ConceptSentimentPair> pairs = new ArrayList<>();
		for (SentimentSet set : sentimentSets) {
			if (set.getPairs().size() > 0) {
				for (ConceptSentimentPair pair : set.getPairs()) {
					if (!pairs.contains(pair))
						pairs.add(pair);
				}				
			}
		}
		statisticalResult.setNumPairs(pairs.size());
		statisticalResult.setNumSets(sentimentSets.size());
				
		if (sentimentSets.size() <= k) {
			topKSets = sentimentSets;
		} else {
			Map<Integer, Map<Integer, Integer>> facilityToCustomerAndDistance = 
					FiniteDistanceInitializer
							.initFiniteDistancesFromSetIndexToPairIndex(threshold, sentimentSets, pairs, statisticalResult);
			statisticalResult.addPartialTime(
					PartialTimeIndex.SETUP, 
					Utils.runningTimeInMs(startTime, Constants.NUM_DIGITS_IN_TIME));
			
			StatisticalResultAndTopKByOriginalOrder statisticalResultAndTopKByOriginalOrder = 
					doRandomizedRounding(facilityToCustomerAndDistance, statisticalResult, method);
			
			long startPartialTime = System.nanoTime();
			statisticalResult = statisticalResultAndTopKByOriginalOrder.getStatisticalResult();
			for (Integer order : statisticalResultAndTopKByOriginalOrder.getTopKByOriginalOrders()) {
				topKSets.add(sentimentSets.get(order));
			}	
			statisticalResult.addPartialTime(
					PartialTimeIndex.GET_TOPK, 
					Utils.runningTimeInMs(startPartialTime, Constants.NUM_DIGITS_IN_TIME));
		}		
		
		docToStatisticalResult.put(docId, statisticalResult);
		docToTopKSetsResult.put(docId, topKSets);
		double runningTime = Utils.runningTimeInMs(startTime, Constants.NUM_DIGITS_IN_TIME);
		gatherFinalResult(runningTime, sentimentSets.size() + 1, statisticalResult);		
	}
}
