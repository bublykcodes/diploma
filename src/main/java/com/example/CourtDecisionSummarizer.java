package com.example;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CourtDecisionSummarizer {
	private static final String GROQ_API_URL = "https://api.groq.com/v1/chat/completions";
	private static String apiKey;
	private static Map<String, String> categoryMap; // Maps category_code to name

	public static void main(String[] args) {
		// Load API key from config.properties
		try {
			Configurations configs = new Configurations();
			Configuration config = configs.properties("config.properties");
			apiKey = config.getString("api_token");
		} catch (ConfigurationException e) {
			System.err.println("Error reading config.properties: " + e.getMessage());
			return;
		}

		String datasetDir = args.length > 0 ? args[0] : "e:/KARAGAEB/datasetfordiploma";
		categoryMap = new HashMap<>();

		// Load cause_categories.csv
		Path categoriesFile = Paths.get(datasetDir, "cause_categories.csv");
		if (Files.exists(categoriesFile)) {
			try (Reader reader = Files.newBufferedReader(categoriesFile);
				 CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
				for (CSVRecord record : csvParser) {
					String code = record.get("category_code");
					String name = record.get("name");
					if (code != null && name != null) {
						categoryMap.put(code.trim(), name.trim());
					}
				}
				System.out.println("Loaded " + categoryMap.size() + " categories from cause_categories.csv");
			} catch (IOException e) {
				System.err.println("Error reading cause_categories.csv: " + e.getMessage());
			}
		} else {
			System.err.println("cause_categories.csv not found in " + datasetDir);
		}

		try {
			List<Path> files = Files.list(Paths.get(datasetDir))
					.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(".csv"))
					.filter(path -> !path.getFileName().toString().equals("cause_categories.csv"))
					.limit(3)
					.collect(Collectors.toList());

			if (files.isEmpty()) {
				System.out.println("No court decision CSV files found in " + datasetDir);
				return;
			}

			HttpClient client = HttpClient.newHttpClient();

			for (Path file : files) {
				try (Reader reader = Files.newBufferedReader(file);
					 CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
					for (CSVRecord record : csvParser) {
						String text = record.get("text");
						if (text == null || text.isEmpty()) {
							continue;
						}
						// Truncate text if necessary
						if (text.length() > 1000) {
							text = text.substring(0, 1000);
						}
						String summary = summarizeWithGroq(client, text);
						StringBuilder output = new StringBuilder();
						output.append("Summary for record ").append(record.getRecordNumber())
								.append(" in ").append(file.getFileName()).append(": ").append(summary);

						// Check for category_code and append category name if available
						try {
							String categoryCode = record.get("category_code");
							if (categoryCode != null && !categoryCode.isEmpty()) {
								String categoryName = categoryMap.get(categoryCode.trim());
								if (categoryName != null) {
									output.append(" [Category: ").append(categoryName).append("]");
								} else {
									output.append(" [Category Code: ").append(categoryCode).append(", Name not found]");
								}
							}
						} catch (IllegalArgumentException e) {
							// category_code column not present in this CSV, skip
						}

						System.out.println(output);
					}
				} catch (IOException e) {
					System.err.println("Error reading CSV file " + file + ": " + e.getMessage());
				} catch (Exception e) {
					System.err.println("Error during summarization for " + file + ": " + e.getMessage());
				}
			}
		} catch (IOException e) {
			System.err.println("Error listing files in " + datasetDir + ": " + e.getMessage());
		}
	}

	private static String summarizeWithGroq(HttpClient client, String text) throws Exception {
		String prompt = "Summarize the following text in a concise manner:\n" + text;
		String requestBody = """
                {
                    "model": "grok-3",
                    "messages": [
                        {
                            "role": "user",
                            "content": "%s"
                        }
                    ],
                    "max_tokens": 200
                }
                """.formatted(prompt.replace("\"", "\\\""));

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(GROQ_API_URL))
				.header("Authorization", "Bearer " + apiKey)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(requestBody))
				.build();

		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new RuntimeException("Groq API error: " + response.body());
		}

		// Parse JSON response
		String body = response.body();
		int start = body.indexOf("\"content\":\"") + 10;
		int end = body.indexOf("\"", start);
		if (start == -1 || end == -1) {
			throw new RuntimeException("Failed to parse Groq API response: " + body);
		}
		return body.substring(start, end);
	}
}