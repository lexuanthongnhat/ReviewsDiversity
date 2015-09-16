package edu.ucr.cs.dblab.nle020.metamap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SemanticGroups {
	private static Map<String, Set<String>> groupMap = new HashMap<String, Set<String>>();
	
	// Activities & Behaviors
	public static final Set<String> ACTI = new HashSet<String>(Arrays.asList(
			"evnt", "acty", "bhvr", "socb", "inbe", 
			"dora", "ocac", "gora", "mcha"));

	// Anatomy
	public static final Set<String> ANAT = new HashSet<String>(Arrays.asList(
			"anst", "emst", "ffas", "bdsy", "bpoc", 
			"tisu", "cell", "celc", "blor", "bsoj", "bdsu"));
	
	// Chemicals & Drugs - Matt missed "bodm"
	public static final Set<String> CHEM = new HashSet<String>(Arrays.asList(
			"chem", "chvs",	"orch", "strd", "eico", 
			"nnon", "opco", "aapp", "carb", "lipd",
			"chvf", "phsu", "bacs", "nsba", "horm", 
			"enzy", "vita", "imft",	"irda", "hops", 
			"rcpt", "antb", "elii", "inch", "clnd", "bodm"));
	// Concepts & Ideas - Matt miss "rnlw", wrong "mlw"
	public static final Set<String> CONC = new HashSet<String>(Arrays.asList(
			"cnce", "idcn", "tmco", "qlco", "qnco",
			"spco", "grpa", "ftcn", "inpr",	"lang",	"clas", "rnlw"));
	
	// Devices
	public static final Set<String> DEVI = new HashSet<String>(Arrays.asList(
			"medd", "resd", "drdd"));
	
	// Disorders
	public static final Set<String> DISO = new HashSet<String>(Arrays.asList(
			"acab", "anab", "comd", "cgab",	"dsyn", 
			"emod", "fndg", "inpo", "mobd", "neop", "patf", "sosy" ));
	
	// Genes & Molecular Sequences
	public static final Set<String> GENE = new HashSet<String>(Arrays.asList(
			"gngm", "mosq", "nusq", "amas",	"crbs" ));
	
	// Geographic Areas
	public static final Set<String> GEOG = new HashSet<String>(Arrays.asList("geoa"));
	
	// Living Beings
	public static final Set<String> LIVB = new HashSet<String>(Arrays.asList(
			"orgm", "plnt", "fngs",	"virs", "bact", 
			"anim", "vtbt", "amph", "bird", "fish", 
			"rept",	"mamm", "humn", "grup", "prog", 
			"popg", "famg", "aggp", "podg",	"arch", "euka"));
	
	// Objects
	public static final Set<String> OBJC = new HashSet<String>(Arrays.asList(
			"enty", "phob", "mnob", "sbst",	"food"));
	
	// Occupations
	public static final Set<String> OCCU = new HashSet<String>(Arrays.asList("ocdi", "bmod"));
	
	// Organizations
	public static final Set<String> ORGA = new HashSet<String>(Arrays.asList(
			"orgt", "hcro", "pros",	"shro"));
	
	// Phenomena
	public static final Set<String> PHEN = new HashSet<String>(Arrays.asList(
			"lbtr", "biof", "phpr", "hcpp",	"eehu", "npop"));
	
	// Physiology
	public static final Set<String> PHYS = new HashSet<String>(Arrays.asList(
			"orga", "phsf", "orgf", "menp",	"ortf", "celf", "moft", "genf", "clna"));
	
	// Procedures
	public static final Set<String> PROC = new HashSet<String>(Arrays.asList(
			"diap", "edac", "hlca", "lbpr",	"mbrt", "resa", "topp"));
	
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
	
	private SemanticGroups() {
		throw new AssertionError("This class is noninstantiable");
	}
	
	// Guarantee: each type belong to only one group
	public static String getSemanticGroup(String semanticType) {
		String result = null;
		semanticType = semanticType.toLowerCase();
		
		for (String group : groupMap.keySet()) {
			if (groupMap.get(group).contains(semanticType))
				result = group;
		}
		
		return result;
	}
	
	// Guarantee: each type belong to only one group
	public static String getSemanticGroupName(String semanticType) {
		String result = null;

		
		if (ACTI.contains(semanticType))
			result = SemanticConstants.ACTI_NAME;
		else if (ANAT.contains(semanticType))
			result = SemanticConstants.ANAT_NAME;
		else if (CHEM.contains(semanticType))
			result = SemanticConstants.CHEM_NAME;
		else if (CONC.contains(semanticType))
			result = SemanticConstants.CONC_NAME;
		else if (DEVI.contains(semanticType))
			result = SemanticConstants.DEVI_NAME;
		else if (DISO.contains(semanticType))
			result = SemanticConstants.DISO_NAME;
		else if (GENE.contains(semanticType))
			result = SemanticConstants.GENE_NAME;
		else if (GEOG.contains(semanticType))
			result = SemanticConstants.GEOG_NAME;
		else if (LIVB.contains(semanticType))
			result = SemanticConstants.LIVB_NAME;
		else if (OBJC.contains(semanticType))
			result = SemanticConstants.OBJC_NAME;
		else if (OCCU.contains(semanticType))
			result = SemanticConstants.OCCU_NAME;
		else if (ORGA.contains(semanticType))
			result = SemanticConstants.ORGA_NAME;
		else if (PHEN.contains(semanticType))
			result = SemanticConstants.PHEN_NAME;
		else if (PHYS.contains(semanticType))
			result = SemanticConstants.PHYS_NAME;
		else if (PROC.contains(semanticType))
			result = SemanticConstants.PROC_NAME;
		
		return result;
	}
	
	public static Set<String> getTypesOfGroup(String group) {
		group = group.toUpperCase().trim();
		
		if (groupMap.keySet().contains(group))
			return groupMap.get(group);
		else 
			return null;					
	}
}
