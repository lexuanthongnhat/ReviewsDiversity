package edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.metamap.MetaMapParser;
import edu.ucr.cs.dblab.nle020.metamap.Sentence;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;
import edu.ucr.cs.dblab.nle020.utilities.Utils;
import gov.nih.nlm.nls.metamap.Ev;

public class PairExtractorThread implements Runnable {
	
	ConcurrentMap<Integer, List<Review>> docToReviews;
	private ConcurrentMap<Integer, List<ConceptSentimentPair>> docToPairs;
	private int index;
	
	private MetaMapParser mmParser = new MetaMapParser();

	public PairExtractorThread(
			ConcurrentMap<Integer, List<Review>> docToReviews,
			ConcurrentMap<Integer, List<ConceptSentimentPair>> docToPairs,
			int index) {
		super();
		this.docToReviews = docToReviews;
		this.docToPairs = docToPairs;
		this.index = index;
	}

	@Override
	public void run() {
		buildDataset();	
	}

	public void buildDataset() {
		long startTime = System.currentTimeMillis();
		
		Integer[] docIDs = docToReviews.keySet().toArray(new Integer[docToReviews.size()]);
		
		// Ex. 4 threads, interval = 100 (1% dataset):
		//			index = 0:  0  , 400, 800
		//			index = 1:	100, 500, 900
		//          index = 2:  200, 600, 1000
		// 			index = 3:  300, 700, 1100
//		for (int i = 0 + index * Constants.INTERVAL; i < docToReviews.size(); i += Constants.INTERVAL * Constants.NUM_THREADS) {
		for (int i = index; i < docToReviews.size(); i += Constants.NUM_THREADS) {
			
			Integer docID = docIDs[i];
					
			for (Review review : docToReviews.get(docID)) {
			
				List<ConceptSentimentPair> pairs = getConceptSentimentPairs(review);
				if (!docToPairs.containsKey(docID)) {
					docToPairs.put(docID, new ArrayList<ConceptSentimentPair>());					
					docToPairs.get(docID).addAll(pairs);
				} else {
					List<ConceptSentimentPair> currentPairs = docToPairs.get(docID);
					for (ConceptSentimentPair pair : pairs) {
						if (!currentPairs.contains(pair)) {
							currentPairs.add(pair);
						} else {
							currentPairs.get(currentPairs.indexOf(pair)).incrementCount(pair.getCount());
						}
					}
				}	
			}
		}		
		
		mmParser.disconnect();
		Utils.printRunningTime(startTime, Thread.currentThread().getName()	+ " finished");
	}
	
	private List<ConceptSentimentPair> getConceptSentimentPairs(Review review) {			
		Map<Sentence, List<Ev>> sentenceMap = mmParser.parseToSentenceMap(review.getBody());		
		return SentimentCalculator.calculateSentiment(sentenceMap);
	}
	
	public ConcurrentMap<Integer, List<Review>> getDocToReviews() {
		return docToReviews;
	}

	public void setDocToReviews(ConcurrentMap<Integer, List<Review>> docToReviews) {
		this.docToReviews = docToReviews;
	}
	
	public ConcurrentMap<Integer, List<ConceptSentimentPair>> getDocToPairs() {
		return docToPairs;
	}

	public void setDocToPairs(ConcurrentMap<Integer, List<ConceptSentimentPair>> docToPairs) {
		this.docToPairs = docToPairs;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}
}
