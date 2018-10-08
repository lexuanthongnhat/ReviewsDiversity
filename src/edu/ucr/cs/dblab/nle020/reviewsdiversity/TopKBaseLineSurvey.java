package edu.ucr.cs.dblab.nle020.reviewsdiversity;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import edu.ucr.cs.dblab.nle020.metamap.MetaMapParser;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.composite.GreedySetThreadImpl;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset.DoctorSentimentReview;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset.SentimentCalculator;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentReview;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSentence;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.SentimentSet;
import edu.ucr.cs.dblab.nle020.utils.Utils;
import gov.nih.nlm.nls.metamap.Ev;
import gov.nih.nlm.nls.metamap.Position;

public class TopKBaseLineSurvey {

	private static final int K = 3;
	private static final float THRESHOLD = 0.3f;

	private static int NUM_DOCS_TO_SURVEY = 3;
	private static int NUM_REVIEWS_PER_DOC = 6;
	private static int THRESHOLD_ON_NUM_SENTENCE_PER_REVIEW = 30;
	private static final int SENTENCE_COLUMN_WIDTH = 36;
	private static XSSFWorkbook wb = new XSSFWorkbook();
	private static Font highlightFont = wb.createFont();
	private static Font headingFont = wb.createFont();
	private static MetaMapParser mmParser = new MetaMapParser();
	private static List<Integer> DOCS = new ArrayList<>(Arrays.asList(796942, 1052723, 378031,
			784091, 303476, 1088737, 250230, 149560, 1106190, 893234));

	//private static Integer[] indices = new Integer[] {106, 109, 110, 111, 120, 130, 140, 150, 160, 170};
	private static Set<Integer> indices = Utils.randomIndices(1000, NUM_DOCS_TO_SURVEY);
	// Discard "dr" concept - C0031831
	private static Set<String> ignoreCuis = new HashSet<String>(Arrays.asList(
			new String[]{"C0031831"}
			));

	static {
		highlightFont.setColor(HSSFColor.GREEN.index);
		highlightFont.setUnderline((byte) 1);
		highlightFont.setBold(true);

		//headingFont.setBold(true);
		headingFont.setFontName("Calibri");
		headingFont.setFontHeightInPoints((short) 13);
		headingFont.setColor(IndexedColors.BLUE_GREY.index);
	}

	public static void main(String[] args) {
		String surveyFolder = "src/main/resources/survey/topsentences/";
		//prepareSurvey(surveyFolder);

		collectSurveys(surveyFolder + "new-survey/");
	}

	private static void collectSurveys(String surveyFolder) {
		List<Path> surveys = new ArrayList<>();

		try {
			Files.walkFileTree(Paths.get(surveyFolder), new SimpleFileVisitor<Path>(){

				@Override
				public FileVisitResult visitFile(Path file,
						BasicFileAttributes attrs) throws IOException {

					if  (file.toString().endsWith(".xlsx"))
						surveys.add(file);
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}

		Map<Integer, Map<String, Integer>> docToCoveragesOurMethod = new HashMap<>();
		Map<Integer, Map<String, Integer>> docToCoveragesBliu = new HashMap<>();
		Map<Integer, Map<String, Integer>> docToCoveragesTextRank = new HashMap<>();
		Map<Integer, Map<String, Integer>> docToCoveragesSummry = new HashMap<>();
		Map<Integer, Integer> docToRowUnitNum = new HashMap<>();
		surveys.stream().forEach(file -> collectSurvey(file,
				docToCoveragesOurMethod,
				docToCoveragesBliu,
				docToCoveragesTextRank,
				docToCoveragesSummry,
				docToRowUnitNum));

		List<String> participants = new ArrayList<>();
		for (Path file: surveys) {
			participants.add(file.getFileName().toString());
		}

		try (BufferedWriter writer = Files.newBufferedWriter(
				Paths.get(surveyFolder + "survey-detail.csv"),
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

			writer.write("Participants, Method");
			for (Integer doc : DOCS)
				writer.write(", " + doc);
			writer.newLine();

			for (String participant : participants) {
				String ourMethodStr = participant + ", our method";
				String bliuStr = ", bing liu";
				String textrankStr = ", textrank";
				String summryStr = ", summry";

				for (Integer doc : DOCS) {
					ourMethodStr += ", ";
					if (docToCoveragesOurMethod.get(doc).containsKey(participant))
						ourMethodStr += docToCoveragesOurMethod.get(doc).get(participant);

					bliuStr += ", ";
					if (docToCoveragesBliu.get(doc).containsKey(participant))
						bliuStr += docToCoveragesBliu.get(doc).get(participant);

					textrankStr += ", ";
					if (docToCoveragesTextRank.get(doc).containsKey(participant))
						textrankStr += docToCoveragesTextRank.get(doc).get(participant);

					summryStr += ", ";
					if (docToCoveragesSummry.get(doc).containsKey(participant))
						summryStr += docToCoveragesSummry.get(doc).get(participant);
				}

				writer.write(ourMethodStr); writer.newLine();
				writer.write(bliuStr); writer.newLine();
				writer.write(textrankStr); writer.newLine();
				writer.write(summryStr); writer.newLine();
			}

			writer.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}


		surveyStatistics(docToRowUnitNum,
				docToCoveragesOurMethod, docToCoveragesBliu,
				docToCoveragesTextRank, docToCoveragesSummry,
		    surveyFolder + "survey-result.csv");
		surveySummary(participants, docToRowUnitNum, docToCoveragesOurMethod, docToCoveragesBliu,
				docToCoveragesTextRank, docToCoveragesSummry,
		    surveyFolder + "survey-summary.csv");
	}

	private static void surveyStatistics(
			Map<Integer, Integer> docToRowUnitNum,
			Map<Integer, Map<String, Integer>> docToCoveragesOurMethod,
			Map<Integer, Map<String, Integer>> docToCoveragesBaseline,
			Map<Integer, Map<String, Integer>> docToCoveragesTextRank,
			Map<Integer, Map<String, Integer>> docToCoveragesSummry,
			String outputPath) {

		Map<Integer, Double> docToAverageOurMethod = new HashMap<>();
		Map<Integer, Double> docToAverageBaseline  = new HashMap<>();
		Map<Integer, Double> docToAverageTextRank  = new HashMap<>();
		Map<Integer, Double> docToAverageSummry  = new HashMap<>();


		for (Integer docId : docToRowUnitNum.keySet()) {
			double averageOurMethod = docToCoveragesOurMethod.get(docId).values().stream()
					.collect(Collectors.averagingDouble(coverage -> (double) coverage));
			double averageBaseline = docToCoveragesBaseline.get(docId).values().stream()
					.collect(Collectors.averagingDouble(coverage -> (double) coverage));
			double averageTextRank = docToCoveragesTextRank.get(docId).values().stream()
					.collect(Collectors.averagingDouble(coverage -> (double) coverage));
			double averageSummry = docToCoveragesSummry.get(docId).values().stream()
					.collect(Collectors.averagingDouble(coverage -> (double) coverage));

			docToAverageOurMethod.put(docId, averageOurMethod);
			docToAverageBaseline.put(docId, averageBaseline);
			docToAverageTextRank.put(docId, averageTextRank);
			docToAverageSummry.put(docId, averageSummry);
		}

		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath),
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

			writer.write("docId, #sentences, our method, bliu, textrank, summry");
			writer.newLine();

			for (Integer docId : docToRowUnitNum.keySet()) {
				writer.write(docId + ", " + docToRowUnitNum.get(docId) + ", "
						+ docToAverageOurMethod.get(docId) + ", " + docToAverageBaseline.get(docId) + ", "
						+ docToAverageTextRank.get(docId) + ", " + docToAverageSummry.get(docId));
				writer.newLine();
			}

			writer.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("Find the result in \"" + outputPath + "\"");
	}

	private static void surveySummary(
			List<String> participants,
	    Map<Integer, Integer> docToRowUnitNum,
			Map<Integer, Map<String, Integer>> docToCoveragesOurMethod,
			Map<Integer, Map<String, Integer>> docToCoveragesBaseline,
			Map<Integer, Map<String, Integer>> docToCoveragesTextRank,
			Map<Integer, Map<String, Integer>> docToCoveragesSummry,
			String outputPath) {

		List<List<Double>> participantWinCounts = new ArrayList<>();

		for (String participant : participants) {
			List<Double> winCounts = new ArrayList<>(Arrays.asList(0.0, 0.0, 0.0, 0.0));

			for (Integer docId : docToRowUnitNum.keySet()) {
				if (!docToCoveragesOurMethod.get(docId).containsKey(participant))
					continue;
				int ourMethod = docToCoveragesOurMethod.get(docId).get(participant);
				int baseline = docToCoveragesBaseline.get(docId).get(participant);
				int textrank = docToCoveragesTextRank.get(docId).get(participant);
				int summry = docToCoveragesSummry.get(docId).get(participant);

				int countMax = IntStream.of(ourMethod, baseline, textrank, summry).max().getAsInt();
				double winPoint = 1;
				int sameMaxCount = 0;
				if (ourMethod == countMax)
					sameMaxCount++;
				if (baseline == countMax)
					sameMaxCount++;
				if (textrank == countMax)
					sameMaxCount++;
				if (summry == countMax)
					sameMaxCount++;

				if (sameMaxCount >= 2)
					winPoint = 1;

				if (ourMethod == countMax) {
					sameMaxCount++;
					winCounts.set(0, winCounts.get(0) + winPoint);
				}
				if (baseline == countMax) {
					sameMaxCount++;
					winCounts.set(1, winCounts.get(1) + winPoint);
				}
				if (textrank == countMax) {
					sameMaxCount++;
					winCounts.set(2, winCounts.get(2) + winPoint);
				}
				if (summry == countMax) {
					sameMaxCount++;
					winCounts.set(3, winCounts.get(3) + winPoint);
				}
			}
			participantWinCounts.add(winCounts);
		}

		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath),
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			writer.write("Summary");
			writer.newLine();

			writer.write("participants, our method wins, bliu wins, textrank wins, summry wins, "
									 + "(if 2 methods are the same -> both win)");
			writer.newLine();
			for (int i = 0; i < participants.size(); ++i) {
				List<Double> winCount = participantWinCounts.get(i);
				writer.write("participant (" + i + "), " + winCount.get(0) + ", "
										 + winCount.get(1) + ", " + winCount.get(2) + ", " + winCount.get(3) + ", ");
				writer.newLine();
			}
			writer.newLine();
			writer.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("Find the survey summary in \"" + outputPath + "\"");
	}

	private static void collectSurvey(Path file,
			Map<Integer, Map<String, Integer>> docToCoveragesOurMethod,
			Map<Integer, Map<String, Integer>> docToCoveragesBliu,
			Map<Integer, Map<String, Integer>> docToCoveragesTextRank,
			Map<Integer, Map<String, Integer>> docToCoveragesSummry,
			Map<Integer, Integer> docToRowUnitNum) {
		String fileName = file.getFileName().toString();
		System.out.println(fileName);

		Map<Integer, List<Integer>> docToIndicesOurMethod = new HashMap<>();
		Map<Integer, List<Integer>> docToIndicesBliu = new HashMap<>();
		Map<Integer, List<Integer>> docToIndicesTextRank = new HashMap<>();
		Map<Integer, List<Integer>> docToIndicesSummry = new HashMap<>();
		for (Integer doc : DOCS) {
			docToIndicesOurMethod.put(doc, new ArrayList<>(Arrays.asList(1, 2, 3)));
		}

		docToIndicesBliu.put(796942, new ArrayList<>(Arrays.asList(4, 5, 6)));
		docToIndicesBliu.put(1106190, new ArrayList<>(Arrays.asList(4, 5, 6)));
		docToIndicesBliu.put(893234, new ArrayList<>(Arrays.asList(2, 3, 4)));
		for (int i = 1; i < 8; i++)
			docToIndicesBliu.put(DOCS.get(i), new ArrayList<>(Arrays.asList(3, 4, 5)));

		docToIndicesTextRank.put(796942, new ArrayList<>(Arrays.asList(1, 7, 6)));
		docToIndicesTextRank.put(1052723, new ArrayList<>(Arrays.asList(1, 6, 3)));
		docToIndicesTextRank.put(378031, new ArrayList<>(Arrays.asList(1, 6, 7)));
		docToIndicesTextRank.put(784091, new ArrayList<>(Arrays.asList(1, 6, 7)));
		docToIndicesTextRank.put(303476, new ArrayList<>(Arrays.asList(4, 2, 7)));
		docToIndicesTextRank.put(1088737, new ArrayList<>(Arrays.asList(6, 7, 8)));
		docToIndicesTextRank.put(250230, new ArrayList<>(Arrays.asList(5, 6, 4)));
		docToIndicesTextRank.put(149560, new ArrayList<>(Arrays.asList(5, 1, 2)));
		docToIndicesTextRank.put(1106190, new ArrayList<>(Arrays.asList(7, 3, 8)));
		docToIndicesTextRank.put(893234, new ArrayList<>(Arrays.asList(2, 1, 4)));

		docToIndicesSummry.put(796942, new ArrayList<>(Arrays.asList(1, 8, 7)));
		docToIndicesSummry.put(1052723, new ArrayList<>(Arrays.asList(7, 6, 4)));
		docToIndicesSummry.put(378031, new ArrayList<>(Arrays.asList(1, 8, 9)));
		docToIndicesSummry.put(784091, new ArrayList<>(Arrays.asList(3, 6, 2)));
		docToIndicesSummry.put(303476, new ArrayList<>(Arrays.asList(1, 4, 6)));
		docToIndicesSummry.put(1088737, new ArrayList<>(Arrays.asList(6, 1, 2)));
		docToIndicesSummry.put(250230, new ArrayList<>(Arrays.asList(7, 5, 8)));
		docToIndicesSummry.put(149560, new ArrayList<>(Arrays.asList(6, 2, 7)));
		docToIndicesSummry.put(1106190, new ArrayList<>(Arrays.asList(9, 8, 10)));
		docToIndicesSummry.put(893234, new ArrayList<>(Arrays.asList(1, 5, 6)));


		try {
			Workbook wb = new XSSFWorkbook(file.toString());
			Iterator<Sheet> sheetIterator = wb.iterator();
			while (sheetIterator.hasNext()) {
				Sheet sheet = sheetIterator.next();
				if (sheet.getSheetName().startsWith("Instruction"))
					continue;

				Integer docId = Integer.parseInt(sheet.getSheetName());
				Row row = sheet.getRow(0);

				int count = 0;
				int countOurMethod = 0;
				int countBliu = 0;
				int countTextRank = 0;
				int countSummry = 0;
				for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum();
				    ++rowIndex) {
					row = sheet.getRow(rowIndex);

					boolean coveredByOurMethod = false;
					boolean coveredByBliu = false;
					boolean coveredByTextRank = false;
					boolean coveredBySummry = false;
					for (int cellIndex = row.getFirstCellNum() + 1; cellIndex <= row.getLastCellNum();
					    ++cellIndex) {
						String cellString = "";
						if (row.getCell(cellIndex) != null)
							cellString = row.getCell(cellIndex).getStringCellValue();

						if (cellString.equalsIgnoreCase("x")) {
							if (docToIndicesOurMethod.get(docId).contains(cellIndex))
								coveredByOurMethod = true;
							if (docToIndicesBliu.get(docId).contains(cellIndex))
								coveredByBliu = true;
							if (docToIndicesTextRank.get(docId).contains(cellIndex))
								coveredByTextRank = true;
							if (docToIndicesSummry.get(docId).contains(cellIndex))
								coveredBySummry = true;
						}
					}

					if (coveredByOurMethod)
						++countOurMethod;
					if (coveredByBliu)
						++countBliu;
					if (coveredByTextRank)
						++countTextRank;
					if (coveredBySummry)
						++countSummry;
					++count;
				}

				if (countOurMethod > 0 || countBliu > 0 || countTextRank > 0 || countSummry > 0) {
					if (!docToRowUnitNum.containsKey(docId)) {
						docToRowUnitNum.put(docId, count);
						docToCoveragesOurMethod.put(docId, new HashMap<>());
						docToCoveragesBliu.put(docId, new HashMap<>());
						docToCoveragesTextRank.put(docId, new HashMap<>());
						docToCoveragesSummry.put(docId, new HashMap<>());
					}

					docToCoveragesOurMethod.get(docId).put(fileName, countOurMethod);
					docToCoveragesBliu.get(docId).put(fileName, countBliu);
					docToCoveragesTextRank.get(docId).put(fileName, countTextRank);
					docToCoveragesSummry.get(docId).put(fileName, countSummry);
				}
			}

			wb.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unused")
	private static void prepareSurvey(String surveyFolder) {
		long startTime = System.currentTimeMillis();

		/*Map<Integer, List<SentimentReview>> docToSentimentReviews = chooseDoctorToSentimentReviews(
		    "src/main/resources/survey/pre_selected_survey.txt");*/
    Map<String, List<SentimentReview>> docToSentimentReviews = chooseDoctorToSentimentReviews();

		Map<String, List<SentimentSentence>> docToSentimentSentences =
		    convertToDocToSentimentSentences(docToSentimentReviews);
		Map<String, List<SentimentSentence>> docToTopSentencesOurMethod = getKSentencesOurMethod(
		    docToSentimentSentences, K, 0.5f);
		Map<String, List<SentimentSentence>> docToTopSentencesBaseline = getKSentencesBaseline(
		    docToSentimentSentences);
		Map<String, List<SentimentSentence>> docToTopSentences = new HashMap<>();
		for (String docId : docToTopSentencesOurMethod.keySet()) {
			docToTopSentences.put(docId, reorderTopSentences(docToTopSentencesOurMethod.get(docId),
			                                                 docToTopSentencesBaseline.get(docId)));
		}


		String outputExcelPath = surveyFolder + "survey.xlsx";
		outputToExcel(docToSentimentSentences, docToTopSentences, outputExcelPath);
		Utils.writeToJson(docToTopSentencesOurMethod, surveyFolder + "our-method.txt");
		Utils.writeToJson(docToTopSentencesBaseline, surveyFolder + "baseline.txt");

		Utils.printRunningTime(startTime, "Finished outputing survey to \"" + outputExcelPath + "\"");
	}

	// our method sentences only first, then shared sentences of our method and baseline,
	// end by baseline only
	private static List<SentimentSentence> reorderTopSentences(
			List<SentimentSentence> ourMethodSentences, List<SentimentSentence> baselineSentences) {
		List<SentimentSentence> reorderedSentences = new ArrayList<>();

		ourMethodSentences.stream().forEach(sentence -> {
				if (!baselineSentences.contains(sentence))
					reorderedSentences.add(sentence);
				});
		ourMethodSentences.stream().forEach(sentence -> {
			if (baselineSentences.contains(sentence))
				reorderedSentences.add(sentence);
			});

		baselineSentences.stream().forEach(sentence -> {
			if (!ourMethodSentences.contains(sentence))
				reorderedSentences.add(sentence);
			});
		if (reorderedSentences.size() > 2 * K)
			System.err.println("Wrong reorder of top sentences");

		return reorderedSentences;
	}

	private static Map<String, List<SentimentSentence>> convertToDocToSentimentSentences(
			Map<String, List<SentimentReview>> docToSentimentReviews) {
		Map<String, List<SentimentSentence>> docToSentimentSentences = new HashMap<>();
		for (String docId : docToSentimentReviews.keySet()) {
			List<SentimentSentence> sentences = new ArrayList<>();
			for (SentimentReview review : docToSentimentReviews.get(docId)) {
				List<SentimentSentence> reviewSentences = review.getSentences();
				sentences.addAll(reviewSentences);
			}
			docToSentimentSentences.put(docId, sentences);
		}

		return docToSentimentSentences;
	}

	private static Map<String, List<SentimentSentence>> getKSentencesOurMethod(
	    Map<String, List<SentimentSentence>> docToSentimentSentences,
	    int k,
	    float threshold) {

		Map<String, List<SentimentSet>> docToSentimentSets = new HashMap<>();
		for (String docId : docToSentimentSentences.keySet()) {
			List<SentimentSet> sentimentSets = new ArrayList<>();
			docToSentimentSentences.get(docId).forEach(sentence -> sentimentSets.add(sentence));
			docToSentimentSets.put(docId, sentimentSets);
		}

		Map<String, List<SentimentSentence>> docToTopKSentences = new HashMap<>();

		ConcurrentMap<String, StatisticalResult> docToStatisticalResult = new ConcurrentHashMap<>();
		ConcurrentMap<String, List<SentimentSet>> docToTopKSets = new ConcurrentHashMap<>();

		GreedySetThreadImpl ourMethod = new GreedySetThreadImpl(k, threshold,
				docToStatisticalResult, docToTopKSets, 0, 1, docToSentimentSets);
		ourMethod.run();


		for (String docId : docToTopKSets.keySet()) {
			List<SentimentSentence> sentences = new ArrayList<>();
			docToTopKSets.get(docId).forEach(set -> {
			  // To avoid error when writing to Json (recursive FullPair)
				set.setFullPairs(new ArrayList<>());
				sentences.add((SentimentSentence) set);
			});
			docToTopKSentences.put(docId, sentences);
		}

		return docToTopKSentences;
	}

	private static Map<String, List<SentimentSentence>> getKSentencesBaseline(
	    Map<String, List<SentimentSentence>> docToSentimentSentences) {

		Map<String, List<SentimentSet>> docToSentimentSets = new HashMap<>();
		for (String docId : docToSentimentSentences.keySet()) {
			List<SentimentSet> sentimentSets = new ArrayList<>();
			docToSentimentSentences.get(docId).forEach(sentence -> sentimentSets.add(sentence));
			docToSentimentSets.put(docId, sentimentSets);
		}

		Map<String, List<SentimentSentence>> docToTopKSentences = new HashMap<>();
		for (String docId : docToSentimentSets.keySet()) {
//			List<SentimentSet> sets = FreqBasedTopSets.selectMostPopular(docToSentimentSets.get(docId), K);
			List<SentimentSet> sets = new ArrayList<>();
			List<SentimentSentence> sentences = new ArrayList<>();
			sets.forEach(set -> {
			  // To avoid error when writing to Json (recursive FullPair)
				set.setFullPairs(new ArrayList<>());
				sentences.add((SentimentSentence) set);
			});
			docToTopKSentences.put(docId, sentences);
		}

		return docToTopKSentences;
	}

	private static void outputToExcel(
			Map<String, List<SentimentSentence>> docToSentimentSentences,
			Map<String, List<SentimentSentence>> docToTopSentences,
			String outputExcelPath) {

		Map<SentimentSentence, XSSFRichTextString> sentenceToRichText = new HashMap<>();
		for (String docId : docToSentimentSentences.keySet()) {
			docToSentimentSentences.get(docId).forEach( sentence ->
				sentenceToRichText.put(sentence, prepareColoredRichTextSentence(sentence.getSentence()))
				);
		}

		outputToExcel(docToSentimentSentences, docToTopSentences, outputExcelPath, sentenceToRichText);
	}

	private static void outputToExcel(
			Map<String, List<SentimentSentence>> docToSentimentSentences,
			Map<String, List<SentimentSentence>> docToTopSentences,
			String outputExcelPath,
			Map<SentimentSentence, XSSFRichTextString> sentenceToRichText) {

		for (String docId : docToSentimentSentences.keySet()) {
			XSSFSheet sheet = wb.createSheet(docId + "");
			fillSheet(sheet, docToSentimentSentences.get(docId), docToTopSentences.get(docId),
			    sentenceToRichText);
		}

		try {
			wb.write(Files.newOutputStream(Paths.get(outputExcelPath)));
			wb.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void fillSheet(XSSFSheet sheet,
			List<SentimentSentence> sentimentSentences,
			List<SentimentSentence> topSentences,
			Map<SentimentSentence, XSSFRichTextString> sentenceToRichText) {

		int numColumns = topSentences.size() + 1;
		sheet.createFreezePane(numColumns + 1, 1);
		for (int i = 0; i < numColumns + 1; ++i){
			sheet.setColumnWidth(i, SENTENCE_COLUMN_WIDTH * 256);
		}

		fillHeader(sheet, topSentences, sentenceToRichText);
		fillBodyRows(sheet, sentimentSentences, sentenceToRichText);

	}

	private static void fillBodyRows(XSSFSheet sheet,
			List<SentimentSentence> sentimentSentences,
			Map<SentimentSentence, XSSFRichTextString> sentenceToRichText) {

		CellStyle normalCs = sheet.getWorkbook().createCellStyle();
		normalCs.setWrapText(true);

		for (int rowId = 1; rowId <= sentimentSentences.size(); ++rowId) {
			Row row = sheet.createRow(rowId);
			Cell cellSentence = row.createCell(0);
			cellSentence.setCellStyle(normalCs);
			cellSentence.setCellValue(sentenceToRichText.get(sentimentSentences.get(rowId - 1)));
		}
	}

	private static void fillHeader(
			Sheet sheet,
			List<SentimentSentence> topSentences,
			Map<SentimentSentence, XSSFRichTextString> sentenceToRichText) {

		Row row = sheet.createRow(0);

		CellStyle headingCs = sheet.getWorkbook().createCellStyle();
		headingCs.setWrapText(true);
		headingCs.setFont(headingFont);

		CellStyle wrapTextCs = sheet.getWorkbook().createCellStyle();
		wrapTextCs.setWrapText(true);

		Cell cellSentence = row.createCell(0);
		cellSentence.setCellValue("Type \"x\" for covering sentences");
		cellSentence.setCellStyle(headingCs);

		Cell[] cellTopSentences = new Cell[topSentences.size()];
		for (int i = 0; i < topSentences.size(); ++i) {
			cellTopSentences[i] = row.createCell(i + 1);
			cellTopSentences[i].setCellStyle(wrapTextCs);
			cellTopSentences[i].setCellValue(sentenceToRichText.get(topSentences.get(i)));
		}
	}

	private static XSSFRichTextString prepareColoredRichTextSentence(String sentence) {

		XSSFRichTextString richText = new XSSFRichTextString(sentence);
		List<Ev> mappings = mmParser.getValidMappings(sentence);
		System.out.println("Parsing: \"" + sentence + "\"");
		for (Ev ev : mappings) {

			List<Position> positions;
			try {
				positions = ev.getPositionalInfo();
				for (Position pos : positions) {
					richText.applyFont(pos.getX(), pos.getX() + pos.getY(), highlightFont);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return richText;
	}

	private static Map<String, List<SentimentReview>> chooseDoctorToSentimentReviews() {
		Map<String, List<SentimentReview>> completeDocToReviews = importRawCompleteDocToReviews(
		    TopPairsProgram.DOC_TO_REVIEWS_PATH);
		Map<String, List<SentimentReview>> docToReviews = new HashMap<>();

		Set<Integer> indices = Utils.randomIndices(completeDocToReviews.size(), NUM_DOCS_TO_SURVEY);

		String[] allDocIds = new String[completeDocToReviews.size()];
		completeDocToReviews.keySet().toArray(allDocIds);

		for (Integer index : indices)
			docToReviews.put(allDocIds[index], completeDocToReviews.get(allDocIds[index]));

		Map<String, List<SentimentReview>> choosenDocToReviews = new HashMap<>();
		for (String docId : docToReviews.keySet()) {
			choosenDocToReviews.put(docId, pickUpReviewsForSurvey(docToReviews.get(docId)));
		}

		return choosenDocToReviews;
	}

	private static Map<String, List<SentimentReview>> chooseDoctorToSentimentReviews(
	    String inputFile) {

		Map<String, List<SentimentReview>> docToReviews = new HashMap<>();
		List<DoctorSentimentReview> doctorSentimentReviews =
		    TopPairsProgram.importDoctorSentimentReviewsDataset(inputFile);

		for (DoctorSentimentReview doctorSentimentReview : doctorSentimentReviews) {
			List<SentimentReview> reviews = doctorSentimentReview.getSentimentReviews();
			String docId = doctorSentimentReview.getDocId();
			docToReviews.put(docId, reviews);
		}

    return docToReviews;
  }

	private static List<SentimentReview> pickUpReviewsForSurvey(
	    List<SentimentReview> completeSetOfReviews) {

		List<SentimentReview> pickUpReviews = new ArrayList<>();

		completeSetOfReviews.removeIf(review ->
			SentimentCalculator.breakingIntoSentences(
			    review.getRawReview().getBody(),
			    Constants.USE_ADVANCED_SENTENCE_BREAKING).size() > THRESHOLD_ON_NUM_SENTENCE_PER_REVIEW);

		updatePairsOfSentimentReviews(completeSetOfReviews);
		List<String> choosenCuis = new ArrayList<>();

		if (completeSetOfReviews.size() <= NUM_REVIEWS_PER_DOC)
			pickUpReviews = completeSetOfReviews;
		else
			while (pickUpReviews.size() < NUM_REVIEWS_PER_DOC) {
				pickUpReviews.add(pickNextReview(completeSetOfReviews, choosenCuis));
			}

		return pickUpReviews;
	}

	private static SentimentReview pickNextReview(
			List<SentimentReview> completeSetOfReviews,
			List<String> choosenCuis) {

		SentimentReview nextReview = completeSetOfReviews.get(0);
		int maxUnion = 0;
		for (SentimentReview review : completeSetOfReviews) {
			int unionSize = unionSize(review.getPairs(), choosenCuis);
			if ( unionSize > maxUnion) {
				maxUnion = unionSize;
				nextReview = review;
			}
		}

		completeSetOfReviews.remove(nextReview);
		for (ConceptSentimentPair pair : nextReview.getPairs())
			choosenCuis.add(pair.getCui());

		return nextReview;
	}

	private static int unionSize(List<ConceptSentimentPair> pairs,
			List<String> choosenCuis) {
		int unionSize = 0;
		for (ConceptSentimentPair pair : pairs) {
			if (choosenCuis.contains(pair.getCui()))
				++unionSize;
		}

		return unionSize;
	}

	private static void updatePairsOfSentimentReviews(
			List<SentimentReview> pickUpReviews) {
		for (SentimentReview review : pickUpReviews) {
			List<ConceptSentimentPair> pairs = new ArrayList<>();
			for (SentimentSentence sentence : review.getSentences()) {
				pairs.addAll(sentence.getPairs());
			}

			review.setPairs(pairs);
		}

	}

	private static Map<String, List<SentimentReview>> importRawCompleteDocToReviews(
			String docToReviewsPath) {
		Map<String, List<SentimentReview>> rawDocToReviews = new HashMap<>();
		List<DoctorSentimentReview> doctorSentimentReviews =
		    TopPairsProgram.importDoctorSentimentReviewsDataset(docToReviewsPath);

		for (DoctorSentimentReview doctorSentimentReview : doctorSentimentReviews) {
			List<SentimentReview> reviews = doctorSentimentReview.getSentimentReviews();
			for (SentimentReview review : reviews)
				for (SentimentSentence sentence : review.getSentences())
					sentence.getPairs().removeIf(pair -> isIgnoreCui(pair.getCui().toUpperCase()));

			rawDocToReviews.put(doctorSentimentReview.getDocId(),
			    doctorSentimentReview.getSentimentReviews());
		}

		return rawDocToReviews;
	}

	private static boolean isIgnoreCui(String cui) {
		return ignoreCuis.contains(cui);
	}
}
