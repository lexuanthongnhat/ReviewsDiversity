package edu.ucr.cs.dblab.nle020.metamap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 
 * @author Thong Nhat
 *
 */
public class SemanticGroupsByTUI {

	private static Map<String, Set<String>> groupMap = new HashMap<String, Set<String>>();

	// Activities & Behaviors
	public static final Set<String> ACTI = new HashSet<String>(Arrays.asList(
			"T052", "T053", "T056", "T051", "T064", 
			"T055",	"T066", "T057", "T054"));

	// Anatomy
	public static final Set<String> ANAT = new HashSet<String>(Arrays.asList(
			"T017", "T029", "T023", "T030", "T031", 
			"T022", "T025", "T026", "T018", "T021", "T024"));
	
	// Chemicals & Drugs
	public static final Set<String> CHEM = new HashSet<String>(Arrays.asList(
			"T116", "T195", "T123", "T122", "T118", 
			"T103", "T120", "T104", "T200", "T111", 
			"T196", "T126", "T131", "T125", "T129", 
			"T130", "T197", "T119", "T124", "T114", 
			"T109", "T115", "T121", "T192", "T110", "T127"));
	// Concepts & Ideas
	public static final Set<String> CONC = new HashSet<String>(Arrays.asList(
			"T185", "T077", "T169", "T102", "T078",
			"T170", "T171", "T080", "T081", "T089", 
			"T082", "T079"));
	
	// Devices
	public static final Set<String> DEVI = new HashSet<String>(Arrays.asList(
			"T203", "T074", "T075"));
	
	// Disorders
	public static final Set<String> DISO = new HashSet<String>(Arrays.asList(
			"T020", "T190", "T049", "T019", "T047", 
			"T050", "T033", "T037", "T048", "T191", 
			"T046", "T184"));
	
	// Genes & Molecular Sequences
	public static final Set<String> GENE = new HashSet<String>(Arrays.asList(
			"T087", "T088", "T028", "T085", "T086"));
	
	// Geographic Areas
	public static final Set<String> GEOG = new HashSet<String>(Arrays.asList("T083"));
	
	// Living Beings
	public static final Set<String> LIVB = new HashSet<String>(Arrays.asList(
			"T100", "T011", "T008", "T194", "T007",
			"T012", "T204", "T099", "T013", "T004",
			"T096", "T016", "T015", "T001", "T101", 
			"T002", "T098", "T097", "T014", "T010", "T005"));
	
	// Objects
	public static final Set<String> OBJC = new HashSet<String>(Arrays.asList(
			"T071", "T168", "T073", "T072", "T167"));
	
	// Occupations
	public static final Set<String> OCCU = new HashSet<String>(Arrays.asList("T091", "T090"));
	
	// Organizations
	public static final Set<String> ORGA = new HashSet<String>(Arrays.asList(
			"T093", "T092", "T094", "T095"));
	
	// Phenomena
	public static final Set<String> PHEN = new HashSet<String>(Arrays.asList(
			"T038", "T069", "T068", "T034", "T070", "T067"
			));
	
	// Physiology
	public static final Set<String> PHYS = new HashSet<String>(Arrays.asList(
			"T043", "T201", "T045", "T041", "T044", 
			"T032", "T040", "T042", "T039"));
	
	// Procedures
	public static final Set<String> PROC = new HashSet<String>(Arrays.asList(
			"T060", "T065", "T058", "T059", "T063", 
				"T062", "T061"));

	static {		
		groupMap.put(SemanticConstants.ACTI, ACTI);
		groupMap.put(SemanticConstants.ANAT, ANAT);
		
		groupMap.put(SemanticConstants.CHEM, CHEM);
		groupMap.put(SemanticConstants.CONC, CONC);
		
		groupMap.put(SemanticConstants.DEVI, DEVI);
		groupMap.put(SemanticConstants.DISO, DISO);
		
		groupMap.put(SemanticConstants.GENE, GENE);
		groupMap.put(SemanticConstants.GEOG, GEOG);
		
		groupMap.put(SemanticConstants.LIVB, LIVB);
		
		groupMap.put(SemanticConstants.OBJC, OBJC);
		groupMap.put(SemanticConstants.OCCU, OCCU);		
		groupMap.put(SemanticConstants.ORGA, ORGA);
		
		groupMap.put(SemanticConstants.PHEN, PHEN);
		groupMap.put(SemanticConstants.PHYS, PHYS);
		groupMap.put(SemanticConstants.PROC, PROC);
	}
	
	private SemanticGroupsByTUI() {
		throw new AssertionError("This class is noninstantiable");
	}
	
	// Guarantee: each TUI belong to only one group
	public static String getSemanticGroup(String TUI) {
		String result = null;
		TUI = TUI.toUpperCase();
		
		if (ACTI.contains(TUI))
			result = SemanticConstants.ACTI;
		else if (ANAT.contains(TUI))
			result = SemanticConstants.ANAT;
		else if (CHEM.contains(TUI))
			result = SemanticConstants.CHEM;
		else if (CONC.contains(TUI))
			result = SemanticConstants.CONC;
		else if (DEVI.contains(TUI))
			result = SemanticConstants.DEVI;
		else if (DISO.contains(TUI))
			result = SemanticConstants.DISO;
		else if (GENE.contains(TUI))
			result = SemanticConstants.GENE;
		else if (GEOG.contains(TUI))
			result = SemanticConstants.GEOG;
		else if (LIVB.contains(TUI))
			result = SemanticConstants.LIVB;
		else if (OBJC.contains(TUI))
			result = SemanticConstants.OBJC;
		else if (OCCU.contains(TUI))
			result = SemanticConstants.OCCU;
		else if (ORGA.contains(TUI))
			result = SemanticConstants.ORGA;
		else if (PHEN.contains(TUI))
			result = SemanticConstants.PHEN;
		else if (PHYS.contains(TUI))
			result = SemanticConstants.PHYS;
		else if (PROC.contains(TUI))
			result = SemanticConstants.PROC;
		
		return result;
	}
	
	// Guarantee: each TUI belong to only one group
	public static String getSemanticGroupName(String TUI) {
		String result = null;
		TUI = TUI.toUpperCase();
		
		if (ACTI.contains(TUI))
			result = SemanticConstants.ACTI_NAME;
		else if (ANAT.contains(TUI))
			result = SemanticConstants.ANAT_NAME;
		else if (CHEM.contains(TUI))
			result = SemanticConstants.CHEM_NAME;
		else if (CONC.contains(TUI))
			result = SemanticConstants.CONC_NAME;
		else if (DEVI.contains(TUI))
			result = SemanticConstants.DEVI_NAME;
		else if (DISO.contains(TUI))
			result = SemanticConstants.DISO_NAME;
		else if (GENE.contains(TUI))
			result = SemanticConstants.GENE_NAME;
		else if (GEOG.contains(TUI))
			result = SemanticConstants.GEOG_NAME;
		else if (LIVB.contains(TUI))
			result = SemanticConstants.LIVB_NAME;
		else if (OBJC.contains(TUI))
			result = SemanticConstants.OBJC_NAME;
		else if (OCCU.contains(TUI))
			result = SemanticConstants.OCCU_NAME;
		else if (ORGA.contains(TUI))
			result = SemanticConstants.ORGA_NAME;
		else if (PHEN.contains(TUI))
			result = SemanticConstants.PHEN_NAME;
		else if (PHYS.contains(TUI))
			result = SemanticConstants.PHYS_NAME;
		else if (PROC.contains(TUI))
			result = SemanticConstants.PROC_NAME;
		
		return result;
	}
	
	public static Set<String> getTUIsOfGroup(String group) {
		group = group.toUpperCase().trim();
		
		if (groupMap.keySet().contains(group))
			return groupMap.get(group);
		else 
			return null;					
	}
}
