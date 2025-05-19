package com.example;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class CourtDecisionSummarizer {
	private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
	private static String apiKey;
	private static Map<String, String> categoryMap; // Маппинг category_code на name
	private static final int MAX_RETRIES = 3; // Максимум повторных попыток
	private static final long BASE_DELAY_MS = 2000; // Базовая задержка 2 секунды

	public static void main(String[] args) {
		// Загрузка API-ключа из config.properties
		try {
			Configurations configs = new Configurations();
			Configuration config = configs.properties("config.properties");
			apiKey = config.getString("api_token");
		} catch (ConfigurationException e) {
			System.err.println("Ошибка чтения config.properties: " + e.getMessage());
			return;
		}

		String datasetDir = args.length > 0 ? args[0] : "e:/KARAGAEB/datasetfordiploma";
		categoryMap = new HashMap<>();

		// Загрузка cleaned_cause_categories.csv
		Path categoriesFile = Paths.get(datasetDir, "cleaned_cause_categories.csv");
		Path outputFile = Paths.get(datasetDir, "analyzed_cause_categories.csv");
		if (!Files.exists(categoriesFile)) {
			System.err.println("cleaned_cause_categories.csv не найден в " + datasetDir);
			return;
		}

		// Загрузка категорий в categoryMap и подготовка данных
		List<String[]> records = new ArrayList<>();
		try (Reader reader = Files.newBufferedReader(categoriesFile);
			 CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
			for (CSVRecord record : csvParser) {
				String code = record.get("category_code");
				String name = record.get("name");
				String text = record.isMapped("text") ? record.get("text") : "";
				if (code != null && name != null) {
					categoryMap.put(code.trim(), name.trim());
					records.add(new String[]{code, name, text});
				}
			}
			System.out.println("Загружено " + categoryMap.size() + " категорий из cleaned_cause_categories.csv");
		} catch (IOException e) {
			System.err.println("Ошибка чтения cleaned_cause_categories.csv: " + e.getMessage());
			return;
		}

		// Обработка категорий и заполнение столбца text (ограничение на 10 записей для теста)
		HttpClient client = HttpClient.newHttpClient();
		List<String[]> updatedRecords = new ArrayList<>();
		updatedRecords.add(new String[]{"category_code", "name", "text"}); // Заголовок

		int recordCount = 0;
		int maxRecords = 10; // Ограничение для теста
		for (String[] record : records) {
			if (recordCount >= maxRecords) {
				System.out.println("Достигнуто ограничение в " + maxRecords + " записей для теста.");
				break;
			}
			recordCount++;

			String code = record[0];
			String name = record[1];
			String text = record[2];
			if (name == null || name.isEmpty()) {
				System.err.println("Пустое имя для записи с кодом " + code);
				updatedRecords.add(record); // Сохраняем без изменений
				continue;
			}
			// Ограничение длины текста для API
			if (name.length() > 1000) {
				name = name.substring(0, 1000);
			}
			try {
				// Запрос на оценку и аналитику
				String analysis = analyzeWithGroq(client, name);
				if (analysis == null || analysis.trim().isEmpty()) {
					System.err.println("Пустой анализ для записи с кодом " + code);
					updatedRecords.add(record); // Сохраняем без изменений
					continue;
				}
				updatedRecords.add(new String[]{code, name, analysis});
				StringBuilder output = new StringBuilder();
				output.append("Анализ для записи с кодом ").append(code)
						.append(": ").append(analysis)
						.append(" [Категория: ").append(name)
						.append(", Код: ").append(code).append("]");
				System.out.println(output);
			} catch (Exception e) {
				System.err.println("Ошибка при анализе для записи с кодом " + code + ": " + e.getMessage());
				updatedRecords.add(record); // Сохраняем без изменений
			}
		}

		// Запись результатов в analyzed_cause_categories.csv
		try (Writer writer = Files.newBufferedWriter(outputFile);
			 CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
			csvPrinter.printRecords(updatedRecords);
			System.out.println("Результаты сохранены в " + outputFile);
		} catch (IOException e) {
			System.err.println("Ошибка записи в " + outputFile + ": " + e.getMessage());
		}
	}

	private static String analyzeWithGroq(HttpClient client, String text) throws Exception {
		// Промпт для оценки и аналитики
		String prompt = "Оцени серьёзность и возможные последствия следующего события, а также предоставь краткий анализ: " + text;
		String requestBody = """
                {
                    "model": "meta-llama/llama-4-scout-17b-16e-instruct",
                    "messages": [
                        {
                            "role": "user",
                            "content": "%s"
                        }
                    ],
                    "max_tokens": 200
                }
                """.formatted(prompt.replace("\"", "\\\""));

		int retryCount = 0;
		while (retryCount < MAX_RETRIES) {
			try {
				// Базовая задержка для соблюдения лимита
				Thread.sleep(BASE_DELAY_MS);

				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(GROQ_API_URL))
						.header("Authorization", "Bearer " + apiKey)
						.header("Content-Type", "application/json")
						.POST(HttpRequest.BodyPublishers.ofString(requestBody))
						.build();

				HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() != 200) {
					String body = response.body();
					System.err.println("Ответ API Groq: " + body); // Отладочный вывод
					if (body.contains("rate_limit_exceeded")) {
						// Извлечение времени ожидания
						long waitTime = extractWaitTime(body);
						System.err.println("Лимит запросов, ожидание " + waitTime + " мс");
						Thread.sleep(waitTime);
						retryCount++;
						continue;
					}
					throw new RuntimeException("Ошибка API Groq: " + body);
				}

				// Парсинг JSON-ответа
				String body = response.body();
				System.out.println("Полный ответ Groq: " + body); // Отладочный вывод
				int start = body.indexOf("\"content\":\"") + 10;
				int end = body.indexOf("\"", start);
				if (start == -1 || end == -1) {
					throw new RuntimeException("Не удалось разобрать ответ API Groq: " + body);
				}
				String content = body.substring(start, end);
				return content.isEmpty() ? null : content;

			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException("Прервано ожидание: " + e.getMessage());
			}
		}
		throw new RuntimeException("Превышено количество попыток для запроса");
	}

	private static long extractWaitTime(String errorBody) {
		try {
			int start = errorBody.indexOf("Please try again in ") + 20;
			int end = errorBody.indexOf("ms", start);
			String waitTimeStr = errorBody.substring(start, end);
			return Long.parseLong(waitTimeStr);
		} catch (Exception e) {
			return BASE_DELAY_MS; // Возврат базовой задержки
		}
	}
}