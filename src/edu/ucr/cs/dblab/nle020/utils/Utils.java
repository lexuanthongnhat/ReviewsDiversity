package edu.ucr.cs.dblab.nle020.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Utils {
	/** Get CPU time in nanoseconds. */
	public static long getCpuTime( ) {
	    ThreadMXBean bean = ManagementFactory.getThreadMXBean( );
	    return bean.isCurrentThreadCpuTimeSupported( ) ?
	        bean.getCurrentThreadCpuTime( ) : 0L;
	}
	
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
	
	public static double rounding(double number, int numDigits) {
		double forRounding = Math.pow(10, numDigits);
		return (double) Math.round(number * forRounding) / forRounding;
	}
	
	public static double runningTimeInMs(long startTime, int numDigits) {
		return rounding(
					(double) (System.nanoTime() - startTime) / 1000000.0f, 
					numDigits
				);
	}
	
	public static double runningCpuTimeInMs(long startTimeInNano, long stopTimeInNano, int numDigits) {
		return rounding(
					(double) (stopTimeInNano - startTimeInNano) / 1000000.0f, 
					numDigits
				);
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
	
	public static <T> void writeToJson(T object, String outputPath) {
			
		ObjectMapper mapper = new ObjectMapper();
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath),
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			
			mapper.writeValue(writer, object);
		} catch (IOException e) {
			System.err.println("Can't write to file \"" + outputPath + "\" or error with the object");
			System.err.println(e.getMessage());
		} 
	}
	
	public static <T> T readCollectionFromJson(String filePath, TypeReference<T> typeReference) {
		
		ObjectMapper mapper = new ObjectMapper();
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath))) {
			T collection = mapper.readValue(reader, typeReference);
			
			return collection;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public static <T> T readValueFromJson(String filePath, Class<T> type) {
		
		ObjectMapper mapper = new ObjectMapper();
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath))) {
			T value = mapper.readValue(reader, type);

			return value;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
}
