package edu.ucr.cs.dblab.nle020.ontology;

import java.util.HashSet;
import java.util.Set;

public class SnomedNode {
	private long SCUI;
	private Set<SnomedNode> outSet = new HashSet<SnomedNode>();		// Outgoing edges
	private Set<SnomedNode> inSet = new HashSet<SnomedNode>();
	public SnomedNode(long sCUI) {
		super();
		SCUI = sCUI;
	}
	
	public void addOutEdge(SnomedNode child) {
		outSet.add(child);
		child.inSet.add(this);
	}
	
	public void addInEdge(SnomedNode parent) {
		inSet.add(parent);
		parent.outSet.add(this);
	}
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		
		builder.append("SCUI: " + SCUI);
		builder.append(" outSet: " + outSet.toString());
		builder.append(" inSet: " + inSet.toString());
		
		return builder.toString();
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (SCUI ^ (SCUI >>> 32));
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
		SnomedNode other = (SnomedNode) obj;
		if (SCUI != other.SCUI)
			return false;
		return true;
	}
	public long getSCUI() {
		return SCUI;
	}
	public void setSCUI(long sCUI) {
		SCUI = sCUI;
	}
	public Set<SnomedNode> getOutSet() {
		return outSet;
	}
	public void setOutSet(Set<SnomedNode> outSet) {
		this.outSet = outSet;
	}
	public Set<SnomedNode> getInSet() {
		return inSet;
	}
	public void setInSet(Set<SnomedNode> inSet) {
		this.inSet = inSet;
	}	
}
