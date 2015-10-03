package edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import edu.stanford.nlp.ling.TaggedWord;
import edu.ucr.cs.dblab.nle020.metamap.MetaMapParser;
import edu.ucr.cs.dblab.nle020.metamap.Sentence;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentReview;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSentence;
import edu.ucr.cs.dblab.nle020.utils.Utils;
import gov.nih.nlm.nls.metamap.Ev;
import gov.nih.nlm.nls.metamap.Position;

public class SentimentCalculator {

	private SentimentDictionary senDictionary = SentimentDictionary.getInstance();	
	private MetaMapParser mmParser = new MetaMapParser();
	private POSTagger tagger = new POSTagger();
	
//	private boolean countConceptSentimentItSelf = false;

	private static ConcurrentMap<String, Set<String>> CUIToDeweys = new ConcurrentHashMap<String, Set<String>>();
	public SentimentCalculator() {
		initCUIToDeweys();
	}

/*	public SentimentCalculator(boolean countConceptSentimentItSelf) {
		super();
		this.countConceptSentimentItSelf = countConceptSentimentItSelf;
		initCUIToDeweys();
	}
*/
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

	private static List<String> breakingIntoSentences(String paragraph, boolean useAdvancedMethod) {
		List<String> result = new ArrayList<String>();

		String sentences = paragraph;
		/*		sentences = sentences.replace("Mr.", "Mr").replace("mr.", "mr").replace("MR.", "MR");
		sentences = sentences.replace("Mrs.", "Mrs").replace("mrs.", "mrs").replace("MRS.", "MRS");
		sentences = sentences.replace("Miss.", "Miss");
		sentences = sentences.replace("DR.", "DR").replace("Dr.", "Dr").replace("dr.", "dr").replace("dR.", "dR");
		sentences = sentences.replace("Drs.", "Drs").replace("DRs.", "DRs");*/

		// These strings stop Metamap
		sentences = sentences.replaceAll("â€¦", "").replaceAll("¨", "").replaceAll("¦", "");

		if (useAdvancedMethod) {
			//			result = tagger.breakingIntoSentences(sentences);

			BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.US);
			iterator.setText(sentences);
			int lastIndex = iterator.first();
			while (lastIndex != BreakIterator.DONE) {
				int firstIndex = lastIndex;
				lastIndex = iterator.next();

				if (lastIndex != BreakIterator.DONE) {
					String sentence = sentences.substring(firstIndex, lastIndex);
					result.add(sentence);
				}
			}		
		} else {
			for (String sentence : sentences.split("[.\\?!]")) {
				if (sentence.trim().length() > 0)
					result.add(sentence);
			}
		}

		return result;
	}

	public SentimentReview calculateSentimentReview(RawReview rawReview) {
		SentimentReview sentimentReview = new SentimentReview(String.valueOf(rawReview.getId()));
		sentimentReview.setRawReview(rawReview);

		String body = rawReview.getBody();

		// TODO - better way to breaking into sentences
		List<String> sentenceBodies = breakingIntoSentences(body, Constants.USE_ADVANCED_SENTENCE_BREAKING);

		int sentenceCount = 0;
		List<SentimentSentence> sentimentSentences = new ArrayList<SentimentSentence>();
		for (String sentenceBody : sentenceBodies) {
			SentimentSentence sentimentSentence = new SentimentSentence(
					String.valueOf(rawReview.getId()) + "_s" + sentenceCount,
					sentenceBody, 
					String.valueOf(rawReview.getId()));			
			System.out.println("Parsing " + sentenceBody);

			sentimentSentence.setPairs(calculateSentiment(sentenceBody));
			if (sentimentSentence.getPairs().size() > 0) {
				sentimentSentences.add(sentimentSentence);
				++sentenceCount;
			}
		}
		
		sentimentReview.setSentences(sentimentSentences);

		return sentimentReview;
	}

	public List<ConceptSentimentPair> calculateSentiment(String sentenceBody) {
		int offset = 0;		// Single Sentence
		return calculateSentiment(sentenceBody, offset);
	}

	public List<ConceptSentimentPair> calculateSentiment(String sentenceBody, int offset) {

		List<Ev> mappings = mmParser.getValidMappings(sentenceBody);
		return calculateSentimentOfConceptSubsetInSentence(sentenceBody, offset, mappings);
	}

	/**
	 * Calculating sentiments for every concept of a review
	 * @param sentenceMap - mapping of each sentence to its list of concept-mappings
	 * @return List of all Concept-Sentiment Pairs
	 */
	public List<ConceptSentimentPair> calculateSentiment(Map<Sentence, List<Ev>> sentenceMap) {
		List<ConceptSentimentPair> result = new ArrayList<ConceptSentimentPair>();

		int offset = 0;
		for (Sentence sentence : sentenceMap.keySet()) {
			offset = sentence.getStartPos();
			List<ConceptSentimentPair> pairsWithInSentence = calculateSentimentOfConceptSubsetInSentence(
					sentence.getString(), 
					offset,
					sentenceMap.get(sentence));
			
			for (ConceptSentimentPair pair : pairsWithInSentence) {
				if (!result.contains(pair))
					result.add(pair);
				else
					result.get(result.indexOf(pair)).incrementCount(pair.getCount());
			}			
		}

		return result;
	}
	
	private List<ConceptSentimentPair> calculateSentimentOfConceptSubsetInSentence(String sentenceBody, int offset, List<Ev> mappings) {
		List<ConceptSentimentPair> result = new ArrayList<ConceptSentimentPair>();
		
		List<TaggedWord> POSTags = tagger.tagPOS(sentenceBody);

		// Mapping the order, position of every word in sentence
		Map<Integer, TaggedWord> positionToWord = calculatePositionToWord(POSTags, offset);				// start_position -> word
		Map<Integer, Integer> positionToOrder = calculatePositionToOrder(POSTags, offset);					// position -> order

		// Get the order of concepts, assumption: there may be duplicate concepts in the same sentence
		Map<String, List<Integer>> conceptToOrders = new HashMap<String, List<Integer>>();		
		Map<String, String> conceptToName = new HashMap<String, String>();
		Map<String, List<String>> conceptToTypes = new HashMap<String, List<String>>();

		// To check the duplicate CUI for the same word (same position)
		Map<String, List<Ev>> CUIToMapping = new HashMap<String, List<Ev>>();
		for (Ev conceptEv : mappings) {	
			try {
				String CUI = conceptEv.getConceptId();		

				boolean isDuplicatedWithinSentence = isConceptDuplicatedWithinSentence(CUIToMapping, conceptEv);

				if (!isDuplicatedWithinSentence) {
					int conceptOrder = calculateConceptOrder(sentenceBody, offset, POSTags, positionToOrder, conceptEv);

					// conceptOrder = 0 when the concept is duplicated.
					if (conceptOrder != 0) {
						if (!conceptToOrders.containsKey(CUI)) {
							conceptToOrders.put(CUI, new ArrayList<Integer>());
							conceptToName.put(CUI, conceptEv.getConceptName());
							conceptToTypes.put(CUI, conceptEv.getSemanticTypes());
						}

						conceptToOrders.get(CUI).add(conceptOrder);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}						
		}

		// Calculating the sentiment of concept			

		for (String CUI : conceptToOrders.keySet()) {
			for (Integer conceptOrder : conceptToOrders.get(CUI)) {
				boolean countConceptSentimentItSelf = false;
				float score = accumulateSentimentScoreBasedOnConceptOrder(positionToWord, positionToOrder, conceptOrder, 
						countConceptSentimentItSelf);
				
				// FIXME - remove for higher speed after experiment
				countConceptSentimentItSelf = true;
				float scoreWithSelfSentiment = accumulateSentimentScoreBasedOnConceptOrder(positionToWord, positionToOrder, conceptOrder, 
						countConceptSentimentItSelf);
				
				if (!Float.isNaN(score)) {
					if (CUIToDeweys.get(CUI) != null) {
						ConceptSentimentPair pair = 
								new ConceptSentimentPair(CUI, conceptToName.get(CUI), score, 1, conceptToTypes.get(CUI));
						pair.addDeweys(CUIToDeweys.get(CUI));
						
//						pair.setSentimentWithSelfCount(scoreWithSelfSentiment);

						if (!result.contains(pair))
							result.add(pair);
						else
							result.get(result.indexOf(pair)).incrementCount();									
					}
				}
			}
		}
		
		return result;
	}

	private float accumulateSentimentScoreBasedOnConceptOrder(
			Map<Integer, TaggedWord> positionToWord,
			Map<Integer, Integer> positionToOrder, 
			Integer conceptOrder,
			boolean countConceptSentimentItSelf) {
		
		int forRounding = (int) Math.pow(10, Constants.NUM_DIGIT_PRECISION_OF_SENTIMENT);
		float score = 0.0f;
		float sum = 0.0f;

		for (Integer position : positionToWord.keySet()) {						
			TaggedWord tag = positionToWord.get(position);
			float wordScore = senDictionary.getScore(tag.word(), tagger.simpleTag(tag.tag())); 
			if (wordScore != 0.0f) {
				float distance = Math.abs(positionToOrder.get(position) - conceptOrder);
				
				if (distance == 0.0f && countConceptSentimentItSelf)
					distance = 1;
				
				if (distance != 0.0f) {
					score += wordScore/distance;
					sum += (float) 1.0f/distance;
				}
			}				
		}
		if (sum != 0) {
			// Get 2 digits precision only
			score = (float)Math.round(score/sum * forRounding) / forRounding;
			if (Math.abs(score) > 1)
				System.err.println("There must be something wrong here!");
		} else 
			score = Float.NaN;
		return score;
	}	
	
	private Map<Integer, TaggedWord> calculatePositionToWord(List<TaggedWord> POSTags, int offset) {
		Map<Integer, TaggedWord> positionToWord = new HashMap<Integer, TaggedWord>();				// start_position -> word

		for (TaggedWord tag : POSTags) {
			if (!tag.word().matches("\\W+")) {
				int beginPosition = tag.beginPosition() + offset;		
				positionToWord.put(beginPosition, tag);										
			}				
		}

		return positionToWord;
	}

	private Map<Integer, Integer> calculatePositionToOrder(List<TaggedWord> POSTags, int offset) {
		Map<Integer, Integer> positionToOrder = new HashMap<Integer, Integer>();					// position -> order		

		int order = 1;			
		for (TaggedWord tag : POSTags) {
			if (!tag.word().matches("\\W+")) {
				int beginPosition = tag.beginPosition() + offset;		
				positionToOrder.put(beginPosition, order);
				order++;					
			}				
		}

		return positionToOrder;
	}
	

	private boolean isConceptDuplicatedWithinSentence(Map<String, List<Ev>> currentDistinctCUIToMapping, Ev ev) throws Exception {
		
		boolean isDuplicated = false;
		
		String CUI = ev.getConceptId();
		if (!currentDistinctCUIToMapping.containsKey(CUI)) {
			currentDistinctCUIToMapping.put(CUI, new ArrayList<Ev>());
			currentDistinctCUIToMapping.get(CUI).add(ev);
		} else {
			for (Ev currentEv : currentDistinctCUIToMapping.get(CUI)) {
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
				currentDistinctCUIToMapping.get(CUI).add(ev);
			}
		}
		return isDuplicated;
	}
	
	private int calculateConceptOrder(String sentenceBody, int offset,
			List<TaggedWord> sentencePOSTags, Map<Integer, Integer> positionToOrder,
			Ev conceptEv) throws Exception {
		int conceptOrder = 0;
		for (Position pos : conceptEv.getPositionalInfo()) {	
			if (positionToOrder.containsKey(pos.getX())) {
				conceptOrder += positionToOrder.get(pos.getX());				
			} else {
				// This is the case the concept is in a complex word, ex. "look" in "good-looking"
				// 		this case, there is only the POSTag for "good-looking", not for "look"
				//		so, positionToOrder only contains entry for "good-looking"
				conceptOrder += orderOfConceptWithinCompoundWord(offset, sentencePOSTags, positionToOrder, pos);

				// The case of 2 matched words but only 1 position - 2 words span 2 tags
				if (conceptOrder == 0) 
					conceptOrder += orderOfConceptWithTwoWordsButOnePosition(offset, sentencePOSTags, positionToOrder, pos);	

				if (conceptOrder == 0) 
					System.err.println("Can't find concept's matched words " 
							+ "\"" + conceptEv.getMatchedWords() + "\"" + " of concept \"" + conceptEv.getConceptName() 
							+ "\" at positions " + conceptEv.getPositionalInfo()
							+ "in sentence: \"" + sentenceBody + "\"\n"
							+ "\tPOSTags: " + sentencePOSTags);

			}
		}
		conceptOrder /= conceptEv.getPositionalInfo().size();
		return conceptOrder;
	}
	
	// The case of 2 matched words but only 1 position - 2 words span 2 tags
	private int orderOfConceptWithTwoWordsButOnePosition(int offset,
			List<TaggedWord> POSTags, Map<Integer, Integer> positionToOrder, Position pos) {
		int conceptOrder = 0;
		
		for (int j = 0; j < POSTags.size() - 1; j++) {

			if (offset + POSTags.get(j).beginPosition() <= pos.getX() 
					&& offset + POSTags.get(j + 1).endPosition() >= pos.getX() + pos.getY()) { 		
				conceptOrder += positionToOrder.get(offset + POSTags.get(j).beginPosition());
				break;
			}
		}
		return conceptOrder;
	}

	// This is the case the concept is in a complex word, ex. "look" in "good-looking"
	// 		this case, there is only the POSTag for "good-looking", not for "look"
	//		so, positionToOrder only contains entry for "good-looking"
	private int orderOfConceptWithinCompoundWord(int offset,
			List<TaggedWord> POSTags, Map<Integer, Integer> positionToOrder,
			Position conceptPosition) {

		int conceptOrder = 0;
		for (TaggedWord tag : POSTags) {
			// Minus 2 for the case that "'s" is not counted in the same word's POS tag
			if (offset + tag.beginPosition() <= conceptPosition.getX() 
					&& offset + tag.endPosition() >= conceptPosition.getX() + conceptPosition.getY() - 2) { 		
				conceptOrder += positionToOrder.get(offset + tag.beginPosition());
				break;
			}
		}
		return conceptOrder;
	}

/*	public boolean isCountConceptSentimentItSelf() {
		return countConceptSentimentItSelf;
	}

	public void setCountConceptSentimentItSelf(boolean countConceptSentimentItSelf) {
		this.countConceptSentimentItSelf = countConceptSentimentItSelf;
	}*/
}
