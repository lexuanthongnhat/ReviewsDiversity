package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants.PartialTimeIndex;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.utils.Utils;

public class Greedy {
	protected int k = 0;
	protected float threshold = 0.0f;

	protected FullPair root = new FullPair(Constants.ROOT_CUI);
	
	protected ConcurrentMap<String, StatisticalResult> docToStatisticalResult;
	protected ConcurrentMap<String, List<ConceptSentimentPair>> docToTopKPairsResult;
	
	public Greedy(int k, float threshold,
			ConcurrentMap<String, StatisticalResult> docToStatisticalResult,
			ConcurrentMap<String, List<ConceptSentimentPair>> docToTopKPairsResult) {
		super();
		this.k = k;
		this.threshold = threshold;
		this.docToStatisticalResult = docToStatisticalResult;
		this.docToTopKPairsResult = docToTopKPairsResult;
	}

	/**
	 * Run Greedy Algorithm for a doctor's data set
	 * @param docId - doctor ID
	 * @param conceptSentimentPairs - list of sentiment units/nodes in K-medians
	 */
	void runGreedyPerDoc(String docId, List<ConceptSentimentPair> conceptSentimentPairs) {
		//long startTime = System.currentTimeMillis();
		long startTime = System.nanoTime();
				
		StatisticalResult statisticalResult = new StatisticalResult(docId, k, threshold);		
		List<FullPair> topK = new ArrayList<>();

//		printInitialization();		
		List<FullPair> pairs = new ArrayList<>();
		//initPairs(conceptSentimentPairs, pairs, statisticalResult);
		FiniteDistanceInitializer.initFullPairs(conceptSentimentPairs, pairs, statisticalResult);
		
		Map<FullPair, Map<FullPair, Integer>> distances = new HashMap<>();	
		if (Constants.DEBUG_MODE)
			initDistances(conceptSentimentPairs, distances, pairs);
				
		PriorityQueue<FullPair> heap = initHeap(pairs);
		statisticalResult.addPartialTime(
				PartialTimeIndex.SETUP,
				Utils.runningTimeInMs(startTime, Constants.NUM_DIGITS_IN_TIME));		
		
		long startPartialTime = System.nanoTime();
		if (pairs.size() <= k) {
			topK = pairs;
		} else {
			for (int i = 0; i < k; i++) {
				chooseNextPair(heap, topK, statisticalResult);
			}
		}
		statisticalResult.addPartialTime(
				PartialTimeIndex.MAIN,
				Utils.runningTimeInMs(
						startPartialTime, 
						Constants.NUM_DIGITS_IN_TIME));	
				
		if (Constants.DEBUG_MODE)
			checkResult(topK, distances, statisticalResult);
		
		startPartialTime = System.nanoTime();
		List<ConceptSentimentPair> topKPairsResult = convertTopKFullPairsToTopKPairs(
		    conceptSentimentPairs, topK);
		statisticalResult.addPartialTime(
				PartialTimeIndex.GET_TOPK, 
				Utils.runningTimeInMs(
						startPartialTime,  
						Constants.NUM_DIGITS_IN_TIME));
		
		docToTopKPairsResult.put(docId, topKPairsResult);
		docToStatisticalResult.put(docId, statisticalResult);			
		
		double runningTime = Utils.runningTimeInMs(
				startTime, Constants.NUM_DIGITS_IN_TIME);	
		gatherFinalResult(runningTime, conceptSentimentPairs, statisticalResult, topK);
//		Utils.printRunningTime(startTime, "Greedy finished docId " + docId);
	}	
	
	private List<ConceptSentimentPair> convertTopKFullPairsToTopKPairs(
			Collection<ConceptSentimentPair> conceptSentimentPairs,
			List<FullPair> topK) {
		List<ConceptSentimentPair> topKPairsResult = new ArrayList<>();
		Set<String> topSetIds = new HashSet<>();
		for (FullPair fullPair : topK) {
			topSetIds.add(fullPair.getId());
		}
		for (ConceptSentimentPair conceptSentimentPair : conceptSentimentPairs) {
			if (topSetIds.contains(conceptSentimentPair.getId() + "_s" + conceptSentimentPair.getSentiment()))
				topKPairsResult.add(conceptSentimentPair);
		}
		return topKPairsResult;
	}
	
	private void initDistances(List<ConceptSentimentPair> conceptSentimentPairs, 
			Map<FullPair, Map<FullPair, Integer>> distances, List<FullPair> fullPairs) {
		
		distances.put(root, new HashMap<>());
		distances.get(root).put(root, 0);
		for (int i = 0; i < fullPairs.size(); i++) {
			FullPair pair = fullPairs.get(i);	
			distances.put(pair, new HashMap<>());
			
			distances.get(root).put(pair, conceptSentimentPairs.get(i).calculateRootDistance());
			distances.get(pair).put(root, Constants.INVALID_DISTANCE);
		}
		
		for (int i = 0; i < fullPairs.size(); i++) {
			FullPair pair = fullPairs.get(i);			
			for (int j = i; j < fullPairs.size(); j ++) {
				FullPair other = fullPairs.get(j);
				int distance = Constants.INVALID_DISTANCE;
				
				distance = conceptSentimentPairs.get(i).calculateDistance(conceptSentimentPairs.get(j),
				                                                          threshold);
									
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
	
	@SuppressWarnings("unused")
	private void initNumPotentialUsefulCover(StatisticalResult result,
	    List<ConceptSentimentPair> conceptSentimentPairs) {
	  
		for (int i = 0; i < conceptSentimentPairs.size() - 1; ++i) {
			ConceptSentimentPair pair1 = conceptSentimentPairs.get(i);
			for (int j = i + 1; j < conceptSentimentPairs.size(); ++j) {
				ConceptSentimentPair pair2 = conceptSentimentPairs.get(j); 
			
				int distance = pair1.calculateDistance(pair2);
				if (distance != Constants.INVALID_DISTANCE && distance != 0) {
						result.increaseNumPotentialUsefulCover();
				}
			}
		}
	}
	
	@SuppressWarnings("unused")
	private void initNumPotentialUsefulCoverWithThreshold(StatisticalResult result,
	    List<FullPair> fullPairs) {
		for (FullPair pair : fullPairs) {
			if (pair.getCustomerMap() != null) {
				for (FullPair customer : pair.getCustomerMap().keySet()) {
//					if (pair.getCustomerMap().get(customer) != 0) {
						result.increaseNumPotentialUsefulCoverWithThreshold();
//					}
				}
			}
		}
	}
	
	private PriorityQueue<FullPair> initHeap(List<FullPair> pairs) {
		PriorityQueue<FullPair> heap = new PriorityQueue<>(k, new Comparator<FullPair>(){

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
	private void chooseNextPair(
			PriorityQueue<FullPair> heap, 
			List<FullPair> topK, 
			StatisticalResult statisticalResult) {
		//long startTime = System.currentTimeMillis();
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
												
				statisticalResult.decreaseFinalCost(partialBenefit);
			}			
		}
		
		// Update the heap of unchosen pairs
		for (FullPair servedPair : nextPair.getCustomerMap().keySet()) {
			for (FullPair potentialHost : servedPair.getPotentialHosts()) {
				heap.remove(potentialHost);
				heap.add(potentialHost);
			}
		}
		
		//Utils.printRunningTime(startTime, "Finished a nextPair iteration");
	}
	
	private void checkResult(
			List<FullPair> topK, Map<FullPair, Map<FullPair, Integer>> distances, 
			StatisticalResult statisticalResult) {
	
		long verifyingCost = 0;
	
		List<FullPair> topKPlus = new ArrayList<>(topK);
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

		System.out.println("Cost: " + statisticalResult.getFinalCost() + " - Verifying Cost: " + verifyingCost);
		if (verifyingCost != statisticalResult.getFinalCost())
			System.err.println("Greedy Error at docID " + statisticalResult.getDocID());
	}

	private void gatherFinalResult(double runningTime, 
			List<ConceptSentimentPair> conceptSentimentPairs, 
			StatisticalResult statisticalResult, 
			List<FullPair> topK) {
		if (conceptSentimentPairs.size() <= k) {
			statisticalResult.setFinalCost(0);
			statisticalResult.setNumUncovered(0);
			statisticalResult.setRunningTime(0);
			statisticalResult.setNumUsefulCover(0);
		} else {
			statisticalResult.setRunningTime(runningTime);
			statisticalResult.setNumPairs(conceptSentimentPairs.size());
			statisticalResult.setNumUncovered(root.getCustomerMap().size());
			
			/*for (FullPair pair : topK) {
				for (FullPair customer : pair.getCustomerMap().keySet()) {
					if (pair.getCustomerMap().get(customer) != 0)
						statisticalResult.increaseNumUsefulCover();
				}
			}*/
			
			int numEdges = 0;
			for (int i = 0; i < conceptSentimentPairs.size(); ++i) {
				for (int j = i + 1; j < conceptSentimentPairs.size(); ++j) {
					if (conceptSentimentPairs.get(i).calculateDistance(conceptSentimentPairs.get(j))
							!= Constants.INVALID_DISTANCE)
						++numEdges;
				}
			}
			statisticalResult.setNumEdges(numEdges);
		}
		docToStatisticalResult.put(statisticalResult.getDocID(), statisticalResult);
	}

	@SuppressWarnings("unused")
	private void printInitialization(List<FullPair> pairs, List<FullPair> topK, StatisticalResult result) {
		System.err.println("PAIRS: (size " + pairs.size() + ", cost " + result.getInitialCost() + ")");
		for (FullPair pair : pairs) {
			if (!topK.contains(pair))
				System.out.println(pair.toString());
		}
	}
	
	@SuppressWarnings("unused")
	private void printResult(List<FullPair> topK, int datasetSize, StatisticalResult result) {
		System.err.println("TOP-" + k + " with threshold " + threshold + " (actual size: " + topK.size() 
				+ ", cost " + result.getFinalCost() + ")");
		for (FullPair pair : topK) {
			System.out.println(pair.toString());
		}
		System.err.println("Uncovered pairs: (size " + root.getCustomerMap().size() + " out of " + datasetSize + ")");
		for (FullPair pair : root.getCustomerMap().keySet()) {
			System.err.println(pair.toString());
		}
	}
	
	@SuppressWarnings("unused")
	private class InitPairsRunnable implements Runnable {
		private List<ConceptSentimentPair> conceptSentimentPairs;
		private List<FullPair> fullPairs;
		private int startIndex;
		private int length;
					
		public InitPairsRunnable(List<ConceptSentimentPair> conceptSentimentPairs,
				List<FullPair> fullPairs, int startIndex, int length) {
			super();
			this.conceptSentimentPairs = conceptSentimentPairs;
			this.fullPairs = fullPairs;
			this.startIndex = startIndex;
			this.length = length;
		}
		
		private void computeDirectly() {
			// Init the customers and potential hosts
			for (int i = startIndex; i < startIndex + length; ++i) {
				Utils.printTotalHeapSize("Init customer, iteration " + i);
				
				FullPair pair = fullPairs.get(i);			
				for (int j = i + 1; j < fullPairs.size(); j ++) {
					FullPair other = fullPairs.get(j);
									
					if (Constants.DEBUG_MODE) {
						conceptSentimentPairs.get(i).testDistance(conceptSentimentPairs.get(j));
						conceptSentimentPairs.get(j).testDistance(conceptSentimentPairs.get(i));
					}
					
					int distance = Constants.INVALID_DISTANCE;
					
					distance = conceptSentimentPairs.get(i).calculateDistance(conceptSentimentPairs.get(j), threshold);
					
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
		}

		@Override
		public void run() {
			computeDirectly();			
		}		
	}
	
}
