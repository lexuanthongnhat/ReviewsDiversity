package edu.ucr.cs.dblab.nle020.reviewsdiversity.units;

import java.util.ArrayList;
import java.util.List;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.FullPair;

public abstract class SentimentSet {
	private String id;
	private int benefit;
	private List<FullPair> fullPairs = new ArrayList<FullPair>();
	private List<ConceptSentimentPair> pairs = new ArrayList<ConceptSentimentPair>();
	
	public SentimentSet(){ 
		super();
	}	
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

	public List<ConceptSentimentPair> getPairs() {
		return pairs;
	}
	public void setPairs(List<ConceptSentimentPair> pairs) {
		this.pairs = pairs;
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
	
	@Override
	public String toString() {
		return "SentimentSet [id=" + id + ", fullPairs=" + fullPairs + "]";
	}
	public int getBenefit() {
		return benefit;
	}
	public void setBenefit(int benefit) {
		this.benefit = benefit;
	}
}
