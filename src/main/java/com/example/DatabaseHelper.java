package com.example;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;

import java.sql.*;

public class DatabaseHelper {
    private static Connection connection;

    public static void connect() throws Exception {
        Configurations configs = new Configurations();
        Configuration config = configs.properties("config.properties");
        String url = config.getString("db.url");
        String user = config.getString("db.user");
        String password = config.getString("db.password");
        connection = DriverManager.getConnection(url, user, password);
    }

    public static void insertOrUpdate(String code, String name, String text) throws SQLException {
        String query = """
            INSERT INTO cause_analysis (category_code, name, text)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE
            name = VALUES(name),
            text = VALUES(text)
        """;
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, code);
            stmt.setString(2, name);
            stmt.setString(3, text);
            stmt.executeUpdate();
        }
    }

    public static void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
