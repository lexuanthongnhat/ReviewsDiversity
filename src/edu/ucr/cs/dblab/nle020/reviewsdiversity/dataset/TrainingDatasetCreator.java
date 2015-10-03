package edu.ucr.cs.dblab.nle020.reviewsdiversity.dataset;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucr.cs.dblab.nle020.metamap.SemanticGroups;
import edu.ucr.cs.dblab.nle020.metamap.SemanticTypes;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.Constants;
import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;
import edu.ucr.cs.dblab.nle020.utils.Utils;

public class TrainingDatasetCreator {
	
	private final static int FREQUENCY_THRESHOLD = 20;
	private final static String SOURCE_FOLDER = Constants.SOURCE_PATH;	
	private final static String DESKTOP_FOLDER;
	static {
		if (Files.isDirectory(Paths.get("C:\\Users\\Thong Nhat\\Desktop")))
			DESKTOP_FOLDER = "C:\\Users\\Thong Nhat\\Desktop\\";
		 else 
			DESKTOP_FOLDER = "C:\\Users\\Nhat XT Le\\Desktop\\";
	}
	
	public static void addCuiRow() {
		String filePath = SOURCE_FOLDER + "sample_based_on_type_complete.xlsx";
		String updatedFilePath = SOURCE_FOLDER + "sample_based_on_type_final.xlsx";
		
		String simplePairsPath = SOURCE_FOLDER + "simple_pairs.txt";
		List<SimplePair> simplePairs;
		if (!Files.exists(Paths.get(simplePairsPath))) {
			simplePairs = buildSimplePairsFromConceptSentimentPairs();
			try {
				exportSimplePairsToJson(simplePairs, simplePairsPath);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			simplePairs = importSimplePairsFromJson(simplePairsPath);
		}
		
		List<SimplePair> frequentSimplePairs = simplePairs.stream().filter(pair -> pair.count >= FREQUENCY_THRESHOLD).collect(Collectors.toList());
		System.out.println("# of pairs with frequency threshold of " + FREQUENCY_THRESHOLD + " is " + frequentSimplePairs.size());
		simplePairs = null;
		
		Map<String, Integer> conceptNameToCount = importConceptNameToIsValid(filePath);
		Map<String, Set<String>> conceptNameToCuis = getConceptNameToCuis(conceptNameToCount, frequentSimplePairs);
		
		updateExcelFile(filePath, updatedFilePath, conceptNameToCuis);
	}
	
	private static void updateExcelFile(String filePath, String updatedFilePath,
			Map<String, Set<String>> conceptNameToCuis) {
		try {
			Workbook wb = new XSSFWorkbook(filePath);
			Sheet sheet = wb.getSheetAt(0);
			
			for (int i = 1; i < Constants.TRAINING_SIZE + 1; ++i) {
				Row row = sheet.getRow(i);
				
				String conceptName = row.getCell(0).getStringCellValue().trim();

				Set<String> cuis = conceptNameToCuis.get(conceptName);
				String cuiString = "";
				for (String cui : cuis) {
					cuiString = cuiString + cui + ", ";
				}
				if (cuiString.length() > 2) {
					cuiString = cuiString.substring(0, cuiString.length() - 2);
				}
				
				row.createCell(5).setCellValue(cuiString);
			}
			
			wb.write(new FileOutputStream(updatedFilePath));
			wb.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

	private static Map<String, Set<String>> getConceptNameToCuis(
			Map<String, Integer> conceptNameToCount,
			List<SimplePair> frequentSimplePairs) {
		
		Map<String, Set<String>> conceptNameToCuis = new HashMap<String, Set<String>>();
		for (SimplePair pair : frequentSimplePairs) {
			String pairName = pair.getName();
			if (conceptNameToCount.containsKey(pairName) && conceptNameToCount.get(pairName) == pair.count) {
				if (!conceptNameToCuis.containsKey(pairName))
					conceptNameToCuis.put(pairName, new HashSet<String>());
				
				conceptNameToCuis.get(pairName).add(pair.id);
			}
		}
		
		return conceptNameToCuis;
	}

	private static Map<String, Integer> importConceptNameToIsValid(
			String filePath) {
		Map<String, Integer> conceptNameToCount = new HashMap<String, Integer>();
		
		try {
			Workbook wb = new XSSFWorkbook(filePath);
			Sheet sheet = wb.getSheetAt(0);
			
			for (int i = 1; i < Constants.TRAINING_SIZE + 1; ++i) {
				Row row = sheet.getRow(i);
				
				String conceptName = row.getCell(0).getStringCellValue().trim();
				Integer conceptCount = (int) row.getCell(1).getNumericCellValue();
				
				conceptNameToCount.put(conceptName, conceptCount);
			}
			
			wb.close();
		} catch (IOException e) {
			e.printStackTrace();
		}		
		
		return conceptNameToCount;
	}

	public static void buildTrainingDataset() {
		long startTime = System.currentTimeMillis();
		
		int trainingSize = Constants.TRAINING_SIZE;
		
		String simplePairsPath = SOURCE_FOLDER + "simple_pairs.txt";
		List<SimplePair> simplePairs;
		if (!Files.exists(Paths.get(simplePairsPath))) {
			simplePairs = buildSimplePairsFromConceptSentimentPairs();
			try {
				exportSimplePairsToJson(simplePairs, simplePairsPath);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			simplePairs = importSimplePairsFromJson(simplePairsPath);
		}
		
		List<SimplePair> frequentSimplePairs = simplePairs.stream().filter(pair -> pair.count >= FREQUENCY_THRESHOLD).collect(Collectors.toList());
		System.out.println("# of pairs with frequency threshold of " + FREQUENCY_THRESHOLD + " is " + frequentSimplePairs.size());
		simplePairs = null;
		
		Map<String, Set<SimplePair>> typeToSimplePairs = buildTypeToSimplePairs(frequentSimplePairs);
		Map<String, Set<SimplePair>> groupToSimplePairs = buildGroupToSimplePairs(typeToSimplePairs);
		
		Utils.printRunningTime(startTime, "There are " + typeToSimplePairs.size() + " types");
		Utils.printRunningTime(startTime, "There are " + groupToSimplePairs.size() + " groups");
		
		Set<SimplePair> sampleBasedOnType = samplingSimplePairs(typeToSimplePairs, trainingSize, false);
		Set<SimplePair> sampleBasedOnGroup = samplingSimplePairs(groupToSimplePairs, trainingSize, true);
		
		exportToExcelFile(sampleBasedOnType, false, SOURCE_FOLDER + "sample_based_on_type.xlsx");
		exportToExcelFile(sampleBasedOnGroup, true, SOURCE_FOLDER + "sample_based_on_group.xlsx");
	}

	private static void exportToExcelFile(Set<SimplePair> sampleBasedOnType, boolean isGroup,
			String outputPath) {
		
		try {
			Workbook wb = new XSSFWorkbook();
			Sheet sheet = wb.createSheet("sample");
			sheet.createFreezePane(5, 1);
			
			addHeader(sheet, isGroup);
			int rowIndex = 1;
			for (SimplePair pair : sampleBasedOnType) {
				Row row = sheet.createRow(rowIndex);
				++rowIndex;
				addRow(row, pair, isGroup);
			}
			
			wb.write(new FileOutputStream(outputPath));
			wb.close();
		} catch (IOException e) {
			e.printStackTrace();
		} 
		System.out.println("Exported to \"" + outputPath + "\"");
	}

	private static void addRow(Row row, SimplePair pair, boolean isGroup) {
		Font nameCellFont = row.getSheet().getWorkbook().createFont();
		nameCellFont.setBold(true);
		nameCellFont.setFontName("Calibri");
		nameCellFont.setFontHeightInPoints((short) 13);
		
		CellStyle nameCellCS = row.getSheet().getWorkbook().createCellStyle();		
		nameCellCS.setWrapText(true);
		nameCellCS.setFont(nameCellFont);
		
		CellStyle normalCellCS = row.getSheet().getWorkbook().createCellStyle();		
		normalCellCS.setWrapText(true);
		
		Cell cellName = row.createCell(0);
		Cell cellCount = row.createCell(1);
		Cell cellGroup = row.createCell(2);
		Cell cellCui = row.createCell(5);
		
		cellName.setCellStyle(nameCellCS);
		cellCount.setCellStyle(normalCellCS);
		cellGroup.setCellStyle(normalCellCS);
		cellCui.setCellStyle(normalCellCS);
		
		cellName.setCellValue(pair.getName());
		cellCount.setCellValue(pair.getCount());
		cellCui.setCellValue(pair.getId());
		
		String typeString = "";
		if (!isGroup) {			
			for (String type : pair.types) {
				typeString = typeString + SemanticTypes.getInstance().getFullTypeNameByType(type) + ", ";
			}
			if (typeString.length() > 0)
				typeString = typeString.substring(0, typeString.length() - 2);
		} else {
			Set<String> groups = new HashSet<String>();
			
			for (String type : pair.types) {
				groups.add(SemanticGroups.getSemanticGroupName(type));				
			}
			for (String group : groups) {
				typeString = typeString + group + ", ";
			}
			if (typeString.length() > 0)
				typeString = typeString.substring(0, typeString.length() - 2);
		}
		
		cellGroup.setCellValue(typeString);		
	}

	private static void addHeader(Sheet sheet, boolean isGroup) {
		Font headingFont = sheet.getWorkbook().createFont();
		headingFont.setBold(true);
		headingFont.setFontName("Calibri");
		headingFont.setFontHeightInPoints((short) 13);
		headingFont.setColor(IndexedColors.BLUE_GREY.index);
		
		CellStyle cs = sheet.getWorkbook().createCellStyle();		
		cs.setWrapText(true);
		cs.setFont(headingFont);
		
		Row row = sheet.createRow(0);
		
		Cell cellName = row.createCell(0);
		Cell cellCount = row.createCell(1);
		Cell cellGroup = row.createCell(2);
		
		Cell cellUseful = row.createCell(4);
		Cell cellCui = row.createCell(5);
		
		cellName.setCellStyle(cs);
		cellCount.setCellStyle(cs);
		cellGroup.setCellStyle(cs);
		cellUseful.setCellStyle(cs);
		cellCui.setCellStyle(cs);
		
		cellName.setCellValue("Concept");
		cellCount.setCellValue("Count");
		cellCui.setCellValue("CUI");
		
		if (isGroup)
			cellGroup.setCellValue("Group");
		else 
			cellGroup.setCellValue("Type");
		
		cellUseful.setCellValue("Useful? (type \"x\" if yes, leave it blank if no)");
	}

	/**
	 * 
	 * @param categoryToSimplePairs - category can be type or group
	 * @param numSamples
	 * @param isGroup - true if it's group, false if it's type
	 * @return
	 */
	private static Set<SimplePair> samplingSimplePairs(
			Map<String, Set<SimplePair>> categoryToSimplePairs,
			int numSamples,
			boolean isGroup) {
		Set<SimplePair> sample = new HashSet<SimplePair>();
		
		Set<SimplePair> allPairs = new HashSet<SimplePair>();
		for (Set<SimplePair> pairs : categoryToSimplePairs.values()) {
			allPairs.addAll(pairs);
		}
		int numPairs = allPairs.size();
		
		int minNumPerCategory = numSamples / categoryToSimplePairs.size();
		
		Map<String, Integer> categoryToMinNum = new HashMap<String, Integer>();
		Map<String, Set<SimplePair>> categoryToSamplePairs = new HashMap<String, Set<SimplePair>>();

		for (String category : categoryToSimplePairs.keySet()) {
			categoryToSamplePairs.put(category, new HashSet<SimplePair>());
			Set<SimplePair> pairs = categoryToSimplePairs.get(category);
			
			if (pairs.size() < minNumPerCategory) 
				categoryToMinNum.put(category, pairs.size());
			else
				categoryToMinNum.put(category, minNumPerCategory);
		}
								
		Map<String, Double> categoryToPosibility = new HashMap<String, Double>();
		for (String type : categoryToSimplePairs.keySet()) {
			categoryToPosibility.put(type, 1.0f / (double) categoryToSimplePairs.get(type).size());
		}
		
		int count = 0;
		for (String category : categoryToSimplePairs.keySet()) {
			while (categoryToSamplePairs.get(category).size() < categoryToMinNum.get(category) && count < numSamples) {
				for (SimplePair pair : categoryToSimplePairs.get(category)) {
					if (rollTheDice(categoryToPosibility.get(category))) {
						sample.add(pair);
						count = sample.size();
						for (String chosenType : pair.getTypes()) {
							
							if (!isGroup) {
								if (categoryToSamplePairs.containsKey(chosenType))
									categoryToSamplePairs.get(chosenType).add(pair);
							} else {
								String group = SemanticGroups.getSemanticGroup(chosenType);
								if (categoryToSamplePairs.containsKey(group))
									categoryToSamplePairs.get(group).add(pair);
							}
						}
					}
				}
			}
		}
		
		count = sample.size();
		
		double uniformProbability = 1.0f / (double) numPairs;
		while (count < numSamples) {
			for (SimplePair pair : allPairs) {
				if (rollTheDice(uniformProbability) && count < numSamples) {
					sample.add(pair);
					count = sample.size();
				}
				if (count >= numSamples)
					break;
			}
		}		
		if (count != numSamples)
			System.err.println("Sth wrong, count = " + count + " while numSamples = " + numSamples);
		
		return sample;
	}

	private static boolean rollTheDice(double probability) {
		if (Math.random() < probability)
			return true;
		else 
			return false;
	}
	
	private static Map<String, Set<SimplePair>> buildGroupToSimplePairs(
			Map<String, Set<SimplePair>> typeToSimplePairs) {
		Map<String, Set<SimplePair>> groupToSimplePairs = new HashMap<String, Set<SimplePair>>();
		for (String type : typeToSimplePairs.keySet()) {
			String group = SemanticGroups.getSemanticGroup(type);
			if (!groupToSimplePairs.containsKey(group))
				groupToSimplePairs.put(group, new HashSet<SimplePair>());
			groupToSimplePairs.get(group).addAll(typeToSimplePairs.get(type));
		}
		
		return groupToSimplePairs;
	}

	/**
	 * @param frequentSimplePairs
	 * @return
	 */
	private static Map<String, Set<SimplePair>> buildTypeToSimplePairs(
			List<SimplePair> frequentSimplePairs) {
		Map<String, Set<SimplePair>> typeToSimplePairs = new HashMap<String, Set<SimplePair>>();
		for (SimplePair pair : frequentSimplePairs) {
			for (String type : pair.getTypes()) {
				if (!typeToSimplePairs.containsKey(type))
					typeToSimplePairs.put(type, new HashSet<SimplePair>());
				typeToSimplePairs.get(type).add(pair);
			}
		}
		return typeToSimplePairs;
	}

	private static void exportSimplePairsToJson(List<SimplePair> simplePairs,
			String simplePairsPath) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		
		Files.deleteIfExists(Paths.get(simplePairsPath));		
		
		int count = 1;
		for (SimplePair pair : simplePairs) {
			BufferedWriter writer = Files.newBufferedWriter(Paths.get(simplePairsPath), 
					StandardOpenOption.CREATE, 
					StandardOpenOption.APPEND);
			if (count > 1)						
				writer.newLine();
			++count;
			
			mapper.writeValue(writer, pair);
			writer.close();
		}				
	}

	private static List<SimplePair> importSimplePairsFromJson(
			String simplePairsPath) {
		List<SimplePair> simplePairs = new ArrayList<SimplePair>();
		ObjectMapper mapper = new ObjectMapper();
		
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(simplePairsPath))) {
			String line;
			while ((line = reader.readLine()) != null) {
				SimplePair pair = mapper.readValue(line.trim(), SimplePair.class);
				simplePairs.add(pair);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		System.out.println("# of all SimplePairs is " + simplePairs.size());
		return simplePairs;
	}

	/**
	 * @return
	 */
	private static List<SimplePair> buildSimplePairsFromConceptSentimentPairs() {
		long startTime = System.currentTimeMillis();
		
		final String VALID_CONCEPT_PATH = SOURCE_FOLDER + "valid_1.txt";
		final String INVALID_CONCEPT_PATH = SOURCE_FOLDER + "invalid_1.txt";
		
		Map<RawReview, List<ConceptSentimentPair>> reviewToValidConceptSentimentPairs = 
				PairExtractor.readRawReviewToConceptSentimentPairs(VALID_CONCEPT_PATH);
		Map<RawReview, List<ConceptSentimentPair>> reviewToInvalidConceptSentimentPairs = 
				PairExtractor.readRawReviewToConceptSentimentPairs(INVALID_CONCEPT_PATH);
		
		List<SimplePair> simplePairs = new ArrayList<SimplePair>();
		addSimplePairs(reviewToValidConceptSentimentPairs.values(), simplePairs);
		addSimplePairs(reviewToInvalidConceptSentimentPairs.values(), simplePairs);
		
		Utils.printRunningTime(startTime, "Finished getting " + simplePairs.size() + " pairs");
		return simplePairs;
	}
	
	private static void addSimplePairs(
			Collection<List<ConceptSentimentPair>> ConceptSentimentPairsCollection,
			List<SimplePair> simplePairs) {
		
		for (List<ConceptSentimentPair> conceptSentimentPairsList : ConceptSentimentPairsCollection) {
			for (ConceptSentimentPair conceptSentimentPair : conceptSentimentPairsList) {
				SimplePair simplePair = new SimplePair(
						conceptSentimentPair.getId(), 
						conceptSentimentPair.getName(), 
						conceptSentimentPair.getCount(), 
						conceptSentimentPair.getTypes());
				
				if (!simplePairs.contains(simplePair))
					simplePairs.add(simplePair);
				else {
					simplePairs.get(simplePairs.indexOf(simplePair)).increaseCount(conceptSentimentPair.getCount());
				}
			}
		}
	}

	private static class SimplePair{
		private String id;
		private String name;
		private int count;
		private List<String> types;		
				
		public SimplePair(){}
		
		public SimplePair(String id, String name, int count, List<String> types) {
			super();
			this.id = id;
			this.name = name;
			this.count = count;
			this.types = types;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((id == null) ? 0 : id.hashCode());
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
			SimplePair other = (SimplePair) obj;
			if (id == null) {
				if (other.id != null)
					return false;
			} else if (!id.equals(other.id))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "SimplePair [id=" + id + ", name=" + name + ", count="
					+ count + ", types=" + types + "]";
		}

		public void increaseCount() {
			++count;
		}
		public void increaseCount(int increment) {
			count += increment;
		}
		
		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
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
		public int getCount() {
			return count;
		}
		public void setCount(int count) {
			this.count = count;
		}		
	}
}
