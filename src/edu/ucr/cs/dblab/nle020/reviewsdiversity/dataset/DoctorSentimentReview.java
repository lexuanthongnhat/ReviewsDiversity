package edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset;

import java.util.List;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentReview;

public class DoctorSentimentReview {
	private String docId;
	private List<SentimentReview> sentimentReviews;
	public String getDocId() {
		return docId;
	}
	public void setDocId(String docId) {
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
	
	public DoctorSentimentReview(
			String docId,
			List<SentimentReview> sentimentReviews) {
		super();
		this.docId = docId;
		this.sentimentReviews = sentimentReviews;
	}
	
}
