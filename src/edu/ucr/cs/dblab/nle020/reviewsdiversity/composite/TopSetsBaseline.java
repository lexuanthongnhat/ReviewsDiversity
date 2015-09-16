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
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;
import edu.ucr.cs.dblab.nle020.utilities.Utils;

public class TopSetsBaseline {
	private static int k = Constants.K;
	
	private static void getTopKSentimentSet(String inputPath, TopPairsProgram.SetOption setOption) {
		long startTime = System.currentTimeMillis();		
			
		Map<Integer, List<SentimentSet>> docToSentimentSets = importSentimentSets(inputPath, setOption);
		
		Map<Integer, List<SentimentSet>> docToTopKSets = new HashMap<Integer, List<SentimentSet>>();		
		for (Integer docId : docToSentimentSets.keySet()) {
			docToTopKSets.put(docId, extractTopKFromList(docToSentimentSets.get(docId)));
		}
		
		
		Utils.printRunningTime(startTime, "Finished topK baseline for " + setOption);
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
		
		List<Integer> setSize = new ArrayList<Integer>();
		for (String posNegConcept : posNegConceptToSentimentSets.keySet()) {
			setSize.add(posNegConceptToSentimentSets.get(posNegConcept).size());
		}
		Collections.sort(setSize, new Comparator<Integer>() {

			@Override
			public int compare(Integer o1, Integer o2) {
				return o2 - o1;
			}
		});
		
		int kLargest = setSize.get(k);
		int count = 0;
		Map<String, Set<SentimentSet>> kPosNegConceptToSentimentSets = new HashMap<String, Set<SentimentSet>>();
		for (String posNegConcept : posNegConceptToSentimentSets.keySet()) {
			if (posNegConceptToSentimentSets.get(posNegConcept).size() > kLargest) {
				kPosNegConceptToSentimentSets.put(posNegConcept, posNegConceptToSentimentSets.get(posNegConcept));
				++ count;
			}
		}
		for (String posNegConcept : posNegConceptToSentimentSets.keySet()) {
			if (count < k) {
				if (posNegConceptToSentimentSets.get(posNegConcept).size() == kLargest) {
					kPosNegConceptToSentimentSets.put(posNegConcept, posNegConceptToSentimentSets.get(posNegConcept));
					++ count;
				}
			} else
				break;
		}
		
		List<SentimentSet> randomKSets = new ArrayList<SentimentSet>();
		Random random = new Random();
		while (randomKSets.size() < k) {
			for (String posNegConcept : kPosNegConceptToSentimentSets.keySet()) {
				List<SentimentSet> currentSets = new ArrayList<SentimentSet>(); 				
				currentSets.addAll(kPosNegConceptToSentimentSets.get(posNegConcept));
				
				currentSets.stream().forEach(set -> {
						if (randomKSets.contains(set))
							currentSets.remove(set);
					});
				
				SentimentSet randomSet = currentSets.get(random.nextInt(currentSets.size()));
				randomKSets.add(randomSet);
			}
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
			if (this.getContainingSets().size() > o.getContainingSets().size())
				return 1;
			else if (this.getContainingSets().size() < o.getContainingSets().size())
				return -1;
			else 
				return 0;
		}

	}
}
