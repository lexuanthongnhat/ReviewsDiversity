package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants.LPMethod;
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
			int[][] distances = initDistances(pairs, threshold);
			StatisticalResultAndTopKByOriginalOrder statisticalResultAndTopKByOriginalOrder = doILP(distances, statisticalResult, LPMethod.AUTOMATIC);
			
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
	
	protected StatisticalResultAndTopKByOriginalOrder doILP(int[][] distances, StatisticalResult statisticalResult,
			Constants.LPMethod method){
		
		int numFacilities = distances.length;
		int numCustomers = distances[0].length;
		
		double[] facilityOpen = new double[numFacilities];
		double[][] facilityConnect = new double[numFacilities][numCustomers];
		boolean integralModel = true;
		executeModel(distances, k, LPMethod.AUTOMATIC, integralModel, facilityOpen, facilityConnect, statisticalResult);
		
		
		// Get top K by original order
		List<Integer> topKByOriginalOrders = new ArrayList<Integer>();
		for (int f = 1; f < numFacilities; ++f) {			// Start from "1" because the first is the root
			if (facilityOpen[f] == 1.0) {
				topKByOriginalOrders.add(f - 1);
			}
		}
		
		return new StatisticalResultAndTopKByOriginalOrder(statisticalResult, topKByOriginalOrders);
	}
	
	/**
	 * Building and executing ILP/LP model
	 * @param distances - input the distances between facility-customer
	 * @param method - input MLP solver
	 * @param integralModel - input to choose whether it's ILP or just LP 
	 * @param facilityOpen - output of facility opening indicator
	 * @param facilityConnect - output of facility-customer connection
	 * @param statisticalResult - output of objective
	 */
	public static void executeModel(
			int[][]distances, int k, Constants.LPMethod method, boolean integralModel, 
			double[] facilityOpen,
			double[][] facilityConnect,
			StatisticalResult statisticalResult) {
		
		try {
			int numFacilities = distances.length;			// Including the root
			int numCustomers = distances[0].length;			
			
			GRBEnv env = new GRBEnv();
			env.set(GRB.IntParam.OutputFlag, 0);
			env.set(GRB.IntParam.Method, method.method());
			
			GRBModel model = new GRBModel(env);
			model.set(GRB.StringAttr.ModelName, "Non-Metric Uncapacitated Facility");
			
			// Facility open indicator
			GRBVar[] open = new GRBVar[numFacilities];
			
			if (integralModel) 
				for (int f = 0; f < numFacilities; ++f) {
					open[f] = model.addVar(0, 1, 0, GRB.BINARY, "open" + f);
				}
			else
				for (int f = 0; f < numFacilities; ++f) {
					open[f] = model.addVar(0, 1, 0, GRB.CONTINUOUS, "open" + f);
				}
			
			// Facility - Customer connecting indicator
			GRBVar[][] connecting = new GRBVar[numFacilities][numCustomers];
			if (integralModel)
				for (int f = 0; f < numFacilities; ++f) {
					for (int c = 0; c < numCustomers; ++c) {
						if (distances[f][c] != Constants.INVALID_DISTANCE)
							connecting[f][c] = model.addVar(0, 1, distances[f][c], GRB.BINARY, "connecting" + f + "to" + c);
					}
				}
			else
				for (int f = 0; f < numFacilities; ++f) {
					for (int c = 0; c < numCustomers; ++c) {
						if (distances[f][c] != Constants.INVALID_DISTANCE)
							connecting[f][c] = model.addVar(0, 1, distances[f][c], GRB.CONTINUOUS, "connecting" + f + "to" + c);
					}
				}
			
			model.set(GRB.IntAttr.ModelSense, 1);		// Minimization
			model.update();
			
			// Constraint: open the root by default
			model.addConstr(open[0], GRB.EQUAL, 1.0, "defaultRoot");
			
			// Constraint: open k + 1 facilities, including the root
			GRBLinExpr kFacilities = new GRBLinExpr();
			for (int f = 0; f < numFacilities; ++f) {
				kFacilities.addTerm(1.0, open[f]);
			}
			model.addConstr(kFacilities, GRB.EQUAL, k + 1, "kFacilities");
			
			/* 
			 * Constraint: connecting each customer to only one facility
			 * 				Sum_f x(f, c) = 1
			 */			
			GRBLinExpr connectingToOneFacility = new GRBLinExpr();
			for (int c = 0; c < numCustomers; ++c) {
				connectingToOneFacility = new GRBLinExpr();
				for (int f = 0; f < numFacilities; ++f) {
					if (distances[f][c] != Constants.INVALID_DISTANCE)
						connectingToOneFacility.addTerm(1.0, connecting[f][c]);
				}
				model.addConstr(connectingToOneFacility, GRB.EQUAL, 1.0, "connectingToOneFacility" + c);
			}
			
			/*
			 * Constraint: only connect customer to opened facility
			 * 				x(f, c) <= x(f)
			 */
			for (int f = 0; f < numFacilities; ++f) {
				for (int c = 0; c < numCustomers; ++c) {
					if (distances[f][c] != Constants.INVALID_DISTANCE)
						model.addConstr(connecting[f][c], GRB.LESS_EQUAL, open[f], "onlyToOpenedFacility_f" + f + "_c" + "c");
				}
			}						
			
			// Optimize the model
			model.optimize();			
			
			// Prepare some statistics, update result					
			statisticalResult.setFinalCost(model.get(GRB.DoubleAttr.ObjVal));
			if (integralModel)
				statisticalResult.setOptimalCost(model.get(GRB.DoubleAttr.ObjBound));
			
			for (int c = 1; c < numCustomers; ++c) {
				if (connecting[0][c].get(GRB.DoubleAttr.X) == 1.0) {
					statisticalResult.increaseNumUncovered();
				}
			}
			
			
			for (int f = 0; f < numFacilities; ++f) {
				facilityOpen[f] = open[f].get(GRB.DoubleAttr.X);
				for (int c = 0; c < numCustomers; ++c) {
					if (distances[f][c] != Constants.INVALID_DISTANCE) {
						facilityConnect[f][c] = connecting[f][c].get(GRB.DoubleAttr.X);
					}
				}
			}
			
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
	
	// TODO - this method is probably out-dated
	private void checkResult(int[][] distances, int[] facilityOpen, int[][] facilityConnect, StatisticalResult statisticalResult) {
		int numFacilities = facilityOpen.length;
		int numCustomers = facilityConnect[0].length;
		
		long verifyingCost = 0;	
		
		// Test 1
		for (int c = 0; c < numCustomers; ++c) {
			int numConnect = 0;
			for (int f = 0; f < numFacilities; ++f) {
				if (facilityConnect[f][c] == 1) {
					verifyingCost += distances[f][c];
					++numConnect;
				}					
			}
			
			if (numConnect != 1) {
				System.err.println("ILP TEST 1 - ERROR at customer " + c);
				for (int f = 0; f < numFacilities; ++f) {
					if (facilityConnect[f][c] == 1) {
						System.err.println("facility[" + f + "][" + c + "] = " + facilityConnect[f][c] 
								+ ", facilityOpen[" + f + "] = " + facilityOpen[f]);
					}					
				}	
			}
		}

		
		for (int f = 0; f < numFacilities; ++f) {
			if (facilityOpen[f] != 1) {

				for (int c = 0; c < numCustomers; ++c) {
					if (facilityConnect[f][c] == 1) {
						System.err.println("ILP TEST 1 - ERROR 2 at facility " + f + ", customer " + c);
					}					
				}
			}
		}
		
		System.out.println("Cost: " + statisticalResult.getFinalCost() + " - Verifying Cost: " + verifyingCost);
		if (verifyingCost != statisticalResult.getFinalCost())
			System.err.println("ILP Error at docID " + statisticalResult.getDocID());
		
		
		// Test 2
		verifyingCost = 0;
		for (int c = 0; c < numCustomers; ++c) {
			int min = Constants.INVALID_DISTANCE;
			for (int f = 0; f < numFacilities; ++f) {
				if (facilityOpen[f] == 1) {
					if (distances[f][c] < min)
						min = distances[f][c];
				}
			}			
			verifyingCost += min;
		}
		
		if (verifyingCost != statisticalResult.getFinalCost())
			System.err.println("ILP TEST 2 -  Error 2 at docID " + statisticalResult.getDocID() + 
					":\tCost: " + statisticalResult.getFinalCost() + " - Verifying Cost 2: " + verifyingCost);
	}
	

	protected static int[][] initDistances(List<ConceptSentimentPair> conceptSentimentPairs, float sentimentThreshold) {
		int[][] distances = new int[conceptSentimentPairs.size()][conceptSentimentPairs.size()];
		
		// The root
		distances[0][0] = 0;
		for (int j = 1; j < conceptSentimentPairs.size(); ++j) {
			ConceptSentimentPair normalPair = conceptSentimentPairs.get(j);				
				
			distances[0][j] = normalPair.calculateRootDistance();
			distances[j][0] = Constants.INVALID_DISTANCE;
		}
		
		// Normal pairs
		for (int i = 1; i < conceptSentimentPairs.size(); ++i) {
			ConceptSentimentPair pair1 = conceptSentimentPairs.get(i);
			
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
				
				
				if (distance == Constants.INVALID_DISTANCE) {
					distances[i][j] = Constants.INVALID_DISTANCE;
					distances[j][i] = Constants.INVALID_DISTANCE;
				} else if (distance > 0) {
					distances[i][j] = distance;
					distances[j][i] = Constants.INVALID_DISTANCE;
				} else if (distance < 0) {
					distances[i][j] = Constants.INVALID_DISTANCE;
					distances[j][i] = -distance;
				} else if (distance == 0) {
					distances[i][j] = 0;
					distances[j][i] = 0;
				}
			}
		}
		
		
		return distances;
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
