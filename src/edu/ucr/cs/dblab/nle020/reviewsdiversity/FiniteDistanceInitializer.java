package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import edu.ucr.cs.dblab.nle020.ontology.SnomedGraphBuilder;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;

import static edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants.NUM_DOCTORS_TO_EXPERIMENT;

public class FiniteDistanceInitializer {
	static protected float threshold = 0.3f;
	
	/**
	 * Calculate pair of pairs with finite distance
	 * @param conceptSentimentPairs - list of concept-sentiment pairs
	 * @param sentimentThreshold - coverage threshold on sentiment 
	 * @return Map: Ancestor Index --> <Successor Index, Distance>
	 */
	static Map<Integer, Map<Integer, Integer>> initFiniteDistancesFromPairIndexToPairIndex(
			List<ConceptSentimentPair> conceptSentimentPairs, 
			float sentimentThreshold,
			boolean... noRoot) {
		
		Map<Integer, Map<Integer, Integer>> ancestorToSuccessorAndDistance = new HashMap<>();
		
		Map<String, Set<ConceptSentimentPair>> deweyToPairs = mapFromDeweyToPair(conceptSentimentPairs);
		Map<String, Map<String, Integer>> deweyToAncestors = findDeweyToAncesetors(deweyToPairs.keySet());
		
		Map<ConceptSentimentPair, Integer> pairToIndex = new HashMap<>();
		for (int i = 0; i < conceptSentimentPairs.size(); ++i) {
			pairToIndex.put(conceptSentimentPairs.get(i), i);
			ancestorToSuccessorAndDistance.put(i, new HashMap<>());
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
	 * Faster way to initiate finite distances, don't count duplicate ancestor of concept
	 */
	static Map<Integer, Map<Integer, Integer>> initFiniteDistancesFromPairIndexToPairIndex2(
			List<ConceptSentimentPair> conceptSentimentPairs, 
			float sentimentThreshold,
			boolean... noRoot) {
		
		Map<Integer, Map<Integer, Integer>> ancestorToSuccessorAndDistance = new HashMap<>();
		
		Map<String, Set<ConceptSentimentPair>> conceptToPairs = new HashMap<>();
		Map<String, Set<String>> conceptToDeweys = new HashMap<>();
		mapFromConceptToPairsAndDeweys(
				conceptSentimentPairs, 
				conceptToPairs, conceptToDeweys);

		Map<String, Set<String>> deweyToConcepts = new HashMap<>();
		for (String cui : conceptToDeweys.keySet()) {
			for (String dewey : conceptToDeweys.get(cui)) {
				if (!deweyToConcepts.containsKey(dewey))
					deweyToConcepts.put(dewey, new HashSet<>());
				deweyToConcepts.get(dewey).add(cui);
			}
		}
			
		Map<String, Set<String>> conceptToAncestorConcepts = findAncestorConcepts(conceptToDeweys, deweyToConcepts);		
		conceptToAncestorConcepts.remove(Constants.ROOT_CUI);
		
		Map<ConceptSentimentPair, Integer> pairToIndex = new HashMap<>();
		for (int i = 0; i < conceptSentimentPairs.size(); ++i) {
			pairToIndex.put(conceptSentimentPairs.get(i), i);
			ancestorToSuccessorAndDistance.put(i, new HashMap<>());
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
			Map<Integer, Integer> ancestorIndexToDistance = new HashMap<>();
			String concept = successorPair.getCui();
			
			Set<String> ancestorConcepts = conceptToAncestorConcepts.get(concept);
			if (ancestorConcepts == null) continue;
			for (String ancestorConcept : ancestorConcepts) {
				for (ConceptSentimentPair ancestorPair : conceptToPairs.get(ancestorConcept)) {
					int ancestorIndex = pairToIndex.get(ancestorPair);
					if (!successorPair.equals(ancestorPair) 
							|| !ancestorIndexToDistance.containsKey(ancestorIndex)) {
						int distanceFromAncestorToSuccessor = 
								ancestorPair.calculateDistance(successorPair, sentimentThreshold);
						if (distanceFromAncestorToSuccessor != Constants.INVALID_DISTANCE)
							ancestorToSuccessorAndDistance.get(ancestorIndex)
									.put(successorIndex, distanceFromAncestorToSuccessor);
					}
				}				
			}
		}
				
		return ancestorToSuccessorAndDistance;
	}
	
	
	private static Map<String, Set<String>> findAncestorConcepts(
			Map<String, Set<String>> conceptToDeweys,
			Map<String, Set<String>> deweyToConcepts) {
		Map<String, Set<String>> conceptToAncestorConcepts = new HashMap<>();
		
		for (String concept : conceptToDeweys.keySet()) {
			Set<String> ancestorConcepts = new HashSet<>();
			Set<String> deweys = conceptToDeweys.get(concept);
			
			boolean nextDewey = false;
			for (String dewey : deweys) {
				if (nextDewey) nextDewey = false;
								
				ancestorConcepts.addAll(deweyToConcepts.get(dewey));
				int indexOfLastDot = dewey.length();
				
				// Loop over potential ancestors, indexOfLastDot > 1 to discard the root
				while ((indexOfLastDot = dewey.substring(0, indexOfLastDot).lastIndexOf(".")) > 0 
						&& indexOfLastDot > 1) {									
					String ancestorDewey = dewey.substring(0, indexOfLastDot);
					Set<String> ancestorCandidates = deweyToConcepts.get(ancestorDewey);
					
					if (deweys.contains(ancestorDewey)) {
						// Ancestors of ancestorDewey was/will be cared, no need to do anything 
						nextDewey = true;
						break;
					} else if ( ancestorCandidates == null) 
						continue;
					else
						ancestorConcepts.addAll(ancestorCandidates);						
				}
				
				if (nextDewey)
					continue;
			}
	//		ancestorConcepts.remove(concept);
			conceptToAncestorConcepts.put(concept, ancestorConcepts);
		}
		
		return conceptToAncestorConcepts;
	}


	private static void mapFromConceptToPairsAndDeweys(
			List<ConceptSentimentPair> conceptSentimentPairs,
			Map<String, Set<ConceptSentimentPair>> conceptToPairs,
			Map<String, Set<String>> conceptToDeweys) {
				
		for (ConceptSentimentPair pair : conceptSentimentPairs) {
			String cui = pair.getCui();
			if (!conceptToPairs.containsKey(cui)) {
				conceptToPairs.put(cui, new HashSet<>());
				conceptToDeweys.put(cui, new HashSet<>(pair.getDeweys()));
			}
			conceptToPairs.get(cui).add(pair);
		}
	}


	private static Map<ConceptSentimentPair, Set<ConceptSentimentPair>> findAncestors(
			List<ConceptSentimentPair> conceptSentimentPairs,
			Map<String, Set<ConceptSentimentPair>> deweyToPairs,
			boolean... noRoot) {
		
		Map<ConceptSentimentPair, Set<ConceptSentimentPair>> pairToAncestors = new HashMap<>();
		
		int i = 0;
		if (noRoot.length == 0 || !noRoot[0])
			i = 1;
		
		for (; i < conceptSentimentPairs.size(); ++i) {
			ConceptSentimentPair pair = conceptSentimentPairs.get(i);
			pairToAncestors.put(pair, new HashSet<>());
			Set<ConceptSentimentPair> ancestors = pairToAncestors.get(pair);
			
			for (String dewey : pair.getDeweys()) {				
				int indexOfLastDot = dewey.length();
				while ((indexOfLastDot = dewey.substring(0, indexOfLastDot).lastIndexOf(".")) > 0) {									
					String ancestorDewey = dewey.substring(0, indexOfLastDot);
					Set<ConceptSentimentPair> ancestorCandidates = deweyToPairs.get(ancestorDewey);
					
					if (ancestorCandidates == null) 
						continue;
					else
						ancestors.addAll(ancestorCandidates);						
				}
			}
		}
		
		return pairToAncestors;
	}


	/**
	 * Initialize the finite distances between pairs for Greedy algorithm
	 * @param conceptSentimentPairs - input
	 * @param fullPairs - output: the data structure holding information for Greedy
	 * @param statisticalResult - output: hold some statistics about the problem
	 */
	static void initFullPairs(
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
				initFiniteDistancesFromPairIndexToPairIndex2(conceptSentimentPairs, statisticalResult.getThreshold(), true);
		
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

	/**
	 * Find all finite distances between pair of pairs
	 * This method is used for computing finite distance from set to pair
	 * @param conceptSentimentPairs - list of concept-sentiment pairs, suppose the first element is not the root
	 * @param sentimentThreshold - coverage threshold on sentiment 
	 * @return Map: Ancestor Pair --> <Successor Pair, Distance>
	 */
	private static Map<ConceptSentimentPair, Map<ConceptSentimentPair, Integer>> getFiniteDistancesFromPairToPair(
			List<ConceptSentimentPair> conceptSentimentPairs, 
			float sentimentThreshold) {
		
		Map<ConceptSentimentPair, Map<ConceptSentimentPair, Integer>> ancestorToSuccessorAndDistance = 
				new HashMap<>();
		
		Map<String, Set<ConceptSentimentPair>> deweyToPairs = mapFromDeweyToPair(conceptSentimentPairs);
		Map<String, Map<String, Integer>> deweyToAncestors = findDeweyToAncesetors(deweyToPairs.keySet());
			
		// pair itself
		for (ConceptSentimentPair pair : conceptSentimentPairs) {
			ancestorToSuccessorAndDistance.put(pair, new HashMap<>());
			ancestorToSuccessorAndDistance.get(pair).put(pair, 0);
		}
					
		// Normal pairs
		for (ConceptSentimentPair successorPair : conceptSentimentPairs) {		
			for (String successorDewey : successorPair.getDeweys()) {
				
				// Looking for the dewey's ancestor of successor dewey
				Map<String, Integer> ancestorToDistance = deweyToAncestors.get(successorDewey);				
				if (ancestorToDistance == null)	continue;
				
				for (String ancestorDewey : ancestorToDistance.keySet()) {
					for (ConceptSentimentPair ancestorPair : deweyToPairs.get(ancestorDewey)) {
						if (!successorPair.equals(ancestorPair) 
								&& Math.abs(successorPair.getSentiment() - ancestorPair.getSentiment()) <= sentimentThreshold) {
							
							Integer currentDistance = ancestorToSuccessorAndDistance.get(ancestorPair).get(successorPair);
							int ancestorDistance = ancestorToDistance.get(ancestorDewey);
							if (currentDistance == null ||
									(currentDistance != null &&	ancestorDistance < currentDistance)) {
								ancestorToSuccessorAndDistance.get(ancestorPair).put(successorPair, ancestorDistance);								
							}								
						}
					}
				}
			}
		}
				
		return ancestorToSuccessorAndDistance;
	}
	
	/**
	 * Find all finite distances between pair of pairs
	 * This method is used for computing finite distance from set to pair
	 * @param conceptSentimentPairs - list of concept-sentiment pairs, suppose the first element is not the root
	 * @param sentimentThreshold - coverage threshold on sentiment 
	 * @return Map: Ancestor Pair --> <Successor Pair, Distance>
	 */
	private static Map<ConceptSentimentPair, Map<ConceptSentimentPair, Integer>> getFiniteDistancesFromPairToPair2(
			List<ConceptSentimentPair> conceptSentimentPairs, 
			float sentimentThreshold) {
		
		Map<ConceptSentimentPair, Map<ConceptSentimentPair, Integer>> ancestorToSuccessorAndDistance = 
				new HashMap<ConceptSentimentPair, Map<ConceptSentimentPair, Integer>>();
		
		Map<String, Set<ConceptSentimentPair>> conceptToPairs = new HashMap<>();
		Map<String, Set<String>> conceptToDeweys = new HashMap<>();
		mapFromConceptToPairsAndDeweys(
				conceptSentimentPairs, 
				conceptToPairs, conceptToDeweys);

		Map<String, Set<String>> deweyToConcepts = new HashMap<>();
		for (String cui : conceptToDeweys.keySet()) {
			for (String dewey : conceptToDeweys.get(cui)) {
				if (!deweyToConcepts.containsKey(dewey))
					deweyToConcepts.put(dewey, new HashSet<>());
				deweyToConcepts.get(dewey).add(cui);
			}
		}
			
		Map<String, Set<String>> conceptToAncestorConcepts = findAncestorConcepts(conceptToDeweys, deweyToConcepts);		
		conceptToAncestorConcepts.remove(Constants.ROOT_CUI);
		
		for (ConceptSentimentPair pair : conceptSentimentPairs) {
			ancestorToSuccessorAndDistance.put(pair, new HashMap<>());
			ancestorToSuccessorAndDistance.get(pair).put(pair, 0);
		}		
		
		// Normal pairs
		for (ConceptSentimentPair successorPair : conceptSentimentPairs) {		
			Map<ConceptSentimentPair, Integer> ancestorPairToDistance = new HashMap<>();
			String concept = successorPair.getCui();
			
			Set<String> ancestorConcepts = conceptToAncestorConcepts.get(concept);
			if (ancestorConcepts == null) continue;
			for (String ancestorConcept : ancestorConcepts) {
				for (ConceptSentimentPair ancestorPair : conceptToPairs.get(ancestorConcept)) {
					if (!successorPair.equals(ancestorPair) 
							|| !ancestorPairToDistance.containsKey(ancestorPair)) {
						int distanceFromAncestorToSuccessor = 
								ancestorPair.calculateDistance(successorPair, sentimentThreshold);
						if (distanceFromAncestorToSuccessor != Constants.INVALID_DISTANCE)
							ancestorToSuccessorAndDistance.get(ancestorPair)
									.put(successorPair, distanceFromAncestorToSuccessor);
					}
				}				
			}
		}
				
		return ancestorToSuccessorAndDistance;
	}
	
	/**
	 * Init the finite distances from the (sets, root) to the concept-sentiment pairs
	 * @return array of length [sentimentSets.size() + 1 ] * conceptSentimentPairs.size()
	 * <br> +1 for the root, result[0] is the distance array of the root
	 */
	public static Map<Integer, Map<Integer, Integer>> initFiniteDistancesFromSetIndexToPairIndex(
			float sentimentThreshold, 
			List<SentimentSet> sentimentSets, 
			List<ConceptSentimentPair> conceptSentimentPairs, 
			StatisticalResult statisticalResult) {
		
		Map<Integer, Map<Integer, Integer>> setIndexToPairIndexAndDistance = new HashMap<>();
							
		// The root
		conceptSentimentPairs.add(0, new ConceptSentimentPair(Constants.ROOT_CUI, 0.0f));
		
		setIndexToPairIndexAndDistance.put(0, new HashMap<>());
		setIndexToPairIndexAndDistance.get(0).put(0, 0);
		int initialCost = 0;		
		for (int j = 1; j < conceptSentimentPairs.size(); ++j) {
			ConceptSentimentPair pair = conceptSentimentPairs.get(j);				
				
			setIndexToPairIndexAndDistance.get(0).put(j, pair.calculateRootDistance());
			initialCost += pair.calculateRootDistance(); 
		}
		statisticalResult.setInitialCost(initialCost);		
		
		Map<ConceptSentimentPair, Map<ConceptSentimentPair, Integer>> ancestorPairToSuccessorPairAndDistance
				= getFiniteDistancesFromPairToPair2(conceptSentimentPairs, sentimentThreshold);
		
		Map<ConceptSentimentPair, Set<Integer>> pairToIndices = new HashMap<>();
		for (int i = 0; i < conceptSentimentPairs.size(); ++i) {
			ConceptSentimentPair pair = conceptSentimentPairs.get(i);
			if (!pairToIndices.containsKey(pair))
				pairToIndices.put(pair, new HashSet<>());
			pairToIndices.get(pair).add(i);
		}
		
		// The sets
		for (int s = 0; s < sentimentSets.size(); ++s) {
			int setAncestorIndex = s + 1;
			setIndexToPairIndexAndDistance.put(setAncestorIndex, new HashMap<>());
			Map<Integer, Integer> pairIndexToDistance = setIndexToPairIndexAndDistance.get(setAncestorIndex);
			
			for (ConceptSentimentPair pair : sentimentSets.get(s).getPairs()) {				
				Map<ConceptSentimentPair, Integer> successorPairToDistance = ancestorPairToSuccessorPairAndDistance.get(pair);
				if (successorPairToDistance == null) 
					continue;
				for (ConceptSentimentPair successorPair : successorPairToDistance.keySet()) {
					int distanceToSuccessor = successorPairToDistance.get(successorPair);
					for (Integer successorPairIndex : pairToIndices.get(successorPair)) {
						if (!pairIndexToDistance.containsKey(successorPairIndex))
							pairIndexToDistance.put(successorPairIndex, distanceToSuccessor);
						else if (pairIndexToDistance.get(successorPairIndex) > distanceToSuccessor)
							pairIndexToDistance.put(successorPairIndex, distanceToSuccessor);
					}
				}
			}
		}		
		
		return setIndexToPairIndexAndDistance;
	}	
	
	/**
	 * Init fullPairs set for GreedySet
	 */
	public static void initFullPairs(
			float sentimentThreshold,
			Collection<? extends SentimentSet> sentimentSets, 
			Collection<FullPair> fullPairSets, StatisticalResult statisticalResult) {
		
		FullPair root = new FullPair(Constants.ROOT_CUI);
		Map<SentimentSet, FullPair> setToFullPairSet = new HashMap<>();
		// Init fullPairSets corresponding to sentimentSets
		fullPairSets.clear();
		for (SentimentSet set : sentimentSets) {
			FullPair fullPairSet = new FullPair(set.getId(), set);
			fullPairSets.add(fullPairSet);
			setToFullPairSet.put(set, fullPairSet);
		}
		
		Map<FullPair, ConceptSentimentPair> fullPairToCSPair = new HashMap<>();
		Map<ConceptSentimentPair, FullPair> csPairToFullPair = new HashMap<>();
		
		List<FullPair> fullPairs = new ArrayList<>();
		List<ConceptSentimentPair> conceptSentimentPairs = new ArrayList<>();
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
		
		Map<ConceptSentimentPair, Map<ConceptSentimentPair, Integer>> ancestorPairToSuccessorPairAndDistance
				= getFiniteDistancesFromPairToPair2(conceptSentimentPairs, sentimentThreshold);
		
		
		for (SentimentSet set : sentimentSets) {
			FullPair fullPairSet = setToFullPairSet.get(set);

			for (ConceptSentimentPair ancestorPair : set.getPairs()) {				
				Map<ConceptSentimentPair, Integer> successorPairToDistance = ancestorPairToSuccessorPairAndDistance.get(ancestorPair);
				
				if (successorPairToDistance == null)
					continue;
				for (ConceptSentimentPair successorPair : successorPairToDistance.keySet()) {
					int distanceToSuccessor = successorPairToDistance.get(successorPair);

					FullPair successorFullPair = csPairToFullPair.get(successorPair);
					Integer currentDistance = fullPairSet.getCustomerMap().get(successorFullPair);

					if (currentDistance == null 
							|| distanceToSuccessor < currentDistance) {
						fullPairSet.addCustomer(successorFullPair, distanceToSuccessor);						
					}
				}
			}
			
			for (FullPair successorFullPair : fullPairSet.getCustomerMap().keySet()) {
				successorFullPair.addPotentialHost(fullPairSet);
				fullPairSet.increaseBenefit(
						fullPairToCSPair.get(successorFullPair).calculateRootDistance() 
							- fullPairSet.getCustomerMap().get(successorFullPair));
			}
		}
		
		
		long initialCost = 0;
		// Init the root		
		root.getCustomerMap().clear();
		for (FullPair fullPair : fullPairs) {
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
	}
	
	// Original version from GreedySet
	private static void initPairs(
			float sentimentThreshold,
			Collection<? extends SentimentSet> sentimentSets, 
			Collection<FullPair> fullPairSets, StatisticalResult statisticalResult) {
	
	FullPair root = new FullPair(Constants.ROOT_CUI);
	Map<SentimentSet, FullPair> setToFullPairSet = new HashMap<>();
	// Init fullPairSets corresponding to sentimentSets
	for (SentimentSet set : sentimentSets) {
		FullPair fullPairSet = new FullPair(set.getId(), set);
		fullPairSets.add(fullPairSet);
		setToFullPairSet.put(set, fullPairSet);
	}			
	
	Map<FullPair, ConceptSentimentPair> fullPairToCSPair = new HashMap<>();
	Map<ConceptSentimentPair, FullPair> csPairToFullPair = new HashMap<>();
	
	List<FullPair> fullPairs = new ArrayList<>();
	List<ConceptSentimentPair> conceptSentimentPairs = new ArrayList<>();
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
//				FullPair potentialHost = null;

				for (FullPair fullPairInSet : set.getFullPairs()) {
					int distance = fullPairToCSPair.get(fullPairInSet).calculateDistance(fullPairToCSPair.get(fullPair), sentimentThreshold);
					if (distance < min && distance >= 0) {
						min = distance;
//						potentialHost = fullPairInSet;
					}
				}

				if (min >= 0 && min < Constants.INVALID_DISTANCE) {
					fullPairSet.addCustomer(fullPair, min);
					fullPair.addPotentialHost(fullPairSet);
					
//					fullPairSet.increaseBenefit(fullPairToCSPair.get(potentialHost).calculateRootDistance());
					fullPairSet.increaseBenefit(fullPairToCSPair.get(fullPair).calculateRootDistance() - min);
				}
			}
		}
	}


	long initialCost = 0;
	// Init the root		
	root.getCustomerMap().clear();
	for (FullPair fullPair : fullPairs) {
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
	
//	initNumPotentialUsefulCoverWithThreshold(statisticalResult, fullPairSets);
}
	
	/**
	 * Original version from ILPSet
	 * init the distances between the sets, root and the concept sentiment pairs
	 * @return array of length [sentimentSets.size() + 1 ] * conceptSentimentPairs.size()
	 * <br> +1 for the root, result[0] is the distance array of the root
	 */
	private static Map<Integer, Map<Integer, Integer>> initDistances(
			float sentimentThreshold, 
			List<SentimentSet> sentimentSets, 
			List<ConceptSentimentPair> conceptSentimentPairs, 
			StatisticalResult statisticalResult) {
		
		Map<Integer, Map<Integer, Integer>> facilityToCustomerAndDistance =	new HashMap<>();
//		int[][] distances = new int[sentimentSets.size() + 1][conceptSentimentPairs.size() + 1];
		
		conceptSentimentPairs.add(0, new ConceptSentimentPair(Constants.ROOT_CUI, 0.0f));
		
		// The root
		facilityToCustomerAndDistance.put(0, new HashMap<>());
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
			facilityToCustomerAndDistance.put(sIndex, new HashMap<>());
			
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
	
	
	// Original method from Greedy
	private static void initPairs(
			List<ConceptSentimentPair> conceptSentimentPairs, 
			List<FullPair> fullPairs, 
			StatisticalResult statisticalResult) {
	
		FullPair root = new FullPair(Constants.ROOT_CUI);
		for (ConceptSentimentPair pair: conceptSentimentPairs) {
			// TODO - note that fullPair is identified using hashcode of only id -> id must be unique
			fullPairs.add(new FullPair(pair.getId() + "_s" + pair.getSentiment()));
		}		
		
		// Init the host
		for (FullPair pair : fullPairs) {
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
	static Map<Integer, Map<Integer, Integer>> initDistances(
			List<ConceptSentimentPair> conceptSentimentPairs, 
			float sentimentThreshold) {
		
		Map<Integer, Map<Integer, Integer>> facilityToCustomerAndDistance = new HashMap<>();
		
		// The root
		facilityToCustomerAndDistance.put(0, new HashMap<>());
		facilityToCustomerAndDistance.get(0).put(0, 0);
		for (int j = 1; j < conceptSentimentPairs.size(); ++j) {
			ConceptSentimentPair normalPair = conceptSentimentPairs.get(j);				
			facilityToCustomerAndDistance.get(0).put(j, normalPair.calculateRootDistance());
		}
		
		// Normal pairs
		for (int i = 1; i < conceptSentimentPairs.size(); ++i) {
			ConceptSentimentPair pair1 = conceptSentimentPairs.get(i);
			if (!facilityToCustomerAndDistance.containsKey(i))
				facilityToCustomerAndDistance.put(i, new HashMap<>());
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
							facilityToCustomerAndDistance.put(j, new HashMap<>());
						
						facilityToCustomerAndDistance.get(j).put(i, -distance);
					} else if (distance == 0) {
						if (!facilityToCustomerAndDistance.containsKey(j))
							facilityToCustomerAndDistance.put(j, new HashMap<>());
						
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
		Map<String, Map<String, Integer>> deweyToAncestorAndDistance = new HashMap<>();
		
		
		Map<Integer, Set<String>> levelToDeweys = new HashMap<>();
		for (String dewey : deweys) {
			int level = dewey.split("\\.").length;
			if (!levelToDeweys.containsKey(level))
				levelToDeweys.put(level, new HashSet<>());
			levelToDeweys.get(level).add(dewey);
		}
		
		int maxLevel = 1;
		for (int level : levelToDeweys.keySet())
			if (maxLevel < level)
				maxLevel = level;
		
		// dewey is the ancestor of itself
		for (String dewey : deweys) {
			deweyToAncestorAndDistance.put(dewey, new HashMap<>());
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
						Map<String, Integer> ancestorToDistance = new HashMap<>(deweyToAncestorAndDistance.get(ancestor));
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
		Map<String, Set<ConceptSentimentPair>> deweyToPairs = new HashMap<>();
		
		for (ConceptSentimentPair pair : conceptSentimentPairs) {
			for (String dewey : pair.getDeweys()) {
				if (!deweyToPairs.containsKey(dewey))
					deweyToPairs.put(dewey, new HashSet<>());
				
				deweyToPairs.get(dewey).add(pair);
			}
		}
		
		return deweyToPairs;
	}
	
	public static void main(String[] args) {
		//testInitDistances();
		//testInitPairs();
		//testSetInitFiniteDistances();
		//testInitFullPairSets ();
		//testScalability(20);
		numAncestorAverage();
	}
	
	private static void numAncestorAverage() {
		String inputFilePath = "src/edu/ucr/cs/dblab/nle020/ontology/snomed_deweys.txt";
		
		Map<String, Set<String>> conceptToDeweys = new HashMap<String, Set<String>>();
	
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(inputFilePath))) {
			String line = null;
			while ((line = reader.readLine()) != null) {
				String[] columns = line.split("\t");
				String concept = columns[0];
				String[] deweys = columns[1].split(",");
				
				conceptToDeweys.put(concept, new HashSet<>(Arrays.asList(deweys)));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		Map<String, Set<String>> deweyToConcepts = new HashMap<>();
		for (String cui : conceptToDeweys.keySet()) {
			for (String dewey : conceptToDeweys.get(cui)) {
				if (!deweyToConcepts.containsKey(dewey))
					deweyToConcepts.put(dewey, new HashSet<>());
				deweyToConcepts.get(dewey).add(cui);
			}
		}
		
		Map<String, Set<String>> conceptToAncestorConcepts = findAncestorConcepts(conceptToDeweys, deweyToConcepts);
		
		Map<Integer, Integer> numAncestorAverageToCount = new HashMap<>();
		int sum = 0;
		int count = 0;
		for (String concept : conceptToAncestorConcepts.keySet()) {
			Integer numAncestors = conceptToAncestorConcepts.get(concept).size();			
			if (!numAncestorAverageToCount.containsKey(numAncestors))
				numAncestorAverageToCount.put(numAncestors, 1);
			else
				numAncestorAverageToCount.put(numAncestors, numAncestorAverageToCount.get(numAncestors) + 1);
			
			sum += numAncestors;
			++count;
		}
		outputIntegerToNumberMapToCsv(numAncestorAverageToCount, 
				"src/main/resources/num_ancestor_average_to_count_whole_ontology.csv", 
				"# ancestor", "count");
		System.out.println("The average number of ancestor in the whole ontology is: " + (double) sum / (double) count);
		System.out.println("There are " + conceptToDeweys.keySet().size() + " concepts, " 
					+ deweyToConcepts.keySet().size() + " deweys id");
	}

    @SuppressWarnings("unused")
	private static void testScalability(int numTrials) {
		Map<String, List<ConceptSentimentPair>> docToConceptSentimentPairs =
				TopPairsProgram.importDocToConceptSentimentPairs(
						TopPairsProgram.DOC_TO_REVIEWS_PATH, 
						TopPairsProgram.RANDOMIZE_DOCS);
		
		Map<Integer, List<Double>> numAncestorsToTimes = new HashMap<>();
		
		Map<String, Double> docIdToRunningTime = new HashMap<>();
		Map<String, Integer> docIdToNumAncestors = new HashMap<>();
		
		Map<Integer, Integer> numAncestorAverageToCount = new HashMap<>();
		
		for (int trial = 0; trial < numTrials; trial++) {
			for (String docId : docToConceptSentimentPairs.keySet()) {
				List<ConceptSentimentPair> conceptSentimentPairs = docToConceptSentimentPairs.get(docId);			
				List<ConceptSentimentPair> pairs = new ArrayList<>();
				ConceptSentimentPair root = new ConceptSentimentPair(Constants.ROOT_CUI, 0.0f);
				root.addDewey(SnomedGraphBuilder.ROOT_DEWEY);
				pairs.add(root);
				pairs.addAll(conceptSentimentPairs);											
				
				Double runningTime = 1e10;
				long startTime = System.nanoTime();
				initFiniteDistancesFromPairIndexToPairIndex2(pairs, 0.3f);
				runningTime = (double)(System.nanoTime() - startTime) / (double) 1e6;
				
				if (!docIdToRunningTime.containsKey(docId) 
						|| (docIdToRunningTime.get(docId) > runningTime))
					docIdToRunningTime.put(docId, runningTime);										
			}
		}
		
		// numAncestors information
		for (String docId : docToConceptSentimentPairs.keySet()) {
			List<ConceptSentimentPair> conceptSentimentPairs = docToConceptSentimentPairs.get(docId);			
			List<ConceptSentimentPair> pairs = new ArrayList<>();
			ConceptSentimentPair root = new ConceptSentimentPair(Constants.ROOT_CUI, 0.0f);
			root.addDewey(SnomedGraphBuilder.ROOT_DEWEY);
			pairs.add(root);
			pairs.addAll(conceptSentimentPairs);	
			
			int numAncestors = 0;
			Map<String, Set<ConceptSentimentPair>> conceptToPairs = new HashMap<>();
			Map<String, Set<String>> conceptToDeweys = new HashMap<>();
			mapFromConceptToPairsAndDeweys(
					conceptSentimentPairs, 
					conceptToPairs, conceptToDeweys);

			Map<String, Set<String>> deweyToConcepts = new HashMap<>();
			for (String cui : conceptToDeweys.keySet()) {
				for (String dewey : conceptToDeweys.get(cui)) {
					if (!deweyToConcepts.containsKey(dewey))
						deweyToConcepts.put(dewey, new HashSet<>());
					deweyToConcepts.get(dewey).add(cui);
				}
			}
				
			Map<String, Set<String>> conceptToAncestorConcepts = findAncestorConcepts(conceptToDeweys, deweyToConcepts);
			
			for (int successorIndex = 0; successorIndex < conceptSentimentPairs.size(); ++successorIndex) {		
				ConceptSentimentPair successorPair = conceptSentimentPairs.get(successorIndex);			 
				String concept = successorPair.getCui();
				
				Set<String> ancestorConcepts = conceptToAncestorConcepts.get(concept);
				if (ancestorConcepts == null) continue;
				for (String ancestorConcept : ancestorConcepts) {
					numAncestors += conceptToPairs.get(ancestorConcept).size();
				}
			}	
			numAncestors += pairs.size();			
			if (!numAncestorsToTimes.containsKey(numAncestors))
				numAncestorsToTimes.put(numAncestors, new ArrayList<>());
			docIdToNumAncestors.put(docId, numAncestors);
			
			int numAncestorAverage = numAncestors / pairs.size();
			if (numAncestors % pairs.size() > pairs.size()/2)
				numAncestorAverage++;
			if (!numAncestorAverageToCount.containsKey(numAncestorAverage))
				numAncestorAverageToCount.put(numAncestorAverage, 1);
			else
				numAncestorAverageToCount.put(numAncestorAverage, numAncestorAverageToCount.get(numAncestorAverage) + 1);
		}
		
		for (String docId : docIdToNumAncestors.keySet()) {
			numAncestorsToTimes.get(docIdToNumAncestors.get(docId)).add(docIdToRunningTime.get(docId));
		}		
		Map<Integer, Double> numAncestorsToTimeAverage = new HashMap<>();
		for (Integer numAncestors : numAncestorsToTimes.keySet()) {
			numAncestorsToTimeAverage.put(
					numAncestors, 
					numAncestorsToTimes.get(numAncestors).stream().collect(Collectors.averagingDouble(time -> time)));
		}
		outputIntegerToNumberMapToCsv(numAncestorsToTimeAverage, 
				"src/main/resources/num_ancestor_to_time_average.csv", 
				"# ancestor", "time average");
		
		outputIntegerToNumberMapToCsv(numAncestorAverageToCount, 
				"src/main/resources/num_ancestor_average_to_count.csv", 
				"# ancestor average", "count");
		
		System.out.println();
	}

	private static void outputIntegerToNumberMapToCsv(
			Map<Integer, ? extends Number> keyToValue,
			String outputPath,
			String... titles) {
		
		List<Integer> sortedKeys = new ArrayList<Integer>(keyToValue.keySet());
		Collections.sort(sortedKeys);
		
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath), 
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			
			if (titles.length > 0) {
				for (int i = 0; i < titles.length; ++i)
					writer.write(titles[i] + ",");
				writer.newLine();
			}
			for (Integer key : sortedKeys) {
				writer.write(key + ", " + keyToValue.get(key));
				writer.newLine();
				writer.flush();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

    @SuppressWarnings("unused")
	private static Integer getNumDeweys(
			List<ConceptSentimentPair> conceptSentimentPairs) {
		Set<String> deweys = new HashSet<>();
		
		for (ConceptSentimentPair pair : conceptSentimentPairs)
			deweys.addAll(pair.getDeweys());
		
		return deweys.size();
	}

    @SuppressWarnings("unused")
	private static void testSetInitFiniteDistances () {
		Map<String, List<SentimentSet>> docToSentimentSets =
				TopPairsProgram.importDocToSentimentReviews(
						TopPairsProgram.DOC_TO_REVIEWS_PATH, 
						TopPairsProgram.RANDOMIZE_DOCS,
                        NUM_DOCTORS_TO_EXPERIMENT);
		for (String docId : docToSentimentSets.keySet()) {
			List<SentimentSet> sentimentSets = docToSentimentSets.get(docId);
			StatisticalResult statisticalResult = new StatisticalResult(docId, 10, threshold);		
			
			List<ConceptSentimentPair> pairs = new ArrayList<>();
			for (SentimentSet set : sentimentSets) {
				if (set.getPairs().size() > 0) {
					for (ConceptSentimentPair pair : set.getPairs()) {
						if (!pairs.contains(pair))
							pairs.add(pair);
					}				
				}
			}
			
			
			Map<Integer, Map<Integer, Integer>> setIndexToPairIndexAndDistanceNew = 
					initFiniteDistancesFromSetIndexToPairIndex(threshold, sentimentSets, pairs, statisticalResult);
			pairs.remove(0);
			Map<Integer, Map<Integer, Integer>> setIndexToPairIndexAndDistanceOld = 
					initDistances(threshold, sentimentSets, pairs, statisticalResult);
			
			for (Integer setIndex : setIndexToPairIndexAndDistanceNew.keySet()) {				
				for (Integer pairIndex : setIndexToPairIndexAndDistanceNew.get(setIndex).keySet()) {
					if (setIndexToPairIndexAndDistanceNew.get(setIndex).get(pairIndex) 
							!= setIndexToPairIndexAndDistanceOld.get(setIndex).get(pairIndex))
						System.err.println("Err!!!!!!!!!!!");
				}
			}
		}
	}

	@SuppressWarnings("unused")
	private static void testInitFullPairSets () {
		Map<String, List<SentimentSet>> docToSentimentSets =
				TopPairsProgram.importDocToSentimentReviews(
						TopPairsProgram.DOC_TO_REVIEWS_PATH, 
						TopPairsProgram.RANDOMIZE_DOCS,
                        NUM_DOCTORS_TO_EXPERIMENT);
		for (String docId : docToSentimentSets.keySet()) {
			List<SentimentSet> sentimentSetsOld = docToSentimentSets.get(docId);
			List<SentimentSet> sentimentSetsNew = new ArrayList<>(sentimentSetsOld);
			
			StatisticalResult statisticalResultOld = new StatisticalResult(docId, 10, threshold);		
			StatisticalResult statisticalResultNew = new StatisticalResult(docId, 10, threshold);	
			
			List<FullPair> fullPairSetsOld = new ArrayList<>();
			initPairs(threshold, sentimentSetsOld, fullPairSetsOld, statisticalResultOld);
			
			List<FullPair> fullPairSetsNew = new ArrayList<>();
			initFullPairs(threshold, sentimentSetsNew, fullPairSetsNew, statisticalResultNew);
							
			if (statisticalResultNew.getInitialCost() != statisticalResultOld.getInitialCost())
				System.err.println("Err!!!!!!!!!!!");
			for (int i = 0; i < fullPairSetsNew.size(); ++i) {
				FullPair fullPairSetNew = fullPairSetsNew.get(i);
				FullPair fullPairSetOld = fullPairSetsOld.get(i);
				for (FullPair customer : fullPairSetNew.getCustomerMap().keySet()) {
					if (fullPairSetNew.getCustomerMap().get(customer) != fullPairSetOld.getCustomerMap().get(customer))
						System.err.println("Err!!!!!!!!!!!");
				}
			}
		}
	}

	@SuppressWarnings("unused")
	private static void testInitPairs() {
		Map<String, List<ConceptSentimentPair>> docToConceptSentimentPairs =
				TopPairsProgram.importDocToConceptSentimentPairs(
						TopPairsProgram.DOC_TO_REVIEWS_PATH, 
						TopPairsProgram.RANDOMIZE_DOCS);
		
		for (String docId : docToConceptSentimentPairs.keySet()) {
			List<ConceptSentimentPair> conceptSentimentPairs = docToConceptSentimentPairs.get(docId);
					
			StatisticalResult statisticalResultNew = new StatisticalResult(docId, 10, threshold);		
			List<FullPair> pairsNew = new ArrayList<>();
			
	//		long startTime = System.currentTimeMillis();
			initFullPairs(conceptSentimentPairs, pairsNew, statisticalResultNew);
	//		Utils.printRunningTime(startTime, "New init");
	
			StatisticalResult statisticalResult = new StatisticalResult(docId, 10, threshold);		
			List<FullPair> pairs = new ArrayList<>();
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
	}
	
	@SuppressWarnings("unused")
	private static void testInitDistances() {
		Map<String, List<ConceptSentimentPair>> docToConceptSentimentPairs =
				TopPairsProgram.importDocToConceptSentimentPairs(
						TopPairsProgram.DOC_TO_REVIEWS_PATH, 
						TopPairsProgram.RANDOMIZE_DOCS);
		
		for (String docId : docToConceptSentimentPairs.keySet()) {
			List<ConceptSentimentPair> conceptSentimentPairs = docToConceptSentimentPairs.get(docId);
			
			List<ConceptSentimentPair> pairs = new ArrayList<>();
			ConceptSentimentPair root = new ConceptSentimentPair(Constants.ROOT_CUI, 0.0f);
			root.addDewey(SnomedGraphBuilder.ROOT_DEWEY);
			pairs.add(root);
			pairs.addAll(conceptSentimentPairs);
			
//			long startTime = System.currentTimeMillis();
			Map<Integer, Map<Integer, Integer>> facilityToCustomerAndDistanceNew = initFiniteDistancesFromPairIndexToPairIndex2(pairs, 0.3f);
//			Utils.printRunningTime(startTime, "New init");
	
//			startTime = System.currentTimeMillis();
			Map<Integer, Map<Integer, Integer>> facilityToCustomerAndDistance = initDistances(pairs, 0.3f);
//			Utils.printRunningTime(startTime, "Old init");
			
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
}
