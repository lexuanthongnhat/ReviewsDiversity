package edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset;

/**
 * 
 * @author Thong Nhat
 *
 */
public class Review {
	
	private int docID = 0;
	private String title;
	private String body;
	private int rate = 0;
	public int getDocID() {
		return docID;
	}
	public void setDocID(int docID) {
		this.docID = docID;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getBody() {
		return body;
	}
	public void setBody(String body) {
		this.body = body;
	}
	public int getRate() {
		return rate;
	}
	public void setRate(int rate) {
		this.rate = rate;
	}
	
	public Review () {
		docID = 0;
		rate = 0;
		title = null;
		body = null;
	}
	
	public Review(int docID, String title, String body, int rate) {
		this.docID = docID;
		this.title = title;
		this.body = body;
		this.rate = rate;
	}
}
