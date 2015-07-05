package edu.ucr.cs.dblab.nle020.ontology;

import java.util.HashMap;
import java.util.Map;

public class SnomedGraph {
	Map<Long, SnomedNode> nodes = new HashMap<Long, SnomedNode>();

	public SnomedGraph() {
		super();
	}

	public Map<Long, SnomedNode> getNodes() {
		return nodes;
	}

	public void setNodes(Map<Long, SnomedNode> nodes) {
		this.nodes = nodes;
	}
	
	public void addEdge(Long parent, Long child) {
		if (!nodes.containsKey(parent)) 
			nodes.put(parent, new SnomedNode(parent));

		if (!nodes.containsKey(child)) 
			nodes.put(child, new SnomedNode(child));
		
		nodes.get(parent).addOutEdge(nodes.get(child));			// Automatically add in-edge
	}
	
	public void addNode(Long SCUI) {
		if (!nodes.containsKey(SCUI))
			nodes.put(SCUI, new SnomedNode(SCUI));
	}
	
	public void addNode(SnomedNode node) {
		if (!nodes.containsKey(node.getSCUI()))
			nodes.put(node.getSCUI(), node);
	}
	
	public boolean isInGraph(Long SCUI) {
		if (nodes.containsKey(SCUI))
			return true;
		else
			return false;
	}
	
	public int getNodeNum() {
		return nodes.size();
	}
	
	public SnomedNode getNode(Long SCUI) {
		if (nodes.containsKey(SCUI))
			return nodes.get(SCUI);
		else 
			return null;
	}
	
	public float getOutDegreeAverage() {
		long sum = 0;
		for (SnomedNode node : nodes.values()) {
			sum += node.getOutSet().size();
		}
		
		return ((float) sum) / (float) nodes.size();
	}
}
