package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class FullPair implements Comparable<FullPair> {
	private String CUI;
	private float sentiment;	
	private float threshold = 0.0f;
	
	public FullPair(String cUI, float sentiment) {
		super();
		CUI = cUI;
		this.sentiment = sentiment;
		
		if (CUI.equals(Constants.ROOT_CUI))
			host = this;
	}	
	
	public FullPair(String cUI, float sentiment, float threshold) {
		super();
		CUI = cUI;
		this.sentiment = sentiment;
		this.threshold = threshold;
	}
		
	// The current closest ancestor/facility who cover this pair
	private FullPair host;
	
	// Used to know which pairs to update their benefits after each iteration
	//		Don't include ROOT because the ROOT is default
	//		Don't include the host
	private Set<FullPair> potentialHosts = new HashSet<FullPair>();
	
	// Current pairs that have this pair as their host/facility
	//		KEY: customer, VALUE: distance to the customer
	private ConcurrentMap<FullPair, Integer> customerMap = new ConcurrentHashMap<FullPair, Integer>();
	
	// The possible decrease in the cost if we choose this pair 
	private int benefit = 0;
	
	@Override
	public int compareTo(FullPair o) {		
		return (this.benefit - o.benefit);
	}	

	public boolean isSentimentCover(FullPair p) {
		boolean result = false;
		
		if (this.CUI.equals(Constants.ROOT_CUI) || p.getCUI().equals(Constants.ROOT_CUI))
			result = true;
		else if (Math.abs(this.sentiment - p.sentiment) <= threshold)
			result =  true;
		 
		return result;
	}
	
	
	
	
	
	// For debugging
	private List<String> deweys = new ArrayList<String>();
	
	
	
	
	
	
	
	
	@Override
	public	String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("{<\"CUI\": " + CUI);
		builder.append(", \"Sentiment\": " + sentiment + ">");		
		builder.append(", \"benefit\": " + benefit);
		builder.append(", \"deweys\": " + deweys.toString());
		builder.append(", \"host\": " + host.CUI + ":"+ host.sentiment);
		builder.append(", \"customerMap\": ");
		for (FullPair customer : customerMap.keySet()) {
			builder.append("<" + customer.CUI + ":"+ customer.sentiment + ">: " + customerMap.get(customer) + ", ");
		}
		if (customerMap.size() >= 1)
			builder.delete(builder.length() - 2, builder.length());
		builder.append("}");
		
		return builder.toString();
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
		FullPair other = (FullPair) obj;
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

	public float getThreshold() {
		return threshold;
	}

	public void setThreshold(float threshold) {
		this.threshold = threshold;
	}

	public FullPair getHost() {
		return host;
	}

	public void setHost(FullPair host) {
		this.host = host;
	}

	public ConcurrentMap<FullPair, Integer> getCustomerMap() {
		return customerMap;
	}

	public void setCustomerMap(ConcurrentMap<FullPair, Integer> customerMap) {
		this.customerMap = customerMap;
	}

	public int getBenefit() {
		return benefit;
	}

	public void setBenefit(int benefit) {
		this.benefit = benefit;
	}

	public void increaseBenefit(int increment) {
		benefit += increment;
	}
	public void decreaseBenefit(int decrement) {
		benefit -= decrement;
	}

	public Set<FullPair> getPotentialHosts() {
		return potentialHosts;
	}

	public void setPotentialHosts(Set<FullPair> potentialHosts) {
		this.potentialHosts = potentialHosts;
	}

	public List<String> getDeweys() {
		return deweys;
	}

	public void setDeweys(List<String> deweys) {
		this.deweys = deweys;
	}

	public void addDeweys(Collection<? extends String> deweys) {
		this.deweys.addAll(deweys);
	}
}
