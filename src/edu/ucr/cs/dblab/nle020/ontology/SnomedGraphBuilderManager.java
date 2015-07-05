package edu.ucr.cs.dblab.nle020.ontology;

import java.util.Date;

public class SnomedGraphBuilderManager {
	private final static String RELATIONSHIP_PATH = "resources/sct2_StatedRelationship_Snapshot_US1000124_20140301.txt";
	public final static String SNOMED_DEWEYS_PATH = "src/edu/ucr/cs/dblab/nle020/ontology/snomed_deweys.txt";
	
	private static SnomedGraph graph;
	
	public static void main(String[] args) {
		System.out.println("Heapsize: " + Runtime.getRuntime().totalMemory() + "\t" + new Date());
		SnomedGraphBuilder graphBuilder = new SnomedGraphBuilder();
		graphBuilder.buildGraph(RELATIONSHIP_PATH, SNOMED_DEWEYS_PATH);
		
		graph = graphBuilder.getGraph();
		System.out.println("Heapsize: " + Runtime.getRuntime().totalMemory() + "\t" + new Date());
	}
}
