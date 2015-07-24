package edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.metamap.MetaMapParser;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentReview;
import edu.ucr.cs.dblab.nle020.utilities.Utils;

public class PairExtractorThread implements Runnable {
	private SentimentCalculator sentimentCalculator = new SentimentCalculator();
	
	private ConcurrentMap<Integer, List<RawReview>> docToReviews;
	private ConcurrentMap<Integer, List<SentimentReview>> docToSentimentReviews = new ConcurrentHashMap<Integer, List<SentimentReview>>();

	private int index;
	
	private MetaMapParser mmParser = new MetaMapParser();

	public PairExtractorThread(
			ConcurrentMap<Integer, List<RawReview>> docToReviews,
			ConcurrentMap<Integer, List<SentimentReview>> docToSentimentReviews,
			int index) {
		super();
		this.docToReviews = docToReviews;
		this.docToSentimentReviews = docToSentimentReviews;
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
			List<SentimentReview> sentimentReviews = new ArrayList<SentimentReview>(); 
			for (RawReview rawReview : docToReviews.get(docID)) {
			

				SentimentReview sentimentReview = sentimentCalculator.calculateSentimentReview(rawReview);
				
				if (sentimentReview.getSentences().size() > 0)
					sentimentReviews.add(sentimentReview);
			}
			docToSentimentReviews.put(docID, sentimentReviews);
		}		
		
		mmParser.disconnect();
		Utils.printRunningTime(startTime, Thread.currentThread().getName()	+ " finished");
	}
	
	public ConcurrentMap<Integer, List<RawReview>> getDocToReviews() {
		return docToReviews;
	}

	public void setDocToReviews(ConcurrentMap<Integer, List<RawReview>> docToReviews) {
		this.docToReviews = docToReviews;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}
}
