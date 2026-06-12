package com.fgwh.scripts;

import java.util.List;

public class MetaConfig {
    public List<TableConfig> tables;
    public List<MetricConfig> metrics;

    public static class TableConfig {
        public String name;
        public String role;
        public String description;
        public List<ColumnConfig> columns;
    }

    public static class ColumnConfig {
        public String name;
        public String role;
        public String description;
        public List<String> alias;
        public boolean sync;
    }

    public static class MetricConfig {
        public String name;
        public String description;
        public List<String> relevant_columns;
        public List<String> alias;
    }
}
