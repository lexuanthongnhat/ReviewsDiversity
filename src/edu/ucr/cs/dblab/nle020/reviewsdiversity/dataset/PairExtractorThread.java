package edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.metamap.MetaMapParser;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentReview;
import edu.ucr.cs.dblab.nle020.utils.Utils;

public class PairExtractorThread implements Runnable {
	private SentimentCalculator sentimentCalculator = new SentimentCalculator();
	
	private ConcurrentMap<String, List<RawReview>> docToReviews;
	private ConcurrentMap<String, List<SentimentReview>> docToSentimentReviews;

	private int index;
	
	private MetaMapParser mmParser = new MetaMapParser();

	public PairExtractorThread(
			ConcurrentMap<String, List<RawReview>> docToReviews,
			ConcurrentMap<String, List<SentimentReview>> docToSentimentReviews,
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

		String[] docIDs = docToReviews.keySet().toArray(new String[docToReviews.size()]);
		
		// Ex. 4 threads, interval = 100 (1% dataset):
		//			index = 0:  0  , 400, 800
		//			index = 1:	100, 500, 900
		//          index = 2:  200, 600, 1000
		// 			index = 3:  300, 700, 1100
//		for (int i = 0 + index * Constants.INTERVAL_TO_SAMPLE_REVIEW; i < docToReviews.size(); i += Constants.INTERVAL_TO_SAMPLE_REVIEW * Constants.NUM_THREADS) {
		for (int i = index; i < docToReviews.size(); i += Constants.NUM_THREADS) {

			String docID = docIDs[i];
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
	
	public ConcurrentMap<String, List<RawReview>> getDocToReviews() {
		return docToReviews;
	}

	public void setDocToReviews(ConcurrentMap<String, List<RawReview>> docToReviews) {
		this.docToReviews = docToReviews;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}
}
