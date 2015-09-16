package edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset;

/**
 * 
 * @author Thong Nhat
 *
 */
public class RawReview {
	
	private int id = 0;
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
	
	public RawReview () {
		docID = 0;
		rate = 0;
		title = null;
		body = null;
	}
	
	public RawReview(int id) {
		this.id = id;
	}
	
	public RawReview(int id, int docID, String title, String body, int rate) {
		this.id = id;
		this.docID = docID;
		this.title = title;
		this.body = body;
		this.rate = rate;
	}

	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + id;
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
		RawReview other = (RawReview) obj;
		if (id != other.id)
			return false;
		return true;
	}
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}	
}


