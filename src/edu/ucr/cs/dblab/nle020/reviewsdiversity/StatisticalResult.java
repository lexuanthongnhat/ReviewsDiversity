package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import java.util.HashMap;
import java.util.Map;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants.PartialTimeIndex;

public class StatisticalResult {
	private int docID = 0;
	
	private int k = 0;
	private float threshold = 0.0f;
	
	private double initialCost = -1;
	private double finalCost = -1;
	private double optimalCost = -1;
	
	private int numUncovered = 0;
	private int numPairs = 0;
	private int numSets = 0;								// Reviews or Sentences
	private int numFacilities = 0;							// # of choosen facilities - for randomized rounding
	
	private int numUsefulCover = 0;							// After Algorithm, Actual ancestor-child relationship/edge, not two pairs of the same CUI
	private int numPotentialUsefulCover = 0;				// Before Algorithm
	private int numPotentialUsefulCoverWithThreshold = 0;	// Before Algorithm, care about sentiment cover
	
	private int numEdges = 0;					

	private double runningTime = 0;
	private Map<Constants.PartialTimeIndex, Double> partialTimes = new HashMap<Constants.PartialTimeIndex, Double>();

	public void aggregateAnother(StatisticalResult other) {
		this.finalCost 		+= other.finalCost;
		
		this.numUncovered 	+= other.numUncovered;
		
		this.runningTime 	+= other.runningTime;
		for (Constants.PartialTimeIndex index : partialTimes.keySet()) {
			this.partialTimes.put(index, this.partialTimes.get(index) + other.partialTimes.get(index));
		}
	}
	
	public void switchToMin(StatisticalResult other) {
		if (other.runningTime < this.runningTime) { 
	//			|| other.partialTimes.get(PartialTimeIndex.SETUP) < this.partialTimes.get(PartialTimeIndex.SETUP)) {
			
			this.runningTime = other.runningTime;
			for (Constants.PartialTimeIndex index : partialTimes.keySet()) {
				this.partialTimes.put(index, other.partialTimes.get(index));				
			}
			
			this.finalCost = other.finalCost;
		}
		
		Double otherSetupTime = other.partialTimes.get(PartialTimeIndex.SETUP);
		Double thisSetupTime = this.partialTimes.get(PartialTimeIndex.SETUP);
		if (otherSetupTime != null && thisSetupTime != null && otherSetupTime < thisSetupTime)
			this.partialTimes.put(PartialTimeIndex.SETUP, otherSetupTime);
	}
	
	public StatisticalResult averagingBy(int numTrials) {
		double temp = (double) numTrials;
		this.finalCost 		/= temp;
		
		this.numUncovered 	/= temp;
		
		this.runningTime 	/= temp;
		for (Constants.PartialTimeIndex index : partialTimes.keySet()) {
			this.partialTimes.put(index, this.partialTimes.get(index)/temp);
		}
		
		return this;
	}
	
	public StatisticalResult(){}
	
	public StatisticalResult(int k, float threshold, double finalCost, double runningTime) {
		this.k = k;
		this.threshold = threshold;		
		this.finalCost = finalCost;
		this.runningTime = runningTime;
	}
	
	public StatisticalResult(int docID, int k, float threshold) {
		super();
		this.docID = docID;
		this.k = k;
		this.threshold = threshold;
	}

	public void addPartialTime(Constants.PartialTimeIndex index, double partialTime) {
		partialTimes.put(index, partialTime);
	}
	public double getPartialTime(Constants.PartialTimeIndex index) {
		if (partialTimes.containsKey(index))
			return partialTimes.get(index);
		else
			return -1.0f;
	}
	
	public void increaseRunningTime(double amount) {
		runningTime += amount;
	}
	
	public void increasePartialTime(PartialTimeIndex index, double amount) {
		if (!partialTimes.containsKey(index))
			partialTimes.put(index, amount);
		else
			partialTimes.put(index, partialTimes.get(index) + amount);
	}
	
	public void increasePartialTime(StatisticalResult stat) {
		for (PartialTimeIndex index : stat.getPartialTimes().keySet()) {
			if (!partialTimes.containsKey(index))
				partialTimes.put(index, stat.getPartialTime(index));
			else
				partialTimes.put(index, partialTimes.get(index) + stat.getPartialTime(index));
		}
	}
	
	@Override
	public String toString() {
		return "StatisticalResult [docID=" + docID + ", k=" + k
				+ ", threshold=" + threshold + ", finalCost=" + finalCost
				+ ", numPairs=" + numPairs + ", numEdges=" + numEdges
				+ ", runningTime=" + runningTime
				+ ", partialTimes=" + partialTimes + "]";
	}

	public int getDocID() {
		return docID;
	}

	public void setDocID(int docID) {
		this.docID = docID;
	}

	public int getK() {
		return k;
	}

	public void setK(int k) {
		this.k = k;
	}

	public float getThreshold() {
		return threshold;
	}

	public void setThreshold(float threshold) {
		this.threshold = threshold;
	}

	public double getInitialCost() {
		return initialCost;
	}

	public void setInitialCost(double initialCost) {
		this.initialCost = initialCost;
	}

	public double getFinalCost() {
		return finalCost;
	}

	public void setFinalCost(double finalCost) {
		this.finalCost = finalCost;
	}
	
	public void decreaseFinalCost(double amount) {
		finalCost -= amount;
	}

	public double getOptimalCost() {
		return optimalCost;
	}

	public void setOptimalCost(double optimalCost) {
		this.optimalCost = optimalCost;
	}

	public int getNumUncovered() {
		return numUncovered;
	}

	public void setNumUncovered(int numUncovered) {
		this.numUncovered = numUncovered;
	}
	
	public void increaseNumUncovered() {
		++numUncovered;
	}

	public int getNumPairs() {
		return numPairs;
	}

	public void setNumPairs(int numPairs) {
		this.numPairs = numPairs;
	}

	public double getRunningTime() {
		return runningTime;
	}

	public void setRunningTime(double runningTime) {
		this.runningTime = runningTime;
	}
	
	public int getNumUsefulCover() {
		return numUsefulCover;
	}

	public void setNumUsefulCover(int numUsefulCover) {
		this.numUsefulCover = numUsefulCover;
	}
	
	public void increaseNumUsefulCover() {
		++numUsefulCover;
	}

	public int getNumPotentialUsefulCover() {
		return numPotentialUsefulCover;
	}

	public void setNumPotentialUsefulCover(int numPotentialUsefulCover) {
		this.numPotentialUsefulCover = numPotentialUsefulCover;
	}
	
	public void increaseNumPotentialUsefulCover() {
		++numPotentialUsefulCover;
	}

	public int getNumPotentialUsefulCoverWithThreshold() {
		return numPotentialUsefulCoverWithThreshold;
	}

	public void setNumPotentialUsefulCoverWithThreshold(
			int numPotentialUsefulCoverWithThreshold) {
		this.numPotentialUsefulCoverWithThreshold = numPotentialUsefulCoverWithThreshold;
	}
	
	public void increaseNumPotentialUsefulCoverWithThreshold() {
		++numPotentialUsefulCoverWithThreshold;
	}

	public int getNumFacilities() {
		return numFacilities;
	}

	public void setNumFacilities(int numFacilities) {
		this.numFacilities = numFacilities;
	}

	public int getNumSets() {
		return numSets;
	}

	public void setNumSets(int numSets) {
		this.numSets = numSets;
	}

	public Map<Constants.PartialTimeIndex, Double> getPartialTimes() {
		return partialTimes;
	}

	public void setPartialTimes(Map<Constants.PartialTimeIndex, Double> partialTimes) {
		this.partialTimes = partialTimes;
	}

	public int getNumEdges() {
		return numEdges;
	}

	public void setNumEdges(int numEdges) {
		this.numEdges = numEdges;
	}
	
	public void increaseNumEdges() {
		++numEdges;
	}
}
