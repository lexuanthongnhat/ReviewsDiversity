package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants.LPMethod;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants.PartialTimeIndex;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.utils.Utils;
import gurobi.GRB;
import gurobi.GRBEnv;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBModel;
import gurobi.GRBVar;
/**
 * Integer Linear Programming Algorithm
 * @author Thong Nhat
 */
public class ILP {
	protected int k = 0;
	protected float threshold = 0.0f;

	protected static ConceptSentimentPair root = new ConceptSentimentPair(Constants.ROOT_CUI, 0.0f);
	
	protected ConcurrentMap<Integer, StatisticalResult> docToStatisticalResult = new ConcurrentHashMap<Integer, StatisticalResult>();
	private ConcurrentMap<Integer, List<ConceptSentimentPair>> docToTopKPairsResult = new ConcurrentHashMap<Integer, List<ConceptSentimentPair>>();
	
	// Used for ILPSetThreadImpl
	public ILP(int k, float threshold,
			ConcurrentMap<Integer, StatisticalResult> docToStatisticalResult) {
		super();
		this.k = k;
		this.threshold = threshold;
		this.docToStatisticalResult = docToStatisticalResult;
	}
	
	// Used for ILPThreadImpl
	public ILP(int k, float threshold,
			ConcurrentMap<Integer, StatisticalResult> docToStatisticalResult,
			ConcurrentMap<Integer, List<ConceptSentimentPair>> docToTopKPairsResult) {
		super();
		this.k = k;
		this.threshold = threshold;
		this.docToStatisticalResult = docToStatisticalResult;
		this.docToTopKPairsResult = docToTopKPairsResult;
	}

	/**
	 * Run Integer Linear Programming for a doctor's data set
	 * @param docId - doctor ID
	 * @param docToSentimentSets - list of sentiment units/nodes in K-medians
	 * @return Result's statistics
	 */
	protected void runILPPerDoc(int docId, List<ConceptSentimentPair> conceptSentimentPairs) {
		long startTime = System.nanoTime();
		
		StatisticalResult statisticalResult = new StatisticalResult(docId, k, threshold);
		
		List<ConceptSentimentPair> topKPairs = new ArrayList<ConceptSentimentPair>();
		statisticalResult.setNumPairs(conceptSentimentPairs.size());
		
		List<ConceptSentimentPair> pairs = new ArrayList<ConceptSentimentPair>();
		pairs.add(root);
		pairs.addAll(conceptSentimentPairs);
		
		if (pairs.size() <= k + 1) {
			topKPairs = pairs;
		} else {
//			Map<Integer, Map<Integer, Integer>> facilityToCustomerAndDistance = initDistances(pairs, threshold);
			Map<Integer, Map<Integer, Integer>> facilityToCustomerAndDistance = 
					FiniteDistanceInitializer.initFiniteDistancesFromPairIndexToPairIndex2(pairs, threshold);
/*			int temp = 0;
			for (int facility : facilityToCustomerAndDistance.keySet()) {
				temp += facilityToCustomerAndDistance.get(facility).size();
			}
			statisticalResult.setNumEdges(temp);*/
			
			statisticalResult.addPartialTime(
					PartialTimeIndex.SETUP,
					Utils.runningTimeInMs(startTime, Constants.NUM_DIGITS_IN_TIME));
			
			StatisticalResultAndTopKByOriginalOrder statisticalResultAndTopKByOriginalOrder = 
					doILP(facilityToCustomerAndDistance, statisticalResult, LPMethod.AUTOMATIC);
			
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
	
	protected StatisticalResultAndTopKByOriginalOrder doILP(
			Map<Integer, Map<Integer, Integer>> facilityToCustomerAndDistance,
			StatisticalResult statisticalResult,
			Constants.LPMethod method) {
		long startTime = System.nanoTime();
		
		Map<Integer, Double> openedFacilities = new HashMap<Integer, Double>();
		Map<Integer, Map<Integer, Double>> openedFacilityToCustomerAndConnection = new HashMap<Integer, Map<Integer, Double>>();
		boolean integralModel = true;
		executeModel(k, LPMethod.AUTOMATIC, integralModel, 
				facilityToCustomerAndDistance,
				openedFacilities,
				openedFacilityToCustomerAndConnection,
				statisticalResult);
		
		statisticalResult.addPartialTime(
				PartialTimeIndex.MAIN,
				Utils.runningTimeInMs(startTime, Constants.NUM_DIGITS_IN_TIME));	
//		System.out.println(Utils.runningTimeInMs(startTime, Constants.NUM_DIGITS_IN_TIME));
		// Get top K by original order
		List<Integer> topKByOriginalOrders = new ArrayList<Integer>();
		for (Integer f : openedFacilities.keySet()) {			// Start from "1" because the first is the root
			if (f > 0 && openedFacilities.get(f) == 1.0) {
				topKByOriginalOrders.add(f - 1);
			}
		}
			
		return new StatisticalResultAndTopKByOriginalOrder(statisticalResult, topKByOriginalOrders);
	}
	
	/**
	 * Building and executing ILP/LP model
	 * @param k - number of selected items
	 * @param method - input MLP solver
	 * @param integralModel - input to choose whether it's ILP or just LP 
	 * @param facilityToCustomerAndDistance - input the distances between facility-customer
	 * @param openedFacilities - output of facility opening indicator
	 * @param openedFacilityToCustomerAndConnection - output of facility-customer connection
	 * @param statisticalResult - output of objective
	 */
	public static void executeModel(
			int k, Constants.LPMethod method, boolean integralModel,
			Map<Integer, Map<Integer, Integer>> facilityToCustomerAndDistance,
			Map<Integer, Double> openedFacilities,
			Map<Integer, Map<Integer, Double>> openedFacilityToCustomerAndConnection,
			StatisticalResult statisticalResult) {
		
		try {
			int numFacilities = facilityToCustomerAndDistance.keySet().size();			// Including the root
			
			GRBEnv env = new GRBEnv();
			env.set(GRB.IntParam.OutputFlag, 0);
			env.set(GRB.IntParam.Method, method.method());
	//		env.set(GRB.IntParam.Threads, 0);
			
			GRBModel model = new GRBModel(env);
			model.set(GRB.StringAttr.ModelName, "Non-Metric Uncapacitated Facility");
			
			// Facility open indicator
			List<GRBVar> open = new ArrayList<GRBVar>();			
			if (integralModel) 
				for (int f = 0; f < numFacilities; ++f) {
					open.add(model.addVar(0, 1, 0, GRB.BINARY, "open" + f));
				}
			else
				for (int f = 0; f < numFacilities; ++f) {
					open.add(model.addVar(0, 1, 0, GRB.CONTINUOUS, "open" + f));
				}
			
			// Facility - Customer connecting indicator
			Map<Integer, Map<Integer, GRBVar>> connecting = new HashMap<Integer, Map<Integer, GRBVar>>();
			if (integralModel) {
				for (Integer f : facilityToCustomerAndDistance.keySet()) {					
					Map<Integer, Integer> customerToDistance = facilityToCustomerAndDistance.get(f);
					if (customerToDistance.size() > 0)
						connecting.put(f, new HashMap<Integer, GRBVar>());
					
					for (Integer c : customerToDistance.keySet()) {						
						connecting.get(f).put(
								c, 
								model.addVar(0, 1, customerToDistance.get(c), GRB.BINARY, "connecting" + f + "to" + c));
					}
				}
			} else {
				for (Integer f : facilityToCustomerAndDistance.keySet()) {					
					Map<Integer, Integer> customerToDistance = facilityToCustomerAndDistance.get(f);
					if (customerToDistance.size() > 0)
						connecting.put(f, new HashMap<Integer, GRBVar>());
					
					for (Integer c : customerToDistance.keySet()) {						
						connecting.get(f).put(
								c, 
								model.addVar(0, 1, customerToDistance.get(c), GRB.CONTINUOUS, "connecting" + f + "to" + c));
					}
				}
			}
			
			model.set(GRB.IntAttr.ModelSense, 1);		// Minimization
			model.update();
			
			// Constraint: open the root by default
			model.addConstr(open.get(0), GRB.EQUAL, 1.0, "defaultRoot");
			
			// Constraint: open k + 1 facilities, including the root
			GRBLinExpr kFacilities = new GRBLinExpr();
			for (int f = 0; f < numFacilities; ++f) {
				kFacilities.addTerm(1.0, open.get(f));
			}
			model.addConstr(kFacilities, GRB.EQUAL, k + 1, "kFacilities");
			
			/* 
			 * Constraint: connecting each customer to only one facility
			 * 				Sum_f x(f, c) = 1
			 * number = number of location/pairs
			 */			
			Map<Integer, Set<Integer>> customerToFacilities = new HashMap<Integer, Set<Integer>>();
			for (Integer f : facilityToCustomerAndDistance.keySet()) {
				Map<Integer, Integer> customerToDistance = facilityToCustomerAndDistance.get(f);				
				for (Integer c : customerToDistance.keySet()) {
					if (!customerToFacilities.containsKey(c))
						customerToFacilities.put(c, new HashSet<Integer>());
					customerToFacilities.get(c).add(f);
				}
			}
			GRBLinExpr connectingToOneFacility = new GRBLinExpr();
			for (Integer c : customerToFacilities.keySet()) {	
				connectingToOneFacility = new GRBLinExpr();
				for (Integer f : customerToFacilities.get(c)) {
					connectingToOneFacility.addTerm(1.0, connecting.get(f).get(c));
				}
				model.addConstr(connectingToOneFacility, GRB.EQUAL, 1.0, "connectingToOneFacility" + c);
			}
			
			/*
			 * Constraint: only connect customer to opened facility
			 * 				x(f, c) <= x(f)
			 * number = number of edges
			 */
			for (Integer f : facilityToCustomerAndDistance.keySet()) {
				for (Integer c : facilityToCustomerAndDistance.get(f).keySet()) {	
					model.addConstr(connecting.get(f).get(c), GRB.LESS_EQUAL, open.get(f), "onlyToOpenedFacility_f" + f + "_c" + "c");
				}
			}						
					
			model.update();
			
			long startTime = System.nanoTime();
			// Optimize the model
			model.optimize();
			statisticalResult.addPartialTime(
					PartialTimeIndex.LP, 
					Utils.runningTimeInMs(startTime, Constants.NUM_DIGITS_IN_TIME));
						
			// Prepare some statistics, update result					
			statisticalResult.setFinalCost(model.get(GRB.DoubleAttr.ObjVal));
			if (integralModel)
				statisticalResult.setOptimalCost(model.get(GRB.DoubleAttr.ObjBound));
			
			
			for (int f = 0; f < numFacilities; ++f) {
				if (open.get(f).get(GRB.DoubleAttr.X) > 0) {
					openedFacilities.put(f, open.get(f).get(GRB.DoubleAttr.X));
					openedFacilityToCustomerAndConnection.put(f, new HashMap<Integer, Double>());
					for (Integer c : connecting.get(f).keySet()) {
						if (connecting.get(f).get(c).get(GRB.DoubleAttr.X) > 0) {
							openedFacilityToCustomerAndConnection.get(f).put(
									c, 
									connecting.get(f).get(c).get(GRB.DoubleAttr.X));
						}
					}
				}
			}
	
			/*if (statisticalResult.getNumPairs() + statisticalResult.getNumEdges() + 1 != model.get(GRB.IntAttr.NumVars)
					|| statisticalResult.getNumPairs() + statisticalResult.getNumEdges() + 3 != model.get(GRB.IntAttr.NumConstrs)) {
				System.out.println(statisticalResult);
				System.out.println("# variables: " + model.get(GRB.IntAttr.NumVars));		
				System.out.println("# linear constraints: " + model.get(GRB.IntAttr.NumConstrs));
				System.out.println("# nonzeros: " + model.get(GRB.IntAttr.NumNZs));
			}
			
			if (model.get(GRB.IntAttr.NumQConstrs) > 0 || model.get(GRB.IntAttr.NumSOS) > 0) {
				System.out.println("# quaratic constraints: " + model.get(GRB.IntAttr.NumQConstrs));
				System.out.println("# sos constraints: " + model.get(GRB.IntAttr.NumSOS));
			}*/
			
			model.dispose();
			env.dispose();
		} catch (GRBException e) {
		      System.out.println("Error code: " + e.getErrorCode() + ". " + e.getMessage());
		      e.printStackTrace();
		}
	}

	protected void gatherFinalResult(double runningTime, int datasetSize, StatisticalResult statisticalResult) {
		
		if (datasetSize <= k + 1) {
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
	
	
	
	public static class StatisticalResultAndTopKByOriginalOrder{
		StatisticalResult statisticalResult;
		List<Integer> topKByOriginalOrders;
		public StatisticalResult getStatisticalResult() {
			return statisticalResult;
		}
		public List<Integer> getTopKByOriginalOrders() {
			return topKByOriginalOrders;
		}
		public StatisticalResultAndTopKByOriginalOrder(
				StatisticalResult statisticalResult, List<Integer> topKByOriginalOrders) {
			super();
			this.statisticalResult = statisticalResult;
			this.topKByOriginalOrders = topKByOriginalOrders;
		}
		
		
	}

}
