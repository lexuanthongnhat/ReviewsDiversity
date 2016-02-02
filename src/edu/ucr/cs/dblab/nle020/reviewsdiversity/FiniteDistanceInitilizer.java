package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.ucr.cs.dblab.nle020.ontology.SnomedGraphBuilder;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.utils.Utils;

public class FiniteDistanceInitilizer {
	static protected float threshold = 0.3f;
	
	/**
	 * Calculate pair of pairs with finite distance
	 * @param conceptSentimentPairs - list of concept-sentiment pairs, suppose the first element is the root
	 * @param sentimentThreshold - coverage threshold on sentiment 
	 * @return Map: Ancestor --> <Successor, Distance>
	 */
	public static Map<Integer, Map<Integer, Integer>> initFiniteDistances(
			List<ConceptSentimentPair> conceptSentimentPairs, 
			float sentimentThreshold,
			boolean... noRoot) {
		
		Map<Integer, Map<Integer, Integer>> ancestorToSuccessorAndDistance = new HashMap<Integer, Map<Integer, Integer>>();
		
		Map<String, Set<ConceptSentimentPair>> deweyToPairs = mapFromDeweyToPair(conceptSentimentPairs);
		Map<String, Map<String, Integer>> deweyToAncestors = findDeweyToAncesetors(deweyToPairs.keySet());
		
		Map<ConceptSentimentPair, Integer> pairToIndex = new HashMap<ConceptSentimentPair, Integer>();
		for (int i = 0; i < conceptSentimentPairs.size(); ++i) {
			pairToIndex.put(conceptSentimentPairs.get(i), i);
			ancestorToSuccessorAndDistance.put(i, new HashMap<Integer, Integer>());
			ancestorToSuccessorAndDistance.get(i).put(i, 0);
		}
				
		// The root
		if (noRoot.length == 0 || !noRoot[0]) {
			for (int j = 1; j < conceptSentimentPairs.size(); ++j) {
				ConceptSentimentPair normalPair = conceptSentimentPairs.get(j);				
				ancestorToSuccessorAndDistance.get(0).put(j, normalPair.calculateRootDistance());
			}		
		}
		
		// Normal pairs
		for (int successorIndex = 0; successorIndex < conceptSentimentPairs.size(); ++successorIndex) {		
			ConceptSentimentPair successorPair = conceptSentimentPairs.get(successorIndex);			
			for (String successorDewey : successorPair.getDeweys()) {
				
				// Looking for the dewey's ancestor of successor dewey
				Map<String, Integer> ancestorToDistance = deweyToAncestors.get(successorDewey);				
				if (ancestorToDistance == null)	continue;
				
				for (String ancestorDewey : ancestorToDistance.keySet()) {
					for (ConceptSentimentPair ancestorPair : deweyToPairs.get(ancestorDewey)) {
						if (successorPair != ancestorPair 
								&& Math.abs(successorPair.getSentiment() - ancestorPair.getSentiment()) <= sentimentThreshold) {
							
							int ancestorIndex = pairToIndex.get(ancestorPair);
							Integer currentDistance = ancestorToSuccessorAndDistance.get(ancestorIndex).get(successorIndex);
							int ancestorDistance = ancestorToDistance.get(ancestorDewey);
							if (currentDistance == null ||
									(currentDistance != null &&	ancestorDistance < currentDistance)) {
								ancestorToSuccessorAndDistance.get(ancestorIndex).put(successorIndex, ancestorDistance);								
							}								
						}
					}
				}
			}
		}
				
		return ancestorToSuccessorAndDistance;
	}

	/**
	 * Initialize the finite distances between pairs for Greedy algorithm
	 * @param conceptSentimentPairs - input
	 * @param fullPairs - output: the data structure holding information for Greedy
	 * @param statisticalResult - output: hold some statistics about the problem
	 */
	public static void initFullPairs(
			List<ConceptSentimentPair> conceptSentimentPairs, 
			List<FullPair> fullPairs, 
			StatisticalResult statisticalResult) {
	
		FullPair root = new FullPair(Constants.ROOT_CUI);
		for (int i = 0 ; i < conceptSentimentPairs.size() ; i++) {			
			// note that fullPair is identified using hashcode of only id -> id must be unique
			fullPairs.add(new FullPair(conceptSentimentPairs.get(i).getId() 
											+ "_s" + conceptSentimentPairs.get(i).getSentiment()));
		}		
				
		Map<Integer, Map<Integer, Integer>> ancestorToSuccessorAndDistance = 
				initFiniteDistances(conceptSentimentPairs, statisticalResult.getThreshold(), true);
		
		// Init the host
		for (int i = 0; i < fullPairs.size(); i++) {
			FullPair pair = fullPairs.get(i);			
			pair.getCustomerMap().put(pair, 0);					// It can serve itself	
			pair.getPotentialHosts().add(pair);
			pair.setHost(root);			
		}
				
		for (int ancestorIndex = 0; ancestorIndex < fullPairs.size(); ++ancestorIndex) {		
			FullPair ancestorFullPair = fullPairs.get(ancestorIndex);
			
			Map<Integer, Integer> successorIndexToDistance = ancestorToSuccessorAndDistance.get(ancestorIndex);
			for (Integer successorIndex : successorIndexToDistance.keySet()) {
				FullPair successorFullPair = fullPairs.get(successorIndex);
				ancestorFullPair.addCustomer(successorFullPair, successorIndexToDistance.get(successorIndex));
				successorFullPair.addPotentialHost(ancestorFullPair);
			}			
		}
		
		// Init benefit and the root
		long initialCost = 0;
		for (int i = 0; i < fullPairs.size(); i++) {
			int distanceFromRoot = conceptSentimentPairs.get(i).calculateRootDistance();
			
			FullPair pair = fullPairs.get(i);
			pair.setBenefit(pair.getCustomerMap().size() * distanceFromRoot);
			
			root.getCustomerMap().put(fullPairs.get(i), distanceFromRoot);
			initialCost += distanceFromRoot;
		}
		
		// Init result
		statisticalResult.setInitialCost(initialCost);
		statisticalResult.setFinalCost(initialCost);
	}	
	
	// Original method from Greedy
	private static void initPairs(
			List<ConceptSentimentPair> conceptSentimentPairs, 
			List<FullPair> fullPairs, 
			StatisticalResult statisticalResult) {
	
		FullPair root = new FullPair(Constants.ROOT_CUI);
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
				
				distance = conceptSentimentPairs.get(i)
						.calculateDistance(conceptSentimentPairs.get(j), threshold);
				
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
			pair.setBenefit(
					pair.getCustomerMap().size() * conceptSentimentPairs.get(i).calculateRootDistance()); 
		}
		
		long initialCost = 0;
		// Init the root
		root.getCustomerMap().clear();
		for (int i = 0; i < fullPairs.size(); i++) {			
			int distance = conceptSentimentPairs.get(i).calculateRootDistance();
			root.getCustomerMap().put(fullPairs.get(i), distance);
			initialCost += distance;
		}		
		
		// Init result
		statisticalResult.setInitialCost(initialCost);
		statisticalResult.setFinalCost(initialCost);
//		initNumPotentialUsefulCover(result, conceptSentimentPairs);
//		initNumPotentialUsefulCoverWithThreshold(statisticalResult, fullPairs);
	}
	
	// Original method from ILP
	protected static Map<Integer, Map<Integer, Integer>> initDistances(
			List<ConceptSentimentPair> conceptSentimentPairs, 
			float sentimentThreshold) {
		
		Map<Integer, Map<Integer, Integer>> facilityToCustomerAndDistance = new HashMap<Integer, Map<Integer, Integer>>();
		
		// The root
		facilityToCustomerAndDistance.put(0, new HashMap<Integer, Integer>());
		facilityToCustomerAndDistance.get(0).put(0, 0);
		for (int j = 1; j < conceptSentimentPairs.size(); ++j) {
			ConceptSentimentPair normalPair = conceptSentimentPairs.get(j);				
			facilityToCustomerAndDistance.get(0).put(j, normalPair.calculateRootDistance());
		}
		
		// Normal pairs
		for (int i = 1; i < conceptSentimentPairs.size(); ++i) {
			ConceptSentimentPair pair1 = conceptSentimentPairs.get(i);
			if (!facilityToCustomerAndDistance.containsKey(i))
				facilityToCustomerAndDistance.put(i, new HashMap<Integer, Integer>());
			facilityToCustomerAndDistance.get(i).put(i, 0);
			
			for (int j = i + 1; j < conceptSentimentPairs.size(); ++j) {
				ConceptSentimentPair pair2 = conceptSentimentPairs.get(j);				
				int distance = Constants.INVALID_DISTANCE;	

				if (Constants.DEBUG_MODE) {
					pair1.testDistance(pair2);
					pair2.testDistance(pair1);
				}
								
				int temp = pair1.calculateDistance(pair2, sentimentThreshold);
				if (temp != Constants.INVALID_DISTANCE) {
					distance = temp;
				}
											
				if (distance != Constants.INVALID_DISTANCE) {
					if (distance > 0) {			
						
						facilityToCustomerAndDistance.get(i).put(j, distance);
					} else if (distance < 0) {
						if (!facilityToCustomerAndDistance.containsKey(j))
							facilityToCustomerAndDistance.put(j, new HashMap<Integer, Integer>());
						
						facilityToCustomerAndDistance.get(j).put(i, -distance);
					} else if (distance == 0) {
						if (!facilityToCustomerAndDistance.containsKey(j))
							facilityToCustomerAndDistance.put(j, new HashMap<Integer, Integer>());
						
						facilityToCustomerAndDistance.get(i).put(j, distance);
						facilityToCustomerAndDistance.get(j).put(i, -distance);
					}
				}
			}
		}
				
		return facilityToCustomerAndDistance;
	}
	
	private static Map<String, Map<String, Integer>> findDeweyToAncesetors(
			Set<String> deweys) {
		Map<String, Map<String, Integer>> deweyToAncestorAndDistance = new HashMap<String, Map<String, Integer>>();
		
		
		Map<Integer, Set<String>> levelToDeweys = new HashMap<Integer, Set<String>>();
		for (String dewey : deweys) {
			int level = dewey.split("\\.").length;
			if (!levelToDeweys.containsKey(level))
				levelToDeweys.put(level, new HashSet<String>());
			levelToDeweys.get(level).add(dewey);
		}
		
		int maxLevel = 1;
		for (int level : levelToDeweys.keySet())
			if (maxLevel < level)
				maxLevel = level;
		
		// dewey is the ancestor of itself
		for (String dewey : deweys) {
			deweyToAncestorAndDistance.put(dewey, new HashMap<String, Integer>());
			deweyToAncestorAndDistance.get(dewey).put(dewey, 0);
		}
		
		// get ancestor from the closest ancestors
		for (int level = 2; level <= maxLevel; ++level) {
			if (!levelToDeweys.containsKey(level))
				continue;
			
			for (String successor : levelToDeweys.get(level)) {	
				int indexOfLastDot = successor.length();
				int ancestorLevel = level;
				 while ((indexOfLastDot = successor.substring(0, indexOfLastDot).lastIndexOf(".")) > 0) {									
					String ancestor = successor.substring(0, indexOfLastDot);
					--ancestorLevel;
					
					if (levelToDeweys.containsKey(ancestorLevel) && levelToDeweys.get(ancestorLevel).contains(ancestor)) {						
						int offset = level - ancestorLevel; 
						Map<String, Integer> ancestorToDistance = new HashMap<String, Integer>(deweyToAncestorAndDistance.get(ancestor));
						for (String newAncestor : ancestorToDistance.keySet()) {
							ancestorToDistance.put(newAncestor, ancestorToDistance.get(newAncestor) + offset);
						}
						deweyToAncestorAndDistance.get(successor).putAll(ancestorToDistance);						
						break;
					}
				}
			}
		}
		
		return deweyToAncestorAndDistance;
	}

	private static Map<String, Set<ConceptSentimentPair>> mapFromDeweyToPair(
			List<ConceptSentimentPair> conceptSentimentPairs) {
		Map<String, Set<ConceptSentimentPair>> deweyToPairs = new HashMap<String, Set<ConceptSentimentPair>>();
		
		for (ConceptSentimentPair pair : conceptSentimentPairs) {
			for (String dewey : pair.getDeweys()) {
				if (!deweyToPairs.containsKey(dewey))
					deweyToPairs.put(dewey, new HashSet<ConceptSentimentPair>());
				
				deweyToPairs.get(dewey).add(pair);
			}
		}
		
		return deweyToPairs;
	}
	
	public static void main(String[] args) {
		Map<Integer, List<ConceptSentimentPair>> docToConceptSentimentPairs = 
				TopPairsProgram.importDocToConceptSentimentPairs(
						TopPairsProgram.DOC_TO_REVIEWS_PATH, 
						TopPairsProgram.RANDOMIZE_DOCS);
		
		
		for (Integer docId : docToConceptSentimentPairs.keySet()) {
			//compareInitDistances(docToConceptSentimentPairs, docId);
			//compareInitPairs(docToConceptSentimentPairs, docId);
		}
	}
	

	@SuppressWarnings("unused")
	private static void compareInitPairs(
			Map<Integer, List<ConceptSentimentPair>> docToConceptSentimentPairs,
			Integer docId) {
		List<ConceptSentimentPair> conceptSentimentPairs = docToConceptSentimentPairs.get(docId);
				
		StatisticalResult statisticalResultNew = new StatisticalResult(docId, 10, threshold);		
		List<FullPair> pairsNew = new ArrayList<FullPair>();
		
//		long startTime = System.currentTimeMillis();
		initFullPairs(conceptSentimentPairs, pairsNew, statisticalResultNew);
//		Utils.printRunningTime(startTime, "New init");

		StatisticalResult statisticalResult = new StatisticalResult(docId, 10, threshold);		
		List<FullPair> pairs = new ArrayList<FullPair>();
//		startTime = System.currentTimeMillis();
		initPairs(conceptSentimentPairs, pairs, statisticalResult);
//		Utils.printRunningTime(startTime, "Old init");		

	
		
		if (statisticalResult.getInitialCost() - statisticalResultNew.getInitialCost() != 0) {
			System.err.println("Initial Cost error");
		}
		
		for (int i = 0; i < pairs.size(); ++i) {
			FullPair oldPair = pairs.get(i);
			FullPair newPair = pairsNew.get(i);
			
			int oldSum = 0;
			for (Integer distance : oldPair.getCustomerMap().values())
				oldSum += distance;
			
			int newSum = 0;
			for (Integer distance : newPair.getCustomerMap().values())
				newSum += distance;
			
			if (newSum != oldSum)
				System.err.println("Error in customer map");
			if (oldPair.getPotentialHosts().size() != newPair.getPotentialHosts().size()) 
				System.err.println("Error in potential host");
			
		}
	}
	
	@SuppressWarnings("unused")
	private static void compareInitDistances(
			Map<Integer, List<ConceptSentimentPair>> docToConceptSentimentPairs,
			Integer docId) {
		List<ConceptSentimentPair> conceptSentimentPairs = docToConceptSentimentPairs.get(docId);
		
		List<ConceptSentimentPair> pairs = new ArrayList<ConceptSentimentPair>();
		ConceptSentimentPair root = new ConceptSentimentPair(Constants.ROOT_CUI, 0.0f);
		root.addDewey(SnomedGraphBuilder.ROOT_DEWEY);
		pairs.add(root);
		pairs.addAll(conceptSentimentPairs);
		
		long startTime = System.currentTimeMillis();
		Map<Integer, Map<Integer, Integer>> facilityToCustomerAndDistanceNew = initFiniteDistances(pairs, 0.3f);
		Utils.printRunningTime(startTime, "New init");

		startTime = System.currentTimeMillis();
		Map<Integer, Map<Integer, Integer>> facilityToCustomerAndDistance = initDistances(pairs, 0.3f);
		Utils.printRunningTime(startTime, "Old init");
		
		for (Integer facility : facilityToCustomerAndDistance.keySet()) {
			for (Integer customer : facilityToCustomerAndDistance.get(facility).keySet()) {
				if (facilityToCustomerAndDistance.get(facility).get(customer) 
						!= facilityToCustomerAndDistanceNew.get(facility).get(customer)) {
					System.err.println("Error at docID " + docId + ": facility " + facility + " - customer " + customer);
					System.err.println("Old: " + facilityToCustomerAndDistance);
					System.err.println("New: " + facilityToCustomerAndDistanceNew);
				}
			}
		}
		System.out.println();
	}
}
