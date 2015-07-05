package edu.ucr.cs.dblab.nle020.reviewsdiversity;

public class PurePair {
	private String CUI;
	private float sentiment;
	public String getCUI() {
		return CUI;
	}
	public void setCUI(String cUI) {
		CUI = cUI;
	}
	public float getSentiment() {
		return sentiment;
	}
	public void setSentiment(float sentiment) {
		this.sentiment = sentiment;
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((CUI == null) ? 0 : CUI.hashCode());
		result = prime * result + Float.floatToIntBits(sentiment);
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
		PurePair other = (PurePair) obj;
		if (CUI == null) {
			if (other.CUI != null)
				return false;
		} else if (!CUI.equals(other.CUI))
			return false;
		if (Float.floatToIntBits(sentiment) != Float
				.floatToIntBits(other.sentiment))
			return false;
		return true;
	}
	public PurePair(String cUI, float sentiment) {
		super();
		CUI = cUI;
		this.sentiment = sentiment;
	}	
	
}
