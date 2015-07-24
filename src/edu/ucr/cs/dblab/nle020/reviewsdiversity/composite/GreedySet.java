package edu.ucr.cs.dblab.nle020.reviewsdiversity.composite;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.FullPair;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.TopPairsResult;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;

public class GreedySet {
	protected int k = 0;
	protected float threshold = 0.0f;

	protected FullPair root = new FullPair(Constants.ROOT_CUI);
	
	protected ConcurrentMap<Integer, TopPairsResult> docToTopPairsResult = new ConcurrentHashMap<Integer, TopPairsResult>();
			
	public GreedySet(int k, float threshold,
			ConcurrentMap<Integer, TopPairsResult> docToTopPairsResult) {
		super();
		this.k = k;
		this.threshold = threshold;
		this.docToTopPairsResult = docToTopPairsResult;
	}
	
	/**
	 * Run Greedy Algorithm for a doctor's data set
	 * @param docId - doctor ID
	 * @param sentimentUnits - list of sentiment units/nodes in K-medians
	 * @return Result's statistics
	 */
	protected TopPairsResult runGreedyPerDoc(int docId, Collection<? extends SentimentSet> sentimentSets) {
		long startTime = System.currentTimeMillis();
		
		TopPairsResult result = new TopPairsResult(docId, k, threshold);;

//		printInitialization();		
		Map<FullPair, Map<FullPair, Integer>> distances = new HashMap<FullPair, Map<FullPair, Integer>>();	
		

		List<FullPair> topK = new ArrayList<FullPair>();	
		List<FullPair> fullPairSets = new ArrayList<FullPair>();
		initPairs(sentimentSets, fullPairSets, result, distances);
		
		PriorityQueue<FullPair> heap = initHeap(fullPairSets);
		if (fullPairSets.size() <= k) {
			topK.addAll(fullPairSets);
		} else {
			for (int i = 0; i < k; i++) {
				if (System.currentTimeMillis() - startTime > 1000 * 60)
					System.err.println("???");
				chooseNextPair(heap, topK, result);
			}
		}
		
		gatherFinalResult(System.currentTimeMillis() - startTime, fullPairSets.size(), result, topK);
				

//		Utils.printRunningTime(startTime, "Greedy finished docId " + docId);
//		printResult();	
		return result;
	}	
	
	private void initPairs(Collection<? extends SentimentSet> sentimentSets, 
				Collection<FullPair> fullPairSets, TopPairsResult result, 
				Map<FullPair, Map<FullPair, Integer>> distances) {
	
			Map<SentimentSet, FullPair> setToFullPairSet = new HashMap<SentimentSet, FullPair>();
			// Init fullPairSets corresponding to sentimentSets
			for (SentimentSet set : sentimentSets) {
				FullPair fullPairSet = new FullPair(set.getId(), set);
				fullPairSets.add(fullPairSet);
				setToFullPairSet.put(set, fullPairSet);
			}		
					
			Map<FullPair, ConceptSentimentPair> pairToConceptSentiments = new HashMap<FullPair, ConceptSentimentPair>();
					
			for (SentimentSet set : sentimentSets) {
				// There is no duplicated ConceptSentimentPair in a set
				for (ConceptSentimentPair conceptSentimentPair : set.getPairs()) {
					
					// FullPair need unique id
					FullPair pair = new FullPair(conceptSentimentPair.getId() + "_s" + conceptSentimentPair.getSentiment(), set);
					set.addFullPair(pair);
					pairToConceptSentiments.put(pair, conceptSentimentPair);
				}
			}
							
			// Init the host
			for (FullPair pair : pairToConceptSentiments.keySet()) {
				pair.setHost(root);
			}
			
			for (SentimentSet set : sentimentSets) {
				FullPair fullPairSet = setToFullPairSet.get(set);
				for (FullPair pair : set.getFullPairs()) {
					pair.addPotentialHost(fullPairSet);
					fullPairSet.addCustomer(pair, 0);
				}
			}
			
			for (FullPair pair : pairToConceptSentiments.keySet()) {
				setToFullPairSet.get(pair.getParent())
					.increaseBenefit(pairToConceptSentiments.get(pair).calculateRootDistance());
			}
			
			
			for (SentimentSet set : sentimentSets) {
				FullPair fullPairSet = setToFullPairSet.get(set);
				
				for (FullPair otherPair : pairToConceptSentiments.keySet()) {
					if (!set.getFullPairs().contains(otherPair)) {
						
						int min = Constants.INVALID_DISTANCE;					
						FullPair potentialHost = null;
						
						for (FullPair pair : set.getFullPairs()) {
							int distance = pairToConceptSentiments.get(pair).calculateDistance(
									pairToConceptSentiments.get(otherPair), 
									threshold);
							if (distance < min && distance >= 0) {
								min = distance;
								potentialHost = pair;
							}
						}
						
						if (min >= 0 && min < Constants.INVALID_DISTANCE && potentialHost != null) {
							fullPairSet.addCustomer(otherPair, min);
							otherPair.addPotentialHost(fullPairSet);
							fullPairSet.increaseBenefit(pairToConceptSentiments.get(potentialHost).calculateRootDistance());
						}
					}
				}
			}
			
			
			long initialCost = 0;
			// Init the root		
			root.getCustomerMap().clear();
			for (FullPair pair : pairToConceptSentiments.keySet()) {
	//			root.setHost(root);
				
				int distance = pairToConceptSentiments.get(pair).calculateRootDistance();
				root.addCustomer(pair, distance);
				initialCost += distance;
			}
			
			// Init result
			result.setInitialCost(initialCost);
			result.setFinalCost(initialCost);
			result.setNumSets(sentimentSets.size());
			int numPairs = 0;
			for (SentimentSet set : sentimentSets) {
				numPairs += set.getPairs().size();
			}
			result.setNumPairs(numPairs);			
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
		// Choose next pair
		FullPair nextPairSet = heap.poll();
		topK.add(nextPairSet);
		
		// Update pair of this set
		for (FullPair inSetPair : nextPairSet.getParent().getFullPairs()) {
			if (inSetPair.getHost().distanceToCustomer(inSetPair) == 0 
					&& !inSetPair.getHost().equals(nextPairSet)) {
				inSetPair.getHost().removeCustomer(inSetPair);
				inSetPair.setHost(nextPairSet);
				inSetPair.removePotentialHost(nextPairSet);
			}
		}		
		
		// Update the customerMap - who it serves, and potentialHosts of servedPairs
		//		served pairs - ones that have next pair as the closest cover ancestor
		
		for (FullPair servedPair : nextPairSet.getCustomerMap().keySet()) {			
			if (nextPairSet.getParent().getFullPairs().contains(servedPair) 
					&& servedPair.getHost().distanceToCustomer(servedPair) == 0)
				continue;
			
			int partialBenefit = servedPair.getHost().distanceToCustomer(servedPair) - nextPairSet.distanceToCustomer(servedPair);			
			
			// nextPair is not better than the current host of this servedPair
			if (partialBenefit <= 0) {
				nextPairSet.removeCustomer(servedPair); 
				servedPair.removePotentialHost(nextPairSet);
			} else {		
			// nextPair becomes new host of this servedPair
				
				int distanceFromOldHost = servedPair.getHost().distanceToCustomer(servedPair);				
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
				int distanceFromNewHost = nextPairSet.distanceToCustomer(servedPair);
				for (FullPair potentialHost : servedPair.getPotentialHosts()) {
					int distanceFromPotentialHost = potentialHost.distanceToCustomer(servedPair);
					potentialHost.decreaseBenefit(distanceFromOldHost - distanceFromPotentialHost);
					
					int benefitOverNextPair = distanceFromNewHost - distanceFromPotentialHost;
					if (benefitOverNextPair > 0) {
						potentialHost.increaseBenefit(benefitOverNextPair);
					}
				}
												
				result.decreaseFinalCost(partialBenefit);
			}
		}
		
		
		
		// Update the heap of unchosen pairs
		for (FullPair servedPair : nextPairSet.getCustomerMap().keySet()) {
			for (FullPair potentialHost : servedPair.getPotentialHosts()) {
				heap.remove(potentialHost);
				heap.add(potentialHost);
			}
		}
		
	}
	
	
	private void gatherFinalResult(long runningTime, int datasetSize, TopPairsResult result, List<FullPair> topK) {
		if (datasetSize <= Constants.K) {
			result.setFinalCost(0);
			result.setNumUncovered(0);
			result.setRunningTime(0);
			result.setNumUsefulCover(0);
		} else {
			result.setNumUncovered(root.getCustomerMap().size());
			result.setRunningTime(runningTime);
		}
		docToTopPairsResult.put(result.getDocID(), result);
	}
}
