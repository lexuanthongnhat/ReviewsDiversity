/**
 * Nhat: should have the position (offset) in its review parent too
 */
package edu.ucr.cs.dblab.nle020.reviewsdiversity.units;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown=true)
public class SentimentSentence extends SentimentSet {
	
	private String sentence;	
	private String reviewId;		
	
	public SentimentSentence(){
		super();
	}
	
	public SentimentSentence(String id) {
		super(id);
	}
	
	public SentimentSentence(String id, List<ConceptSentimentPair> pairs) {
		super(id, pairs);
	}
	
	public SentimentSentence(String id, String sentence){
		super(id);
		this.sentence = sentence;
	}
	
	public SentimentSentence(String id, String sentence, String reviewId){
		super(id);
		this.sentence = sentence;
		this.reviewId = reviewId;
	}

	public String getSentence() {
		return sentence;
	}

	public void setSentence(String sentence) {
		this.sentence = sentence;
	}

	public String getReviewId() {
		return reviewId;
	}

	public void setReviewId(String reviewId) {
		this.reviewId = reviewId;
	}
}
