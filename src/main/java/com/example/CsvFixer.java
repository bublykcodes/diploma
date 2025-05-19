package com.example;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CsvFixer {
    public static void main(String[] args) {
        // Указываем путь к файлам
        String inputFile = "E:\\KARAGAEB\\datasetfordiploma\\regions.csv";
        String outputFile = "E:\\KARAGAEB\\datasetfordiploma\\cleaned_regions.csv";

        try {
            // Чтение и обработка CSV
            List<String[]> processedData = processCsv(inputFile);

            // Запись результата в новый CSV
            writeCsv(processedData, outputFile);

            System.out.println("Файл успешно обработан и сохранён как " + outputFile);
        } catch (IOException e) {
            System.err.println("Ошибка при обработке файла: " + e.getMessage());
        }
    }

    private static List<String[]> processCsv(String inputFile) throws IOException {
        List<String[]> result = new ArrayList<>();
        result.add(new String[]{"category_code", "name", "text"}); // Заголовок с новым столбцом text

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new FileInputStream(inputFile), StandardCharsets.UTF_8))) {
            // Пропускаем заголовок, если он есть
            reader.skip(1);

            // Регулярное выражение для разделения строки типа "2036 Захоплення заручників"
            Pattern pattern = Pattern.compile("^(\\d+)\\s+(.+)$");

            String[] line;
            while ((line = reader.readNext()) != null) {
                String code;
                String name;

                if (line.length == 1) {
                    // Данные в одном столбце
                    Matcher matcher = pattern.matcher(line[0].trim());
                    if (matcher.matches()) {
                        code = matcher.group(1).trim();
                        name = matcher.group(2).trim();
                    } else {
                        System.err.println("Некорректная строка: " + line[0]);
                        continue;
                    }
                } else if (line.length >= 2) {
                    // Данные уже разделены на два столбца
                    code = line[0].trim();
                    name = line[1].trim();
                } else {
                    System.err.println("Пропущена строка с некорректным форматом");
                    continue;
                }

                // Добавляем обработанную строку с пустым столбцом text
                result.add(new String[]{code, name, ""});
            }
        } catch (CsvValidationException e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    private static void writeCsv(List<String[]> data, String outputFile) throws IOException {
        try (CSVWriter writer = new CSVWriter(
                new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8),
                ',', CSVWriter.DEFAULT_QUOTE_CHARACTER, CSVWriter.DEFAULT_ESCAPE_CHARACTER, CSVWriter.DEFAULT_LINE_END)) {
            writer.writeAll(data);
        }
    }
}