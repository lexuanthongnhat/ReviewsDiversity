package edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;

/**
 * 
 * @author Thong Nhat
 *		POS(Part-Of-Speech) tagger using Stanford NLP tagger
 */
public class POSTagger {
	
	private Map<String, Set<String>> wordTypes = new HashMap<String, Set<String>>();
	private Map<String, String> toSimplePOS = new HashMap<String, String>();
	
	private MaxentTagger tagger;
	private String modelPath = "D:\\UCR Google Drive\\Libs\\stanford-postagger-2015-04-20\\models\\english-left3words-distsim.tagger";
	private TokenizerFactory<CoreLabel> ptbTokenizerFactory = PTBTokenizer.factory(new CoreLabelTokenFactory(), 
														"untokenizable=noneKeep");
	
	public POSTagger() {
		init();
	}
	
	private void init() {
		
		// Reference:  Alphabetical list of part-of-speech tags used in the Penn Treebank Project:
		//				https://www.ling.upenn.edu/courses/Fall_2003/ling001/penn_treebank_pos.html
		// adjective
		wordTypes.put("a", new HashSet<String>(Arrays.asList("JJ", "JJR", "JJS")));		
		// adverb
		wordTypes.put("r", new HashSet<String>(Arrays.asList("RB", "RBR", "RBS")));		
		// verb
		wordTypes.put("v", new HashSet<String>(Arrays.asList("VB", "VBD", "VBG",
																"VBN", "VBP", "VBZ")));		
		// name/noun
		wordTypes.put("n", new HashSet<String>(Arrays.asList("NN", "NNS", "NNP", "NNPS")));
		
		// For easier access
		// Adjective
		toSimplePOS.put("JJ", "a");		
		toSimplePOS.put("JJR", "a");		
		toSimplePOS.put("JJS", "a");
		
		// Adverb
		toSimplePOS.put("RB", "r");		
		toSimplePOS.put("RBR", "r");		
		toSimplePOS.put("RBS", "r");
		
		// Verb
		toSimplePOS.put("VB", "v");		
		toSimplePOS.put("VBD", "v");		
		toSimplePOS.put("VBG", "v");
		toSimplePOS.put("VBN", "v");		
		toSimplePOS.put("VBP", "v");		
		toSimplePOS.put("VBZ", "v");
		
		// Name/Noun
		toSimplePOS.put("NN", "n");
		toSimplePOS.put("NNS", "n");
		toSimplePOS.put("NNP", "n");
		toSimplePOS.put("NNPS", "n");
		
		tagger = new MaxentTagger(modelPath);
	}
	
	public List<TaggedWord> tagPOS(String text) {
		List<TaggedWord> result = new ArrayList<TaggedWord>();
		
		DocumentPreprocessor documentPreprocessor = new DocumentPreprocessor(new StringReader(text));
		documentPreprocessor.setTokenizerFactory(ptbTokenizerFactory);
		
		for (List<HasWord> sentence : documentPreprocessor) {
			result.addAll(tagger.tagSentence(sentence));			
		}
		
		return result;
	}
	
	public List<String> tokenize(String sentence) {
		List<String> result = new ArrayList<String>();
		
		DocumentPreprocessor documentPreprocessor = new DocumentPreprocessor(new StringReader(sentence));
		documentPreprocessor.setTokenizerFactory(ptbTokenizerFactory);
		
		Iterator<List<HasWord>> i = documentPreprocessor.iterator();
		while (i.hasNext()) {
			for (HasWord word : i.next()) {
				
				String token = word.word();				
				if (!token.matches("\\W+|'s|'re|'m|'ll")) {
					result.add(token);
				}
			}
		}
		
		return result;
	}
	
	public String simpleTag(String tag) {				
		return toSimplePOS.get(tag.trim().toUpperCase());
	}
}
