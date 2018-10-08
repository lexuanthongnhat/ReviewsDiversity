package edu.ucr.cs.dblab.nle020.reviewsdiversity.units;

import java.util.List;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset.RawReview;

public class SentimentReview extends SentimentSet {

	private RawReview rawReview;
	private List<SentimentSentence> sentences;

	@SuppressWarnings("unused")
	public SentimentReview () {
		super();
	}
	
	public SentimentReview(String id) {
		super(id);
	}
	
	public SentimentReview(String id, List<ConceptSentimentPair> pairs, RawReview rawReview) {
		super(id, pairs);
		this.rawReview = rawReview;		
	}
	
	
	public SentimentReview(String id, List<ConceptSentimentPair> pairs,
			RawReview rawReview,
			List<SentimentSentence> sentences) {
		super(id, pairs);
		this.rawReview = rawReview;
		this.sentences = sentences;
	}

	public RawReview getRawReview() {
		return rawReview;
	}

	public void setRawReview(RawReview rawReview) {
		this.rawReview = rawReview;
	}

	public List<SentimentSentence> getSentences() {
		return sentences;
	}

	public void setSentences(List<SentimentSentence> sentences) {
		this.sentences = sentences;
	}
}
