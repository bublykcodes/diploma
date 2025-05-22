package com.example;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        String datasetDir = "E:\\KARAGAEB\\datasetfordiploma";
        String rawCsvPath = datasetDir + "\\cause_categories.csv";
        String cleanedCsvPath = datasetDir + "\\cleaned_cause_categories.csv";

        System.out.println("=== OSINT CSV МЕНЮ ===");
        System.out.println("1. Очистити CSV (CsvFixer)");
        System.out.println("2. Проаналізувати N рядків з cleaned CSV через CourtDecisionSummarizer");
        System.out.print("Оберіть опцію: ");

        int option = scanner.nextInt();
        scanner.nextLine(); // зчитати enter

        switch (option) {
            case 1 -> {
                System.out.println("Виконується очищення CSV...");
                CsvFixer.main(new String[]{});
            }
            case 2 -> {
                System.out.print("Скільки рядків обробити?: ");
                int n = scanner.nextInt();
                scanner.nextLine();
                CsvParser parser = new CsvParser(cleanedCsvPath, n);
                parser.runSummarizer();
            }
            default -> System.out.println("Невірна опція.");
        }
    }
}
