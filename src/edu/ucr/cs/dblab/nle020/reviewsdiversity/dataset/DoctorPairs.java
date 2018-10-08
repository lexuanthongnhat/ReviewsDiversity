package edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset;

import java.util.List;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;

public class DoctorPairs {
	private String docID;
	private RawReview rawReview;
	private List<ConceptSentimentPair> pairs;
	
	public String getDocID() {
		return docID;
	}
	public void setDocID(String docID) {
		this.docID = docID;
	}
	public List<ConceptSentimentPair> getPairs() {
		return pairs;
	}
	public void setPairs(List<ConceptSentimentPair> pairs) {
		this.pairs = pairs;
	}
	
	public DoctorPairs() {}
	
	public DoctorPairs(String docID, List<ConceptSentimentPair> pairs) {
		super();
		this.docID = docID;
		this.pairs = pairs;
	}
	public RawReview getRawReview() {
		return rawReview;
	}
	public void setRawReview(RawReview rawReview) {
		this.rawReview = rawReview;
	}
}
