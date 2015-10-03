package edu.ucr.cs.dblab.nle020.reviewsdiversity.composite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.TopPairsProgram;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.TopPairsProgram.SetOption;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;
import edu.ucr.cs.dblab.nle020.utils.Utils;

public class TopSetsBaseline {
	private static int k = 5;
	
	public static void main (String[] args) {
		long startTime = System.currentTimeMillis();		

		SetOption setOption = SetOption.SENTENCE;
		Map<Integer, List<SentimentSet>> docToTopKSentences = 
				getTopKSentimentSet(TopPairsProgram.DOC_TO_REVIEWS_PATH, setOption);
		
		String outputPath = TopPairsProgram.OUTPUT_FOLDER + "top_" + setOption + "_baseline_k" + k + ".txt";
		TopPairsProgram.outputTopKToJson(outputPath, docToTopKSentences);
		
		System.out.println("Outputed to \"" + outputPath + "\"");
		Utils.printRunningTime(startTime, "Finished topK baseline for " + setOption);
	}
	
	private static Map<Integer, List<SentimentSet>> getTopKSentimentSet(String inputPath, TopPairsProgram.SetOption setOption) {
			
		Map<Integer, List<SentimentSet>> docToSentimentSets = importSentimentSets(inputPath, setOption);
		
		Map<Integer, List<SentimentSet>> docToTopKSets = new HashMap<Integer, List<SentimentSet>>();		
		for (Integer docId : docToSentimentSets.keySet()) {
			docToTopKSets.put(docId, extractTopKFromList(docToSentimentSets.get(docId)));
		}		
		
		return docToTopKSets;
	}
	
	
	
	private static List<SentimentSet> extractTopKFromList(List<SentimentSet> sentimentSets) {
		
		Map<String, Set<SentimentSet>> posNegConceptToSentimentSets = new HashMap<String, Set<SentimentSet>>();
		for (SentimentSet sentimentSet : sentimentSets) {
			for (ConceptSentimentPair pair : sentimentSet.getPairs()) {
				String posNegConcept = pair.getId();
				if (pair.getSentiment() > 0)
					posNegConcept = posNegConcept + "_pos";
				else
					posNegConcept = posNegConcept + "_neg";
				
				if (!posNegConceptToSentimentSets.containsKey(posNegConcept))
					posNegConceptToSentimentSets.put(posNegConcept, new HashSet<SentimentSet>());
				
				posNegConceptToSentimentSets.get(posNegConcept).add(sentimentSet);
			}
		}
		
		List<PosNegConcept> posNegConcepts = new ArrayList<PosNegConcept>();
		for (String concept : posNegConceptToSentimentSets.keySet()) {
			posNegConcepts.add(new PosNegConcept(concept, posNegConceptToSentimentSets.get(concept)));
		}
		
		PriorityQueue<PosNegConcept> sortedPosNegConcepts = new PriorityQueue<>(posNegConcepts);		
		
		Random random = new Random();
		List<SentimentSet> randomKSets = new ArrayList<SentimentSet>();
		int sizeAtLastRound = 0;						// to check if we can get a new set after each round 
		while (randomKSets.size() < k) {
			if (sortedPosNegConcepts.size() == 0) {
				if (sizeAtLastRound == randomKSets.size()) {
					System.out.println("Can't get more, # PosNegConcept is " + posNegConcepts.size());
					break;
				} else {
					sortedPosNegConcepts.addAll(posNegConcepts);
					sizeAtLastRound = randomKSets.size();
				}
			}
			
			PosNegConcept nextPosNegConcept = sortedPosNegConcepts.poll();
			List<SentimentSet> unexistedSentimentSets = new ArrayList<SentimentSet>();
			nextPosNegConcept.getContainingSets().stream().forEach(set -> {
				if (!randomKSets.contains(set))
					unexistedSentimentSets.add(set);
			});
			
			if (unexistedSentimentSets.size() > 0)
				randomKSets.add(unexistedSentimentSets.get(random.nextInt(unexistedSentimentSets.size())));
		}
				
		return randomKSets;
	}



	private static Map<Integer, List<SentimentSet>> importSentimentSets(String inputPath, TopPairsProgram.SetOption setOption) {
		Map<Integer, List<SentimentSet>> docToSentimentSets = new HashMap<Integer, List<SentimentSet>>();	
		
		switch (setOption) {
		case REVIEW: 	
			docToSentimentSets = TopPairsProgram.importDocToSentimentReviews(inputPath, Constants.NUM_DOCTORS_TO_EXPERIMENT);
			break;
		case SENTENCE:  
			docToSentimentSets = TopPairsProgram.importDocToSentimentSentences(inputPath, Constants.NUM_DOCTORS_TO_EXPERIMENT);
			break;
		default: 		
			docToSentimentSets = TopPairsProgram.importDocToSentimentReviews(inputPath, Constants.NUM_DOCTORS_TO_EXPERIMENT);
			break;
		}
		
		return docToSentimentSets;
	}
	
	private static class PosNegConcept implements Comparable<PosNegConcept> {
		String concept;
		Set<SentimentSet> containingSets;
		public PosNegConcept(String concept, Set<SentimentSet> containingSets) {
			super();
			this.concept = concept;
			this.containingSets = containingSets;
		}
		public String getConcept() {
			return concept;
		}
		public Set<SentimentSet> getContainingSets() {
			return containingSets;
		}
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result
					+ ((concept == null) ? 0 : concept.hashCode());
			return result;
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			PosNegConcept other = (PosNegConcept) obj;
			if (concept == null) {
				if (other.concept != null)
					return false;
			} else if (!concept.equals(other.concept))
				return false;
			return true;
		}
		@Override
		public int compareTo(PosNegConcept o) {
			return o.getContainingSets().size() - this.getContainingSets().size();
		}

	}
}
