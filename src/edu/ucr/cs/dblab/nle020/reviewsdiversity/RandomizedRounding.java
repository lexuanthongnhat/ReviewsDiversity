package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants.PartialTimeIndex;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.ILP.StatisticalResultAndTopKByOriginalOrder;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.utils.Utils;

public class RandomizedRounding {
	protected int k = 0;
	protected float threshold = 0.0f;
	
	protected Constants.LPMethod method = Constants.MY_DEFAULT_LP_METHOD;	
	protected ConceptSentimentPair root = new ConceptSentimentPair(Constants.ROOT_CUI, 0.0f);
	
	protected ConcurrentMap<Integer, StatisticalResult> docToStatisticalResult = new ConcurrentHashMap<Integer, StatisticalResult>();
	private ConcurrentMap<Integer, List<ConceptSentimentPair>> docToTopKPairsResult = new ConcurrentHashMap<Integer, List<ConceptSentimentPair>>();
	
	// For RandomizedRoundingSetThreadImpl
	public RandomizedRounding(int k, float threshold,
			ConcurrentMap<Integer, StatisticalResult> docToStatisticalResult) {
		super();
		this.k = k;
		this.threshold = threshold;
		this.docToStatisticalResult = docToStatisticalResult;
	}
	
	// For RandomizedRoundingThreadImpl
	public RandomizedRounding(int k, float threshold,
			ConcurrentMap<Integer, StatisticalResult> docToStatisticalResult, 
			ConcurrentMap<Integer, List<ConceptSentimentPair>> docToTopKPairsResult,
			Constants.LPMethod method) {
		super();
		this.k = k;
		this.threshold = threshold;
		this.docToStatisticalResult = docToStatisticalResult;
		this.docToTopKPairsResult = docToTopKPairsResult;
		this.method = method;
	}

	/**
	 * Run Linear Programming for a doctor's data set then use Natural Randomized Rounding
	 * @param docId - doctor ID
	 * @param docToSentimentSets - list of sentiment units/nodes in K-medians
	 * @return Result's statistics
	 */
	protected void runRandomizedRoundingPerDoc(int docId, List<ConceptSentimentPair> conceptSentimentPairs) {
		long startTime = System.nanoTime();
		
		StatisticalResult statisticalResult = new StatisticalResult(docId, k, threshold);
		statisticalResult.setNumPairs(conceptSentimentPairs.size());
		List<ConceptSentimentPair> topKPairs = new ArrayList<ConceptSentimentPair>();
				
		List<ConceptSentimentPair> pairs = new ArrayList<ConceptSentimentPair>();		
		pairs.add(root);
		pairs.addAll(conceptSentimentPairs);
		
		if (pairs.size() <= k + 1) {
			topKPairs = pairs;
		} else {
			Map<Integer, Map<Integer, Integer>> facilityToCustomerAndDistance = ILP.initDistances(pairs, threshold);			
			statisticalResult.addPartialTime(
					PartialTimeIndex.SETUP,
					Utils.runningTimeInMs(startTime, Constants.NUM_DIGITS_IN_TIME));	
			
			StatisticalResultAndTopKByOriginalOrder statisticalResultAndTopKByOriginalOrder = 
					doRandomizedRounding(facilityToCustomerAndDistance, statisticalResult, method);	
			
			long startPartialTime = System.nanoTime();
			statisticalResult = statisticalResultAndTopKByOriginalOrder.getStatisticalResult();
			for (Integer order : statisticalResultAndTopKByOriginalOrder.getTopKByOriginalOrders()) {
				topKPairs.add(conceptSentimentPairs.get(order));
			}
			statisticalResult.addPartialTime(
					PartialTimeIndex.GET_TOPK,
					Utils.runningTimeInMs(startPartialTime, Constants.NUM_DIGITS_IN_TIME));	
		}		
		
		docToStatisticalResult.put(docId, statisticalResult);
		docToTopKPairsResult.put(docId, topKPairs);
		double runningTime = Utils.runningTimeInMs(startTime, Constants.NUM_DIGITS_IN_TIME);
		gatherFinalResult(runningTime, pairs.size(), statisticalResult);
	}
		
	protected StatisticalResultAndTopKByOriginalOrder doRandomizedRounding(
			Map<Integer, Map<Integer, Integer>> facilityToCustomerAndDistance, 
			StatisticalResult statisticalResult,
			Constants.LPMethod method){
		long startTime = System.nanoTime();		
		int numFacilities = facilityToCustomerAndDistance.keySet().size();
		
		Map<Integer, Double> openedFacilities = new HashMap<Integer, Double>();
		Map<Integer, Map<Integer, Double>> openedFacilityToCustomerAndConnection = new HashMap<Integer, Map<Integer, Double>>();
		boolean integralModel = false;
		ILP.executeModel(
				k, method, integralModel, 
				facilityToCustomerAndDistance, 
				openedFacilities, 
				openedFacilityToCustomerAndConnection, 
				statisticalResult);
		
		statisticalResult.addPartialTime(
				PartialTimeIndex.MAIN,
				Utils.runningTimeInMs(startTime, Constants.NUM_DIGITS_IN_TIME));	
						
		
		boolean needRandomizedRounding = false;
		for (Integer f : openedFacilities.keySet()) {
			if (openedFacilities.get(f) > 0 && openedFacilities.get(f) < 1) { 
				needRandomizedRounding = true;
				break;
			}
			
			if (!openedFacilityToCustomerAndConnection.keySet().contains(f))
				continue;
			
			for (Integer c : openedFacilityToCustomerAndConnection.get(f).keySet()) {				
				if (openedFacilityToCustomerAndConnection.get(f).get(c) > 0 && 
						openedFacilityToCustomerAndConnection.get(f).get(c) < 1) {
					needRandomizedRounding = true;
					break;
				}
				
			}
			if (needRandomizedRounding)
				break;
		}				

		if (needRandomizedRounding) {
		//	outputToFileForDebug(distances, facilityOpen, facilityConnect, result, DESKTOP_FOLDER + "rr_debug.txt");
			startTime = System.nanoTime();
			roundingRandomly(
					facilityToCustomerAndDistance, 
					openedFacilities, 
					openedFacilityToCustomerAndConnection,
					statisticalResult);
			
			statisticalResult.addPartialTime(
					PartialTimeIndex.RR,
					Utils.runningTimeInMs(startTime, Constants.NUM_DIGITS_IN_TIME));	
		}					
					
		// Get top K by original order
		List<Integer> topKByOriginalOrders = new ArrayList<Integer>();
		for (Integer f : openedFacilities.keySet()) {			// Start from "1" because the first is the root
			if (f > 0 && openedFacilities.get(f) == 1.0) {
				topKByOriginalOrders.add(f - 1);
			}
		}
					
		return new StatisticalResultAndTopKByOriginalOrder(statisticalResult, topKByOriginalOrders);
	}
	
	// Output: update facilityOpen, facilityConnect, statisticalResult
	private void roundingRandomly(			
			Map<Integer, Map<Integer, Integer>> facilityToCustomerAndDistance,
			Map<Integer, Double> openedFacilities,
			Map<Integer, Map<Integer, Double>> openedFacilityToCustomerAndConnection,
			StatisticalResult statisticalResult) {
		long startTime = System.currentTimeMillis();
		System.out.println("Starting Randomized Rounding ...");
		
		boolean withReplacement = false;
		sampleKItemsFaster(openedFacilities, withReplacement);
			
		// Assign customer to the closest opened facility
		Map<Integer, Integer> customerToMinDistance = new HashMap<Integer, Integer>();
		openedFacilityToCustomerAndConnection.clear();
		for (Integer facility : openedFacilities.keySet()) {
			for (Integer customer : facilityToCustomerAndDistance.get(facility).keySet()) {
				if (!customerToMinDistance.containsKey(customer)) {
					customerToMinDistance.put(customer, facilityToCustomerAndDistance.get(facility).get(customer));
					
					if (!openedFacilityToCustomerAndConnection.containsKey(facility))
						openedFacilityToCustomerAndConnection.put(facility, new HashMap<Integer, Double>());
					openedFacilityToCustomerAndConnection.get(facility).put(customer, 1.0);
				} else {			
					if (facilityToCustomerAndDistance.get(facility).get(customer) 
							< customerToMinDistance.get(customer)) {
						if (!openedFacilityToCustomerAndConnection.containsKey(facility))
							openedFacilityToCustomerAndConnection.put(facility, new HashMap<Integer, Double>());
						
						customerToMinDistance.put(customer,	facilityToCustomerAndDistance.get(facility).get(customer));
						openedFacilityToCustomerAndConnection.get(facility).put(customer, 1.0);
					}
				}
			}
		}
		
		// Assign customers to the closest selected facility 
		double assignmentCost = 0.0f;
		for (Integer c : customerToMinDistance.keySet()) {
			assignmentCost += customerToMinDistance.get(c);
		}		
		statisticalResult.setFinalCost(assignmentCost);
	
		Utils.printRunningTime(startTime, "Finished Randomized Rounding");
	}	
	
	private void sampleKItemsFaster(Map<Integer, Double> openedFacilities, boolean withReplacement) {
		int numCandidates = openedFacilities.size();		
		double[] candidates = new double[numCandidates - 1];
		Map<Integer, Integer> indexToFacility = new HashMap<Integer, Integer>();
		int index = 0;
		for (Integer f : openedFacilities.keySet()) {
			if (f > 0) {
				indexToFacility.put(index, f);
				candidates[index] = openedFacilities.get(f);
				++index;
			}
		}
		openedFacilities.clear();
		openedFacilities.put(0, 1.0);
		
		Set<Integer> selected = new HashSet<Integer>();		
		if (withReplacement)
			selected = sampleKItemsWithReplacement(candidates);
		else
			selected = sampleKItemsWithoutReplacement(candidates);
		
		for (Integer selectedIndex : selected)
			openedFacilities.put(indexToFacility.get(selectedIndex), 1.0);
		
//		System.out.println();
	}
	
	private Set<Integer> sampleKItemsWithReplacement(double[] candidates) {
		int numCandidates = candidates.length;
		double[] originalCandidates = candidates.clone();		
		
		Set<Integer> selected = new HashSet<Integer>();		 
		double facilityCost = 0;
		
		double[] probs = new double[numCandidates + 1];
		probs[0] = 0;		
		double tempSum = 0;
		for (int f = 0; f < numCandidates; ++f) {
			tempSum += originalCandidates[f];
			probs[f + 1] = tempSum / (double) k;
		}
		
		while (facilityCost < k) {	
			// sampling once per iteration					
			double r = Math.random();
			for (int f = 0; f < numCandidates; ++f) {
				if (r >= probs[f] && r <= probs[f + 1]) {					
					if (!selected.contains(f)) {
						selected.add(f);
						facilityCost++;				
					}
					break;
				}
			}					
		}	
		return selected;
	}
	
	private Set<Integer> sampleKItemsWithoutReplacement(double[] candidates) {
		int numCandidates = candidates.length;
		double[] originalCandidates = candidates.clone();		
		
		Set<Integer> selected = new HashSet<Integer>();
		Map<Integer, Integer> swap = new HashMap<Integer, Integer>(); 
		double facilityCost = 0;
		int numLeft = numCandidates;
		while (facilityCost < k) {
			double[] probs = new double[numLeft + 1];
			probs[0] = 0;		
			double tempSum = 0;
			double leftSum = 0;
			for (int f = 0; f < numLeft; ++f) 
				leftSum += originalCandidates[f];
			for (int f = 0; f < numLeft; ++f) {
				tempSum += originalCandidates[f];
				probs[f + 1] = tempSum / leftSum;
			}
			
			// sampling once per iteration					
			double r = Math.random();
			for (int f = 0; f < numLeft; ++f) {
				if (r >= probs[f] && r <= probs[f + 1]) {					
					if (!swap.containsKey(f))
						selected.add(f);
					else
						selected.add(swap.get(f));
					originalCandidates[f] = originalCandidates[numLeft - 1];
					swap.put(f, numLeft - 1);
					
					numLeft--;
					facilityCost++;						
					break;
				}
			}					
		}	
		return selected;
	}
	
	
	
/*	private static class Unit {
		int id = 0;
		int distance = 0;
		PriorityQueue<Unit> ancestors = new PriorityQueue<Unit>(new Comparator<Unit>(){
			@Override
			public int compare(Unit o1, Unit o2) {			
				return o1.distance - o2.distance;
			}
		});
		
		public Unit(int id) {
			super();
			this.id = id;
		}
			
		public Unit(int id, int distance) {
			super();
			this.id = id;
			this.distance = distance;
		}

		public PriorityQueue<Unit> getAncestors() {
			return ancestors;
		}
				
		public void addAncestor(Unit ancestor) {
			ancestors.add(ancestor);
		}
	}
*/	
	
	@SuppressWarnings("unused")
	private boolean rollTheDice(double probability) {
		if (Math.random() < probability)
			return true;
		else 
			return false;
	}
	
	protected void gatherFinalResult(double runningTime, int datasetSize, StatisticalResult statisticalResult) {
		
		if (datasetSize <= k + 1) {
			statisticalResult.setNumFacilities(datasetSize);
			statisticalResult.setRunningTime(0);
			statisticalResult.setFinalCost(0);
			statisticalResult.setOptimalCost(0);
			statisticalResult.setNumUncovered(0);
			statisticalResult.setNumUsefulCover(0);
		} else {
			statisticalResult.setRunningTime(runningTime);
		}
		docToStatisticalResult.put(statisticalResult.getDocID(), statisticalResult);
	}
	
	@SuppressWarnings("unused")
	private void outputToFileForDebug(int[][] distances, 
			double[] facilityOpen, double[][] facilityConnect, 
			StatisticalResult statisticalResult, String outputPath) {
		
		int numFacilities = facilityOpen.length;
		int numCustomers = facilityConnect[0].length;
		
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath), 
				StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
			
			System.out.println("\nDoctorID " + statisticalResult.getDocID() + " Open facilities: ");
			
			writer.append("\nDoctorID " + statisticalResult.getDocID() + " Open facilities: ");
			writer.newLine();
			
			if (facilityOpen[0] < 1) {
				System.err.println("Root is not choosen completely, facilityOpen[0] = " + facilityOpen[0]);
				
				writer.append("Root is not choosen completely, facilityOpen[0] = " + facilityOpen[0]);
				writer.newLine();
			}
			for (int f = 0; f < numFacilities; ++f) {
				if (facilityOpen[f] > 0) {
					System.out.println("facilityOpen[" + f + "] = " + facilityOpen[f]);
					
					writer.append("facilityOpen[" + f + "] = " + facilityOpen[f]);
					writer.newLine();
				}					
			}
			
			System.out.println("Connecting facilities to customers: ");
			
			writer.append("Connecting facilities to customers: ");
			writer.newLine();
			for (int c = 0; c < numCustomers; ++c) {
				for (int f = 0; f < numFacilities; ++f) {			// Start from "1" because the first is the root

					if (facilityConnect[f][c] > 0 && facilityConnect[f][c] < 1) {
						System.out.println("Customer " + c + " by " + f +  
								"\tfacilityConnect[" + f + "][" + c + "] " + facilityConnect[f][c] +
								"\t distance " + distances[f][c]);
						
						writer.append("Customer " + c + " by " + f +  
								"\tfacilityConnect[" + f + "][" + c + "] " + facilityConnect[f][c] +
								"\t distance " + distances[f][c]);
						writer.newLine();
					}
				}
			}	
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
