package edu.ucr.cs.dblab.nle020.reviewsdiversity;

public class Constants {
	public final static int NUM_THREADS = 16;
	public final static int NUM_THREADS_ALGORITHM = 1;
	public final static boolean USE_SECOND_MULTI_THREAD = true;
	
	public final static int INTERVAL = 100;
	public final static int NUM_DIGIT_PRECISION_SENTIMENT = 2;
	
	public final static int INVALID_DISTANCE = Integer.MAX_VALUE;
	public final static int INVALID_DISTANCE_FOR_ILP = Integer.MAX_VALUE;
	//public final static int INVALID_DISTANCE_FOR_ILP = 10000;
	
	
	public final static int K = 10;
	public final static float THRESHOLD = 0.2f;
	
	public final static String ROOT_CUI = "ROOT";
	
	public final static int NUM_DOCS = 1000;
	
	public final static boolean DEBUG_MODE = false; 
	
	public final static String CUI_TO_DEWEYS_PATH = "src/edu/ucr/cs/dblab/nle020/ontology/cui_deweys.txt";
}
