package edu.ucr.cs.dblab.nle020.metamap;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * This class import the full list of Semantic Type including Type Indentifier, TUI and Full Name.
 * 		Note: both typeMap and tuiMap use lower case
 * @author Nhat XT Le 
 */
public class SemanticTypes {
	
	private Map<String, SemanticType> typeMap = new HashMap<String, SemanticType>();		
	private Map<String, SemanticType> tuiMap = new HashMap<String, SemanticType>();		
	
	
	private static final SemanticTypes INSTANCE = new SemanticTypes(); 
	
	public static SemanticTypes getInstance() {
		return INSTANCE;
	}
	
	private SemanticTypes() {
		
		Date start = new Date();
		try {
			BufferedReader reader = Files.newBufferedReader(Paths.get(
					"src/edu/ucr/cs/dblab/nle020/metamap/SemanticTypes_2013AA.txt"));
			
			String line = null;
			while ((line = reader.readLine()) != null) {
				String[] tokens = line.split("\\|");
				String type = tokens[0].trim().toLowerCase();
				String tui = tokens[1].trim().toLowerCase();
				String fullName = tokens[2].trim();
				if (tokens.length > 3) {
					for (int i = 3; i < tokens.length ; i++)
						fullName = fullName + " " + tokens[i];
				}
				
				SemanticType sentimentType = new SemanticType(tui, type, fullName, SemanticGroups.getSemanticGroup(type));
				
				typeMap.put(type, sentimentType);
				tuiMap.put(tui, sentimentType);				
			}
			
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();			
		}
		
		Date finish = new Date();
		System.out.println("Importing semantic types in " + (finish.getTime() - start.getTime()) + " ms");
	}
	

	public String getTUI(String type) {
		String result = null;
		
		type = type.trim();
		if (type.length() > 0 && typeMap.containsKey(type.toLowerCase())) {
			result = typeMap.get(type.toLowerCase()).getTUI();
		}
		
		return result;
	}
	
	public String getType(String TUI) {
		String result = null;
		
		TUI = TUI.trim();
		if (TUI.length() > 0 && tuiMap.containsKey(TUI.toLowerCase())) {
			result = tuiMap.get(TUI.toLowerCase()).getType();
		}
		
		return result;
	}
	
	public String getGroupByTUI(String TUI) {
		String result = null;
		
		TUI = TUI.trim();
		if (TUI.length() > 0 && tuiMap.containsKey(TUI.toLowerCase())) {
			result = tuiMap.get(TUI.toLowerCase()).getGroup();
		}
		
		return result;
	}	
	public String getGroupNameByType(String type) {
		String result = null;
		
		type = type.trim();
		if (type.length() > 0 && typeMap.containsKey(type.toLowerCase())) {
			result = SemanticGroups.getSemanticGroupName(type);
		}
		
		return result;
	}
	
	public String getGroupNameByTUI(String TUI) {
		String result = null;
		
		TUI = TUI.trim();
		if (TUI.length() > 0 && tuiMap.containsKey(TUI.toLowerCase())) {
			result = SemanticGroups.getSemanticGroupName(tuiMap.get(TUI).getType());
		}
		
		return result;
	}	
	public String getGroupByType(String type) {
		String result = null;
		
		type = type.trim();
		if (type.length() > 0 && typeMap.containsKey(type.toLowerCase())) {
			result = typeMap.get(type.toLowerCase()).getGroup();
		}
		
		return result;
	}
	
	public String getFullTypeNameByTUI(String TUI) {
		String result = null;
		
		TUI = TUI.trim();
		if (TUI.length() > 0 && tuiMap.containsKey(TUI.toLowerCase())) {
			result = tuiMap.get(TUI.toLowerCase()).getFullName();
		}
		
		return result;
	}	
	public String getFullTypeNameByType(String type) {
		String result = null;
		
		type = type.trim();
		if (type.length() > 0 && typeMap.containsKey(type.toLowerCase())) {
			result = typeMap.get(type.toLowerCase()).getFullName();
		}
		
		return result;
	}
	
	
	public SemanticType getSemanticByTUI(String TUI) {
		SemanticType result = null;
		
		TUI = TUI.trim();
		if (TUI.length() > 0 && tuiMap.containsKey(TUI.toLowerCase())) {
			result = tuiMap.get(TUI.toLowerCase());
		}
		
		return result;
	}	
	public SemanticType getSemanticByType(String type) {
		SemanticType result = null;
		
		type = type.trim();
		if (type.length() > 0 && typeMap.containsKey(type.toLowerCase())) {
			result = typeMap.get(type.toLowerCase());
		}
		
		return result;
	}
}
