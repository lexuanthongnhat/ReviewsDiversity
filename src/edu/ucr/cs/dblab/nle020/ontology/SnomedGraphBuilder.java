package edu.ucr.cs.dblab.nle020.ontology;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.ucr.cs.dblab.nle020.utils.Utils;

public class SnomedGraphBuilder {

	public final static long ROOT_SNOMED = 138875005;
	public final static String ROOT_DEWEY = "$";
	private SnomedGraph graph;
	
	public SnomedGraphBuilder() {
		graph = new SnomedGraph();
		
		SnomedNode rootNode = new SnomedNode(ROOT_SNOMED);
		graph.addNode(rootNode);
	}
	
	public void buildGraph(String relationshipPath, String outputPath) {
		
		importGraph(relationshipPath);
		exportGraph(outputPath);
	}

	private void importGraph(String relationshipPath) {
		long startTime = System.currentTimeMillis();
		System.out.println("Heapsize: " + Runtime.getRuntime().totalMemory() + "\t" + new Date());
		
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(relationshipPath))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String[] columns = line.split("\\t");

				// Active && "is-a" relationship
				if (columns[2].equals("1") && columns[7].equals("116680003"))
					addEdgeToGraph(Long.parseLong(columns[5]), Long.parseLong(columns[4]));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}		
		
		Utils.printRunningTime(startTime, "Finished importing Snomed Hierarchy");
		System.err.println("# Nodes: " + graph.getNodeNum() + ", Out-Degree Average: " + graph.getOutDegreeAverage());
	}
	
	private void addEdgeToGraph(long parent, long child) {
		graph.addEdge(parent, child);		
	}

	private void exportGraph(String outputPath) {

		long startTime = System.currentTimeMillis();
		System.out.println("Heapsize before exporting graph: " + Runtime.getRuntime().totalMemory() + "\t" + new Date());
		
		Map<Long, Set<String>> SCUIToDeweys = assignDeweyLabels();
		
		System.out.println("Heapsize after exporting graph: " + Runtime.getRuntime().totalMemory() + "\t" + new Date());

		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath), 
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			
			for (Long SCUI : SCUIToDeweys.keySet()) {
				String deweys = "";
				for (String dewey : SCUIToDeweys.get(SCUI)) {
					if (deweys.length() == 0)
						deweys = dewey;
					else 
						deweys = deweys + "," + dewey;
				}
				writer.append(SCUI + "\t" + deweys);
				writer.newLine();
			}			
			writer.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		Utils.printRunningTime(startTime, "Assigned dewey labels");
		SCUIToDeweys = null;
	}

	
	private Map<Long, Set<String>> assignDeweyLabels() {
		Map<Long, Set<String>> SCUIToDeweys = new HashMap<Long, Set<String>>();
		
		// Init root
		SCUIToDeweys.put(ROOT_SNOMED, new HashSet<String>());
		SCUIToDeweys.get(ROOT_SNOMED).add(ROOT_DEWEY);
		
		SnomedNode rootNode = graph.getNode(ROOT_SNOMED);		
		Set<SnomedNode> workingNodes = new HashSet<SnomedNode>();		
		Set<SnomedNode> nextWorkingNodes = new HashSet<SnomedNode>();
		nextWorkingNodes.add(rootNode);
		
		do {			
			workingNodes.clear();
			workingNodes.addAll(nextWorkingNodes);
			nextWorkingNodes = new HashSet<SnomedNode>();
			
			for (SnomedNode workingNode : workingNodes) {				// Do all nodes of this level
				List<SnomedNode> childs = new ArrayList<SnomedNode>(); 
				childs.addAll(workingNode.getOutSet());
				for (int i = 0; i < childs.size(); ++i) {
					Long childSCUI = childs.get(i).getSCUI();
					
					if (!SCUIToDeweys.containsKey(childSCUI))
						SCUIToDeweys.put(childSCUI, new HashSet<String>());
					
					for (String parentDewey : SCUIToDeweys.get(workingNode.getSCUI())) {
						SCUIToDeweys.get(childSCUI).add(parentDewey + "." + i);
					}
				}				
				
				nextWorkingNodes.addAll(workingNode.getOutSet());
			}
		} while (nextWorkingNodes.size() >= 1);
		
		return SCUIToDeweys;
	}

	public SnomedGraph getGraph() {
		return graph;
	}	
}
