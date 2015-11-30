package edu.ucr.cs.dblab.nle020.reviewsdiversity;

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

	private double runningTime = 0;

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

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("docID " + docID + ",\tk = " + k + ",\t threshold = " + threshold + "\n");
		builder.append("numPairs:\t" + numPairs + "\t numUncovered: " + numUncovered + "\trunningTime: " + runningTime + " ms\n");
		builder.append("initialCost:\t" + initialCost + "\t finalCost: " + finalCost);
		if (optimalCost != -1)
			builder.append("\t optimalCost: \t" + optimalCost + ",\t ratio: " + finalCost/optimalCost);
		
		return builder.toString();
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
	
}
