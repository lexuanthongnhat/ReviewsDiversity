package edu.ucr.cs.dblab.nle020.reviewsdiversity.units;

import java.util.HashMap;
import java.util.Map;

public class PairDistances {
	// Cache the distances to avoid calculating again and again
	private final static Map<String, Map<String, Integer>> distances = new HashMap<String, Map<String, Integer>>();
	
	public static Integer getDistance(String cui1, String cui2) {
		if (distances.get(cui1) == null || distances.get(cui1).get(cui2) == null) 
			return null;
		else 
			return distances.get(cui1).get(cui2);
	}
	
	public static void putDistances(String cui1, String cui2, Integer distance) {
		if (!distances.containsKey(cui1))
			distances.put(cui1, new HashMap<String, Integer>());

		distances.get(cui1).put(cui2, distance);
	}
}
