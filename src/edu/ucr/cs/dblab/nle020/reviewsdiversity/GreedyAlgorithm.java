package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset.ConceptSentimentPair;

public class GreedyAlgorithm implements Runnable {
	
	private int k = 0;
	private float threshold = 0.0f;

	private int docID;
	private List<FullPair> pairs = new ArrayList<FullPair>();
	private FullPair root = new FullPair(Constants.ROOT_CUI, 0.0f);
	
	private Map<FullPair, Map<FullPair, Integer>> distances = new HashMap<FullPair, Map<FullPair, Integer>>();	
	
	private PriorityQueue<FullPair> heap;
	private List<FullPair> topK = new ArrayList<FullPair>();	
	long cost = 0;
	
	private ConcurrentMap<Integer, TopPairsResult> docToTopPairsResult = new ConcurrentHashMap<Integer, TopPairsResult>();
	TopPairsResult result;
	
	public GreedyAlgorithm(int k, float threshold, int docID, 
			List<ConceptSentimentPair> csPairs, ConcurrentMap<Integer, TopPairsResult> docToTopPairsResult) {
		super();
		this.k = k;
		this.threshold = threshold;
		this.docID = docID;
		this.docToTopPairsResult = docToTopPairsResult;
		
		initPairs(csPairs);
		
		if (Constants.DEBUG_MODE)
			initDistances(csPairs);
	}
	
	private void initDistances(List<ConceptSentimentPair> csPairs) {
		distances.put(root, new HashMap<FullPair, Integer>());
		distances.get(root).put(root, 0);
		for (int i = 0; i < pairs.size(); i++) {
			FullPair pair = pairs.get(i);	
			distances.put(pair, new HashMap<FullPair, Integer>());
			
			distances.get(root).put(pair, csPairs.get(i).calculateRootDistance());
			distances.get(pair).put(root, Constants.INVALID_DISTANCE);
		}
		
		for (int i = 0; i < pairs.size(); i++) {
			FullPair pair = pairs.get(i);			
			for (int j = i; j < pairs.size(); j ++) {
				FullPair other = pairs.get(j);
				int distance = Constants.INVALID_DISTANCE;
				if (pair.isSentimentCover(other))
					distance = csPairs.get(i).calculateDistance(csPairs.get(j));
									
				// "pair" is the ancestor of "other"
				if (distance > 0 && distance != Constants.INVALID_DISTANCE) {
					distances.get(pair).put(other, distance);
					distances.get(other).put(pair, Constants.INVALID_DISTANCE);
				} else if (distance < 0) {
					distances.get(other).put(pair, -distance);
					distances.get(pair).put(other, Constants.INVALID_DISTANCE);
				} else {
					distances.get(other).put(pair, distance);
					distances.get(pair).put(other, distance);					
				}				
			}
		}
	}
	
	private void initPairs(List<ConceptSentimentPair> csPairs) {
	
		for (int i = 0 ; i < csPairs.size() ; i++) {
			pairs.add(new FullPair(csPairs.get(i).getCUI(), csPairs.get(i).getSentiment(), threshold));
		}		
		
		// Init the host
		for (int i = 0; i < pairs.size(); i++) {
			FullPair pair = pairs.get(i);			
			pair.getCustomerMap().put(pair, 0);					// It can serve itself	
			pair.getPotentialHosts().add(pair);
			pair.setHost(root);
		}
		
		// Init the customers and potential hosts
		for (int i = 0; i < pairs.size() - 1; i++) {
			FullPair pair = pairs.get(i);			
			for (int j = i + 1; j < pairs.size(); j ++) {
				FullPair other = pairs.get(j);
								
				if (Constants.DEBUG_MODE) {
					csPairs.get(i).testDistance(csPairs.get(j));
					csPairs.get(j).testDistance(csPairs.get(i));
				}
				
				int distance = Constants.INVALID_DISTANCE;
				if (pair.isSentimentCover(other))
					distance = csPairs.get(i).calculateDistance(csPairs.get(j));
				
				// 2 pairs are in the same branch and sentiment coverable
				if (distance != Constants.INVALID_DISTANCE) {
					
					// "pair" is the ancestor of "other"
					if (distance > 0) {
						pair.getCustomerMap().put(other, distance);
						other.getPotentialHosts().add(pair);
					} else if (distance < 0) {
						other.getCustomerMap().put(pair, -distance);
						pair.getPotentialHosts().add(other);
					} else {
						pair.getCustomerMap().put(other, distance);
						other.getPotentialHosts().add(pair);
						
						other.getCustomerMap().put(pair, -distance);
						pair.getPotentialHosts().add(other);
					}
				}
			}
		}
		
		// Init benefit
		for (int i = 0; i < pairs.size(); i++) {
			FullPair pair = pairs.get(i);
			pair.setBenefit(pair.getCustomerMap().size() * csPairs.get(i).calculateRootDistance()); 
		}
		
		// Init deweys - for debugging
		for (int i = 0; i < pairs.size(); i++) {
			pairs.get(i).addDeweys(csPairs.get(i).getDeweys());
		}
		
		// Init the root
		for (int i = 0; i < pairs.size(); i++) {
			root.setHost(root);
			
			int distance = csPairs.get(i).calculateRootDistance();
			root.getCustomerMap().put(pairs.get(i), distance);
			cost += distance;
		}
		
		
		// Init result
		result = new TopPairsResult(docID, k, threshold);
		result.setInitialCost(cost);
		result.setNumPairs(csPairs.size());
		initNumPotentialUsefulCover(csPairs);
		initNumPotentialUsefulCoverWithThreshold();
	}

	
	private void initNumPotentialUsefulCover(List<ConceptSentimentPair> csPairs) {
		for (int i = 0; i < csPairs.size() - 1; ++i) {
			ConceptSentimentPair pair1 = csPairs.get(i);
			for (int j = i + 1; j < csPairs.size(); ++j) {
				ConceptSentimentPair pair2 = csPairs.get(j); 
			
				int distance = pair1.calculateDistance(pair2);
				if (distance != Constants.INVALID_DISTANCE) {
					if (!pair1.getCUI().equals(pair2.getCUI()))
						result.increaseNumPotentialUsefulCover();
				}
			}
		}
	}
	
	private void initNumPotentialUsefulCoverWithThreshold() {
		for (FullPair pair : pairs) {
			if (pair.getCustomerMap() != null) {
				for (FullPair customer : pair.getCustomerMap().keySet()) {
					if (!customer.getCUI().equals(pair.getCUI())) {
						result.increaseNumPotentialUsefulCoverWithThreshold();
					}
				}
			}
		}
	}
	
	@Override
	public void run() {
		long startTime = System.currentTimeMillis();
//		Utils.printTotalHeapSize("Before greedy of docID " + docID);
		
//		printInitialization();		
		initHeap();
		
		if (pairs.size() <= k) {
			topK = pairs;
		} else {
			for (int i = 0; i < k; i++) {
				chooseNextPair();
			}
		}
		
		
		if (Constants.DEBUG_MODE)
			checkResult();
		
		gatherFinalResult(System.currentTimeMillis() - startTime);

//		Utils.printTotalHeapSize("After greedy of docID " + docID, true);
//		Utils.printRunningTime(startTime, "Greedy finished docID " + docID);
//		printResult();
	}
	
	private void checkResult() {
	
		long verifyingCost = 0;
	
		List<FullPair> topKPlus = new ArrayList<FullPair>();
		topKPlus.addAll(topK);
		topKPlus.add(root);
		for (FullPair pair : distances.keySet()) {
			int minDistance = Integer.MAX_VALUE;
			for (FullPair host : topKPlus) {
				if (distances.get(host).get(pair) < minDistance) {
					
					minDistance = distances.get(host).get(pair); 
				}
			}

			verifyingCost += minDistance;
		}

		System.out.println("Cost: " + cost + " - Verifying Cost: " + verifyingCost);
		if (verifyingCost != cost)
			System.err.println("Greedy Error at docID " + docID);
	}

	private void gatherFinalResult(long runningTime) {
		if (pairs.size() <= Constants.K) {
			result.setFinalCost(0);
			result.setNumUncovered(0);
			result.setRunningTime(0);
			result.setNumUsefulCover(0);
		} else {
			result.setFinalCost(cost);
			result.setNumUncovered(root.getCustomerMap().size());
			result.setRunningTime(runningTime);
			
			
			for (FullPair pair : topK) {
				for (FullPair customer : pair.getCustomerMap().keySet()) {
					if (!customer.getCUI().equals(pair.getCUI()))
						result.increaseNumUsefulCover();
				}
			}
		}
		docToTopPairsResult.put(docID, result);
	}

	@SuppressWarnings("unused")
	private void printInitialization() {
		System.err.println("PAIRS: (size " + pairs.size() + ", cost " + cost + ")");
		for (FullPair pair : pairs) {
			if (!topK.contains(pair))
				System.out.println(pair.toString());
		}
	}
	
	@SuppressWarnings("unused")
	private void printResult() {
		System.err.println("TOP-" + k + " with threshold " + threshold + " (actual size: " + topK.size() + ", cost " + cost + ")");
		for (FullPair pair : topK) {
			System.out.println(pair.toString());
		}
		System.err.println("Uncovered pairs: (size " + root.getCustomerMap().size() + " out of " + pairs.size() + ")");
		for (FullPair pair : root.getCustomerMap().keySet()) {
			System.err.println(pair.toString());
		}
	}
	
	private void initHeap() {
		
		heap = new PriorityQueue<FullPair>(k, new Comparator<FullPair>(){

			@Override
			public int compare(FullPair o1, FullPair o2) {			
				// Reverse of natural ordering since the head of queue is the least element
				return (o2.getBenefit() - o1.getBenefit());			
			}			
		});
		for (FullPair pair : pairs) {
			heap.add(pair);
		}
	}
		
	private void chooseNextPair() {
		// Choose next pair
		FullPair nextPair = heap.poll();
		topK.add(nextPair);
		
		// Update the customerMap - who it serves, and potentialHosts of servedPairs
		//		served pairs - ones that have next pair as the closest cover ancestor
		
		for (Map.Entry<FullPair, Integer> entry : nextPair.getCustomerMap().entrySet()) {
			FullPair servedPair = entry.getKey();
			
			int partialBenefit = servedPair.getHost().getCustomerMap().get(servedPair) - nextPair.getCustomerMap().get(servedPair);
			
			// nextPair is not better than the current host of this servedPair
			if (partialBenefit <= 0) {
				nextPair.getCustomerMap().remove(servedPair); 
				servedPair.getPotentialHosts().remove(nextPair);
			} else {		
			// nextPair becomes new host of this servedPair
				
				int distanceFromOldHost = servedPair.getHost().getCustomerMap().get(servedPair);				
				servedPair.getHost().getCustomerMap().remove(servedPair);
				
				// TODO - servedPair ~ previous next pair
				servedPair.setHost(nextPair);
				servedPair.getPotentialHosts().remove(nextPair);			// potential --> actual host
				
				/* 
				 * Update the benefit of unchosen pairs - only need to care the potential hosts of servedPairs
				 * 		partialBenefit(nextPair) 				= distance(currentHost, servedPair) - distance(nextPair, servedPair)
				 * 		benefit(potentialHost over currentHost) = distance(currentHost, servedPair) - distance(potentialHost, servedPair)
				 * 		benefit(potentialHost over nextPair)	= distance(nextPair, servedPair) 	- distance(potentialHost, servedPair)
				 * 												= partialBenefit(nextPair) - benefit(potentialHost over currentHost) 
				 */
				for (FullPair potentialHost : servedPair.getPotentialHosts()) {
					int distanceFromPotentialHost = potentialHost.getCustomerMap().get(servedPair);
					potentialHost.decreaseBenefit(distanceFromOldHost - distanceFromPotentialHost);
					int benefitOverNextPair = nextPair.getCustomerMap().get(servedPair) - distanceFromPotentialHost;
					if (benefitOverNextPair > 0) {
						potentialHost.increaseBenefit(benefitOverNextPair);
					}
				}
												
				cost -= partialBenefit;
			}			
		}
		
		// Update the heap of unchosen pairs
		for (FullPair servedPair : nextPair.getCustomerMap().keySet()) {
			for (FullPair potentialHost : servedPair.getPotentialHosts()) {
				heap.remove(potentialHost);
				heap.add(potentialHost);
			}
		}
	}
	
	
	/*	private List<PurePair> leaves;
	private List<PurePair> leavesWithParent;
	private List<PurePair> ancestors;
	
	// KEY: Pair/Facility --> VALUES: Pairs/Customers that the key is able to cover/serve
	private Map<PurePair, Set<PurePair>> coverMap;
	
	// KEY: Pair/Facility --> VALUES: Pairs/Customers that the key currently serves
	//		The KEY is the closest ancestor that currently cover the VALUES   
	private Map<PurePair, Set<PurePair>> servingMap;
	
	private Map<PurePair, PurePair> pairToCoverMap;
	
	private void initLeaves() {
		
	}
	
	private boolean isLeaf(PurePair p) {
		boolean result = false;
		
		return result;
	}*/
}
