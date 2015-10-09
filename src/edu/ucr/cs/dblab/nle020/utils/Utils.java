package edu.ucr.cs.dblab.nle020.utils;

import java.util.Date;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class Utils {
	
	public static void printRunningTime(long startTime, String message, boolean... useErr) {

		if (useErr.length == 0 || useErr[0] == false)
			System.out.println(message + " in \t" + (double) (System.currentTimeMillis() - startTime) / 1000.0f + " s");
		else if (useErr[0] == true)
			System.err.println(message + " in \t" + (double) (System.currentTimeMillis() - startTime) / 1000.0f + " s");
	}	
	
	public static void printTotalHeapSize(String message, boolean... useErr) {
		if (useErr.length == 0 || useErr[0] == false)
			System.out.println(message + "\t" + Runtime.getRuntime().totalMemory()/Math.pow(2, 20) + " MB\t" + new Date());
		else if (useErr[0] == true)
			System.err.println(message + "\t" + Runtime.getRuntime().totalMemory()/Math.pow(2, 20) + " MB\t" + new Date());
	}
	
	// bound is exclusive
	public static Set<Integer> randomIndices(int bound, int numIndices) {
		
		Set<Integer> indices = new HashSet<Integer>();
		Random random = new Random();
		while (indices.size() < numIndices) {
			indices.add(random.nextInt(bound));
		}
		
		return indices;
	}
}
