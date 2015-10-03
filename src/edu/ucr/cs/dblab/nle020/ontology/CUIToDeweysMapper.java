package edu.ucr.cs.dblab.nle020.ontology;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.ucr.cs.dblab.nle020.metamap.SemanticConstants;
import edu.ucr.cs.dblab.nle020.metamap.SemanticGroupsByTUI;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;
import edu.ucr.cs.dblab.nle020.umls.DBUtil;
import edu.ucr.cs.dblab.nle020.utils.Utils;

public class CUIToDeweysMapper {

	Map<String, Set<String>> CUIToSnomeds = new HashMap<String, Set<String>>();
	Map<Long, Set<String>> snomedToDeweys = new HashMap<Long, Set<String>>();
	
	Map<String, Set<String>> CUIToDeweysMap = new HashMap<String, Set<String>>();
	
	
	private Set<String> invalidTUIs = new HashSet<String>();
	private Set<String> invalidSemanticGroups = new HashSet<String>();
	private Set<String> erroneousCUIs = new HashSet<String>();
	private Set<String> exceptionalCUIS = new HashSet<String>();
	
	public CUIToDeweysMapper() {
		init();
	}
	
	private void init() {
		initCUIToSnomeds(CUIToSnomedMapper.CUI_SNOMEDS_PATH);
		initSnomedToDeweys(SnomedGraphBuilderManager.SNOMED_DEWEYS_PATH);
		
		
//		initInvalidTUIs();
		initErroneousCUIs("src/edu/ucr/cs/dblab/nle020/metamap/erroneous_cuis.txt");
		initExceptionalCUIs("src/edu/ucr/cs/dblab/nle020/metamap/exceptional_cuis.txt");
		
		initCUIToDeweysMap();
	}
	
	private void initCUIToSnomeds(String CUIToSnomedsPath) {
		long startTime = System.currentTimeMillis();
		Utils.printTotalHeapSize("Heapsize BEFORE readding CUIToSnomed");
		
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(CUIToSnomedsPath))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String[] columns = line.split("\\t");
				String CUI = columns[0];
				if (!CUIToSnomeds.containsKey(CUI)) 
					CUIToSnomeds.put(CUI, new HashSet<String>());
				
				for (String snomed : columns[1].split(",")) {
					CUIToSnomeds.get(CUI).add(snomed);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		Utils.printTotalHeapSize("Heapsize AFTER readding CUIToSnomed");
		Utils.printRunningTime(startTime, "Retrieved CUIToSnomed");
	}
	
	private void initSnomedToDeweys(String snomedToDeweysPath) {
		long startTime = System.currentTimeMillis();
		Utils.printTotalHeapSize("Heapsize BEFORE readding SnomedToDeweys");
		
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(snomedToDeweysPath))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String[] columns = line.split("\\t");
				Long snomed = Long.valueOf(columns[0]);
				if (!snomedToDeweys.containsKey(snomed)) 
					snomedToDeweys.put(snomed, new HashSet<String>());
				
				for (String dewey : columns[1].split(",")) {
					snomedToDeweys.get(snomed).add(dewey);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		Utils.printTotalHeapSize("Heapsize AFTER readding SnomedToDeweys");
		Utils.printRunningTime(startTime, "Retrieved SnomedToDeweys");
	}
	
	private void initInvalidTUIs() {
		invalidSemanticGroups.add(SemanticConstants.CHEM);
		invalidSemanticGroups.add(SemanticConstants.GEOG);
		invalidSemanticGroups.add(SemanticConstants.CONC);
		
		for (String group : invalidSemanticGroups) {
			invalidTUIs.addAll(SemanticGroupsByTUI.getTUIsOfGroup(group));
		}
				
		invalidTUIs.add("T041");			// Mental Process - menp
		invalidTUIs.add("T099");			// Family Group - famg
	}
	
	private void initErroneousCUIs(String filePath) {
		BufferedReader reader = null;
		
		try {
			reader = Files.newBufferedReader(Paths.get(filePath));
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.trim().length() != 0)
					erroneousCUIs.add(line.trim().toUpperCase());
			}
			
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		} 
	}
	
	private void initExceptionalCUIs(String exceptionalPath) {
		BufferedReader reader = null;
		
		try {
			reader = Files.newBufferedReader(Paths.get(exceptionalPath));
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.trim().length() != 0)
					exceptionalCUIS.add(line.trim().toUpperCase());
			}
			
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		} 
	}	
	
	
	public void initCUIToDeweysMap() {
		long startTime = System.currentTimeMillis();
		Utils.printTotalHeapSize("Heapsize BEFORE initing CUIToDeweysMap");
		
		for (String CUI : exceptionalCUIS) {
			if (!CUIToDeweysMap.containsKey(CUI) && CUIToSnomeds.get(CUI) != null) 
				CUIToDeweysMap.put(CUI, new HashSet<String>());
			
			if (CUIToSnomeds.get(CUI) != null) {
				for (String snomed : CUIToSnomeds.get(CUI)) {
					CUIToDeweysMap.get(CUI).addAll(snomedToDeweys.get(Long.valueOf(snomed)));
				}
			}
		}
		
		DBUtil umlsDB = new DBUtil();
		Statement st = null;
		final String UMLS_NAME = "umls2015";
//		private final static String UMLS_NAME = "umls_2012";

		try {
			umlsDB.setConnection(UMLS_NAME);
			st = umlsDB.getConnection().createStatement();

			StringBuilder invalidTUIsBuilder = new StringBuilder();
			invalidTUIsBuilder.append("(");
			for (String TUI : invalidTUIs) {
				invalidTUIsBuilder.append("'" + TUI + "',");
			}
			if (invalidTUIsBuilder.length() > 2)
				invalidTUIsBuilder.deleteCharAt(invalidTUIsBuilder.length() - 1);
			invalidTUIsBuilder.append(")");
			
			String query = "SELECT DISTINCT CUI FROM mrsty WHERE TUI NOT IN " + invalidTUIsBuilder.toString() + ";";
			if (invalidTUIs.size() == 0)
				query = "SELECT DISTINCT CUI FROM mrsty;";
			
			ResultSet rs = st.executeQuery(query);
			
			while (rs.next()) {
				String CUI = rs.getString("CUI");
			
				if (!erroneousCUIs.contains(CUI)) {
					if (!CUIToDeweysMap.containsKey(CUI) && CUIToSnomeds.get(CUI) != null)
						CUIToDeweysMap.put(CUI, new HashSet<String>());
					
					if (CUIToSnomeds.get(CUI) != null) {
						for (String snomed : CUIToSnomeds.get(CUI)) {
							
							Set<String> deweys = snomedToDeweys.get(Long.valueOf(snomed));
							if (deweys != null)
								CUIToDeweysMap.get(CUI).addAll(deweys);
						}
						
						if (CUIToDeweysMap.get(CUI).size() == 0)
							CUIToDeweysMap.remove(CUI);
					}
				}
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		if (st != null)
			try {
				st.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		umlsDB.closeAll();
		
		Utils.printTotalHeapSize("Heapsize AFTER initing CUIToDeweysMap");
		Utils.printRunningTime(startTime, "Finished getting CUIToDeweysMap with " + CUIToDeweysMap.keySet().size() + " CUIs");		
	}
	
	public void outputCUIToDeweys(String CUIToDeweysPath) {
		long startTime = System.currentTimeMillis();
		Utils.printTotalHeapSize("Heapsize BEFORE outputing CUIToDeweysMap");
		
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(CUIToDeweysPath), 
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			for (String CUI : CUIToDeweysMap.keySet()) {
				
				StringBuilder deweys = new StringBuilder();
				for (String dewey : CUIToDeweysMap.get(CUI)) {
					deweys = deweys.append(dewey + ",");
				}
				if (deweys.length() > 0)
					deweys.deleteCharAt(deweys.length() - 1);
				
				writer.append(CUI + "\t" + deweys.toString());
				writer.newLine();
			}
			
			writer.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		Utils.printTotalHeapSize("Heapsize AFTER outputing CUIToDeweysMap");
		Utils.printRunningTime(startTime, "Finished outputing CUIToDeweysMap with " + CUIToDeweysMap.keySet().size() + " CUIs");
	}
	
	public static void main(String[] args) {
		CUIToDeweysMapper cuiToDeweys = new CUIToDeweysMapper();
		cuiToDeweys.outputCUIToDeweys(Constants.CUI_TO_DEWEYS_PATH);
	}
}
