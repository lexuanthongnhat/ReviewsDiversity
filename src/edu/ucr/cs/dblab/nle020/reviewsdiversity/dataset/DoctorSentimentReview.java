package edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset;

import java.util.List;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentReview;

public class DoctorSentimentReview {
	private Integer docId;
	private List<SentimentReview> sentimentReviews;
	public Integer getDocId() {
		return docId;
	}
	public void setDocId(Integer docId) {
		this.docId = docId;
	}
	public List<SentimentReview> getSentimentReviews() {
		return sentimentReviews;
	}
	public void setSentimentReviews(List<SentimentReview> sentimentReviews) {
		this.sentimentReviews = sentimentReviews;
	}
	
	public DoctorSentimentReview() {
		super();
	}
	
	public DoctorSentimentReview(Integer docId,
			List<SentimentReview> sentimentReviews) {
		super();
		this.docId = docId;
		this.sentimentReviews = sentimentReviews;
	}
	
}
