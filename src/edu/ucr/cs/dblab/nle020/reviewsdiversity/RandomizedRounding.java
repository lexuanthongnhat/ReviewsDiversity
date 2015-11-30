package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants.LPMethod;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.ILP.StatisticalResultAndTopKByOriginalOrder;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.utils.Utils;
import gurobi.GRB;
import gurobi.GRBEnv;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBModel;
import gurobi.GRBVar;

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
			int[][] distances = ILP.initDistances(pairs, threshold);
			StatisticalResultAndTopKByOriginalOrder statisticalResultAndTopKByOriginalOrder = 
					doRandomizedRounding(distances, statisticalResult, method);	
			
			statisticalResult = statisticalResultAndTopKByOriginalOrder.getStatisticalResult();
			for (Integer order : statisticalResultAndTopKByOriginalOrder.getTopKByOriginalOrders()) {
				topKPairs.add(conceptSentimentPairs.get(order));
			}
		}		
		
		docToStatisticalResult.put(docId, statisticalResult);
		docToTopKPairsResult.put(docId, topKPairs);
		double runningTime = (double) (System.nanoTime() - startTime) / Constants.TIME_MS_TO_NS;
		runningTime = Utils.rounding(runningTime, Constants.NUM_DIGITS_IN_TIME);
		gatherFinalResult(runningTime, pairs.size(), statisticalResult);
	}
		
	protected StatisticalResultAndTopKByOriginalOrder doRandomizedRounding(int[][] distances, StatisticalResult statisticalResult,
			Constants.LPMethod method){
		
		int numFacilities = distances.length;
		int numCustomers = distances[0].length;
		
		double[] facilityOpen = new double[numFacilities];
		double[][] facilityConnect = new double[numFacilities][numCustomers];
		boolean integralModel = false;
		ILP.executeModel(distances, k, method, integralModel, facilityOpen, facilityConnect, statisticalResult);
						
		boolean needRandomizedRounding = false;
		for (int f = 0; f < numFacilities; ++f) {
			if (facilityOpen[f] > 0 && facilityOpen[f] < 1) { 
				needRandomizedRounding = true;
				break;
			}
			
			for (int c = 0; c < numCustomers; ++c) {
				if (distances[f][c] != Constants.INVALID_DISTANCE) {
					if (facilityConnect[f][c] > 0 && facilityConnect[f][c] < 1) {
						needRandomizedRounding = true;
						break;
					}
				}
			}
			if (needRandomizedRounding)
				break;
		}				

		if (needRandomizedRounding) {
		//	outputToFileForDebug(distances, facilityOpen, facilityConnect, result, DESKTOP_FOLDER + "rr_debug.txt");
			roundingRandomly(distances, facilityOpen, facilityConnect, statisticalResult);
		}					
					
		// Get top K by original order
		List<Integer> topKByOriginalOrders = new ArrayList<Integer>();
		for (int f = 1; f < numFacilities; ++f) {			// Start from "1" because the first is the root
			if (facilityOpen[f] == 1.0) {
				topKByOriginalOrders.add(f - 1);
			}
		}
					
		return new StatisticalResultAndTopKByOriginalOrder(statisticalResult, topKByOriginalOrders);
	}
	
	// Output: update facilityOpen, facilityConnect, statisticalResult
	private void roundingRandomly(int[][] distances, double[] facilityOpen, double[][] facilityConnect,
			StatisticalResult statisticalResult) {
		System.out.println("Doing RR");
		int numFacilities = facilityOpen.length;
		int numCustomers = facilityConnect[0].length;
		
		double[] originalOpen = facilityOpen.clone();
		double[][] originalConnect = new double[numFacilities][];
		for (int f = 0; f < numFacilities; ++f)
			originalConnect[f] = facilityConnect[f].clone();
		
		for (int f = 0; f < numFacilities; ++f) {
			facilityOpen[f] = 0;
			for (int c = 0; c < numCustomers; ++c) {
				facilityConnect[f][c] = 0;
			}
		}
		facilityOpen[0] = 1;
		
		
		/*List<Unit> customers = new ArrayList<Unit>(); 
		for (int c = 0; c < numCustomers; ++c) {
			Unit customer = new Unit(c);
			for (int f = 0; f < numFacilities; ++f) {
				if (distances[f][c] != Constants.INVALID_DISTANCE)
					customer.addAncestor(new Unit(f, distances[f][c]));
			}
			customers.add(customer);
		}*/
		
		double facilityCost = 0.0f;
		double assignmentCost = 0.0f;

		// sampling once per iteration
		while (facilityCost < k) {			
			for (int f = 1; f < numFacilities; ++f) {
				// Rounding with probability x(f)/|x|				
				if (rollTheDice(originalOpen[f] / k)) {	
					if (facilityOpen[f] == 0) {
						facilityOpen[f] = 1;
						facilityCost++;
						break;
					}
				}			
			}								
		}				
					
/*		// assign customer to the closest selected facility using Unit Data Structure
		Set<Integer> unassignedCustomers = new HashSet<Integer>();
		for (Unit customer : customers) {
			boolean isAssigned = false;
			while (!customer.getAncestors().isEmpty()) {
				Unit facility = customer.getAncestors().poll();
				if (facilityOpen[facility.id] == 1) {
					facilityConnect[facility.id][customer.id] = 1;
					assignmentCost += distances[facility.id][customer.id];
					isAssigned = true;
					break;
				}
			}
			
			if (!isAssigned)
				unassignedCustomers.add(customer.id);
		}		
		
		// Assign the remaining customers to the root
		if (unassignedCustomers.size() > 0) {
			for (Integer c : unassignedCustomers) {
				facilityConnect[0][c] = 1.0f;
				assignmentCost += distances[0][c];									
			}
		}*/
		
		// Assign customers to the closest selected facility 
		for (int c = 0; c < numCustomers; ++c) {
			int facility = 0;
			int minD = distances[0][c];
			for (int f = 0; f < numFacilities; ++f) {
				if (distances[f][c] != Constants.INVALID_DISTANCE && facilityOpen[f] == 1 && distances[f][c] < minD) {
					facility = f;
					minD = distances[f][c];
				}
			}
			assignmentCost += minD;
			facilityConnect[facility][c] = 1;
		}		
	
				
		int numUncovered = 0;
		for (int c = 0; c < numCustomers; ++c) {
			if (facilityConnect[0][c] == 1)
				++numUncovered;			
		}
		
		statisticalResult.setFinalCost(assignmentCost);
		statisticalResult.setNumFacilities(k);
		statisticalResult.setNumUncovered(numUncovered);
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
