package edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset;

public class PairExtractorProgram {

	public static void main(String[] args) throws InterruptedException {
		
		PairExtractor pairExtractor = PairExtractor.getInstance();

//		pairExtractor.buildDataset();
//		pairExtractor.buildDatasetMultiThreads();		
		
//		pairExtractor.outputExcelFile();
		pairExtractor.outputExcelFileMultiThread();
//		pairExtractor.testSentimentCalculation();
		
//		pairExtractor.updateExcelFileWithCorrelation();
		
//		TrainingDatasetCreator.buildTrainingDataset();
//		TrainingDatasetCreator.addCuiRow();
		
	}

}
