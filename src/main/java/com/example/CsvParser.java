package com.example;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class CsvParser {

    private final String cleanedCsvPath;
    private final int maxLines;

    public CsvParser(String cleanedCsvPath, int maxLines) {
        this.cleanedCsvPath = cleanedCsvPath;
        this.maxLines = maxLines;
    }

    public void runSummarizer() {
        System.out.println("Запуск Summarizer для " + maxLines + " рядків з: " + cleanedCsvPath);

        List<String[]> records = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(Paths.get(cleanedCsvPath));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

            int count = 0;
            for (CSVRecord record : csvParser) {
                if (count >= maxLines) break;

                String code = record.get("category_code");
                String name = record.get("name");

                if (code != null && name != null && !name.isEmpty()) {
                    records.add(new String[]{code, name, ""});
                    count++;
                }
            }

        } catch (IOException e) {
            System.err.println("Помилка при читанні CSV: " + e.getMessage());
            return;
        }

        // Тимчасовий файл з обмеженою кількістю рядків
        String tempFilePath = cleanedCsvPath.replace(".csv", "_limited.csv");
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(tempFilePath));
             PrintWriter pw = new PrintWriter(writer)) {

            pw.println("category_code,name,text");
            for (String[] row : records) {
                pw.println(String.join(",", row));
            }

        } catch (IOException e) {
            System.err.println("Помилка при створенні тимчасового CSV: " + e.getMessage());
            return;
        }

        // Передаємо шлях до тимчасового файлу у CourtDecisionSummarizer
        System.out.println("Передача файлу " + tempFilePath + " до CourtDecisionSummarizer...");
        CourtDecisionSummarizer.main(new String[]{new File(tempFilePath).getParent()});
    }
}