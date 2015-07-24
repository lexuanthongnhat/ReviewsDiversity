package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.utilities.Utils;

public class Greedy {
	protected int k = 0;
	protected float threshold = 0.0f;

	protected FullPair root = new FullPair(Constants.ROOT_CUI);
	
	protected ConcurrentMap<Integer, TopPairsResult> docToTopPairsResult = new ConcurrentHashMap<Integer, TopPairsResult>();
			
	public Greedy(int k, float threshold,
			ConcurrentMap<Integer, TopPairsResult> docToTopPairsResult) {
		super();
		this.k = k;
		this.threshold = threshold;
		this.docToTopPairsResult = docToTopPairsResult;
	}

	/**
	 * Run Greedy Algorithm for a doctor's data set
	 * @param docId - doctor ID
	 * @param docToSentimentSets - list of sentiment units/nodes in K-medians
	 * @return Result's statistics
	 */
	protected TopPairsResult runGreedyPerDoc(int docId, List<ConceptSentimentPair> conceptSentimentPairs) {
		long startTime = System.currentTimeMillis();
				
		TopPairsResult result = new TopPairsResult(docId, k, threshold);		
		List<FullPair> topK = new ArrayList<FullPair>();	

//		printInitialization();		
		List<FullPair> pairs = new ArrayList<FullPair>();
		initPairs(conceptSentimentPairs, pairs, result);
		
		Map<FullPair, Map<FullPair, Integer>> distances = new HashMap<FullPair, Map<FullPair, Integer>>();	
		if (Constants.DEBUG_MODE)
			initDistances(conceptSentimentPairs, distances, pairs);
				
		PriorityQueue<FullPair> heap = initHeap(pairs);
		
		if (pairs.size() <= k) {
			topK = pairs;
		} else {
			for (int i = 0; i < k; i++) {
				if (System.currentTimeMillis() - startTime > 1000 * 60)
					System.err.println("???");
				chooseNextPair(heap, topK, result);
			}
		}
				
		if (Constants.DEBUG_MODE)
			checkResult(topK, distances, result);
		
		gatherFinalResult(System.currentTimeMillis() - startTime, pairs.size(), result, topK);

//		Utils.printRunningTime(startTime, "Greedy finished docId " + docId);
//		printResult();	
		return result;
	}	
	
	private void initPairs(List<ConceptSentimentPair> conceptSentimentPairs, List<FullPair> fullPairs, TopPairsResult result) {
		long startTime = System.currentTimeMillis();
		
		for (int i = 0 ; i < conceptSentimentPairs.size() ; i++) {
			
			// TODO - note that fullPair is identified using hashcode of only id -> id must be unique
			fullPairs.add(new FullPair(conceptSentimentPairs.get(i).getId() + "_s" + conceptSentimentPairs.get(i).getSentiment()));
		}		
		
		// Init the host
		for (int i = 0; i < fullPairs.size(); i++) {
			FullPair pair = fullPairs.get(i);			
			pair.getCustomerMap().put(pair, 0);					// It can serve itself	
			pair.getPotentialHosts().add(pair);
			pair.setHost(root);			
		}
		
		// Init the customers and potential hosts
		/*InitPairs initPairsInstance= new InitPairs(conceptSentimentPairs, fullPairs, 0, fullPairs.size());
		ForkJoinPool pool = new ForkJoinPool();
		pool.invoke(initPairsInstance);*/
		
/*		ExecutorService pool = Executors.newFixedThreadPool(Constants.NUM_THREADS_INIT_PAIRS);
		for (int i = 0; i < fullPairs.size(); i++) {
			pool.submit(new InitPairsRunnable(conceptSentimentPairs, fullPairs, i, 1));
		}
		pool.shutdown();
		
		try {
			pool.awaitTermination(2, TimeUnit.DAYS);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		
		for (int i = 0; i < fullPairs.size() - 1; i++) {
//			Utils.printTotalHeapSize("Init customer, iteration " + i);
			
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
		
		// Init benefit
		for (int i = 0; i < fullPairs.size(); i++) {
			FullPair pair = fullPairs.get(i);
			pair.setBenefit(pair.getCustomerMap().size() * conceptSentimentPairs.get(i).calculateRootDistance()); 
		}
		
		long initialCost = 0;
		// Init the root
		//root.setCustomerMap(new ConcurrentHashMap<FullPair, Integer>());
		root.getCustomerMap().clear();
		for (int i = 0; i < fullPairs.size(); i++) {
//			root.setHost(root);
			
			int distance = conceptSentimentPairs.get(i).calculateRootDistance();
			root.getCustomerMap().put(fullPairs.get(i), distance);
			initialCost += distance;
		}
		
		
		// Init result
		result.setInitialCost(initialCost);
		result.setFinalCost(initialCost);
		result.setNumPairs(conceptSentimentPairs.size());
//		initNumPotentialUsefulCover(result, conceptSentimentPairs);
		initNumPotentialUsefulCoverWithThreshold(result, fullPairs);
		
		Utils.printRunningTime(startTime, "Finished initPairs of docId " + result.getDocID(), true);
	}

	private void initDistances(List<ConceptSentimentPair> conceptSentimentPairs, 
			Map<FullPair, Map<FullPair, Integer>> distances, List<FullPair> fullPairs) {
		
		distances.put(root, new HashMap<FullPair, Integer>());
		distances.get(root).put(root, 0);
		for (int i = 0; i < fullPairs.size(); i++) {
			FullPair pair = fullPairs.get(i);	
			distances.put(pair, new HashMap<FullPair, Integer>());
			
			distances.get(root).put(pair, conceptSentimentPairs.get(i).calculateRootDistance());
			distances.get(pair).put(root, Constants.INVALID_DISTANCE);
		}
		
		for (int i = 0; i < fullPairs.size(); i++) {
			FullPair pair = fullPairs.get(i);			
			for (int j = i; j < fullPairs.size(); j ++) {
				FullPair other = fullPairs.get(j);
				int distance = Constants.INVALID_DISTANCE;
				
				distance = conceptSentimentPairs.get(i).calculateDistance(conceptSentimentPairs.get(j), threshold);
									
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
	
	private void initNumPotentialUsefulCover(TopPairsResult result, List<ConceptSentimentPair> conceptSentimentPairs) {
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
	
	private void initNumPotentialUsefulCoverWithThreshold(TopPairsResult result, List<FullPair> fullPairs) {
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
	private void chooseNextPair(PriorityQueue<FullPair> heap, List<FullPair> topK, TopPairsResult result) {
		long startTime = System.currentTimeMillis();
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
												
				result.decreaseFinalCost(partialBenefit);
			}			
		}
		
		// Update the heap of unchosen pairs
		for (FullPair servedPair : nextPair.getCustomerMap().keySet()) {
			for (FullPair potentialHost : servedPair.getPotentialHosts()) {
				heap.remove(potentialHost);
				heap.add(potentialHost);
			}
		}
		
		Utils.printRunningTime(startTime, "Finished a nextPair iteration");
	}
	
	private void checkResult(List<FullPair> topK, Map<FullPair, Map<FullPair, Integer>> distances, TopPairsResult result) {
	
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

		System.out.println("Cost: " + result.getFinalCost() + " - Verifying Cost: " + verifyingCost);
		if (verifyingCost != result.getFinalCost())
			System.err.println("Greedy Error at docID " + result.getDocID());
	}

	private void gatherFinalResult(long runningTime, int datasetSize, TopPairsResult result, List<FullPair> topK) {
		if (datasetSize <= k) {
			result.setFinalCost(0);
			result.setNumUncovered(0);
			result.setRunningTime(0);
			result.setNumUsefulCover(0);
		} else {
			result.setNumUncovered(root.getCustomerMap().size());
			result.setRunningTime(runningTime);
			
			for (FullPair pair : topK) {
				for (FullPair customer : pair.getCustomerMap().keySet()) {
					if (pair.getCustomerMap().get(customer) != 0)
						result.increaseNumUsefulCover();
				}
			}
		}
		docToTopPairsResult.put(result.getDocID(), result);
	}

	@SuppressWarnings("unused")
	private void printInitialization(List<FullPair> pairs, List<FullPair> topK, TopPairsResult result) {
		System.err.println("PAIRS: (size " + pairs.size() + ", cost " + result.getInitialCost() + ")");
		for (FullPair pair : pairs) {
			if (!topK.contains(pair))
				System.out.println(pair.toString());
		}
	}
	
	@SuppressWarnings("unused")
	private void printResult(List<FullPair> topK, int datasetSize, TopPairsResult result) {
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
	
	private class InitPairs extends RecursiveAction {
		private List<ConceptSentimentPair> conceptSentimentPairs;
		private List<FullPair> fullPairs;
		private int startIndex;
		private int length;
					
		public InitPairs(List<ConceptSentimentPair> conceptSentimentPairs,
				List<FullPair> fullPairs, int startIndex, int length) {
			super();
			this.conceptSentimentPairs = conceptSentimentPairs;
			this.fullPairs = fullPairs;
			this.startIndex = startIndex;
			this.length = length;
		}

		@Override
		protected void compute() {
			if (length <= Constants.LENGTH_THRESHOLD) {
				computeDirectly();
				return;
			}
			
			int split = length / 2;
			invokeAll(new InitPairs(conceptSentimentPairs, fullPairs, startIndex, split), 
					new InitPairs(conceptSentimentPairs, fullPairs, startIndex + split, length - split));
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
