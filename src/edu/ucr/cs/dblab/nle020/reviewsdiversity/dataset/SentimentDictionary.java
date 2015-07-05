package edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.ucr.cs.dblab.nle020.utilities.Utils;

public class SentimentDictionary {

	private final static SentimentDictionary INSTANCE = new SentimentDictionary();
	
	private Map<String, Float> dictionary = new HashMap<String, Float>();
	private Map<String, Map<Integer, Float>> senseDictionary = new HashMap<String, Map<Integer, Float>>();
	private Set<String> wordTypes = new HashSet<String>();
	private int maxSenseNum = 0;
	private String dictionaryPath = "src/edu/ucr/cs/dblab/nle020/reviewsdiversity/dataset/SentiWordNet_3.0.0_20130122.txt";
	
	private List<String> exceptionalWords = new ArrayList<String>(Arrays.asList("comfortable#a", "recovery#n"));
	
	private SentimentDictionary() {
		init();
		
		// TODO - be careful here!!!
		correctingExceptionalWords();
	}
	
	private void init() {
		long startTime = System.currentTimeMillis();
		
		BufferedReader reader = null;
		try {
			reader = Files.newBufferedReader(Paths.get(dictionaryPath));
			String line;
			int lineNum = 0;
			
			while ((line = reader.readLine()) != null) {
				lineNum++;
				
				// If it's a comment, skip this line
				if (!line.trim().startsWith("#")) {
					
					// tab separation
					String[] columns = line.split("\t");
					String wordType = columns[0].trim();				// POS - Part Of Speech - ex. a, v, n
					
					if (!wordTypes.contains(wordType)) {
						wordTypes.add(wordType);
					}
					
					// Example line:
					// POS 	ID 			PosS 	NegS 	SynsetTerm#sensenumber 				Desc
					// a 	00009618 	0.5 	0.25 	spartan#4 austere#3 ascetical#2
					// 		
					
					// is this a valid line?
					if (columns.length != 6 ) {
						throw new IllegalArgumentException("Incorrect column format at line: " + lineNum); 
					}
					
					Float score = Float.parseFloat(columns[2]) - Float.parseFloat(columns[3]);
					
					// Get all synnet terms
					String[] terms = columns[4].split(" ");
					for (String term : terms) {
						String[] termAndSenses = term.split("#");
						
						String fullTerm = termAndSenses[0] + "#" + wordType;
						int senseNum = Integer.parseInt(termAndSenses[1].trim());
						
						// Dictionary of term with sense
						if (!senseDictionary.containsKey(fullTerm)) {
							senseDictionary.put(fullTerm, new HashMap<Integer, Float>());
						}
						senseDictionary.get(fullTerm).put(senseNum, score);
						
						if (senseNum > maxSenseNum)
							maxSenseNum = senseNum;
					}							
				}
			}
			
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		} 		
		
		
		// Summary the scores
		for (Map.Entry<String, Map<Integer, Float>> entry : senseDictionary.entrySet()) {
			String term = entry.getKey();
			Map<Integer, Float> scores = entry.getValue();
			
			// Calculate score as weighted average, the weight is: 1/senseNum
			float score = 0.0f;
			float normalization = 0.0f;
			for (Map.Entry<Integer, Float> s : scores.entrySet()) {
				score += s.getValue() / (float) s.getKey();
				normalization += 1.0 / (float) s.getKey();
			}
			
			score /= normalization;
			
			if (score != 0)
				dictionary.put(term, score);
		}
		
		System.out.println("Num of all/non-zero terms: \t\t" + senseDictionary.size()
								+ " - " + dictionary.size());
		
		Utils.printRunningTime(startTime, "Imported sentiment dictionary");
	}

	/**
	 * Flip the sentiment of some exceptional words in medical domain
	 * 		Ex. comfortable, recovery are positive but are currently negative
	 */
	private void correctingExceptionalWords() {
		for (String word : exceptionalWords) {
			dictionary.put(word, 0 - dictionary.get(word));
		}
	}
	
	public float getScore(String word, String pos) {
		float score = 0.0f;
		String key = word + "#" + pos;		
		if (dictionary.containsKey(key))
			score = dictionary.get(key);
		
		return score;
	}
	
	public float getScore(String word, String pos, int senseNum) {
		//return senseDictionary.get(word + "#" + pos).get(new Integer(senseNum));
		float score = 0.0f;
		String key = word + "#" + pos;		
		if (senseDictionary.containsKey(key))
			score = senseDictionary.get(key).get(new Integer(senseNum));
		
		return score;
	}
	
	
	public static SentimentDictionary getInstance() {
		return INSTANCE;
	}
}
