package edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset;

/**
 * 
 * @author Thong Nhat
 *
 */
public class RawReview {
	
	private String id = "id";
	private String docID = "docId";
	private String title;
	private String body;
	private int rate = 0;
	
	public String getDocID() {
		return docID;
	}
	public void setDocID(String docID) {
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
	
	public RawReview () {
		rate = 0;
		title = null;
		body = null;
	}
	
	public RawReview(String id) {
		this.id = id;
	}
	
	public RawReview(String id, String docID, String title, String body, int rate) {
		this.id = id;
		this.docID = docID;
		this.title = title;
		this.body = body;
		this.rate = rate;
	}

	@Override
	public int hashCode() {
		return com.google.common.base.Objects.hashCode(id, docID, title, body, rate);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		RawReview other = (RawReview) obj;
		return id.equals(other.id);
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}	
}


