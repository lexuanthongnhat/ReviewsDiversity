package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import edu.ucr.cs.dblab.nle020.metamap.SemanticTypes;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.umls.SemanticTypeNetwork;

public class AbsoluteSolution implements Runnable {

	private int k = 0;
	private float threshold = 0.0f;

	private int docID;
	private List<FullPair> pairs = new ArrayList<FullPair>();
	private FullPair root = new FullPair(SemanticTypeNetwork.ROOT, 0.0f);
	
	private List<FullPair> topK = new ArrayList<FullPair>();	
	
	long cost = 0;
	
	private static ConcurrentMap<Integer, TopPairsResult> docToTopPairsResult = new ConcurrentHashMap<Integer, TopPairsResult>();
	TopPairsResult result;
	
	
	
	public AbsoluteSolution(int k, float threshold, int docID,
			List<ConceptSentimentPair> csPairs, ConcurrentMap<Integer, TopPairsResult> docToTopPairsResult) {
		super();
		this.k = k;
		this.threshold = threshold;
		this.docID = docID;
		this.docToTopPairsResult = docToTopPairsResult;
		
		initPairs(csPairs);
	}

	private void initPairs(List<ConceptSentimentPair> csPairs) {
		
		for (int i = 0 ; i < csPairs.size() ; i++) {
			pairs.add(new FullPair(csPairs.get(i).getCUI(), csPairs.get(i).getSentiment(), threshold));
		}		
		
		// Init the host
		for (int i = 0; i < pairs.size(); i++) {
			FullPair pair = pairs.get(i);			
			pair.getCustomerMap().put(pair, 0);					// It can serve itself	
			pair.getPotentialHosts().add(pair);
			pair.setHost(root);
		}
		
		// Init the customers and potential hosts
		for (int i = 0; i < pairs.size() - 1; i++) {
			FullPair pair = pairs.get(i);			
			for (int j = 1; j < pairs.size(); j ++) {
				FullPair other = pairs.get(j);
								
				int distance = Constants.INVALID_DISTANCE;
				if (pair.isSentimentCover(other))
					distance = csPairs.get(i).calculateDistance(csPairs.get(j));
				
				// 2 pairs are in the same branch and sentiment coverable
				if (distance != Constants.INVALID_DISTANCE) {
					
					// "pair" is the ancestor of "other"
					if (distance > 0) {
						pair.getCustomerMap().put(other, distance);
						other.getPotentialHosts().add(pair);
					} else if (distance < 0) {
						other.getCustomerMap().put(pair, -distance);
						pair.getPotentialHosts().add(other);
					} else {
						pair.getCustomerMap().put(other, distance);
						other.getPotentialHosts().add(pair);
						
						other.getCustomerMap().put(pair, -distance);
						pair.getPotentialHosts().add(other);
					}
				}
			}
		}
		
		// Init benefit
		for (int i = 0; i < pairs.size(); i++) {
			FullPair pair = pairs.get(i);
			pair.setBenefit(pair.getCustomerMap().size() * csPairs.get(i).calculateRootDistance()); 
		}
		
		// Init networkTypes - for debugging
		for (int i = 0; i < pairs.size(); i++) {
			pairs.get(i).addDeweys(csPairs.get(i).getDeweys());
		}
		
		// Init the root
		for (int i = 0; i < pairs.size(); i++) {
			root.setHost(root);
			
			int distance = csPairs.get(i).calculateRootDistance();
			root.getCustomerMap().put(pairs.get(i), distance);
			cost += distance;
		}
		
		
		// Init result
		result = new TopPairsResult(docID, k, threshold);
		result.setInitialCost(cost);
		result.setNumPairs(csPairs.size());
	}

	@Override
	public void run() {
		cost = getOptimal(pairs, topK);
		System.out.println("cost: " + cost);
	}

	
	private long getOptimal(List<FullPair> pairs, List<FullPair> topK) {
		long optimal = Long.MAX_VALUE;
				
		if (topK.size() < k) {
			int startIndex = 0;
			if (topK.size() > 0) {
				FullPair lastPairInTopK = topK.get(topK.size() - 1);
				startIndex = pairs.indexOf(lastPairInTopK) + 1;
			}
			for (int next = startIndex; next < pairs.size(); ++next) {
				List<FullPair> topKTemp = new ArrayList<FullPair>();
				topKTemp.addAll(topK);
				topKTemp.add(pairs.get(next));
				
				long temp = getOptimal(pairs, topKTemp);
				if (temp < optimal)
					optimal = temp;
			}
		} else if (topK.size() == k) {
			optimal = 0;
			for (FullPair pair : pairs) {
				int min = Constants.INVALID_DISTANCE;
				for (FullPair potentialHost : pair.getPotentialHosts()) {
					if (potentialHost.getCustomerMap().get(pair) < min) {
						min = potentialHost.getCustomerMap().get(pair);
						pair.setHost(potentialHost);
					}
				}
				optimal += min;
			}
		}
		
		return optimal;
	}
	
	private List<String> computeNetworkTypes(ConceptSentimentPair pair) {
		List<String> result = new ArrayList<String>();
		for (String type : pair.getTypes()) {
			String networkType = SemanticTypeNetwork.getNetworkType(SemanticTypes.getInstance().getTUI(type));
			if (networkType != null)
				result.add(networkType);
		}
		
		return result;
	}
}
