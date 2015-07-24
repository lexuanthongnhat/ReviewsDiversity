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

import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
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
	protected ConcurrentMap<Integer, TopPairsResult> docToTopPairsResult = new ConcurrentHashMap<Integer, TopPairsResult>();
		
	public RandomizedRounding(int k, float threshold,
			ConcurrentMap<Integer, TopPairsResult> docToTopPairsResult) {
		super();
		this.k = k;
		this.threshold = threshold;
		this.docToTopPairsResult = docToTopPairsResult;
	}
	
	public RandomizedRounding(int k, float threshold,
			ConcurrentMap<Integer, TopPairsResult> docToTopPairsResult, Constants.LPMethod method) {
		super();
		this.k = k;
		this.threshold = threshold;
		this.docToTopPairsResult = docToTopPairsResult;
		this.method = method;
	}

	/**
	 * Run Linear Programming for a doctor's data set then use Natural Randomized Rounding
	 * @param docId - doctor ID
	 * @param docToSentimentSets - list of sentiment units/nodes in K-medians
	 * @return Result's statistics
	 */
	protected TopPairsResult runRandomizedRoundingPerDoc(int docId, List<ConceptSentimentPair> conceptSentimentPairs) {
		long startTime = System.currentTimeMillis();
		
		TopPairsResult result = new TopPairsResult(docId, k, threshold);
		result.setNumPairs(conceptSentimentPairs.size());
		List<ConceptSentimentPair> topK = new ArrayList<ConceptSentimentPair>();
				
		List<ConceptSentimentPair> pairs = new ArrayList<ConceptSentimentPair>();		
		pairs.add(root);
		pairs.addAll(conceptSentimentPairs);
		
		if (pairs.size() <= k + 1) {
			topK = pairs;
		} else {
			int[][] distances = initDistances(pairs);
			doRandomizedRounding(distances, result, method);	
		}		
		
		gatherFinalResult(System.currentTimeMillis() - startTime, pairs.size(), result);

		return result;
	}
	
	// Update topK, result
	public void doRandomizedRounding(int[][] distances, TopPairsResult result, Constants.LPMethod method) {		
		try {
			int numFacilities = distances.length;
			int numCustomers = distances[0].length;
			
			GRBEnv env = new GRBEnv();
			env.set(GRB.IntParam.OutputFlag, 0);
			env.set(GRB.IntParam.Method, method.method());
			
			GRBModel model = new GRBModel(env);
			model.set(GRB.StringAttr.ModelName, "Non-Metric Uncapacitated Facility");
			
			// Facility open indicator - fractional
			GRBVar[] open = new GRBVar[numFacilities];
			for (int f = 0; f < numFacilities; ++f) {
				open[f] = model.addVar(0, 1, 0, GRB.CONTINUOUS, "open" + f);
			}
			
			// Facility - Customer connecting indicator - fractional
			GRBVar[][] connecting = new GRBVar[numFacilities][numCustomers];
			for (int f = 0; f < numFacilities; ++f) {
				for (int c = 0; c < numCustomers; ++c) {
					connecting[f][c] = model.addVar(0, 1, distances[f][c], GRB.CONTINUOUS, "connecting" + f + "to" + c);
				}
			}
			
			model.set(GRB.IntAttr.ModelSense, 1);		// Minimization
			model.update();
		
			// CONSTRAINTS: 
			
			// Constraint: open the root by default
			model.addConstr(open[0], GRB.EQUAL, 1.0, "defaultRoot");
			
			/*
			 *  Constraint: open k + 1 facilities, including the root
			 *  			Sum_f x(f) = k + 1
			 */
			GRBLinExpr kFacilities = new GRBLinExpr();
			for (int f = 0; f < numFacilities; ++f) {
				kFacilities.addTerm(1.0, open[f]);
			}
			model.addConstr(kFacilities, GRB.LESS_EQUAL, k + 1, "kFacilities");
			
			/* 
			 * Constraint: connecting each customer to only one facility
			 * 				Sum_f x(f, c) = 1
			 */			
			GRBLinExpr connectingToOneFacility = new GRBLinExpr();
			for (int c = 0; c < numCustomers; ++c) {
				connectingToOneFacility = new GRBLinExpr();
				for (int f = 0; f < numFacilities; ++f) {
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
					model.addConstr(connecting[f][c], GRB.LESS_EQUAL, open[f], "onlyToOpenedFacility_f" + f + "_c" + "c");
				}
			}						
			
			// Optimize
			model.optimize();						
			
			// Prepare some statistics, update result					
			result.setFinalCost(model.get(GRB.DoubleAttr.ObjVal));
			result.setNumFacilities(k);
			
			int uncovered = 0;
			for (int c = 1; c < numCustomers; ++c) {
				if (connecting[0][c].get(GRB.DoubleAttr.X) == 1)
					++uncovered;
			}
			result.setNumUncovered(uncovered);
			
			// Retrieving the fractional solution
			//////////////////////////////////////////////////////////////
			double[] facilityOpen = new double[numFacilities];
			double[][] facilityConnect = new double[numFacilities][numCustomers];
									
			if (open[0].get(GRB.DoubleAttr.X) < 1)
				System.err.println("Root is not choosen completely, facilityOpen[0] = " + open[0].get(GRB.DoubleAttr.X));
				
			boolean needRandomizedRounding = false;
			for (int f = 0; f < numFacilities; ++f) {
				facilityOpen[f] = open[f].get(GRB.DoubleAttr.X);
				
				if (facilityOpen[f] > 0 && facilityOpen[f] < 1) {
//					System.err.println("facilityOpen[" + f + "] " + facilityOpen[f]);
					needRandomizedRounding = true;
				}
			}
			
			for (int f = 0; f < numFacilities; ++f) {			
				for (int c = 0; c < numCustomers; ++c) {
					facilityConnect[f][c] = connecting[f][c].get(GRB.DoubleAttr.X);
					if (facilityConnect[f][c] > 0 && facilityConnect[f][c] < 1) {
//						System.err.println("facilityConnect[" + f + "][" + c + "] " + facilityConnect[f][c]);
						needRandomizedRounding = true;
					}
						
				}
			}			

			if (needRandomizedRounding) {
//				outputToFileForDebug(distances, facilityOpen, facilityConnect, result, DESKTOP_FOLDER + "rr_debug.txt");
				roundingRandomly(distances, facilityOpen, facilityConnect, 
						model.get(GRB.DoubleAttr.ObjVal), result, Constants.RR_TERMINATED_BY_K);
			}
			
			
			//////////////////////////////////////////////////////////////////////////////
			// For testing
			if (Constants.DEBUG_MODE) {
	
				
				// Print the solution
	/*			System.err.println("\nTOTAL COSTS: " + model.get(GRB.DoubleAttr.ObjVal));
				System.out.println("Solution:");
				System.out.print("Opened Facilities:\t");*/

				
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
			}
			
			
			model.dispose();
			env.dispose();
		} catch (GRBException e) {
		      System.out.println("Error code: " + e.getErrorCode() + ". " + e.getMessage());
		      e.printStackTrace();
		}
		
	}
	
	// Output: update facilityOpen, facilityConnect
	private void roundingRandomly(int[][] distances, double[] facilityOpen, double[][] facilityConnect, double fractionalOptimalCost,
									TopPairsResult result, boolean terminatedByK) {
		int numFacilities = facilityOpen.length;
		int numCustomers = facilityConnect[0].length;
		
		double[] originalOpen = facilityOpen.clone();
		double[][] originalConnect = new double[numFacilities][];
		for (int f = 0; f < numFacilities; ++f) {
			originalConnect[f] = facilityConnect[f].clone();
		}
		
		double originalFacilityCost = 0;						// can be < k, always <= k, = k most of time
		for (double open : originalOpen) {
			originalFacilityCost += open;
		}
		
		for (int f = 0; f < numFacilities; ++f) {
			facilityOpen[f] = 0;
			for (int c = 0; c < numCustomers; ++c) {
				facilityConnect[f][c] = 0;
			}
		}
		facilityOpen[0] = 1;
		
		// TODO - re-check these threshold if you want to get optimal cost (with more than k facilities)
		double epsilon = 1.0f / (double) numCustomers;
		double facilityCostThreshold = k * Math.log(numCustomers + (double) numCustomers / epsilon);
		double assignmentCostThreshold = fractionalOptimalCost * (1 + epsilon);
		
		
		int unChosen = numCustomers;
		double facilityCost = 0.0f;
		double assignmentCost = 0.0f;
		Map<Integer, Integer> customerToFacility = new HashMap<Integer, Integer>();
		Map<Integer, Set<Integer>> facilityToCustomers = new HashMap<Integer, Set<Integer>>();
		facilityToCustomers.put(0, new HashSet<Integer>());

		double termination = Math.floor(originalFacilityCost) < k ? Math.floor(originalFacilityCost) : k; 
		// root is opened by default
		if (terminatedByK) {
			while (facilityCost <= termination) {
								
				for (int f = 0; f < numFacilities; ++f) {
					// Rounding with probability x(f)/|x|				
					if (rollTheDice(originalOpen[f] / originalFacilityCost)) {	
						if (facilityOpen[f] == 0) {
							facilityOpen[f] = 1;

							facilityToCustomers.put(f, new HashSet<Integer>());
						}
					}			
				}
								
				for (int f = 0; f < numFacilities; ++f) {
					if (facilityOpen[f] > 0) {					
						// Rounding with probability x(f, c)/x(f)
						for (int c = 0; c < numCustomers; ++c) {
							/**
							 *  Note: an iteration of facilityConnect can increase the facilityCost more than 1
							 *  	==> need this "if" as a safeguard
							 */
							if (facilityCost >= k + 1)
								break;
							
							if (rollTheDice(originalConnect[f][c] / originalOpen[f]) 
									&& facilityConnect[f][c] == 0.0f) {

								facilityConnect[f][c] = 1;
								assignmentCost += distances[f][c];
								
								if (facilityToCustomers.get(f).size() == 0) 
									++facilityCost;
								facilityToCustomers.get(f).add(c);								

								if (customerToFacility.containsKey(c)) {
									int previous = customerToFacility.get(c);
									assignmentCost -= distances[previous][c];
									facilityConnect[previous][c] = 0;
									
									facilityToCustomers.get(previous).remove(c);
									if (facilityToCustomers.get(previous).size() == 0)
										--facilityCost;
								}
								customerToFacility.put(c, f);
							}
						}
					}
				}
			}				
						
			Set<Integer> chosenFacilities = new HashSet<Integer>();
			chosenFacilities.addAll(customerToFacility.values());
			
			Set<Integer> maxPossibleCustomers = new HashSet<Integer>();
			for (Integer f : chosenFacilities) {
				for (int c = 0; c < numCustomers; ++c) {
					if (originalConnect[f][c] > 0)
						maxPossibleCustomers.add(c);
				}
			} 
			
			// Randomly assign customers to chosen facilities as much as possible
			while (customerToFacility.size() < maxPossibleCustomers.size()) {
				for (Integer f : chosenFacilities) {
					// Rounding with probability x(f, c)/x(f)
					for (int c = 0; c < numCustomers; ++c) {				
						if (rollTheDice(originalConnect[f][c] / originalOpen[f]) 
								&& facilityConnect[f][c] == 0.0f) {

							facilityConnect[f][c] = 1;
							assignmentCost += distances[f][c];
							
							if (facilityToCustomers.get(f).size() == 0) 
								++facilityCost;
							facilityToCustomers.get(f).add(c);								

							if (customerToFacility.containsKey(c)) {
								int previous = customerToFacility.get(c);
								assignmentCost -= distances[previous][c];
								facilityConnect[previous][c] = 0;
								
								facilityToCustomers.get(previous).remove(c);
								if (facilityToCustomers.get(previous).size() == 0)
									--facilityCost;
							}
							customerToFacility.put(c, f);
						}
					}
				}
			}
			
			// Assign the remaining customers to the root
			if (customerToFacility.size() < numCustomers) {
				for (int c = 0; c < numCustomers; ++c) {
					if (!customerToFacility.containsKey(c)) {
						facilityConnect[0][c] = 1.0f;
						assignmentCost += distances[0][c];
					}					
				}
			}
		} else {
			while (facilityCost < facilityCostThreshold - 1 && (unChosen > 0 || assignmentCost > assignmentCostThreshold)) {
				for (int f = 0; f < numFacilities; ++f) {

					// Rounding with probability x(f)/|x|				
					if (f != 0 && rollTheDice(originalOpen[f] / originalFacilityCost)) {	
						if (facilityOpen[f] == 0) {
							facilityOpen[f] = 1;
							++facilityCost;
						}
					}		

					if (facilityOpen[f] > 0) {					
						// Rounding with probability x(f, c)/x(f)
						for (int c = 0; c < numCustomers; ++c) {
							if (rollTheDice(originalConnect[f][c] / originalOpen[f])) {
								if (facilityConnect[f][c] == 0) {
									facilityConnect[f][c] = 1;
									--unChosen;
									assignmentCost += distances[f][c];

									// When c was assigned before
									for (int previous = 0; previous < originalOpen.length; ++previous) {
										if (previous != f && facilityConnect[previous][c] == 1) {
											facilityConnect[previous][c] = 0;
											++unChosen;
											assignmentCost -= distances[previous][c];
										}
									}
								} 
							}
						}
					}
				}
			}
		}
		
		int numFacilitiesOpened = 0;
		int numUncovered = 0;
		
		for (int f = 1; f < numFacilities; ++f) {
			facilityOpen[f] = 0;
		}
		
		for (int c = 0; c < numCustomers; ++c) {
			if (facilityConnect[0][c] == 1)
				++numUncovered;			
			for (int f = 1; f < numFacilities; ++f) {
				if (facilityConnect[f][c] == 1) {
					facilityOpen[f] = 1;
					break;
				}
			}
		}
		
		for (int f = 1; f < numFacilities; ++f) {
			if (facilityOpen[f] == 1.0)
				++numFacilitiesOpened;
		}				
		
		result.setFinalCost(assignmentCost);
		result.setNumFacilities(numFacilitiesOpened);
		result.setNumUncovered(numUncovered);
	}

	private boolean rollTheDice(double probability) {
		if (Math.random() < probability)
			return true;
		else 
			return false;
	}
	
	protected void gatherFinalResult(long runningTime, int datasetSize, TopPairsResult result) {
		
		if (datasetSize <= k + 1) {
			result.setNumFacilities(datasetSize);
			result.setRunningTime(0);
			result.setFinalCost(0);
			result.setOptimalCost(0);
			result.setNumUncovered(0);
			result.setNumUsefulCover(0);
		} else {
			result.setRunningTime(runningTime);
		}
		docToTopPairsResult.put(result.getDocID(), result);
	}
	
	@SuppressWarnings("unused")
	private void outputToFileForDebug(int[][] distances, 
			double[] facilityOpen, double[][] facilityConnect, 
			TopPairsResult result, String outputPath) {
		
		int numFacilities = facilityOpen.length;
		int numCustomers = facilityConnect[0].length;
		
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath), 
				StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
			
			System.out.println("\nDoctorID " + result.getDocID() + " Open facilities: ");
			
			writer.append("\nDoctorID " + result.getDocID() + " Open facilities: ");
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
	
	protected void checkResult(int[][] distances, double[] facilityOpen, double[][] facilityConnect, TopPairsResult result) {
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
		
		System.out.println("Cost: " + result.getFinalCost() + " - Verifying Cost: " + verifyingCost);
		if (verifyingCost != result.getFinalCost())
			System.err.println("ILP Error at docID " + result.getDocID());
		
		
		// Test 2
		verifyingCost = 0;
		for (int c = 0; c < numCustomers; ++c) {
			int min = Constants.INVALID_DISTANCE_FOR_ILP;
			for (int f = 0; f < numFacilities; ++f) {
				if (facilityOpen[f] == 1) {
					if (distances[f][c] < min)
						min = distances[f][c];
				}
			}			
			verifyingCost += min;
		}
		
		if (verifyingCost != result.getFinalCost())
			System.err.println("RR TEST 2 -  Error 2 at docID " + result.getDocID() + 
					":\tCost: " + result.getFinalCost() + " - Verifying Cost 2: " + verifyingCost);
	}
	

	private int[][] initDistances(List<ConceptSentimentPair> conceptSentimentPairs) {
		int[][] distances = new int[conceptSentimentPairs.size()][conceptSentimentPairs.size()];
		
		// The root
		distances[0][0] = 0;
		for (int j = 1; j < conceptSentimentPairs.size(); ++j) {
			ConceptSentimentPair normalUnit = conceptSentimentPairs.get(j);				
				
			distances[0][j] = normalUnit.calculateRootDistance();
			distances[j][0] = Constants.INVALID_DISTANCE_FOR_ILP;
		}
		
		// Normal pairs
		for (int i = 1; i < conceptSentimentPairs.size(); ++i) {
			ConceptSentimentPair pair1 = conceptSentimentPairs.get(i);
			
			for (int j = i + 1; j < conceptSentimentPairs.size(); ++j) {
				ConceptSentimentPair pair2 = conceptSentimentPairs.get(j);				
				int distance = Constants.INVALID_DISTANCE_FOR_ILP;	

				if (Constants.DEBUG_MODE) {
					pair1.testDistance(pair2);
					pair2.testDistance(pair1);
				}
				
				
				int temp = pair1.calculateDistance(pair2, threshold);
				if (temp != Constants.INVALID_DISTANCE) {
					distance = temp;
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
}
