package com.fgwh.scripts;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BuildMetaKnowledge {
    public static void main(String[] args) throws Exception {
        ScriptSupport.loadDotenv();
        Path configPath = resolveConfigPath(args);
        validateRuntime(configPath);

        MetaConfig config = new YAMLMapper().readValue(configPath.toFile(), MetaConfig.class);
        try (Connection meta = ScriptSupport.metaConnection();
             Connection dw = ScriptSupport.dwConnection()) {
            ScriptSupport.ensureMetaTables(meta);
            if (config.tables != null && !config.tables.isEmpty()) {
                List<Map<String, Object>> columns = saveTables(meta, dw, config.tables);
                saveColumnsToQdrant(columns, ScriptSupport.columnCollection());
                saveValuesToElasticsearch(dw, config.tables);
            }
            if (config.metrics != null && !config.metrics.isEmpty()) {
                List<Map<String, Object>> metrics = saveMetrics(meta, config.metrics);
                saveMetricsToQdrant(metrics, ScriptSupport.metricCollection());
            }
        }
        System.out.println("Build metadata knowledge finished.");
    }

    private static Path resolveConfigPath(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--conf".equals(args[i]) || "--config".equals(args[i]) || "-c".equals(args[i])) {
                Path path = Path.of(args[i + 1]);
                return path.isAbsolute() ? path : Path.of("").toAbsolutePath().resolve(path).normalize();
            }
        }
        return ScriptSupport.DEFAULT_META_CONFIG;
    }

    private static void validateRuntime(Path configPath) {
        if (!configPath.toFile().exists()) {
            throw new IllegalStateException("Meta config file not found: " + configPath);
        }
        String apiKey = ScriptSupport.required("DASHSCOPE_API_KEY");
        if ("your-dashscope-api-key".equals(apiKey)) {
            throw new IllegalStateException("Please set DASHSCOPE_API_KEY before building metadata.");
        }
    }

    private static List<Map<String, Object>> saveTables(Connection meta, Connection dw, List<MetaConfig.TableConfig> tables) throws Exception {
        List<Map<String, Object>> columnInfos = new ArrayList<>();
        String tableSql = """
                INSERT INTO table_info(id, name, role, description)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE name=VALUES(name), role=VALUES(role), description=VALUES(description)
                """;
        String columnSql = """
                INSERT INTO column_info(id, name, type, role, examples, description, alias, table_id)
                VALUES (?, ?, ?, ?, CAST(? AS JSON), ?, CAST(? AS JSON), ?)
                ON DUPLICATE KEY UPDATE
                  name=VALUES(name), type=VALUES(type), role=VALUES(role), examples=VALUES(examples),
                  description=VALUES(description), alias=VALUES(alias), table_id=VALUES(table_id)
                """;

        try (PreparedStatement tableStmt = meta.prepareStatement(tableSql);
             PreparedStatement columnStmt = meta.prepareStatement(columnSql)) {
            for (MetaConfig.TableConfig table : tables) {
                tableStmt.setString(1, table.name);
                tableStmt.setString(2, table.name);
                tableStmt.setString(3, table.role);
                tableStmt.setString(4, table.description);
                tableStmt.executeUpdate();

                Map<String, String> columnTypes = ScriptSupport.columnTypes(dw, table.name);
                for (MetaConfig.ColumnConfig column : table.columns) {
                    List<Object> examples = ScriptSupport.columnValues(dw, table.name, column.name, 10);
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("id", table.name + "." + column.name);
                    info.put("name", column.name);
                    info.put("type", columnTypes.getOrDefault(column.name, ""));
                    info.put("role", column.role);
                    info.put("examples", examples);
                    info.put("description", column.description);
                    info.put("alias", column.alias == null ? List.of() : column.alias);
                    info.put("table_id", table.name);
                    columnInfos.add(info);

                    columnStmt.setString(1, (String) info.get("id"));
                    columnStmt.setString(2, column.name);
                    columnStmt.setString(3, (String) info.get("type"));
                    columnStmt.setString(4, column.role);
                    columnStmt.setString(5, ScriptSupport.JSON.writeValueAsString(examples));
                    columnStmt.setString(6, column.description);
                    columnStmt.setString(7, ScriptSupport.JSON.writeValueAsString(info.get("alias")));
                    columnStmt.setString(8, table.name);
                    columnStmt.executeUpdate();
                }
            }
        }
        System.out.printf("Saved %d tables and %d columns to MySQL meta.%n", tables.size(), columnInfos.size());
        return columnInfos;
    }

    private static List<Map<String, Object>> saveMetrics(Connection meta, List<MetaConfig.MetricConfig> metrics) throws Exception {
        List<Map<String, Object>> metricInfos = new ArrayList<>();
        String metricSql = """
                INSERT INTO metric_info(id, name, description, relevant_columns, alias)
                VALUES (?, ?, ?, CAST(? AS JSON), CAST(? AS JSON))
                ON DUPLICATE KEY UPDATE
                  name=VALUES(name), description=VALUES(description),
                  relevant_columns=VALUES(relevant_columns), alias=VALUES(alias)
                """;
        String relationSql = """
                INSERT INTO column_metric(column_id, metric_id)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE column_id=VALUES(column_id), metric_id=VALUES(metric_id)
                """;

        try (PreparedStatement metricStmt = meta.prepareStatement(metricSql);
             PreparedStatement relationStmt = meta.prepareStatement(relationSql)) {
            for (MetaConfig.MetricConfig metric : metrics) {
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("id", metric.name);
                info.put("name", metric.name);
                info.put("description", metric.description);
                info.put("relevant_columns", metric.relevant_columns == null ? List.of() : metric.relevant_columns);
                info.put("alias", metric.alias == null ? List.of() : metric.alias);
                metricInfos.add(info);

                metricStmt.setString(1, metric.name);
                metricStmt.setString(2, metric.name);
                metricStmt.setString(3, metric.description);
                metricStmt.setString(4, ScriptSupport.JSON.writeValueAsString(info.get("relevant_columns")));
                metricStmt.setString(5, ScriptSupport.JSON.writeValueAsString(info.get("alias")));
                metricStmt.executeUpdate();

                for (String column : metric.relevant_columns == null ? List.<String>of() : metric.relevant_columns) {
                    relationStmt.setString(1, column);
                    relationStmt.setString(2, metric.name);
                    relationStmt.executeUpdate();
                }
            }
        }
        System.out.printf("Saved %d metrics to MySQL meta.%n", metricInfos.size());
        return metricInfos;
    }

    private static void saveColumnsToQdrant(List<Map<String, Object>> columns, String collection) throws Exception {
        ensureQdrantCollection(collection);
        List<PointDraft> points = new ArrayList<>();
        for (Map<String, Object> column : columns) {
            addEmbeddingPoints(points, column, (String) column.get("name"), (String) column.get("description"));
            for (String alias : listOfStrings(column.get("alias"))) {
                addEmbeddingPoints(points, column, alias);
            }
        }
        upsertQdrant(collection, points);
        System.out.printf("Upserted %d column vectors to Qdrant.%n", points.size());
    }

    private static void saveMetricsToQdrant(List<Map<String, Object>> metrics, String collection) throws Exception {
        ensureQdrantCollection(collection);
        List<PointDraft> points = new ArrayList<>();
        for (Map<String, Object> metric : metrics) {
            addEmbeddingPoints(points, metric, (String) metric.get("name"), (String) metric.get("description"));
            for (String alias : listOfStrings(metric.get("alias"))) {
                addEmbeddingPoints(points, metric, alias);
            }
        }
        upsertQdrant(collection, points);
        System.out.printf("Upserted %d metric vectors to Qdrant.%n", points.size());
    }

    private static void addEmbeddingPoints(List<PointDraft> points, Map<String, Object> payload, String... texts) {
        for (String text : texts) {
            if (text != null && !text.isBlank()) {
                points.add(new PointDraft(UUID.randomUUID().toString(), text, payload));
            }
        }
    }

    private static void ensureQdrantCollection(String collection) throws IOException, InterruptedException {
        int dimensions = Integer.parseInt(ScriptSupport.prop("EMBEDDING_DIMENSIONS", "1024"));
        String base = ScriptSupport.qdrantBaseUrl();
        HttpResponse<String> exists = ScriptSupport.request("GET", base + "/collections/" + collection, null);
        if (exists.statusCode() == 200) {
            return;
        }
        Map<String, Object> body = Map.of("vectors", Map.of("size", dimensions, "distance", "Cosine"));
        ScriptSupport.requestMap("PUT", base + "/collections/" + collection, body);
    }

    private static void upsertQdrant(String collection, List<PointDraft> drafts) throws Exception {
        if (drafts.isEmpty()) {
            return;
        }
        List<String> texts = drafts.stream().map(PointDraft::text).toList();
        List<List<Double>> embeddings = embed(texts);
        List<Map<String, Object>> points = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            points.add(Map.of(
                    "id", drafts.get(i).id(),
                    "vector", embeddings.get(i),
                    "payload", drafts.get(i).payload()
            ));
        }
        ScriptSupport.requestMap("PUT", ScriptSupport.qdrantBaseUrl() + "/collections/" + collection + "/points?wait=true", Map.of("points", points));
    }

    private static List<List<Double>> embed(List<String> texts) throws Exception {
        String apiKey = ScriptSupport.required("DASHSCOPE_API_KEY");
        String baseUrl = ScriptSupport.prop("DASHSCOPE_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1");
        String model = ScriptSupport.prop("DASHSCOPE_EMBEDDING_MODEL", "text-embedding-v4");
        int dimensions = Integer.parseInt(ScriptSupport.prop("EMBEDDING_DIMENSIONS", "1024"));
        int batchSize = Integer.parseInt(ScriptSupport.prop("EMBEDDING_BATCH_SIZE", "20"));

        List<List<Double>> embeddings = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += batchSize) {
            List<String> batch = texts.subList(i, Math.min(i + batchSize, texts.size()));
            Map<String, Object> body = Map.of("model", model, "input", batch, "dimensions", dimensions);
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(baseUrl + "/embeddings"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(ScriptSupport.JSON.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = ScriptSupport.HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Embedding request failed: HTTP " + response.statusCode() + " " + response.body());
            }
            Map<String, Object> responseMap = ScriptSupport.JSON.readValue(response.body(), new TypeReference<>() {
            });
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) responseMap.getOrDefault("data", List.of());
            for (Map<String, Object> item : data) {
                @SuppressWarnings("unchecked")
                List<Number> vector = (List<Number>) item.get("embedding");
                embeddings.add(vector.stream().map(Number::doubleValue).toList());
            }
        }
        return embeddings;
    }

    private static void saveValuesToElasticsearch(Connection dw, List<MetaConfig.TableConfig> tables) throws Exception {
        ensureValueIndex();
        int count = 0;
        for (MetaConfig.TableConfig table : tables) {
            for (MetaConfig.ColumnConfig column : table.columns) {
                if (!column.sync) {
                    continue;
                }
                for (Object value : ScriptSupport.columnValues(dw, table.name, column.name, 1_000_000)) {
                    String id = table.name + "." + column.name + "." + String.valueOf(value);
                    Map<String, Object> doc = Map.of("id", id, "value", value, "column_id", table.name + "." + column.name);
                    ScriptSupport.requestMap("PUT", ScriptSupport.esBaseUrl() + "/" + ScriptSupport.valueIndex() + "/_doc/" + ScriptSupport.url(id), doc);
                    count++;
                }
            }
        }
        System.out.printf("Indexed %d values to Elasticsearch.%n", count);
    }

    private static void ensureValueIndex() throws IOException, InterruptedException {
        String uri = ScriptSupport.esBaseUrl() + "/" + ScriptSupport.valueIndex();
        HttpResponse<String> exists = ScriptSupport.request("HEAD", uri, null);
        if (exists.statusCode() == 200) {
            return;
        }
        Map<String, Object> body = Map.of("mappings", Map.of("properties", Map.of(
                "id", Map.of("type", "keyword"),
                "value", Map.of("type", "text"),
                "column_id", Map.of("type", "keyword")
        )));
        ScriptSupport.requestMap("PUT", uri, body);
    }

    private static List<String> listOfStrings(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    private record PointDraft(String id, String text, Map<String, Object> payload) {
    }
}
