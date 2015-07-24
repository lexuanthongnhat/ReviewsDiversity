package edu.ucr.cs.dblab.nle020.reviewsdiversity;

public class Constants {
	public final static int NUM_THREADS = 1;
	public final static int NUM_THREADS_ALGORITHM = 1;
	public final static int NUM_THREADS_INIT_PAIRS = 8;
	public final static int LENGTH_THRESHOLD = 100000;
	public final static boolean USE_SECOND_MULTI_THREAD = true;
	public final static boolean USE_ADVANCED_SENTENCE_BREAKING = true;
	
	public final static int INTERVAL = 1000;
	public final static int NUM_DIGIT_PRECISION_SENTIMENT = 2;
	
	public final static int INVALID_DISTANCE = Integer.MAX_VALUE;
	public final static int INVALID_DISTANCE_FOR_ILP = Integer.MAX_VALUE;	
	//public final static int INVALID_DISTANCE_FOR_ILP = 10000;
	
	
	public final static int K = 10;
	public final static float THRESHOLD = 0.2f;
	
	public final static String ROOT_CUI = "ROOT";
	
	public final static int NUM_DOCS = 1000;
	public final static int NUM_SYNTHETIC_DOCTORS = 100;
	
	public final static boolean DEBUG_MODE = false; 
	
	public final static String CUI_TO_DEWEYS_PATH = "src/edu/ucr/cs/dblab/nle020/ontology/cui_deweys.txt";
	
	
	
	public final static boolean RR_TERMINATED_BY_K = true;
	public final static boolean FIND_BEST_LP_METHOD = false;
	public final static LPMethod MY_DEFAULT_LP_METHOD = LPMethod.DUAL_SIMPLEX;
	public static enum LPMethod {
		AUTOMATIC (-1),
		PRIMAL_SIMPLEX (0),
		DUAL_SIMPLEX (1),
		BARRIER (2),
		CONCURRENT (3),
		DETERMINISTIC_CONCURRENT (4);
		
		private int method;
		LPMethod (int method) {
			this.method = method;
		}
		
		public int method() {
			return method;
		}
	}
}
