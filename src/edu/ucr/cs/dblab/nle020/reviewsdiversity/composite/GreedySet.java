package edu.ucr.cs.dblab.nle020.reviewsdiversity.composite;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants.PartialTimeIndex;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.FullPair;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.StatisticalResult;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;
import edu.ucr.cs.dblab.nle020.utils.Utils;

public class GreedySet {
	protected int k = 0;
	protected float threshold = 0.0f;

	protected FullPair root = new FullPair(Constants.ROOT_CUI);
	
	protected ConcurrentMap<Integer, StatisticalResult> docToStatisticalResult = new ConcurrentHashMap<Integer, StatisticalResult>();
	ConcurrentMap<Integer, List<SentimentSet>> docToTopKSetsResult = new ConcurrentHashMap<Integer, List<SentimentSet>>();
			
	public GreedySet(int k, float threshold,
			ConcurrentMap<Integer, StatisticalResult> docToStatisticalResult,
			ConcurrentMap<Integer, List<SentimentSet>> docToTopKSetsResult) {
		super();
		this.k = k;
		this.threshold = threshold;
		this.docToStatisticalResult = docToStatisticalResult;
		this.docToTopKSetsResult = docToTopKSetsResult;
	}
	
	/**
	 * Run Greedy Algorithm for a doctor's data set
	 * @param docId - doctor ID
	 * @param sentimentUnits - list of sentiment units/nodes in K-medians
	 * @return Result's statistics
	 */
	protected void runGreedyPerDoc(int docId, Collection<? extends SentimentSet> sentimentSets) {
		long startTime = System.nanoTime();
		
		StatisticalResult statisticalResult = new StatisticalResult(docId, k, threshold);;

//		printInitialization();		
		Map<FullPair, Map<FullPair, Integer>> distances = new HashMap<FullPair, Map<FullPair, Integer>>();	
		

		List<FullPair> topK = new ArrayList<FullPair>();	
		List<FullPair> fullPairSets = new ArrayList<FullPair>();
		initPairs(sentimentSets, fullPairSets, statisticalResult, distances);
		if (Constants.DEBUG_MODE)
			initDistances(fullPairSets, distances);		
		
		PriorityQueue<FullPair> heap = initHeap(fullPairSets);
		statisticalResult.addPartialTime(
				PartialTimeIndex.SETUP, 
				Utils.runningTimeInMs(startTime, Constants.NUM_DIGITS_IN_TIME));
		
		long startPartialTime = System.nanoTime();
		if (fullPairSets.size() <= k) {
			topK.addAll(fullPairSets);
		} else {
			for (int i = 0; i < k; i++) {
				chooseNextPair(heap, topK, statisticalResult);
			}
		}
		statisticalResult.addPartialTime(
				PartialTimeIndex.MAIN, 
				Utils.runningTimeInMs(startPartialTime, Constants.NUM_DIGITS_IN_TIME));				
	
		if (Constants.DEBUG_MODE)
			checkResult(topK, distances, statisticalResult);
					
		startPartialTime = System.nanoTime();
		List<SentimentSet> topKSetsResult = convertTopKFullPairsToTopKSets(sentimentSets, topK);
		statisticalResult.addPartialTime(
				PartialTimeIndex.GET_TOPK, 
				Utils.runningTimeInMs(startPartialTime, Constants.NUM_DIGITS_IN_TIME));
		
		docToTopKSetsResult.put(docId, topKSetsResult);
		docToStatisticalResult.put(docId, statisticalResult);			
		
		double runningTime = Utils.runningTimeInMs(startTime, Constants.NUM_DIGITS_IN_TIME);	
		gatherFinalResult(runningTime, fullPairSets.size(), statisticalResult, topK);
	}	
	
	private List<SentimentSet> convertTopKFullPairsToTopKSets(
			Collection<? extends SentimentSet> sentimentSets,
			List<FullPair> topK) {
		List<SentimentSet> topKSetsResult = new ArrayList<SentimentSet>();
		Set<String> topSetIds = new HashSet<String>(); 
		for (FullPair fullPair : topK) {
			topSetIds.add(fullPair.getId());
		}
		for (SentimentSet sentimentSet : sentimentSets) {
			if (topSetIds.contains(sentimentSet.getId()))
				topKSetsResult.add(sentimentSet);
		}
		return topKSetsResult;
	}
	
	private void checkResult(List<FullPair> topK,
			Map<FullPair, Map<FullPair, Integer>> distances,
			StatisticalResult statisticalResult) {
		long verifyingCost = 0;
		
		Set<FullPair> fullPairs = new HashSet<FullPair>();
		for (FullPair fullPairSet : distances.keySet()) {
			fullPairs.addAll(fullPairSet.getCustomerMap().keySet());
		}
		
		for (FullPair customer : fullPairs) {
			long min = Constants.INVALID_DISTANCE;
			for (FullPair potentialHost : topK) {
				if (distances.get(potentialHost).get(customer) != null) {
					if (distances.get(potentialHost).get(customer) < min)
						min = distances.get(potentialHost).get(customer);
				}
			}
			if (min == Constants.INVALID_DISTANCE) {
				if (root.distanceToCustomer(customer) < min)
					min = root.distanceToCustomer(customer);
			}
			verifyingCost += min;
		}
		
		if (verifyingCost != (long) statisticalResult.getFinalCost()) 
			System.err.println("Wrong cost, verying cost: " + verifyingCost + ", cost: " + statisticalResult.getFinalCost());
		
		
		verifyingCost = 0;
		for (FullPair customer : root.getCustomerMap().keySet()) {
			verifyingCost += root.distanceToCustomer(customer);
		}
		for (FullPair facility : topK) {
			for (FullPair customer : facility.getCustomerMap().keySet()) {
				verifyingCost += facility.distanceToCustomer(customer);
			}
		}
		if (verifyingCost != (long) statisticalResult.getFinalCost()) 
			System.err.println("Wrong cost 2, verying cost: " + verifyingCost + ", cost: " + statisticalResult.getFinalCost());
	}

	private void initDistances(List<FullPair> fullPairSets,
			Map<FullPair, Map<FullPair, Integer>> distances) {
		for (FullPair fullPairSet : fullPairSets) {
			distances.put(fullPairSet, new HashMap<FullPair, Integer>());
			
			for (FullPair customer : fullPairSet.getCustomerMap().keySet()) {
				distances.get(fullPairSet).put(customer, fullPairSet.distanceToCustomer(customer));
			}
		}
	}

	private void initPairs(Collection<? extends SentimentSet> sentimentSets, 
				Collection<FullPair> fullPairSets, StatisticalResult statisticalResult, 
				Map<FullPair, Map<FullPair, Integer>> distances) {
		Map<SentimentSet, FullPair> setToFullPairSet = new HashMap<SentimentSet, FullPair>();
		// Init fullPairSets corresponding to sentimentSets
		for (SentimentSet set : sentimentSets) {
			FullPair fullPairSet = new FullPair(set.getId(), set);
			fullPairSets.add(fullPairSet);
			setToFullPairSet.put(set, fullPairSet);
		}			
		
		Map<FullPair, ConceptSentimentPair> fullPairToCSPair = new HashMap<FullPair, ConceptSentimentPair>();
		Map<ConceptSentimentPair, FullPair> csPairToFullPair = new HashMap<ConceptSentimentPair, FullPair>();
		
		List<FullPair> fullPairs = new ArrayList<FullPair>();
		List<ConceptSentimentPair> conceptSentimentPairs = new ArrayList<ConceptSentimentPair>();
		for (SentimentSet set : sentimentSets) {
			set.getFullPairs().clear();
			for (ConceptSentimentPair csPair : set.getPairs()) {
				if (!conceptSentimentPairs.contains(csPair)) { 
					conceptSentimentPairs.add(csPair);
					
					FullPair fullPair = new FullPair(csPair.getId() + "_s" + csPair.getSentiment());					
					fullPairs.add(fullPair);
					
					fullPairToCSPair.put(fullPair, csPair);
					csPairToFullPair.put(csPair, fullPair);
					
					set.addFullPair(fullPair);
				} else {
					set.addFullPair(csPairToFullPair.get(csPair));
				}
			}				
		}
		
	
		for (SentimentSet set : sentimentSets) {
			FullPair fullPairSet = setToFullPairSet.get(set);

			for (FullPair fullPair : fullPairs) {
				if (set.getFullPairs().contains(fullPair)) {
					fullPair.addPotentialHost(fullPairSet);
					fullPairSet.addCustomer(fullPair, 0);
					
					fullPairSet.increaseBenefit(fullPairToCSPair.get(fullPair).calculateRootDistance());
				} else {

					int min = Constants.INVALID_DISTANCE;					
//					FullPair potentialHost = null;

					for (FullPair fullPairInSet : set.getFullPairs()) {
						int distance = fullPairToCSPair.get(fullPairInSet).calculateDistance(fullPairToCSPair.get(fullPair), threshold);
						if (distance < min && distance >= 0) {
							min = distance;
//							potentialHost = fullPairInSet;
						}
					}

					if (min >= 0 && min < Constants.INVALID_DISTANCE) {
						fullPairSet.addCustomer(fullPair, min);
						fullPair.addPotentialHost(fullPairSet);
						
//						fullPairSet.increaseBenefit(fullPairToCSPair.get(potentialHost).calculateRootDistance());
						fullPairSet.increaseBenefit(fullPairToCSPair.get(fullPair).calculateRootDistance() - min);
					}
				}
			}
		}


		long initialCost = 0;
		// Init the root		
		root.getCustomerMap().clear();
		for (FullPair fullPair : fullPairs) {
			//			root.setHost(root);
			fullPair.setHost(root);
			
			int distance = fullPairToCSPair.get(fullPair).calculateRootDistance();
			root.addCustomer(fullPair, distance);
			initialCost += distance;
		}

		// Init result
		statisticalResult.setInitialCost(initialCost);
		statisticalResult.setFinalCost(initialCost);
		statisticalResult.setNumSets(sentimentSets.size());
		statisticalResult.setNumPairs(conceptSentimentPairs.size());			
		
		initNumPotentialUsefulCoverWithThreshold(statisticalResult, fullPairSets);
	}

	private void initNumPotentialUsefulCoverWithThreshold(StatisticalResult statisticalResult, Collection<FullPair> fullPairSets) {
		int numPotentialUsefulCoverWithThreshold = 0;
		for (FullPair userFullSet : fullPairSets) {
			numPotentialUsefulCoverWithThreshold += userFullSet.getCustomerMap().size();			
		}
		
		statisticalResult.setNumPotentialUsefulCoverWithThreshold(numPotentialUsefulCoverWithThreshold);
	}
	
	private PriorityQueue<FullPair> initHeap(List<FullPair> pairs) {
		PriorityQueue<FullPair> heap = new PriorityQueue<FullPair>(k, new Comparator<FullPair>(){

			@Override
			public int compare(FullPair o1, FullPair o2) {			
				// Reverse of natural ordering since the head of queue is the least element
				return (o2.getBenefit() - o1.getBenefit());			
			}			
		});

		for (FullPair pair : pairs) {
			heap.add(pair);
		}

		return heap;
	}


	// Choose the next pair on top of the heap, then update the RELATED pairs and the heap
	private void chooseNextPair(PriorityQueue<FullPair> heap, List<FullPair> topK, StatisticalResult statisticalResult) {
		// Choose next pair
		FullPair nextPairSet = heap.poll();
		topK.add(nextPairSet);
		
/*		// Update pair of this set
		for (FullPair fullPairInSet : nextPairSet.getParent().getFullPairs()) {
			
			// No change in the cost for this case
			if (fullPairInSet.getHost().distanceToCustomer(fullPairInSet) == 0 
					&& !fullPairInSet.getHost().equals(nextPairSet)) {
				fullPairInSet.getHost().removeCustomer(fullPairInSet);
				fullPairInSet.setHost(nextPairSet);
				fullPairInSet.removePotentialHost(nextPairSet);
			}
		}*/		

		Set<FullPair> updatedFullPairs = new HashSet<FullPair>();
		
		// Update the customerMap - who it serves, and potentialHosts of servedPairs
		//		served pairs - ones that have next pair as the closest cover ancestor		
		for (FullPair servedPair : nextPairSet.getCustomerMap().keySet()) {			
/*			if (nextPairSet.getParent().getFullPairs().contains(servedPair) 
					&& servedPair.getHost().distanceToCustomer(servedPair) == 0)
				continue;*/
			
			int distanceFromOldHost = servedPair.getHost().distanceToCustomer(servedPair);
			int distanceFromNewHost = nextPairSet.distanceToCustomer(servedPair);
			
			int partialBenefit = distanceFromOldHost - distanceFromNewHost;			
			
			// nextPair is not better than the current host of this servedPair
			if (partialBenefit <= 0) {
				nextPairSet.removeCustomer(servedPair); 
				servedPair.removePotentialHost(nextPairSet);
			} else {		
			// nextPair becomes new host of this servedPair				
				
				servedPair.getHost().removeCustomer(servedPair);
				
				// TODO - servedPair ~ previous next pair
				servedPair.setHost(nextPairSet);
				servedPair.getPotentialHosts().remove(nextPairSet);			// potential --> actual host
				
				/* 
				 * Update the benefit of unchosen pairs - only need to care the potential hosts of servedPairs
				 * 		partialBenefit(nextPair) 				= distance(currentHost, servedPair) - distance(nextPair, servedPair)
				 * 		benefit(potentialHost over currentHost) = distance(currentHost, servedPair) - distance(potentialHost, servedPair)
				 * 		benefit(potentialHost over nextPair)	= distance(nextPair, servedPair) 	- distance(potentialHost, servedPair)
				 * 												= partialBenefit(nextPair) - benefit(potentialHost over currentHost) 
				 */
				
				Set<FullPair> absoleteHosts = new HashSet<FullPair>();
				for (FullPair potentialHost : servedPair.getPotentialHosts()) {
					if (topK.contains(potentialHost)) {
						System.err.println("humnn");
					}
					int distanceFromPotentialHost = potentialHost.distanceToCustomer(servedPair);
					potentialHost.decreaseBenefit(distanceFromOldHost - distanceFromPotentialHost);

					updatedFullPairs.add(potentialHost);
					
					int benefitOverNextPair = distanceFromNewHost - distanceFromPotentialHost;
					if (benefitOverNextPair > 0) {
						potentialHost.increaseBenefit(benefitOverNextPair);
					} else {
						absoleteHosts.add(potentialHost);
					}
				}
												
				servedPair.getPotentialHosts().removeAll(absoleteHosts);
				statisticalResult.decreaseFinalCost(partialBenefit);
			}
		}
		
		
		
		// Update the heap of unchosen pairs
		for (FullPair updated : updatedFullPairs) {
			heap.remove(updated);
			heap.add(updated);
		}
		
	}
	
	
	private void gatherFinalResult(double runningTime, int datasetSize, StatisticalResult result, List<FullPair> topK) {
		if (datasetSize <= k) {
			result.setFinalCost(0);
			result.setNumUncovered(0);
			result.setRunningTime(0);
			result.setNumUsefulCover(0);
		} else {
			result.setNumUncovered(root.getCustomerMap().size());
			result.setRunningTime(runningTime);
		}
		docToStatisticalResult.put(result.getDocID(), result);
	}
	
	
}
