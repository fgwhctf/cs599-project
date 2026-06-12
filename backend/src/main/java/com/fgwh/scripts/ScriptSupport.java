package com.fgwh.scripts;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ScriptSupport {
    static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();
    static final Path WORKSPACE_ROOT = PROJECT_ROOT.getParent();
    static final Path DEFAULT_ENV = PROJECT_ROOT.resolve(".env");
    static final Path DEFAULT_META_CONFIG = resolveExisting(
            PROJECT_ROOT.resolve("src/main/resources/meta_config.yaml"),
            PROJECT_ROOT.resolve("backend/src/main/resources/meta_config.yaml"),
            PROJECT_ROOT.resolve("conf/meta_config.yaml"),
            WORKSPACE_ROOT.resolve("conf/meta_config.yaml")
    );
    static final Path DEFAULT_APP_CONFIG = resolveExisting(
            PROJECT_ROOT.resolve("src/main/resources/application.yml"),
            PROJECT_ROOT.resolve("backend/src/main/resources/application.yml")
    );
    static final ObjectMapper JSON = new ObjectMapper();
    private static final YAMLMapper YAML = new YAMLMapper();
    static final HttpClient HTTP = HttpClient.newHttpClient();
    private static Map<String, Object> appConfig;

    private ScriptSupport() {
    }

    private static Path resolveExisting(Path... candidates) {
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return candidates[0];
    }

    static void loadDotenv() throws IOException {
        loadDotenv(DEFAULT_ENV);
        loadDotenv(PROJECT_ROOT.resolve("backend/.env"));
        loadDotenv(WORKSPACE_ROOT.resolve(".env"));
        loadDotenv(WORKSPACE_ROOT.resolve("backend/.env"));
    }

    private static void loadDotenv(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                continue;
            }
            String[] parts = line.split("=", 2);
            String key = parts[0].trim();
            String value = stripQuotes(parts[1].trim());
            if (System.getProperty(key) == null && System.getenv(key) == null) {
                System.setProperty(key, value);
            }
        }
    }

    private static String stripQuotes(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    static String prop(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            value = System.getenv(key);
        }
        return value == null || value.isBlank() ? defaultValue : value;
    }

    static String required(String key) {
        String value = prop(key, null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required config: " + key);
        }
        return value;
    }

    private static Map<String, Object> appConfig() {
        if (appConfig != null) {
            return appConfig;
        }
        if (!Files.exists(DEFAULT_APP_CONFIG)) {
            throw new IllegalStateException("Application config file not found: " + DEFAULT_APP_CONFIG);
        }
        try {
            appConfig = YAML.readValue(DEFAULT_APP_CONFIG.toFile(), new TypeReference<>() {
            });
            return appConfig;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read application config: " + DEFAULT_APP_CONFIG, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object configValue(String path) {
        Object current = appConfig();
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static String requiredConfig(String path) {
        Object value = configValue(path);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalStateException("Missing required application config: " + path);
        }
        return resolvePlaceholders(String.valueOf(value));
    }

    private static String optionalConfig(String path, String defaultValue) {
        Object value = configValue(path);
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return resolvePlaceholders(String.valueOf(value));
    }

    private static String resolvePlaceholders(String value) {
        String resolved = value;
        int start = resolved.indexOf("${");
        while (start >= 0) {
            int end = resolved.indexOf('}', start + 2);
            if (end < 0) {
                break;
            }
            String expression = resolved.substring(start + 2, end);
            String[] parts = expression.split(":", 2);
            String replacement = prop(parts[0], parts.length > 1 ? parts[1] : null);
            if (replacement == null) {
                throw new IllegalStateException("Missing required config: " + parts[0]);
            }
            resolved = resolved.substring(0, start) + replacement + resolved.substring(end + 1);
            start = resolved.indexOf("${", start + replacement.length());
        }
        return resolved;
    }

    private static Connection datasourceConnection(String name) throws SQLException {
        String prefix = "data-agent.datasource." + name;
        String driverClassName = optionalConfig(prefix + ".driver-class-name", "");
        if (!driverClassName.isBlank()) {
            try {
                Class.forName(driverClassName);
            } catch (ClassNotFoundException ex) {
                throw new IllegalStateException("Datasource driver not found: " + driverClassName, ex);
            }
        }
        return DriverManager.getConnection(
                requiredConfig(prefix + ".jdbc-url"),
                requiredConfig(prefix + ".username"),
                requiredConfig(prefix + ".password")
        );
    }

    static Connection metaConnection() throws SQLException {
        return datasourceConnection("meta");
    }

    static Connection dwConnection() throws SQLException {
        return datasourceConnection("dw");
    }

    static String qdrantBaseUrl() {
        return requiredConfig("data-agent.qdrant.base-url");
    }

    static String columnCollection() {
        return requiredConfig("data-agent.qdrant.column-collection");
    }

    static String metricCollection() {
        return requiredConfig("data-agent.qdrant.metric-collection");
    }

    static String esBaseUrl() {
        return requiredConfig("data-agent.elasticsearch.base-url");
    }

    static String valueIndex() {
        return requiredConfig("data-agent.elasticsearch.value-index");
    }

    static String url(String raw) {
        return URLEncoder.encode(raw, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static void ensureMetaTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS table_info (
                      id VARCHAR(64) PRIMARY KEY,
                      name VARCHAR(128),
                      role VARCHAR(32),
                      description TEXT
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS column_info (
                      id VARCHAR(64) PRIMARY KEY,
                      name VARCHAR(128),
                      type VARCHAR(64),
                      role VARCHAR(32),
                      examples JSON,
                      description TEXT,
                      alias JSON,
                      table_id VARCHAR(64)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS metric_info (
                      id VARCHAR(64) PRIMARY KEY,
                      name VARCHAR(128),
                      description TEXT,
                      relevant_columns JSON,
                      alias JSON
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS column_metric (
                      column_id VARCHAR(64),
                      metric_id VARCHAR(64),
                      PRIMARY KEY (column_id, metric_id)
                    )
                    """);
        }
    }

    static Map<String, String> columnTypes(Connection connection, String tableName) throws SQLException {
        Map<String, String> result = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM `%s` LIMIT 0".formatted(tableName))) {
            var metaData = rs.getMetaData();
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                result.put(metaData.getColumnLabel(i), metaData.getColumnTypeName(i));
            }
        }
        return result;
    }

    static List<Object> columnValues(Connection connection, String tableName, String columnName, int limit) throws SQLException {
        String sql = "SELECT DISTINCT `%s` FROM `%s` WHERE `%s` IS NOT NULL LIMIT ?".formatted(columnName, tableName, columnName);
        List<Object> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getObject(1));
                }
            }
        }
        return result;
    }

    static HttpResponse<String> request(String method, String uri, Object body) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri));
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8));
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    static Map<String, Object> requestMap(String method, String uri, Object body) throws IOException, InterruptedException {
        HttpResponse<String> response = request(method, uri, body);
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("%s %s failed: HTTP %d %s".formatted(method, uri, response.statusCode(), response.body()));
        }
        if (response.body() == null || response.body().isBlank()) {
            return Map.of();
        }
        return JSON.readValue(response.body(), new TypeReference<>() {
        });
    }
}
