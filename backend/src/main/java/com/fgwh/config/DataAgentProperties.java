package com.fgwh.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "data-agent")
public class DataAgentProperties {

    private Datasource datasource = new Datasource();
    private Qdrant qdrant = new Qdrant();
    private Elasticsearch elasticsearch = new Elasticsearch();

    public Datasource getDatasource() {
        return datasource;
    }

    public void setDatasource(Datasource datasource) {
        this.datasource = datasource;
    }

    public Qdrant getQdrant() {
        return qdrant;
    }

    public void setQdrant(Qdrant qdrant) {
        this.qdrant = qdrant;
    }

    public Elasticsearch getElasticsearch() {
        return elasticsearch;
    }

    public void setElasticsearch(Elasticsearch elasticsearch) {
        this.elasticsearch = elasticsearch;
    }

    public static class Datasource {
        private Database meta = new Database();
        private Database dw = new Database();

        public Database getMeta() {
            return meta;
        }

        public void setMeta(Database meta) {
            this.meta = meta;
        }

        public Database getDw() {
            return dw;
        }

        public void setDw(Database dw) {
            this.dw = dw;
        }
    }

    public static class Database {
        private String jdbcUrl;
        private String username;
        private String password;
        private String driverClassName;

        public String getJdbcUrl() {
            return jdbcUrl;
        }

        public void setJdbcUrl(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }
    }

    public static class Qdrant {
        private String baseUrl;
        private String columnCollection = "column_info_collection";
        private String metricCollection = "metric_info_collection";
        private double scoreThreshold = 0.6;
        private int limit = 3;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getColumnCollection() {
            return columnCollection;
        }

        public void setColumnCollection(String columnCollection) {
            this.columnCollection = columnCollection;
        }

        public String getMetricCollection() {
            return metricCollection;
        }

        public void setMetricCollection(String metricCollection) {
            this.metricCollection = metricCollection;
        }

        public double getScoreThreshold() {
            return scoreThreshold;
        }

        public void setScoreThreshold(double scoreThreshold) {
            this.scoreThreshold = scoreThreshold;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }
    }

    public static class Elasticsearch {
        private String baseUrl;
        private String valueIndex = "value_index";
        private double scoreThreshold = 0.6;
        private int limit = 20;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getValueIndex() {
            return valueIndex;
        }

        public void setValueIndex(String valueIndex) {
            this.valueIndex = valueIndex;
        }

        public double getScoreThreshold() {
            return scoreThreshold;
        }

        public void setScoreThreshold(double scoreThreshold) {
            this.scoreThreshold = scoreThreshold;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }
    }
}
