package edu.ucr.cs.dblab.nle020.stats;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Krippendorff's Alpha Coefficient is a statistical measure of agreement when surveying <br>
 * References: <br>
 * 		1. https://en.wikipedia.org/wiki/Krippendorff%27s_alpha <br>
 * 		2. More about Difference Function - "Computing Krippendorff 's Alpha-Reliability", <br> 
 * 			url: http://repository.upenn.edu/cgi/viewcontent.cgi?article=1043&context=asc_papers <br>
 * @author Nhat X.T. Le
 * @date Sep-28, 2015
 */
public class KrippendorffAlpha {
	private static double min = -1.0d;
	private static double max = +1.0d;
	
	public static enum DataValueType {NOMINAL, INTERVAL, RATIO, POLAR}
	
	/**
	 * @param valueMatrix - 2d array of value, each value is valueMatrix[coder][unit]
	 * @return Krippendorff's Alpha
	 */
	public static double krippendorffAlpha(double[][] valueMatrix, DataValueType dataType) {		
		int numCoders = valueMatrix.length;
		int numUnits = valueMatrix[0].length;
		
		Set<Double> distinctValuesSet = new HashSet<Double>();
		for (double[] valueArray : valueMatrix) {
			for (double value : valueArray) {
				if (!Double.isNaN(value))
					distinctValuesSet.add(value);
			}
		}		
		
		int numValues = distinctValuesSet.size();
		Double[] distinctValues = new Double[numValues];
		distinctValuesSet.toArray(distinctValues);
		
		// coincidence matrix, co[v][v']
		double[][] co = new double[numValues][numValues];
		for (int i = 0; i < numValues; ++i) {
			for (int j = i; j < numValues; ++j) {
				double value1 = distinctValues[i];
				double value2 = distinctValues[j];
				
				for (int originalUnitIndex = 0; originalUnitIndex < numUnits; ++originalUnitIndex) {
					double[] valuesOfOneUnit = new double[numCoders];
					double numNotNaNValues = 0.0d;
					for (int originalCoderIndex = 0; originalCoderIndex < numCoders; ++originalCoderIndex) {
						valuesOfOneUnit[originalCoderIndex] = valueMatrix[originalCoderIndex][originalUnitIndex];
						if (!Double.isNaN(valuesOfOneUnit[originalCoderIndex]))
							++numNotNaNValues;
					}
					
					int temp = numIncidencePairs(valuesOfOneUnit, value1, value2);
					if (temp > 0)
					co[i][j] += (double) temp / (numNotNaNValues - 1.0d);
				}
				
				co[j][i] = co[i][j];
			}
		}
		
		double[] numCoincidence = new double[numValues];
		for (int i = 0; i < numValues; ++i) {
			for (int j = 0; j < numValues; ++j) {
				numCoincidence[i] += co[i][j];
			}
		}
		double numCoincidenceTotal = Arrays.stream(numCoincidence).sum();
		
		double alpha = calculateAlphaFromCovarience(co, distinctValues, numCoincidence, numCoincidenceTotal, dataType);
		return alpha;
	}
		
	private static double calculateAlphaFromCovarience(double[][] co,
			Double[] distinctValues, double[] numCoincidence,
			double numCoincidenceTotal, DataValueType dataType) {
		
		double numerator = 0.0d;
		double denominator = 0.0d;
		
		int numValues = distinctValues.length;		
		for (int i = 0; i < numValues; ++i) {
			for (int j = 0; j < numValues; ++j) {
				double value1 = distinctValues[i];
				double value2 = distinctValues[j];
				
				double diff = difference(value1, value2, dataType);
				numerator += co[i][j] * diff;
				denominator += numCoincidence[i] * numCoincidence[j] * diff / (numCoincidenceTotal - 1);
			}
		}
		
		return 1 - numerator / denominator;
	}

	private static double difference(double value1, double value2,
			DataValueType dataType) {
		
		double diff = 0.0d; 
		switch (dataType) {
		case NOMINAL:
			if (value1 == value2)
				diff = 0.0d;
			else
				diff = 1.0d;
			break;
		case INTERVAL:
			diff = (value1 - value2) * (value1 - value2);
			break;
		case RATIO:
			if (value1 == value2)
				diff = 0;
			else {
				double v1 = normalizeToUnit(value1);
				double v2 = normalizeToUnit(value2);
				double temp = (Math.abs(v1) - Math.abs(v2)) / (Math.abs(v1) + Math.abs(v2));
				diff = temp * temp;
				if (Double.isNaN(diff))
					System.out.println();
			}
			break;		
		case POLAR:
			if (value1 == value2)
				diff = 0;
			else {
				double sum = value1 + value2;
				diff = (value1 - value2) * (value1 - value2) / ((sum - 2 * min) * (2 * max - sum));
			}
			break;
		}
		return diff;
	}
	
	private static double normalizeToUnit(double value) {
	
		return (value - min) / (max - min);
	}

	private static int numIncidencePairs(double[] valuesForOneUnit, Double value1,	Double value2) {
		int numCoders = valuesForOneUnit.length;
		
		int numCoincidence = 0;
		for (int i = 0; i < numCoders; ++i) {
			for (int j = 0; j < numCoders; ++j) {
				if (i != j) {
					int incidentToValue1 = (valuesForOneUnit[i] == value1) ? 1 : 0;
					int incidentToValue2 = (valuesForOneUnit[j] == value2) ? 1 : 0;
					
					numCoincidence += incidentToValue1 * incidentToValue2;
				}
			}
		}
		return numCoincidence;
	}	
	
/*	private static class DistinctValues {
		Map<Double, Map<Double, Double>> coincidienceMatrix = new HashMap<Double, Map<Double, Double>>();
				
		public DistinctValues(Set<Double> distinctValues) {
			for (Double value1 : distinctValues) {
				coincidienceMatrix.put(value1, new HashMap<Double, Double>());
				for (Double value2 : distinctValues) {
					coincidienceMatrix.get(value1).put(value2, Double.NaN);
				}
			}
		}
		
		public int getDistinctValuesNum() {
			return coincidienceMatrix.size();
		}
		
		public double getCoincidence(double value1, double value2) {
			if (coincidienceMatrix.containsKey(value1) && coincidienceMatrix.get(value1).containsKey(value2))
				return coincidienceMatrix.get(value1).get(value2);
			else
				return Double.NaN;
		}
	}*/
	
	public static void main(String[] args) {
		double[] coderA = new double[] {Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 3, 4, 1, 2, 1, 1, 3, 3, Double.NaN, 3};
		double[] coderB = new double[] {1, Double.NaN, 2, 1, 3, 3, 4, 3, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN};
		double[] coderC = new double[] {Double.NaN, Double.NaN, 2, 1, 3, 4, 4, Double.NaN, 2, 1, 1, 3, 3, Double.NaN, 4};
		
		double[][] valueMatrix = new double[][] {coderA, coderB, coderC};
		
		DataValueType[] dataTypes = new DataValueType[] {DataValueType.NOMINAL, DataValueType.INTERVAL, DataValueType.RATIO};
		
		for (DataValueType dataType : dataTypes) {
			double alpha = krippendorffAlpha(valueMatrix, dataType);
			System.out.println("Krippendorf Alpha with " + dataType + " data: " + alpha);
		}
	}

	public static double getMin() {
		return min;
	}

	public static void setMin(double min) {
		KrippendorffAlpha.min = min;
	}

	public static double getMax() {
		return max;
	}

	public static void setMax(double max) {
		KrippendorffAlpha.max = max;
	}
}
