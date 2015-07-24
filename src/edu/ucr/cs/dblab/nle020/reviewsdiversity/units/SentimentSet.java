package edu.ucr.cs.dblab.nle020.reviewsdiversity.units;

import java.util.ArrayList;
import java.util.List;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.FullPair;

public abstract class SentimentSet {
	private String id;
	// The possible decrease in the cost if we choose this set 
	private int benefit = 0;
	
	private List<FullPair> fullPairs = new ArrayList<FullPair>();
	@Override
	public String toString() {
		return "SentimentSet [id=" + id + ", benefit=" + benefit
				+ ", fullPairs=" + fullPairs + "]";
	}
	private List<ConceptSentimentPair> pairs = new ArrayList<ConceptSentimentPair>();
	
	public SentimentSet(){ }	
	public SentimentSet(String id) {
		this.id = id;
	}
	public SentimentSet(String id, List<ConceptSentimentPair> pairs) {
		this.id = id;
		this.pairs = pairs;
	}	
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public List<FullPair> getFullPairs() {
		return fullPairs;
	}
	public void setFullPairs(List<FullPair> fullPairs) {
		this.fullPairs = fullPairs;
	}  
	public void addFullPair(FullPair fullPair) {
		fullPairs.add(fullPair);
	}
	public int getBenefit() {
		return benefit;
	}
	public void setBenefit(int benefit) {
		this.benefit = benefit;
	}
	public void increaseBenefit(int increment) {
		benefit += increment;
	}
	public void decreaseBenefit(int decrement) {
		benefit += decrement;
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SentimentSet other = (SentimentSet) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		return true;
	}
	public List<ConceptSentimentPair> getPairs() {
		return pairs;
	}
	public void setPairs(List<ConceptSentimentPair> pairs) {
		this.pairs = pairs;
	}
	
}
