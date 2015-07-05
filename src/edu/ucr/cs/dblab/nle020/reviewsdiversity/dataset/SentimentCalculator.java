package edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import edu.stanford.nlp.ling.TaggedWord;
import edu.ucr.cs.dblab.nle020.metamap.MetaMapParser;
import edu.ucr.cs.dblab.nle020.metamap.Sentence;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;
import edu.ucr.cs.dblab.nle020.utilities.Utils;
import gov.nih.nlm.nls.metamap.Ev;
import gov.nih.nlm.nls.metamap.Position;

public class SentimentCalculator {
	
	private static SentimentDictionary senDictionary = SentimentDictionary.getInstance();	
	private static MetaMapParser mmParser = new MetaMapParser();
	private static POSTagger tagger = new POSTagger();
		
	private static ConcurrentMap<String, Set<String>> CUIToDeweys = new ConcurrentHashMap<String, Set<String>>();
	static {
		initCUIToDeweys();
	}
	
	private static void initCUIToDeweys() {
		long startTime = System.currentTimeMillis();
		Utils.printTotalHeapSize("Heapsize BEFORE importing CUIToDeweys map");
		
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(Constants.CUI_TO_DEWEYS_PATH))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String[] columns = line.split("\\t");
				
				String SCUI = columns[0];
				Set<String> deweys = new HashSet<String>();
				for (String dewey : columns[1].split(",")) {
					deweys.add(dewey);
				}
				
				CUIToDeweys.putIfAbsent(SCUI, deweys);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		Utils.printRunningTime(startTime, "Importing CUIToDeweys map");
		Utils.printTotalHeapSize("Heapsize AFTER importing CUIToDeweys map");
	}
	
	/**
	 * Calculating sentiments for every concept of a review
	 * @param sentenceMap - mapping of each sentence to its list of concept-mappings
	 * @return List of all Concept-Sentiment Pairs
	 */
	public static List<ConceptSentimentPair> calculateSentiment(Map<Sentence, List<Ev>> sentenceMap) {
		List<ConceptSentimentPair> result = new ArrayList<ConceptSentimentPair>();
		int forRounding = (int) Math.pow(10, Constants.NUM_DIGIT_PRECISION_SENTIMENT);
		
		int offset = 0;
		for (Sentence sentence : sentenceMap.keySet()) {
			offset = sentence.getStartPos();			
			List<Ev> mappings = mmParser.getValidMappings(sentenceMap.get(sentence)); 
			
			List<TaggedWord> POSTags = tagger.tagPOS(sentence.getString());
			
			// Mapping the order, position of every word in sentence
			Map<Integer, TaggedWord> positionToWord = new HashMap<Integer, TaggedWord>();				// start_position -> word
			Map<Integer, Integer> positionToOrder = new HashMap<Integer, Integer>();					// position -> order			
			int order = 1;			
			for (TaggedWord tag : POSTags) {
				if (!tag.word().matches("\\W+")) {
					int beginPosition = tag.beginPosition() + offset;		
					positionToWord.put(beginPosition, tag);						
					positionToOrder.put(beginPosition, order);
					order++;					
				}				
			}
			
			
			// Get the order of concepts, assumption: there may be duplicate concepts in the same sentence
			Map<String, List<Integer>> conceptToOrder = new HashMap<String, List<Integer>>();		
			Map<String, String> conceptNameMap = new HashMap<String, String>();
			Map<String, List<String>> conceptTypes = new HashMap<String, List<String>>();
						
			// To check the duplicate CUI for the same word (same position)
			Map<String, List<Ev>> CUIToMapping = new HashMap<String, List<Ev>>();
			for (Ev ev : mappings) {		
				try {
					String CUI = ev.getConceptId();		
					int conceptOrder = 0;				
					
					// Check duplicated
					boolean isDuplicated = false;
					if (!CUIToMapping.containsKey(CUI)) {
						CUIToMapping.put(CUI, new ArrayList<Ev>());
						CUIToMapping.get(CUI).add(ev);
					} else {
						for (Ev currentEv : CUIToMapping.get(CUI)) {
							List<Position> evPos = ev.getPositionalInfo();
							List<Position> currentEvPos = currentEv.getPositionalInfo();
							if (evPos.size() == currentEvPos.size()) {
								int temp = 0;
								for (int i = 0; i < evPos.size(); i++) {
									temp += evPos.get(i).getX() - currentEvPos.get(i).getX();
									temp += evPos.get(i).getY() - currentEvPos.get(i).getY();
								}
								if (temp == 0) {
									isDuplicated = true;
									break;
								}									
							}
						}
						if (!isDuplicated) {
							CUIToMapping.get(CUI).add(ev);
						}
					}
					
 					if (!isDuplicated) {
						for (Position pos : ev.getPositionalInfo()) {	
							if (positionToOrder.containsKey(pos.getX())) {
								conceptOrder += positionToOrder.get(pos.getX());	
/*								positionToWord.remove(pos.getX());
								positionToOrder.remove(pos.getX());*/				
							} else {
								// This is the case the concept is in a complex word, ex. "look" in "good-looking"
								// 		this case, there is only the POSTag for "good-looking", not for "look"
								//		so, positionToOrder only contains entry for "good-looking"
								for (TaggedWord tag : POSTags) {
									// Minus 2 for the case that "'s" is not counted in the same word's POS tag
									if (offset + tag.beginPosition() <= pos.getX() 
											&& offset + tag.endPosition() >= pos.getX() + pos.getY() - 2) { 		
										conceptOrder += positionToOrder.get(offset + tag.beginPosition());
										break;
									}
								}
								
								// The case of 2 matched words but only 1 position - 2 words span 2 tags
								if (conceptOrder == 0) {
									for (int j = 0; j < POSTags.size() - 1; j++) {
										
										if (offset + POSTags.get(j).beginPosition() <= pos.getX() 
												&& offset + POSTags.get(j + 1).endPosition() >= pos.getX() + pos.getY()) { 		
											conceptOrder += positionToOrder.get(offset + POSTags.get(j).beginPosition());
											break;
										}
									}	
								}
								
								
								if (conceptOrder == 0) 
									System.err.println("Can't find concept's matched words " 
											+ "\"" + ev.getMatchedWords() + "\"" + " of concept \"" + ev.getConceptName() 
											+ "\" at positions " + ev.getPositionalInfo()
											+ "in sentence: \"" + sentence.getString() + "\"\n"
											+ "\tPOSTags: " + POSTags);
								
							}
						}
						conceptOrder /= ev.getPositionalInfo().size();
						
						// conceptOrder = 0 when the concept is duplicated.
						if (conceptOrder != 0) {
							if (!conceptToOrder.containsKey(CUI)) {
								conceptToOrder.put(CUI, new ArrayList<Integer>());
								conceptNameMap.put(CUI, ev.getConceptName());
								conceptTypes.put(CUI, ev.getSemanticTypes());
							}
							
							conceptToOrder.get(CUI).add(conceptOrder);
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}						
			}
						
			// Calculating the sentiment of concept			
			float score = 0.0f;
			float sum = 0.0f;
			for (String CUI : conceptToOrder.keySet()) {
				for (Integer conceptOrder : conceptToOrder.get(CUI)) {
					score = 0.0f;
					sum = 0.0f;
					
					for (Integer position : positionToWord.keySet()) {						
						TaggedWord tag = positionToWord.get(position);
						float wordScore = senDictionary.getScore(tag.word(), tagger.simpleTag(tag.tag())); 
						if (wordScore != 0.0f) {
							float distance = Math.abs(positionToOrder.get(position) - conceptOrder);
							if (distance != 0.0f) {
								score += wordScore/distance;
								sum += (float) 1.0f/distance;
							}
						}				
					}
					
					if (sum != 0) {
						// Get 2 digits precision only
						score = (float)Math.round(score/sum * forRounding) / forRounding;
						if (Math.abs(score) > 1) {
							System.err.println("There must be something wrong here!");
						}
						
						
						if (CUIToDeweys.get(CUI) != null) {
							ConceptSentimentPair pair = 
									new ConceptSentimentPair(CUI, conceptNameMap.get(CUI), score, 1, conceptTypes.get(CUI));
							pair.addDeweys(CUIToDeweys.get(CUI));
						
							if (!result.contains(pair)) {
								result.add(pair);
							} else {
								result.get(result.indexOf(pair)).incrementCount();
							}				
						}
					}
				}
			}
		}
		
		return result;
	}
}
