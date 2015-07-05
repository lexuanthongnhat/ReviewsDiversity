package edu.ucr.cs.dblab.nle020.reviewsdiversity;

/**
 * Treat it as non-metric uncapacitated facility location problem
 * Use Integer Linear Programming (Gurubi) to solve
 * @author Nhat XT Le
 *
 */

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.ontology.SnomedGraphBuilder;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.utilities.Utils;
import gurobi.*;

public class IntegerLinearProgramming implements Runnable {

	private int k = 0;
	private float threshold = 0.0f;

	private int docID;
	private List<ConceptSentimentPair> pairs = new ArrayList<ConceptSentimentPair>();
	private ConceptSentimentPair root = new ConceptSentimentPair("ROOT", 0.0f);
	int[][] distances;
	
	private List<ConceptSentimentPair> topK = new ArrayList<ConceptSentimentPair>();	
	long cost = 0;
	
	long optimalCost = 0;
	int uncoveredPairs = 0;
	int numUsefulCover = 0;
	
	private static ConcurrentMap<Integer, TopPairsResult> docToTopPairsResult = new ConcurrentHashMap<Integer, TopPairsResult>();
	TopPairsResult result;
	
	private int[] facilityOpen;
	private int[][] facilityConnect;
	
	public IntegerLinearProgramming(int k, float threshold, int docID,
			List<ConceptSentimentPair> pairs,
			ConcurrentMap<Integer, TopPairsResult> docToTopPairsResult) {
		super();
		this.k = k;
		this.threshold = threshold;
		this.docID = docID;		
		this.docToTopPairsResult = docToTopPairsResult;
		
		// Init result
		result = new TopPairsResult(docID, k, threshold);
		result.setNumPairs(pairs.size());
		
		root.addDewey(SnomedGraphBuilder.ROOT_DEWEY);
		this.pairs.add(root);
		this.pairs.addAll(pairs);
	}

	@Override
	public void run() {
		long startTime = System.currentTimeMillis();
		Utils.printTotalHeapSize("ILP Before docID " + docID);
		
		if (pairs.size() <= k + 1) {
			topK = pairs;
		} else {
			doILP();	
			
			if (Constants.DEBUG_MODE)
				checkResult();
		}		
		
		gatherFinalResult(System.currentTimeMillis() - startTime);
		
		Utils.printTotalHeapSize("ILP After docID " + docID);
		Utils.printRunningTime(startTime, "ILP finished docID " + docID);
	}	
	
	private void gatherFinalResult(long runningTime) {
		
		if (pairs.size() <= k + 1) {
			result.setRunningTime(0);
			result.setFinalCost(0);
			result.setOptimalCost(0);
			result.setNumUncovered(0);
			result.setNumUsefulCover(numUsefulCover);
		} else {
			result.setRunningTime(runningTime);
			result.setFinalCost(cost);
			result.setOptimalCost(optimalCost);
			result.setNumUncovered(uncoveredPairs);
			result.setNumUsefulCover(numUsefulCover);
		}
		docToTopPairsResult.put(docID, result);
	}
	
	public void doILP() {
		try {
			int numFacilities = pairs.size();			// Including the root
			int numCustomers = pairs.size();
//			int[][] distances = initDistances();
			initDistances();
			
			GRBEnv env = new GRBEnv();
			env.set(GRB.IntParam.OutputFlag, 0);
			
			GRBModel model = new GRBModel(env);
			model.set(GRB.StringAttr.ModelName, "Non-Metric Uncapacitated Facility");
			
			// Facility open indicator
			GRBVar[] open = new GRBVar[numFacilities];
			for (int f = 0; f < numFacilities; ++f) {
				open[f] = model.addVar(0, 1, 0, GRB.BINARY, "open" + f);
			}
			
			// Facility - Customer connecting indicator
			GRBVar[][] connecting = new GRBVar[numFacilities][numCustomers];
			for (int f = 0; f < numFacilities; ++f) {
				for (int c = 0; c < numCustomers; ++c) {
					connecting[f][c] = model.addVar(0, 1, distances[f][c], GRB.BINARY, "connecting" + f + "to" + c);
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
			
			// Constraint: connecting each customer to only one facility
			GRBLinExpr connectingToOneFacility = new GRBLinExpr();
			for (int c = 0; c < numCustomers; ++c) {
				connectingToOneFacility = new GRBLinExpr();
				for (int f = 0; f < numFacilities; ++f) {
					connectingToOneFacility.addTerm(1.0, connecting[f][c]);
				}
				model.addConstr(connectingToOneFacility, GRB.EQUAL, 1.0, "connectingToOneFacility" + c);
			}
			
			// Constraint: only connect customer to opened facility
			for (int f = 0; f < numFacilities; ++f) {
				for (int c = 0; c < numCustomers; ++c) {
					model.addConstr(connecting[f][c], GRB.LESS_EQUAL, open[f], "onlyToOpenedFacility_f" + f + "_c" + "c");
				}
			}
						
			
			// Optimize
			model.optimize();
			
			// Prepare some statistics
			cost = (long) model.get(GRB.DoubleAttr.ObjVal);
			optimalCost = (long) model.get(GRB.DoubleAttr.ObjBound);
			for (int c = 1; c < numCustomers; ++c) {
					if (connecting[0][c].get(GRB.DoubleAttr.X) == 1.0) {
						++uncoveredPairs;
					}
			}
			
			for (int f = 1; f < numFacilities; ++f) {			// Start from "1" because the first is the root
				if (open[f].get(GRB.DoubleAttr.X) == 1.0) {
					topK.add(pairs.get(f));
					
					for (int c = 1; c < numCustomers; ++c) {
						if (connecting[f][c].get(GRB.DoubleAttr.X) == 1.0) {
							if (!pairs.get(f).getCUI().equals(pairs.get(c).getCUI()))
								++numUsefulCover;
						}
					}
				}
			}
			
			
			// For testing
			if (Constants.DEBUG_MODE) {
				facilityOpen = new int[numFacilities];
				facilityConnect = new int[numFacilities][numCustomers];
				for (int f = 0; f < numFacilities; ++f) {
					facilityOpen[f] = 0;
					for (int c = 0; c < numCustomers; ++c) {
						facilityConnect[f][c] = 0;
					}
				}		
				
				// Print the solution
	/*			System.err.println("\nTOTAL COSTS: " + model.get(GRB.DoubleAttr.ObjVal));
				System.out.println("Solution:");
				System.out.print("Opened Facilities:\t");*/
				for (int f = 0; f < numFacilities; ++f) {
					if (open[f].get(GRB.DoubleAttr.X) == 1.0) {
	//					System.out.print(f + "\t");
						facilityOpen[f] = 1;
					}
				}
				
	//			System.out.print("\nConnecting customer:");
				for (int c = 0; c < numCustomers; ++c) {
	//				System.out.print("\n\tCustomer " + c + " to facility ");
					for (int f = 0; f < numFacilities; ++f) {
						if (connecting[f][c].get(GRB.DoubleAttr.X) == 1.0) {
	//						System.out.print(f + "\t");
							facilityConnect[f][c] = 1;
						}
					}				
				}
				System.out.println();
				
	//			System.err.println( model.get(GRB.DoubleAttr.ObjBound));
			}
			
			
			model.dispose();
			env.dispose();
		} catch (GRBException e) {
		      System.out.println("Error code: " + e.getErrorCode() + ". " + e.getMessage());
		}
		
	}
	
	private void checkResult() {
		
		long verifyingCost = 0;	
		int numPairs = pairs.size();	
		
		// Test 1
		for (int c = 0; c < numPairs; ++c) {
			int numConnect = 0;
			for (int f = 0; f < numPairs; ++f) {
				if (facilityConnect[f][c] == 1) {
					verifyingCost += distances[f][c];
					++numConnect;
				}					
			}
			
			if (numConnect != 1) {
				System.err.println("ILP TEST 1 - ERROR at customer " + c);
				for (int f = 0; f < numPairs; ++f) {
					if (facilityConnect[f][c] == 1) {
						System.err.println("facility[" + f + "][" + c + "] = " + facilityConnect[f][c] 
								+ ", facilityOpen[" + f + "] = " + facilityOpen[f]);
					}					
				}	
			}
		}

		
		for (int f = 0; f < numPairs; ++f) {
			if (facilityOpen[f] != 1) {

				for (int c = 0; c < numPairs; ++c) {
					if (facilityConnect[f][c] == 1) {
						System.err.println("ILP TEST 1 - ERROR 2 at facility " + f + ", customer " + c);
					}					
				}
			}
		}
		
		System.out.println("Cost: " + cost + " - Verifying Cost: " + verifyingCost);
		if (verifyingCost != cost)
			System.err.println("ILP Error at docID " + docID);
		
		
		// Test 2
		verifyingCost = 0;
		for (int c = 0; c < numPairs; ++c) {
			int min = Constants.INVALID_DISTANCE_FOR_ILP;
			for (int f = 0; f < numPairs; ++f) {
				if (facilityOpen[f] == 1) {
					if (distances[f][c] < min)
						min = distances[f][c];
				}
			}			
			verifyingCost += min;
		}
		
		System.out.println("Cost: " + cost + " - Verifying Cost 2: " + verifyingCost);
		if (verifyingCost != cost)
			System.err.println("ILP TEST 2 -  Error 2 at docID " + docID);
	}
	

	private int[][] initDistances() {
		distances = new int[pairs.size()][pairs.size()];
		
		// The root
		distances[0][0] = 0;
		for (int j = 1; j < pairs.size(); ++j) {
			ConceptSentimentPair pj = pairs.get(j);				
				
			distances[0][j] = pj.calculateRootDistance();
			distances[j][0] = Constants.INVALID_DISTANCE_FOR_ILP;
		}
		
		// Normal pairs
		for (int i = 1; i < pairs.size(); ++i) {
			ConceptSentimentPair pi = pairs.get(i);
			
			for (int j = i + 1; j < pairs.size(); ++j) {
				ConceptSentimentPair pj = pairs.get(j);				
				int distance = Constants.INVALID_DISTANCE_FOR_ILP;	

				if (Constants.DEBUG_MODE) {
					pi.testDistance(pj);
					pj.testDistance(pi);
				}
				
				if (isSentimentCover(pi, pj)) {
					int temp = pi.calculateDistance(pj);
					if (temp != Constants.INVALID_DISTANCE) {
						distance = temp;
					}
				}
				
				if (distance == Constants.INVALID_DISTANCE_FOR_ILP) {
					distances[i][j] = Constants.INVALID_DISTANCE_FOR_ILP;
					distances[j][i] = Constants.INVALID_DISTANCE_FOR_ILP;
				} else if (distance > 0) {
					distances[i][j] = distance;
					distances[j][i] = Constants.INVALID_DISTANCE_FOR_ILP;
				} else if (distance < 0) {
					distances[i][j] = Constants.INVALID_DISTANCE_FOR_ILP;
					distances[j][i] = -distance;
				} else if (distance == 0) {
					distances[i][j] = 0;
					distances[j][i] = 0;
				}
			}
		}
		
		
		return distances;
	}
	
	public boolean isSentimentCover(ConceptSentimentPair p1, ConceptSentimentPair p2) {
		boolean result = false;
		
		if (p1.equals(root) || p2.equals(root))
			result = true;
		else if (Math.abs(p1.getSentiment() - p2.getSentiment()) <= threshold)
			result =  true;
		 
		return result;
	}
}
