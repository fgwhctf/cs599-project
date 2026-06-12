package com.fgwh.scripts;

import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Scanner;
import java.util.Set;

public class ClearMetaKnowledge {
    private static final String[] META_TABLES = {"column_metric", "metric_info", "column_info", "table_info"};

    public static void main(String[] args) throws Exception {
        ScriptSupport.loadDotenv();
        Set<String> targets = targets(args);
        if (!Arrays.asList(args).contains("-y") && !Arrays.asList(args).contains("--yes")) {
            confirm(targets);
        }
        if (targets.contains("mysql")) {
            clearMysql();
        }
        if (targets.contains("qdrant")) {
            clearQdrant();
        }
        if (targets.contains("es")) {
            clearElasticsearch();
        }
        System.out.println("Done.");
    }

    private static Set<String> targets(String[] args) {
        Set<String> selected = new LinkedHashSet<>();
        for (String arg : args) {
            switch (arg) {
                case "--mysql-only" -> selected.add("mysql");
                case "--qdrant-only" -> selected.add("qdrant");
                case "--es-only" -> selected.add("es");
                default -> {
                }
            }
        }
        if (selected.isEmpty()) {
            selected.add("mysql");
            selected.add("qdrant");
            selected.add("es");
        }
        return selected;
    }

    private static void confirm(Set<String> targets) {
        System.out.println("This will clear metadata targets: " + String.join(", ", targets));
        System.out.print("Type CLEAR to continue: ");
        String answer = new Scanner(System.in).nextLine().trim();
        if (!"CLEAR".equals(answer)) {
            throw new IllegalStateException("Aborted.");
        }
    }

    private static void clearMysql() throws Exception {
        System.out.println("Clearing MySQL meta tables...");
        try (Connection connection = ScriptSupport.metaConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (String table : META_TABLES) {
                statement.execute("TRUNCATE TABLE `" + table + "`");
                System.out.println("  truncated " + table);
            }
            statement.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    private static void clearQdrant() throws Exception {
        System.out.println("Deleting Qdrant collections...");
        for (String collection : new String[]{ScriptSupport.columnCollection(), ScriptSupport.metricCollection()}) {
            HttpResponse<String> response = ScriptSupport.request("DELETE", ScriptSupport.qdrantBaseUrl() + "/collections/" + collection, null);
            if (response.statusCode() == 200) {
                System.out.println("  deleted " + collection);
            } else if (response.statusCode() == 404) {
                System.out.println("  skipped missing " + collection);
            } else {
                throw new IllegalStateException("Delete Qdrant collection failed: HTTP " + response.statusCode() + " " + response.body());
            }
        }
    }

    private static void clearElasticsearch() throws Exception {
        System.out.println("Deleting Elasticsearch indexes...");
        String index = ScriptSupport.valueIndex();
        HttpResponse<String> response = ScriptSupport.request("DELETE", ScriptSupport.esBaseUrl() + "/" + index, null);
        if (response.statusCode() == 200) {
            System.out.println("  deleted " + index);
        } else if (response.statusCode() == 404) {
            System.out.println("  skipped missing " + index);
        } else {
            throw new IllegalStateException("Delete Elasticsearch index failed: HTTP " + response.statusCode() + " " + response.body());
        }
    }
}
