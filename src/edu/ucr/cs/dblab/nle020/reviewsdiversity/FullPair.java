package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;

public class FullPair implements Comparable<FullPair> {
	private String id;
	
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

	// For Greedy Set, keep track of the parent
	private SentimentSet parent = null;
	
	
	// For debugging
	private List<String> deweys = new ArrayList<String>();
	
	public FullPair(String id) {
		this.id = id;
	}
	public FullPair(String id, SentimentSet parent) {
		this.id = id;
		this.parent = parent;
	}
	
	@Override
	public int compareTo(FullPair o) {		
		return (this.benefit - o.benefit);
	}
	
	
	@Override
	public	String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("{\"id\": " + id);
		builder.append(", \"benefit\": " + benefit);
		
		if (host != null) {
			builder.append(", \"host\": " + host.getId());
/*			if (host.getParent() != null)
				builder.append(" p " + host.getParent().getId());*/
		}
		
/*		if (parent != null)
			builder.append(", \"parent\": " + parent.getId());*/
		
		builder.append(", \"potentialHost\": [");
		for (FullPair host : potentialHosts) {
			builder.append(host.id);
			if (host.getParent() != null)
				builder.append(" p " + host.getParent().getId());
			builder.append(", ");
		}
		if (potentialHosts.size() >= 1) {
			builder.delete(builder.length() - 2, builder.length());
		}		
		builder.append("]");
		
		
		builder.append(", \"customerMap\": [");
		for (FullPair customer : customerMap.keySet()) {
			builder.append(customer.id + ": " + customerMap.get(customer) + ", ");
		}
		if (customerMap.size() >= 1) {
			builder.delete(builder.length() - 2, builder.length());
		}
		builder.append("]}");
		
		return builder.toString();
	}	



	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
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
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		return true;
	}
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
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
	
	public void addCustomer(FullPair pair, int distance) {
		customerMap.put(pair, distance);
	}
	
	public void removeCustomer(FullPair customer) {
		customerMap.remove(customer);
	}
	
	public Integer distanceToCustomer(FullPair customer) {
		return customerMap.get(customer);
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
	
	public void addPotentialHost(FullPair host) {
		potentialHosts.add(host);
	}
	
	public void removePotentialHost(FullPair host) {
		potentialHosts.remove(host);
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

	public SentimentSet getParent() {
		return parent;
	}

	public void setParent(SentimentSet parent) {
		this.parent = parent;
	}
}
