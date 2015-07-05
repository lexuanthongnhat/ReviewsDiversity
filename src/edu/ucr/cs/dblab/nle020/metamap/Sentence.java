package edu.ucr.cs.dblab.nle020.metamap;

/**
 * Actually, this class corresponds to utterance in MetaMap. However, I must change the name. There exist the case that a utterance contain several literal sentences.
 * @author Thong Nhat
 *
 */
public class Sentence {
	private volatile int hashCode;			// To cache the hashCode
	
	private String id;
	private String string;
	private int startPos;
	private int length;
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getString() {
		return string;
	}
	public void setString(String string) {
		this.string = string;
	}
	public int getStartPos() {
		return startPos;
	}
	public void setStartPos(int startPos) {
		this.startPos = startPos;
	}
	public int getLength() {
		return length;
	}
	public void setLength(int length) {
		this.length = length;
	}
	public Sentence(String id, String string, int startPos, int length) {
		super();
		this.id = id;
		this.string = string;
		this.startPos = startPos;
		this.length = length;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;		
		int result = hashCode;
		if (result == 0) {
			result = 1;
			result = prime * result + ((id == null) ? 0 : id.hashCode());
			result = prime * result + length;
			result = prime * result + startPos;
			result = prime * result + ((string == null) ? 0 : string.hashCode());
		}
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
		Sentence other = (Sentence) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (length != other.length)
			return false;
		if (startPos != other.startPos)
			return false;
		if (string == null) {
			if (other.string != null)
				return false;
		} else if (!string.equals(other.string))
			return false;
		return true;
	}	
}
