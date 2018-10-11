package edu.ucr.cs.dblab.nle020.reviewsdiversity.units;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.ucr.cs.dblab.nle020.ontology.DeweyUtils;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;
import edu.ucr.cs.dblab.nle020.umls.SemanticTypeNetwork;

public class ConceptSentimentPair{
	private String cui = "";
	private String name;
	private float sentiment;
//	private float sentimentWithSelfCount;
	private int count;	
	private float correlation;
	private List<String> types;
	private Set<String> deweys = new HashSet<>();
	
	public ConceptSentimentPair() {
		super();
	}
	
	public ConceptSentimentPair(String cui) {
		this.cui = cui;
	}
	
	public ConceptSentimentPair(String cui, float sentiment) {
		super();
		this.cui	= cui;
		this.sentiment = sentiment;
	}
		
	public ConceptSentimentPair(String cui, float sentiment, String dewey) {
		super();
		this.cui = cui;
		this.sentiment = sentiment;
		this.deweys.add(dewey);
	}	
	
	public ConceptSentimentPair(String cui, String name, float sentiment,
			int count, List<String> types) {
		super();
		this.cui = cui;
		this.name = name;
		this.sentiment = sentiment;
		this.count = count;
		this.types = types;
	}
	
	/**
	 * @return Distance from the ROOT to this Unit, always positive.
	 */
	public int calculateRootDistance() {
		if (PairDistances.getDistance(Constants.ROOT_CUI, cui) != null)
			return PairDistances.getDistance(Constants.ROOT_CUI, cui);
		
		int min = Integer.MAX_VALUE;
		for (String dewey : deweys) {
			int rootToThisDewey = DeweyUtils.getDeweyDistanceFromRoot(dewey);			
			if (rootToThisDewey < min)
				min = rootToThisDewey;
		}
		
		PairDistances.putDistances(Constants.ROOT_CUI, cui, min);
		
		return min;
	}

	/**
	 *  Calculate the distance from this Unit to the other Unit
	 * @param otherPair - other Unit
	 * @return distance is Constants.INVALID_DISTANCE if 2 nodes are in different branches, <br>
	 * otherwise, it can be both positive and negative.
	 */
	public int calculateDistance(ConceptSentimentPair otherPair) {
		if (PairDistances.getDistance(cui, otherPair.cui) != null)
			return PairDistances.getDistance(cui, otherPair.cui);
		
		int result = Constants.INVALID_DISTANCE;
		
		int minPositive = Constants.INVALID_DISTANCE;
		int maxNegative = Integer.MIN_VALUE;
		
		for (String thisDewey : deweys) {
			for (String otherDewey : otherPair.getDeweys()) {
				int distance = DeweyUtils.getDeweyDistance(thisDewey, otherDewey);
				if (distance < 0) {
					if (distance > maxNegative)
						maxNegative = distance;
				} else {
					if (distance < minPositive) {
						minPositive = distance;
					}
				}
			}
		}
		
		if (minPositive != Constants.INVALID_DISTANCE && maxNegative != Integer.MIN_VALUE) {
	
			if (minPositive == 0 || maxNegative == 0) {
				result = 0;
			} else {
				System.err.println("Distance problem with 2 pairs: " + toString() + " and " + otherPair.toString() 
						+ ", minPositive: " + minPositive + " - maxNegative: " + maxNegative);

				// TODO	- test this
				if (cui.compareTo(otherPair.getId()) > 0) {
					return minPositive;
				} else {
					return maxNegative;
				}
			}
		} else if (minPositive != Constants.INVALID_DISTANCE) {
			result = minPositive;
		} else if (maxNegative != Integer.MIN_VALUE) {
			result = maxNegative;
		}	
		
		
		PairDistances.putDistances(cui, otherPair.cui, result);
		
		return result;
	}
	
	/**
	 *  Calculate the distance from this Unit to the other Unit, care about sentiment threshold for "Cover"
	 * @param other - other Unit
	 * @param threshold - threshold for sentiment Covering
	 * @return distance is Constants.INVALID_DISTANCE if 2 nodes are in different branches or sentiment-uncoverable, <br>
	 * otherwise, it can be both positive and negative.
	 */
	public int calculateDistance(ConceptSentimentPair other, float threshold) {	
		if (isSentimentCover(other, threshold)) 
			return calculateDistance(other);
		else 
			return Constants.INVALID_DISTANCE;
	}	
		
	public ConceptSentimentPair createRoot() {
		return new ConceptSentimentPair(Constants.ROOT_CUI);
	}
	
	public boolean isSentimentCover(ConceptSentimentPair p, float threshold) {
		boolean result = false;
		
		if (deweys.contains("$") || p.deweys.contains("$")
                || this.getId().equals(Constants.ROOT_CUI) || p.getId().equals(Constants.ROOT_CUI))
			result = true;
		else if (Math.abs(this.sentiment - p.sentiment) <= threshold)
			result =  true;
		 
		return result;
	}

	public boolean testDistance(ConceptSentimentPair otherPair) {
		boolean result = true;
		
		int distance = calculateDistance(otherPair);
		if (distance == Constants.INVALID_DISTANCE) {
			if (otherPair.calculateDistance(this) != Constants.INVALID_DISTANCE)
				result = false;
		} else {
			if (otherPair.calculateDistance(this) != -distance)
				result = false;
		}
		
		if (!result)
			System.err.println("Error distance between " + this.toString() + " and " + otherPair.toString());
		
		return result;
	}
	
	public void incrementCount() {
		count++;
	}
	public void incrementCount(int count) {
		this.count += count;
	}
	
	public void addDewey(String dewey) {
		deweys.add(dewey);
	}
	
	public void addDeweys(Collection<? extends String> deweys) {
		this.deweys.addAll(deweys);
	}
	
	public String getCui() {
		return cui;
	}

	public void setCui(String cui) {
		this.cui = cui;
	}
	
	public String getId() {
		return cui;
	}

	public void setId(String id) {
		this.cui = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public List<String> getTypes() {
		return types;
	}
	public void setTypes(List<String> types) {
		this.types = types;
	}
	public float getSentiment() {
		return sentiment;
	}
	public void setSentiment(float sentiment) {
		this.sentiment = sentiment;
	}
	public int getCount() {
		return count;
	}
	public void setCount(int count) {
		this.count = count;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((cui == null) ? 0 : cui.hashCode());
//		result = prime * result + count;
		result = prime * result + Float.floatToIntBits(sentiment);
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
		ConceptSentimentPair other = (ConceptSentimentPair) obj;
		if (cui == null) {
			if (other.cui != null)
				return false;
		} else if (!cui.equals(other.cui))
			return false;
//		if (count != other.count)
//			return false;
		if (Float.floatToIntBits(sentiment) != Float
				.floatToIntBits(other.sentiment))
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("{\"" + cui + "-" + name + "\": \"" + sentiment + "\"}");
//		builder.append("{\"" + cui + "\", \"" + name + "\", \"" + sentiment + "\",\"" + sentimentWithSelfCount + "\"" );
		
		/*builder.append(", \"types\": [");
		for (String type : types) {
			builder.append(SemanticTypeNetwork.getNetworkType(SemanticTypes.getInstance().getTUI(type).toUpperCase()) + ", ");
		}
		builder.delete(builder.length() - 2, builder.length());
		builder.append("]");*/
/*		if (!Float.isNaN(correlation) && Float.isFinite(correlation))
			builder.append(", corr:" + correlation);*/
		
/*		builder.append(", \"deweys\": [");
		for (String dewey : deweys) {
			builder.append(dewey + ", ");
		}
		builder.delete(builder.length() - 2, builder.length());*/
		
		//builder.append("]}");
		
		return builder.toString();
	}

	public Set<String> getDeweys() {
		return deweys;
	}

	public void setDeweys(Set<String> deweys) {
		this.deweys = deweys;
	}
	
	
	
	
	
	// From ROOT type to this pair types
	public int calculateRootTypeDistance() {
		int min = Integer.MAX_VALUE;
		for (String type : types) {
			int rootToThisType = SemanticTypeNetwork.distance(SemanticTypeNetwork.ROOT, type);			
			if (rootToThisType < min)
				min = rootToThisType;
		}
		
		return min;
	}
	
	// Distance between types
	public int calculateTypeDistance(ConceptSentimentPair otherPair) {
		int result = Constants.INVALID_DISTANCE;
		
		int minPositive = Integer.MAX_VALUE;
		int maxNegative = Integer.MIN_VALUE;
		for (String thisType : types) {
			for (String otherType : otherPair.getTypes()) {
				int distance = SemanticTypeNetwork.distance(thisType, otherType);
				if (distance < 0) {
					if (distance > maxNegative)
						maxNegative = distance;
				} else {
					if (distance < minPositive) {
						minPositive = distance;
					}
				}
			}
		}
		
		if (minPositive != Integer.MAX_VALUE && maxNegative != Integer.MIN_VALUE) {
// TODO
//			System.err.println("Distance problem with 2 pairs: " + toString() + " and " + otherPair.toString() 
//					+ ", minPositive: " + minPositive + " - maxNegative: " + maxNegative);
			
			if (minPositive == 0 || maxNegative == 0) {
				result = 0;
			} else if (cui.compareTo(otherPair.getId()) > 0) {
				return minPositive;
			} else {
				return (-minPositive);
			}
		} else if (minPositive != Integer.MAX_VALUE) {
			result = minPositive;
		} else if (maxNegative != Integer.MIN_VALUE) {
			result = maxNegative;
		}			
		
		return result;
	}

	public float getCorrelation() {
		return correlation;
	}

	public void setCorrelation(float correlation) {
		this.correlation = correlation;
	}

/*	public float getSentimentWithSelfCount() {
		return sentimentWithSelfCount;
	}

	public void setSentimentWithSelfCount(float sentimentWithSelfCount) {
		this.sentimentWithSelfCount = sentimentWithSelfCount;
	}*/




}
