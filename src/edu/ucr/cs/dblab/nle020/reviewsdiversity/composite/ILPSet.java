package edu.ucr.cs.dblab.nle020.reviewsdiversity.composite;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.ILP;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.StatisticalResult;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;

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
		long startTime = System.currentTimeMillis();
		
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
			int[][] distances = initDistances(sentimentSets, pairs, statisticalResult);
			
			StatisticalResultAndTopKByOriginalOrder statisticalResultAndTopKByOriginalOrder = doILP(distances, statisticalResult);
			
			statisticalResult = statisticalResultAndTopKByOriginalOrder.getStatisticalResult();
			for (Integer order : statisticalResultAndTopKByOriginalOrder.getTopKByOriginalOrders()) {
				topKSets.add(sentimentSets.get(order));
			}	
		}		
		
		docToStatisticalResult.put(docId, statisticalResult);
		docToTopKSetsResult.put(docId, topKSets);
		gatherFinalResult(System.currentTimeMillis() - startTime, sentimentSets.size() + 1, statisticalResult);		
	}
	
	/**
	 * init the distances between the sets, root and the concept sentiment pairs
	 * @param sentimentSets
	 * @param conceptSentimentPairs
	 * @return array of length [sentimentSets.size() + 1 ] * conceptSentimentPairs.size()
	 * <br> +1 for the root, result[0] is the distance array of the root
	 */
	private int[][] initDistances(List<SentimentSet> sentimentSets, List<ConceptSentimentPair> conceptSentimentPairs, 
			StatisticalResult statisticalResult) {
		int[][] distances = new int[sentimentSets.size() + 1][conceptSentimentPairs.size() + 1];
		
		conceptSentimentPairs.add(0, root);
		// The root
		int initialCost = 0;
		distances[0][0] = 0;
		for (int j = 1; j < conceptSentimentPairs.size(); ++j) {
			ConceptSentimentPair pair = conceptSentimentPairs.get(j);				
				
			distances[0][j] = pair.calculateRootDistance();
			initialCost += distances[0][j]; 
		}
		statisticalResult.setInitialCost(initialCost);		
		
		// The sets
		for (int s = 0; s < sentimentSets.size(); ++s) {
			int sIndex = s + 1;
			distances[sIndex][0] = Constants.INVALID_DISTANCE;
			
			SentimentSet set = sentimentSets.get(s);
			for (int p = 1; p < conceptSentimentPairs.size(); ++p) {
				ConceptSentimentPair pair = conceptSentimentPairs.get(p);
				
				if (set.getPairs().contains(pair)) {
					distances[sIndex][p] = 0;
				} else {
					int min = Constants.INVALID_DISTANCE;
					for (ConceptSentimentPair pairInSet : set.getPairs()) {
						int distance = pairInSet.calculateDistance(pair, threshold);
						
						if (distance >= 0 && distance < min) 
							min = distance;
					}
					distances[sIndex][p] = min;
				}
			}
		}
		
		
		return distances;
	}
}
