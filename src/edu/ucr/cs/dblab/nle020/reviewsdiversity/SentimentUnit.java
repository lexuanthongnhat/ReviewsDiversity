package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset.ConceptSentimentPair;

public abstract class SentimentUnit {
	private String ID;
	
	// From ROOT type to this pair types
	public abstract int calculateRootDistance();
	
	public abstract int calculateDistance(SentimentUnit other);
}
