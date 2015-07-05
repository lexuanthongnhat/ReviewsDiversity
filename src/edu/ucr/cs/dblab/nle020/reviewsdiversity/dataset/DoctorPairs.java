package edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset;

import java.util.List;

public class DoctorPairs {
	private Integer docID;
	private List<ConceptSentimentPair> pairs;
	public Integer getDocID() {
		return docID;
	}
	public void setDocID(Integer docID) {
		this.docID = docID;
	}
	public List<ConceptSentimentPair> getPairs() {
		return pairs;
	}
	public void setPairs(List<ConceptSentimentPair> pairs) {
		this.pairs = pairs;
	}
	
	public DoctorPairs() {}
	
	public DoctorPairs(Integer docID, List<ConceptSentimentPair> pairs) {
		super();
		this.docID = docID;
		this.pairs = pairs;
	}
}
