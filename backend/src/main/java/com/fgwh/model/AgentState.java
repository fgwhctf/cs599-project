package com.fgwh.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentState {

    private final String query;
    private String error;
    private List<String> keywords = new ArrayList<>();
    private List<ColumnInfo> retrievedColumnInfos = new ArrayList<>();
    private List<MetricInfo> retrievedMetricInfos = new ArrayList<>();
    private List<ValueInfo> retrievedValueInfos = new ArrayList<>();
    private List<Map<String, Object>> tableInfos = new ArrayList<>();
    private List<Map<String, Object>> metricInfos = new ArrayList<>();
    private Map<String, Object> dateInfo = new LinkedHashMap<>();
    private Map<String, Object> dbInfo = new LinkedHashMap<>();
    private String sql;
    private List<Map<String, Object>> resultRows = new ArrayList<>();

    public AgentState(String query) {
        this.query = query;
    }

    public String getQuery() {
        return query;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }

    public List<ColumnInfo> getRetrievedColumnInfos() {
        return retrievedColumnInfos;
    }

    public void setRetrievedColumnInfos(List<ColumnInfo> retrievedColumnInfos) {
        this.retrievedColumnInfos = retrievedColumnInfos;
    }

    public List<MetricInfo> getRetrievedMetricInfos() {
        return retrievedMetricInfos;
    }

    public void setRetrievedMetricInfos(List<MetricInfo> retrievedMetricInfos) {
        this.retrievedMetricInfos = retrievedMetricInfos;
    }

    public List<ValueInfo> getRetrievedValueInfos() {
        return retrievedValueInfos;
    }

    public void setRetrievedValueInfos(List<ValueInfo> retrievedValueInfos) {
        this.retrievedValueInfos = retrievedValueInfos;
    }

    public List<Map<String, Object>> getTableInfos() {
        return tableInfos;
    }

    public void setTableInfos(List<Map<String, Object>> tableInfos) {
        this.tableInfos = tableInfos;
    }

    public List<Map<String, Object>> getMetricInfos() {
        return metricInfos;
    }

    public void setMetricInfos(List<Map<String, Object>> metricInfos) {
        this.metricInfos = metricInfos;
    }

    public Map<String, Object> getDateInfo() {
        return dateInfo;
    }

    public void setDateInfo(Map<String, Object> dateInfo) {
        this.dateInfo = dateInfo;
    }

    public Map<String, Object> getDbInfo() {
        return dbInfo;
    }

    public void setDbInfo(Map<String, Object> dbInfo) {
        this.dbInfo = dbInfo;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public List<Map<String, Object>> getResultRows() {
        return resultRows;
    }

    public void setResultRows(List<Map<String, Object>> resultRows) {
        this.resultRows = resultRows;
    }
}
