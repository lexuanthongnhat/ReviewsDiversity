package edu.ucr.cs.dblab.nle020.umls;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.ucr.cs.dblab.nle020.metamap.SemanticTypes;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;

public class SemanticTypeNetwork {

	public final static String ROOT = "ROOT";
	
	private static Map<String, String> TUIToNetworkType = new HashMap<String, String>();
	private static Map<String, Map<String, Integer>> networkTypeDistances = new HashMap<String, Map<String, Integer>>();
	private static Map<String, Map<String, Integer>> TUIDistances = new HashMap<String, Map<String, Integer>>();
	private static Map<String, Map<String, Integer>> typeDistances = new HashMap<String, Map<String, Integer>>();
	
	private SemanticTypeNetwork(){}
	
	static {
		initTUIToNetworkType();
		initDistances();
	}

	private static void initTUIToNetworkType() {
		
		long startTime = System.currentTimeMillis();
		try (BufferedReader reader = Files.newBufferedReader(Paths.get("src/edu/ucr/cs/dblab/nle020/umls/SRDEF.txt"))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String[] tokens = line.split("\\|");
				if (tokens[0].equalsIgnoreCase("STY")) {
					String TUI = tokens[1];
					String networkType = tokens[3];
					TUIToNetworkType.put(TUI, networkType);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		long finishTime = System.currentTimeMillis();
		System.out.println("Finish importing Network Types in " + (finishTime - startTime) + " ms");
	}

	private static void initDistances() {
		long startTime = System.currentTimeMillis();
		
		Set<String> networkTypes = new HashSet<String>();
		networkTypes.addAll(TUIToNetworkType.values());
		networkTypes.add(ROOT);
		
		for (String type : networkTypes) {
			networkTypeDistances.put(type, new HashMap<String, Integer>());
			for (String otherType : networkTypes) {
				networkTypeDistances.get(type).put(otherType, calculateDistance(type, otherType));
			}
		}

		Set<String> TUIs = new HashSet<String>();
		TUIs.addAll(TUIToNetworkType.keySet());
		TUIs.add(ROOT);
		for (String TUI : TUIs) {
			TUIDistances.put(TUI, new HashMap<String, Integer>());
			String networkType;			
			if (TUI.equals(ROOT)) 
				networkType = ROOT;
			else 
				networkType = TUIToNetworkType.get(TUI);
			
			for (String otherTUI : TUIs) {
				String otherNetworkType;			
				if (otherTUI.equals(ROOT)) 
					otherNetworkType = ROOT;
				else 
					otherNetworkType = TUIToNetworkType.get(otherTUI);
				
				TUIDistances.get(TUI).put(otherTUI, networkTypeDistances.get(networkType).get(otherNetworkType));
			}
		}
		
		Set<String> types = new HashSet<String>();
		types.add(ROOT);
		for (String TUI : TUIToNetworkType.keySet()) {
			types.add(SemanticTypes.getInstance().getType(TUI));
		}
		for (String type : types) {
			typeDistances.put(type, new HashMap<String, Integer>());
			String networkType;
			if (type.equals(ROOT))
				networkType = ROOT;
			else
				networkType = TUIToNetworkType.get(SemanticTypes.getInstance().getTUI(type).toUpperCase());
			for (String otherType : types) {
				String otherNetworkType;
				if (otherType.equals(ROOT))
					otherNetworkType = ROOT;
				else
					otherNetworkType = TUIToNetworkType.get(SemanticTypes.getInstance().getTUI(otherType).toUpperCase());	
				
				typeDistances.get(type).put(otherType, networkTypeDistances.get(networkType).get(otherNetworkType));
			}
		}
		
		long finishTime = System.currentTimeMillis();
		System.out.println("Finish calculating Network Type Distances in " + (finishTime - startTime) + " ms");
	}
	
	/**
	 * Note: the distance 	from ancestor to child is positive, 
	 * 						from child to ancestor is negative,
	 * 						of 2 nodes of different branches is INVALID_DISTANCE	 * 
	 * @param ancestor 	- network type
	 * @param child		- network type
	 */
	private static int calculateDistance(String ancestor, String child) {
		int result = Constants.INVALID_DISTANCE;
		
		if (ancestor.equalsIgnoreCase(ROOT)) {
			if (child.equalsIgnoreCase(ROOT))						// To avoid forever recursion 
				result = 0;								
			else 
				result = calculateDistance(child.substring(0, 1), child) + 1;
		} else if (child.equalsIgnoreCase(ROOT)) {
			
			result = calculateDistance(ancestor, ancestor.substring(0, 1)) - 1;
		} else {
		
			ancestor = ancestor.replace("A", "A.").replace("B", "B.");
			child = child.replace("A", "A.").replace("B", "B.");
			String[] anceTokens = ancestor.split("\\.");
			String[] chilTokens = child.split("\\.");
			
			String firstAcne = anceTokens[0].trim();
			String firstChil = chilTokens[0].trim();
			
			if (firstAcne.equalsIgnoreCase(firstChil)) {
				if (anceTokens.length <= chilTokens.length) {
					if (anceTokens.length == 1)
						result = chilTokens.length - anceTokens.length;
					else {
						boolean isAncestorAndChild = true;
						for (int i = 1; i < anceTokens.length; i++) {
							if (!anceTokens[i].trim().equals(chilTokens[i].trim())) {
								isAncestorAndChild = false;
								break;
							}
						}
						if (isAncestorAndChild)
							result = chilTokens.length - anceTokens.length;
					}
				} else {
					if (chilTokens.length == 1)
						result = chilTokens.length - anceTokens.length;
					else {
						boolean isAncestorAndChild = true;
						for (int i = 1; i < chilTokens.length; i++) {
							if (!anceTokens[i].trim().equals(chilTokens[i].trim())) {
								isAncestorAndChild = false;
								break;
							}
						}
						if (isAncestorAndChild)
							result = chilTokens.length - anceTokens.length;
					}
				}
			}
		}
		
		return result;
	}
		
	public static String getNetworkType(String TUI) {
		return TUIToNetworkType.get(TUI.toUpperCase());
	}
	
	public static int distance(String ancestorType, String childType) {
		int distance = 0;
		
		if (!ancestorType.equals(ROOT) && !childType.equals(ROOT)) {
			distance = typeDistances.get(ancestorType.toLowerCase()).get(childType.toLowerCase());
		}
		else if (ancestorType.equals(ROOT) && !childType.equals(ROOT)) {
			distance = calculateDistance(ROOT, getNetworkType(SemanticTypes.getInstance().getTUI(childType).toUpperCase()));
		} else if (!ancestorType.equals(ROOT) && childType.equals(ROOT)) {
			distance = calculateDistance(getNetworkType(SemanticTypes.getInstance().getTUI(ancestorType).toUpperCase()), ROOT);
		}
			
		return distance;
	}
	
	public static int distance(List<String> ancestorTypes, List<String> childTypes) {
		int result = Constants.INVALID_DISTANCE;
		
		int minPositive = Integer.MAX_VALUE;
		int maxNegative = Integer.MIN_VALUE;
		for (String ancestorType : ancestorTypes) {
			for (String childType : childTypes) {
				int distance = SemanticTypeNetwork.distance(ancestorType, childType);
				if (distance < 0) {
					if (distance > maxNegative)
						maxNegative = distance;
				} else {
					if (distance < minPositive) {
						minPositive = distance;
					}
				}
			}
		}
		
		if (minPositive != Integer.MAX_VALUE && maxNegative != Integer.MIN_VALUE) {
			System.err.println("Distance problem with 2 pairs: " + ancestorTypes + " and " + childTypes);
			
		} else if (minPositive != Integer.MAX_VALUE) {
			result = minPositive;
		} else if (maxNegative != Integer.MIN_VALUE) {
			result = maxNegative;
		}			
		
		return result;
	}
}
