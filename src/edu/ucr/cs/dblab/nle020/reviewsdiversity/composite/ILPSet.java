package edu.ucr.cs.dblab.nle020.reviewsdiversity.composite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants.PartialTimeIndex;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.ILP;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.StatisticalResult;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants.LPMethod;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;
import edu.ucr.cs.dblab.nle020.utils.Utils;

public class ILPSet extends ILP {
	ConcurrentMap<Integer, List<SentimentSet>> docToTopKSetsResult = new ConcurrentHashMap<Integer, List<SentimentSet>>();

	public ILPSet(int k, float threshold,
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
	protected void runILPSetPerDoc(int docId, List<SentimentSet> sentimentSets) {
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
		statisticalResult.setNumPairs(pairs.size());
		statisticalResult.setNumSets(sentimentSets.size());
		
		if (sentimentSets.size() <= k) {
			topKSets = sentimentSets;
		} else {
			Map<Integer, Map<Integer, Integer>> facilityToCustomerAndDistance = 
					initDistances(threshold, sentimentSets, pairs, statisticalResult);
			statisticalResult.addPartialTime(
					PartialTimeIndex.SETUP, 
					Utils.runningTimeInMs(startTime, Constants.NUM_DIGITS_IN_TIME));
			
			StatisticalResultAndTopKByOriginalOrder statisticalResultAndTopKByOriginalOrder = 
					doILP(facilityToCustomerAndDistance, statisticalResult, LPMethod.AUTOMATIC);
			
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
	
	/**
	 * init the distances between the sets, root and the concept sentiment pairs
	 * @param sentimentSets
	 * @param conceptSentimentPairs
	 * @return array of length [sentimentSets.size() + 1 ] * conceptSentimentPairs.size()
	 * <br> +1 for the root, result[0] is the distance array of the root
	 */
	protected static Map<Integer, Map<Integer, Integer>> initDistances(
			float sentimentThreshold, 
			List<SentimentSet> sentimentSets, 
			List<ConceptSentimentPair> conceptSentimentPairs, 
			StatisticalResult statisticalResult) {
		
		Map<Integer, Map<Integer, Integer>> facilityToCustomerAndDistance =
				new HashMap<Integer, Map<Integer, Integer>>();
//		int[][] distances = new int[sentimentSets.size() + 1][conceptSentimentPairs.size() + 1];
		
		conceptSentimentPairs.add(0, root);
		
		// The root
		facilityToCustomerAndDistance.put(0, new HashMap<Integer, Integer>());
		facilityToCustomerAndDistance.get(0).put(0, 0);
//		int initialCost = 0;
		
		for (int j = 1; j < conceptSentimentPairs.size(); ++j) {
			ConceptSentimentPair pair = conceptSentimentPairs.get(j);				
				
			facilityToCustomerAndDistance.get(0).put(j, pair.calculateRootDistance());
//			initialCost += pair.calculateRootDistance(); 
		}
//		statisticalResult.setInitialCost(initialCost);		
		
		// The sets
		for (int s = 0; s < sentimentSets.size(); ++s) {
			int sIndex = s + 1;
			facilityToCustomerAndDistance.put(sIndex, new HashMap<Integer, Integer>());
			
			SentimentSet set = sentimentSets.get(s);
			for (int p = 1; p < conceptSentimentPairs.size(); ++p) {
				ConceptSentimentPair pair = conceptSentimentPairs.get(p);
				
				if (set.getPairs().contains(pair)) {
					facilityToCustomerAndDistance.get(sIndex).put(p, 0);
				} else {
					int min = Constants.INVALID_DISTANCE;
					for (ConceptSentimentPair pairInSet : set.getPairs()) {
						int distance = pairInSet.calculateDistance(pair, sentimentThreshold);
						
						if (distance >= 0 && distance < min) 
							min = distance;
					}
					if (min != Constants.INVALID_DISTANCE && min >= 0)
						facilityToCustomerAndDistance.get(sIndex).put(p, min);
				}
			}
		}
		
		
		return facilityToCustomerAndDistance;
	}
}
