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
import edu.ucr.cs.dblab.nle020.metamap.SemanticGroups;
import edu.ucr.cs.dblab.nle020.metamap.SemanticGroupsByTUI;
import edu.ucr.cs.dblab.nle020.umls.DBUtil;
import edu.ucr.cs.dblab.nle020.utilities.Utils;

public class CUIToSnomedMapper {
	
	public final static String CUI_SNOMEDS_PATH = "src/edu/ucr/cs/dblab/nle020/ontology/cui_snomeds.txt";

	Map<String, Set<String>> CUIToSnomeds = new HashMap<String, Set<String>>();
	
	private final static String UMLS_NAME = "umls2015";
//	private final static String UMLS_NAME = "umls_2012";
	private String snomedSAB = "SNOMEDCT_US";
	
	private DBUtil umlsDB;
	private Statement st;
	
	// This block is unused///////////////////////////////////////////////////
	private Set<String> invalidTUIs = new HashSet<String>();
	private Set<String> invalidSemanticGroups = new HashSet<String>();
	private Set<String> erroneousCUIs = new HashSet<String>();
	private Set<String> exceptionalCUIS = new HashSet<String>();
	//////////////////////////////////////////////////////////////////////////
	
	private void init() {
//		initInvalidTUIs();
		initErroneousCUIs("src/edu/ucr/cs/dblab/nle020/metamap/erroneous_cuis.txt");
		initExceptionalCUIs("src/edu/ucr/cs/dblab/nle020/metamap/exceptional_cuis.txt");
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
	
	public DBUtil getUmlsDB() {
		return umlsDB;
	}

	public CUIToSnomedMapper() {
		umlsDB = new DBUtil();	
		init();

		try {
			umlsDB.setConnection(UMLS_NAME);
			st = umlsDB.getConnection().createStatement();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		if (UMLS_NAME.equals("umls_2012"))
			snomedSAB = "SNOMEDCT";
	}
	
	// This method use the join and projection, too slow
	public void importingDateWithFilters() {
		ResultSet rs;
		
/*		StringBuilder invalidTUIsBuilder = new StringBuilder();
		invalidTUIsBuilder.append("(");
		for (String TUI : invalidTUIs) {
			invalidTUIsBuilder.append("'" + TUI + "',");
		}
		if (invalidTUIsBuilder.length() > 2)
			invalidTUIsBuilder.deleteCharAt(invalidTUIsBuilder.length() - 1);
		invalidTUIsBuilder.append(")");*/
		
/*		String query = "SELECT DISTINCT mrconso.CUI, mrconso.SCUI FROM mrconso INNER JOIN mrsty ON mrconso.CUI = mrsty.CUI"
				+ " WHERE TUI NOT IN " + invalidTUIsBuilder.toString() + " AND LAT=\"ENG\" AND SAB=\"" + snomedSAB + "\" "
				+ "AND TTY=\"FN\" AND STT=\"PF\" AND ISPREF=\"Y\";";*/
		String query = "SELECT DISTINCT mrconso.CUI, mrconso.SCUI, mrsty.TUI FROM mrconso INNER JOIN mrsty ON mrconso.CUI = mrsty.CUI"
				+ " WHERE LAT=\"ENG\" AND SAB=\"" + snomedSAB + "\" "
				+ "AND TTY=\"FN\" AND STT=\"PF\" AND ISPREF=\"Y\";";		
		
		try {
			rs = st.executeQuery(query);
			while (rs.next()) {
				if (!invalidTUIs.contains(rs.getString("TUI"))) {
					String CUI = rs.getString("CUI");
					String SCUI = rs.getString("SCUI");
					
					if (!erroneousCUIs.contains(CUI)) {
						if (CUI != null && SCUI != null) {
							if (!CUIToSnomeds.containsKey(CUI))
								CUIToSnomeds.put(CUI, new HashSet<String>());
							
							CUIToSnomeds.get(CUI).add(SCUI);
						}
					}
				}
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		// Exceptional CUIs
		for (String CUI : exceptionalCUIS) {
			if (!CUIToSnomeds.containsKey(CUI)) {
				query = "SELECT DISTINCT CUI, SCUI FROM umls2015.mrconso "
						+ "WHERE LAT=\"ENG\" AND SAB=\"" + snomedSAB + "\" "
						+ "AND TTY=\"FN\" AND STT=\"PF\" AND ISPREF=\"Y\" AND CUI='" + CUI + "';";
				
				try {
					rs = st.executeQuery(query);
					while (rs.next()) {
						String SCUI = rs.getString("SCUI");
						
						if (SCUI != null) {
							if (!CUIToSnomeds.containsKey(CUI))
								CUIToSnomeds.put(CUI, new HashSet<String>());
							
							CUIToSnomeds.get(CUI).add(SCUI);
						}
					}
					rs.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public void importingData() {		
		ResultSet rs;
		String query = "SELECT DISTINCT CUI, SCUI FROM umls2015.mrconso "
				+ "WHERE LAT=\"ENG\" AND SAB=\"" + snomedSAB + "\" "
				+ "AND TTY=\"FN\" AND STT=\"PF\" AND ISPREF=\"Y\"";
		
		try {
			rs = st.executeQuery(query);
			while (rs.next()) {
				String CUI = rs.getString("CUI");
				String SCUI = rs.getString("SCUI");
				
				if (CUI != null && SCUI != null) {
					if (!CUIToSnomeds.containsKey(CUI))
						CUIToSnomeds.put(CUI, new HashSet<String>());
					
					CUIToSnomeds.get(CUI).add(SCUI);
				}
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

	}
	
	public void exportData() {
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(CUI_SNOMEDS_PATH), 
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			for (String CUI : CUIToSnomeds.keySet()) {
				StringBuilder SCUIs = new StringBuilder();
				for (String SCUI : CUIToSnomeds.get(CUI)) {
					SCUIs = SCUIs.append(SCUI + ",");
				}
				if (SCUIs.length() > 0)
					SCUIs.deleteCharAt(SCUIs.length() - 1);
				
				writer.append(CUI + "\t" + SCUIs.toString());
				writer.newLine();
			}
			
			writer.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void closeAll() {
		if (st != null)
			try {
				st.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		
		umlsDB.closeAll();		
	}
	
	public int getCUINum() {
		return CUIToSnomeds.keySet().size();
	}
	
	public static void main(String[] args) {
		long startTime = System.currentTimeMillis();		
		Utils.printTotalHeapSize("Heapsize BEFORE getting CUIToSnomed");
		
		CUIToSnomedMapper mapper = new CUIToSnomedMapper();
		mapper.importingData();
//		mapper.importingDateWithFilters();
		mapper.exportData();
		mapper.closeAll();
		
		Utils.printTotalHeapSize("Heapsize AFTER getting CUIToSnomed");
		Utils.printRunningTime(startTime, "Finished " + mapper.getCUINum() + " CUIs");
	}
}
