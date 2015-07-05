package edu.ucr.cs.dblab.nle020.umls;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

public class UMLSDBUtil {
	
	private final static String UMLS_NAME = "umls2015";
//	private final static String UMLS_NAME = "umls_2012";
	
	private String snomedSAB = "SNOMEDCT_US";
	
	private DBUtil umlsDB;
	private Statement st;
	
	public DBUtil getUmlsDB() {
		return umlsDB;
	}

	public UMLSDBUtil() {
		umlsDB = new DBUtil();		

		try {
			umlsDB.setConnection(UMLS_NAME);
			st = umlsDB.getConnection().createStatement();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if (UMLS_NAME.equals("umls_2012"))
			snomedSAB = "SNOMEDCT";
	}
	
	public void close() {
		try {
			st.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	
	public Set<String> cuiToSnomed(String CUI) {
		Set<String> result = new HashSet<String>();
		
		try {
			String query = "SELECT SCUI FROM mrconso "
					+ "WHERE sab = '" + snomedSAB + "' AND lat = 'ENG' AND tty = 'FN' AND stt = 'PF' "
					+ "AND ispref = 'Y' AND mrconso.CUI = '" + CUI + "';";
			
			ResultSet rs = st.executeQuery(query);
			if (rs == null)
				System.err.println("Can't find SCUI of " + CUI);
			else
				while (rs.next()) {
					String SCUI = rs.getString("SCUI"); 
					if (SCUI != null) {
						result.add(SCUI);
					}
				}
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		
		return result;
	}
	
	public Set<String> cuiToSnomed(String CUI, String validTuis) {
		Set<String> result = new HashSet<String>();
		
		try {
			ResultSet rs = st.executeQuery("SELECT mrconso.SCUI FROM mrconso INNER JOIN mrsty ON mrsty.cui = mrconso.cui"
					+ " INNER JOIN srdef ON srdef.UI = mrsty.tui WHERE abr IN " + validTuis
					+ " AND sab = '" + snomedSAB + "' AND lat = 'ENG' AND tty = 'FN' AND stt = 'PF' AND ispref = 'Y'"
					+ " AND mrconso.CUI = '" + CUI + "';");
			while (rs.next()) {
				String SCUI = rs.getString("SCUI"); 
				if (SCUI != null) {
					result.add(SCUI);
				}
			}
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		
		return result;
	}
}
