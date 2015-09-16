package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import java.util.ArrayList;
import java.util.List;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;

public class SetResult {
	private String id;
	private String text;
	
	private List<ConceptSentimentPair> pairs = new ArrayList<ConceptSentimentPair>();

	public SetResult() {
		super();
	}

	public SetResult(String id, String text, List<ConceptSentimentPair> pairs) {
		super();
		this.id = id;
		this.text = text;
		this.pairs = pairs;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public List<ConceptSentimentPair> getPairs() {
		return pairs;
	}

	public void setPairs(List<ConceptSentimentPair> pairs) {
		this.pairs = pairs;
	}
}
