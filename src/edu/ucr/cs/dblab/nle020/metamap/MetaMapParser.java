package edu.ucr.cs.dblab.nle020.metamap;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import gov.nih.nlm.nls.metamap.Ev;
import gov.nih.nlm.nls.metamap.Mapping;
import gov.nih.nlm.nls.metamap.MetaMapApi;
import gov.nih.nlm.nls.metamap.MetaMapApiImpl;
import gov.nih.nlm.nls.metamap.PCM;
import gov.nih.nlm.nls.metamap.Result;
import gov.nih.nlm.nls.metamap.Utterance;

/**
 * @author Nhat XT Le
 *
 */
public class MetaMapParser {
		
	private final static String SNOMED_SAB = "SNOMEDCT_US";
	
	private MetaMapApi api = null;
	private String options = "-y";
	private String hostname = "localhost";
	private Set<String> invalidSemanticTypes = new HashSet<String>();
	private Set<String> invalidSemanticGroups = new HashSet<String>();
	private Set<String> erroneousCUIs = new HashSet<String>();
	private Set<String> exceptionalCUIS = new HashSet<String>();
	private final int TIME_OUT = 1000 * 60 * 60 * 48;			// 48 hours
	
	public MetaMapParser() {
		api = new MetaMapApiImpl(TIME_OUT);
		api.setOptions(options);
		api.getSession().setTimeout(TIME_OUT);
		
		initInvalidSemanticTypes();
		initErroneousCUIs("src/edu/ucr/cs/dblab/nle020/metamap/erroneous_cuis.txt");
		initExceptionalCUIs("src/edu/ucr/cs/dblab/nle020/metamap/exceptional_cuis.txt");
	}
	
	public MetaMapParser(String hostname) {
		api = new MetaMapApiImpl(hostname);
		api.setOptions(options);
		api.setTimeout(TIME_OUT);
		api.getSession().setTimeout(TIME_OUT);
		this.hostname = hostname;
		
		initInvalidSemanticTypes();
		initErroneousCUIs("src/edu/ucr/cs/dblab/nle020/metamap/erroneous_cuis.txt");
		initExceptionalCUIs("src/edu/ucr/cs/dblab/nle020/metamap/exceptional_cuis.txt");
	}

	private void initInvalidSemanticTypes() {
		invalidSemanticGroups.add(SemanticConstants.CHEM);
		invalidSemanticGroups.add(SemanticConstants.GEOG);
		invalidSemanticGroups.add(SemanticConstants.CONC);
		
		for (String group : invalidSemanticGroups) {
			invalidSemanticTypes.addAll(SemanticGroups.getTypesOfGroup(group));
		}
		
		
		invalidSemanticTypes.add("menp");			// Mental Process
		invalidSemanticTypes.add("famg");			// Family Group
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
	
	public String getOptions() {
		return options;
	}

	public void setOptions(String options) {
		this.options = options;
		if (api != null)
			api.setOptions(options);
	}

	public String getHostname() {
		return hostname;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}
	
	
	/**
	 * Get both utterances and their corresponding mapping
	 * @param text
	 * @return Map: utterance string -> mappings
	 */
	public Map<String, List<Ev>> parseToUtteranceMap(String text) {
		Map<String, List<Ev>> result = new HashMap<String, List<Ev>>();
		
		List<Result> resultList = api.processCitationsFromString(options, text);
		for (Result re : resultList) {
			List<Utterance> utters;
			try {
				utters = re.getUtteranceList();
				for (Utterance utter : utters) {
					String utterance = utter.getString();
					List<Ev> mappings = new ArrayList<Ev>();
					
					List<PCM> pcms = utter.getPCMList();
					for (PCM pcm : pcms) {
						for (Mapping map : pcm.getMappingList()) {
							for (Ev ev : map.getEvList()) {	
								if (isValidSource(ev)) {																	
									if (exceptionalCUIS.contains(ev.getConceptId()) || 
											(!erroneousCUIs.contains(ev.getConceptId()) && !isStopWord(ev))) 
										mappings.add(ev);
								}
							}
						}
					}
					
					result.put(utterance, mappings);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

		}
		
		return result;
	}
	
	/**
	 * Get mappings for each sentence
	 * @param text
	 * @return Map: sentence (id, string, position) -> mappings
	 */
	public Map<Sentence, List<Ev>> parseToSentenceMap(String text) {
		Map<Sentence, List<Ev>> result = new HashMap<Sentence, List<Ev>>();
		
		List<Result> resultList = api.processCitationsFromString(options, text);
		for (Result re : resultList) {
			List<Utterance> utters;
			try {
				utters = re.getUtteranceList();
				for (Utterance utter : utters) {
					Sentence sentence = new Sentence(utter.getId(), utter.getString(), utter.getPosition().getX(), utter.getPosition().getY());					
					List<Ev> mappings = new ArrayList<Ev>();
					
					List<PCM> pcms = utter.getPCMList();
					for (PCM pcm : pcms){
						for (Mapping map : pcm.getMappingList()) {
							for (Ev ev : map.getEvList()) {
								if (isValidSource(ev)) {
									if (exceptionalCUIS.contains(ev.getConceptId()) || 
											(!erroneousCUIs.contains(ev.getConceptId()) && !isStopWord(ev))) {
										mappings.add(ev);
									}
								}
							}
						}
					}
					
					result.put(sentence, mappings);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		return result;
	}
	
	/**
	 * Get mappings for each sentence and filtering on valid mappings
	 * @param text
	 * @return Map: sentence (id, string, position) -> valid mappings
	 */
	public Map<Sentence, List<Ev>> parseToValidSentenceMap(String text) {
		Map<Sentence, List<Ev>> result = new HashMap<Sentence, List<Ev>>();
		
		List<Result> resultList = api.processCitationsFromString(options, text);
		for (Result re : resultList) {
			List<Utterance> utters;
			try {
				utters = re.getUtteranceList();
				for (Utterance utter : utters) {
					Sentence sentence = new Sentence(utter.getId(), utter.getString(), utter.getPosition().getX(), utter.getPosition().getY());					
					List<Ev> mappings = new ArrayList<Ev>();
					
					List<PCM> pcms = utter.getPCMList();
					for (PCM pcm : pcms){
						for (Mapping map : pcm.getMappingList()) {
							for (Ev ev : map.getEvList()) {
								if (isValidSource(ev)) {
									if (!erroneousCUIs.contains(ev.getConceptId())  && !isStopWord(ev) 
											&& isValidType(ev.getSemanticTypes() )) {
										mappings.add(ev);
									}
								}
							}
						}
					}
					
					result.put(sentence, mappings);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		return result;
	}
	
	public List<Ev> getValidMappings(String sentence) {
		List<Ev> result = new ArrayList<Ev>();
		
		List<Result> resultList = api.processCitationsFromString(options, sentence);
		for (Result re : resultList) {
			List<Utterance> utters;
			try {
				utters = re.getUtteranceList();
				for (Utterance utter : utters) {										
					List<Ev> mappings = new ArrayList<Ev>();
					
					List<PCM> pcms = utter.getPCMList();
					for (PCM pcm : pcms){
						for (Mapping map : pcm.getMappingList()) {
							for (Ev ev : map.getEvList()) {
								if (isValidSource(ev) && isValidType(ev.getSemanticTypes())
									&& !erroneousCUIs.contains(ev.getConceptId()) && !isStopWord(ev) ) {
										
										mappings.add(ev);
									
								}
							}
						}
					}
					
					result.addAll(mappings);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		return result;
	}
	
	public List<Ev> getValidMappings(List<Ev> mappings) {
		List<Ev> result = new ArrayList<Ev>();
		
		for (Ev ev : mappings) {
			try {
				if (isValidType(ev.getSemanticTypes()) && isValidSource(ev)) {
					result.add(ev);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		return result;
	}
	
	public List<Ev> getInvalidMappings(List<Ev> mappings) {
		List<Ev> result = new ArrayList<Ev>();
		
		for (Ev ev : mappings) {
			try {
				if (!isValidType(ev.getSemanticTypes()) || !isValidSource(ev)) {
					result.add(ev);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		return result;
	}
	
	/**
	 * Check if this list containing valid semantic type 
	 * @param types - not in Txxx form
	 * @return true if valid
	 */
	public boolean isValidType(List<String> types) {
		boolean isValid = false;
		
		for (String type : types) {
			if (!invalidSemanticTypes.contains(type)) {
				isValid = true;
				break;
			}
		}
		return isValid;
	}
	
	public void disconnect() {
		api.disconnect();
		api = null;
	}
	
	private boolean isValidSource(Ev ev) {
		boolean result = false;
		try {
			if (ev.getSources().contains(SNOMED_SAB))
				result = true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return result;
	}
	
	private boolean isStopWord(Ev ev) {
		boolean result = false;
		try {
			if (ev.getMatchedWords().size() == 1) {
				if (lemurStopWords.contains(ev.getMatchedWords().get(0).trim().toLowerCase())) 
					result = true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return result;
	}
	
	private final static List<String> lemurStopWords = Arrays.asList(
			"a", "about", "above", "according", "across", "after", "afterwards", "again", "against", "albeit", 
			"all", "almost", "alone", "along", "already", "also", "although", "always", "am", "among", "amongst", 
			"an", "and", "another",	"any", "anybody", "anyhow", "anyone", "anything", "anyway", "anywhere", 
			"apart", "are", "around", "as", "at", "av", "be", "became", "because", "become", "becomes", 
			"becoming", "been", "before", "beforehand", "behind", "being", "below", "beside", "besides", 
			"between", "beyond", "both", "but", "by", "can", "cannot", "canst", "certain", "cf", "choose", 
			"contrariwise", "cos", "could", "cu", "day", "do", "does", "doesn't", "doing", "dost", "doth", 
			"double", "down", "dual", "during", "each", "either", "else", "elsewhere", "enough", "et", "etc", 
			"even", "ever", "every", "everybody", "everyone", "everything", "everywhere", "except", "excepted", 
			"excepting", "exception", "exclude", "excluding", "exclusive", "far", "farther", "farthest", "few", 
			"ff", "first", "for", "formerly", "forth", "forward",	"from", "front", "further", "furthermore", 
			"furthest", "get", "go", "had", "halves", "hardly", "has", "hast", "hath", "have", "he", "hence", 
			"henceforth", "her", "here", "hereabouts", "hereafter", "hereby", "herein", "hereto", "hereupon", 
			"hers", "herself", "him", "himself", "hindmost", "his", "hither", "hitherto", "how", "however", 
			"howsoever", "i", "ie", "if", "in", "inasmuch", "inc", "include", "included", "including", "indeed", 
			"indoors", "inside", "insomuch", "instead", "into", "inward", "inwards", "is", "it", "its", "itself", 
			"just", "kind", "kg", "km", "last", "latter", "latterly", "less", "lest", "let", "like", "little", 
			"ltd", "many", "may", "maybe", "me", "meantime", "meanwhile", "might", "moreover", "most", "mostly", 
			"more", "mr", "mrs", "ms", "much", "must", "my", "myself", "namely", "need", "neither", "never", 
			"nevertheless", "next", "no", "nobody",	"none", "nonetheless", "noone", "nope", "nor", "not", 
			"nothing", "notwithstanding", "now", "nowadays", "nowhere", "of", "off", "often", "ok", "on", "once", 
			"one", 	"only", "onto", "or", "other", "others", "otherwise", "ought", "our", "ours", "ourselves", 
			"out", "outside", "over", "own", "per", "perhaps", "plenty", "provide", "quite", "rather", "really", 
			"round", "said", "sake", "same", "sang", "save", "saw", "see", "seeing", "seem", "seemed", "seeming", 
			"seems", "seen", "seldom", "selves", "sent", "several", "shalt", "she", "should", "shown", "sideways", 
			"since", "slept", "slew", "slung", "slunk", "smote", "so", "some", 	"somebody", "somehow", "someone", 
			"something", "sometime", "sometimes", "somewhat", "somewhere", "spake", "spat", "spoke", "spoken", 
			"sprang", "sprung", "stave", "staves", "still", "such", "supposing", "than", "that", "the", "thee", 
			"their", "them", "themselves", "then", "thence", "thenceforth", "there", "thereabout", "thereabouts", 
			"thereafter", "thereby", "therefore", "therein", "thereof", "thereon", "thereto", "thereupon", "these", 
			"they", "this", "those", "thou", "though", "thrice", "through", "throughout", "thru", "thus", "thy", 
			"thyself", "till", "to", "together", "too", "toward", "towards", "ugh", "unable", "under", "underneath", 
			"unless", "unlike", "until", "up", "upon", "upward", "upwards", "us", "use", "used", "using", "very", 
			"via", "vs", "want", "was", "we", "week", "well", "were", "what", "whatever", "whatsoever",	"when", 
			"whence", "whenever", "whensoever", "where", "whereabouts", "whereafter", "whereas", "whereat", "whereby", 
			"wherefore", "wherefrom", "wherein", "whereinto", "whereof", "whereon", "wheresoever", "whereto", 
			"whereunto", "whereupon", "wherever", "wherewith", "whether", "whew", "which", "whichever", "whichsoever", 
			"while", "whilst", "whither", "who", "whoa", "whoever", "whole", "whom", "whomever", "whomsoever", 
			"whose", "whosoever", "why", "will", "wilt", "with", "within", "without", "worse", "worst", "would", 
			"wow", "ye", "yet", "year", "yippee", "you", "your", "yours", "yourself", "yourselves",

			"can't", "don't", "i'm", "it's", "that's", "you're", "he's", "she's", "they're", "we're", "cant"  // this last line is added by Nhat
	);	
}
