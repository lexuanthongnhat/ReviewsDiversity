package edu.ucr.cs.dblab.nle020.ontology;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;

public class DeweyUtils {

	private final static String ROOT_DEWEY = SnomedGraphBuilder.ROOT_DEWEY;
	
	// Cache the distances to avoid calculating again and again
	private final static Map<String, Map<String, Integer>> distances = new HashMap<String, Map<String, Integer>>();
	
	public static int getDeweyDistanceFromRoot(String dewey) {
		return getDeweyDistance(ROOT_DEWEY, dewey);
	}
	
	// If the childDewey turn out to be the actual ancestor, then the returned value is negative
	// If 2 deweys are in different branches, then distance = INVALID_DISTANCE
	public static int getDeweyDistance(String ancestorDewey, String childDewey) {
		if (distances.containsKey(ancestorDewey) && distances.get(ancestorDewey).containsKey(childDewey)) {
			if (distances.get(ancestorDewey).get(childDewey) == null) {
				System.err.println("ERROR over here with " + ancestorDewey + " and " + childDewey);				
			} else			
				return distances.get(ancestorDewey).get(childDewey);		
		}
		
		int result = Constants.INVALID_DISTANCE;
			
		if (ancestorDewey.equals(childDewey))
			result = 0;
		else {
			String[] ancestor = ancestorDewey.split("\\.");
			String[] child = childDewey.split("\\.");
			
			if (ancestorDewey.equals(ROOT_DEWEY)) {
				result = getDeweyDistanceFromAncestorToChild(ancestor, child);
			} else if (childDewey.equals(ROOT_DEWEY)) {
				result = -getDeweyDistanceFromAncestorToChild(child, ancestor);
			} else {
				if (ancestor.length <= child.length)
					result = getDeweyDistanceFromAncestorToChild(ancestor, child);
				else {
				// Let see if the child turns out to be the actual ancestor
					result = -getDeweyDistanceFromAncestorToChild(child, ancestor);
					if (result == - Constants.INVALID_DISTANCE)
						result = Constants.INVALID_DISTANCE;
				}
			}
		}
		
		
		// Caching the distance
		if (!distances.containsKey(ancestorDewey))
			distances.put(ancestorDewey, new HashMap<String, Integer>());
		
		distances.get(ancestorDewey).put(childDewey, result);
		
		return result;
	}
		
	// This method always return the positive value
	// 		The first argument is thought to be the ancestor by default
	private static int getDeweyDistanceFromAncestorToChild(String[] shorter, String[] longer) {
		int result = Constants.INVALID_DISTANCE;
		
		boolean inDifferentBranch = false;
		for (int i = 0; i < shorter.length; ++i) {
			if (!shorter[i].equals(longer[i])) {
				inDifferentBranch = true;
				break;
			}			
		}
		
		if (!inDifferentBranch) 
			result = longer.length - shorter.length;
		
		return result;
	}

	public static void main(String[] args) {
		String inputFilePath = "src/edu/ucr/cs/dblab/nle020/ontology/snomed_deweys.txt";
		
		int dagHeight = 0;
		int maxNumDeweys = 0;
		List<Integer> numDeweys = new ArrayList<Integer>();
		
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(inputFilePath))) {
			String line = null;
			while ((line = reader.readLine()) != null) {
				String[] deweys = line.split("\t")[1].split(",");
				int numDewey = deweys.length;
				numDeweys.add(numDewey);
				if (maxNumDeweys < numDewey)
					maxNumDeweys = numDewey;
				
				for (String dewey : deweys) {
					String[] levels = dewey.split("\\.");
					if (dagHeight < levels.length)
						dagHeight = levels.length;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		System.out.println("dagHeight = " + dagHeight + ", maxNumDeweys = " + maxNumDeweys 
				+ ", average of numDewey = " + numDeweys.stream().collect(Collectors.averagingInt(dewey -> dewey)));
	}
}
