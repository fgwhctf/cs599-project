package com.fgwh.service;

import com.fgwh.model.AgentState;
import com.fgwh.model.ColumnInfo;
import com.fgwh.model.MetricInfo;
import com.fgwh.model.ValueInfo;
import com.fgwh.repository.DwRepository;
import com.fgwh.repository.ElasticValueRepository;
import com.fgwh.repository.MetaRepository;
import com.fgwh.repository.QdrantRepository;
import com.fgwh.util.SqlGuard;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
public class DataAgentService {

    private final KeywordService keywordService;
    private final LlmService llmService;
    private final EmbeddingModel embeddingModel;
    private final QdrantRepository qdrantRepository;
    private final ElasticValueRepository elasticValueRepository;
    private final MetaRepository metaRepository;
    private final DwRepository dwRepository;

    public DataAgentService(
            KeywordService keywordService,
            LlmService llmService,
            EmbeddingModel embeddingModel,
            QdrantRepository qdrantRepository,
            ElasticValueRepository elasticValueRepository,
            MetaRepository metaRepository,
            DwRepository dwRepository
    ) {
        this.keywordService = keywordService;
        this.llmService = llmService;
        this.embeddingModel = embeddingModel;
        this.qdrantRepository = qdrantRepository;
        this.elasticValueRepository = elasticValueRepository;
        this.metaRepository = metaRepository;
        this.dwRepository = dwRepository;
    }

    public AgentState run(String query, SseEventSink sink) {
        AgentState state = new AgentState(query);

        extractKeywords(state, sink);
        recallColumn(state, sink);
        recallValue(state, sink);
        recallMetric(state, sink);
        mergeRetrievedInfo(state, sink);
        filterTable(state, sink);
        filterMetric(state, sink);
        addExtraContext(state, sink);
        generateSql(state, sink);
        validateSql(state, sink);
        if (state.getError() != null) {
            correctSql(state, sink);
        }
        executeSql(state, sink);
        return state;
    }

    private void extractKeywords(AgentState state, SseEventSink sink) {
        sink.progress("抽取关键词", "running");
        state.setKeywords(keywordService.extract(state.getQuery()));
        sink.progress("抽取关键词", "success");
    }

    private void recallColumn(AgentState state, SseEventSink sink) {
        sink.progress("字段信息召回", "running");
        Set<String> keywords = new LinkedHashSet<>(state.getKeywords());
        keywords.addAll(llmService.jsonStringList("extend_keywords_for_column_recall", Map.of("query", state.getQuery())));

        Map<String, ColumnInfo> dedup = new LinkedHashMap<>();
        for (String keyword : keywords) {
            float[] embedding = embeddingModel.embed(keyword);
            for (ColumnInfo columnInfo : qdrantRepository.searchColumns(embedding)) {
                dedup.putIfAbsent(columnInfo.id(), columnInfo);
            }
        }
        state.setRetrievedColumnInfos(new ArrayList<>(dedup.values()));
        sink.progress("字段信息召回", "success");
    }

    private void recallValue(AgentState state, SseEventSink sink) {
        sink.progress("字段取值召回", "running");
        Set<String> keywords = new LinkedHashSet<>(state.getKeywords());
        keywords.addAll(llmService.jsonStringList("extend_keywords_for_value_recall", Map.of("query", state.getQuery())));

        Map<String, ValueInfo> dedup = new LinkedHashMap<>();
        for (String keyword : keywords) {
            for (ValueInfo valueInfo : elasticValueRepository.search(keyword)) {
                dedup.putIfAbsent(valueInfo.id(), valueInfo);
            }
        }
        state.setRetrievedValueInfos(new ArrayList<>(dedup.values()));
        sink.progress("字段取值召回", "success");
    }

    private void recallMetric(AgentState state, SseEventSink sink) {
        sink.progress("指标召回", "running");
        Set<String> keywords = new LinkedHashSet<>(state.getKeywords());
        keywords.addAll(llmService.jsonStringList("extend_keywords_for_metric_recall", Map.of("query", state.getQuery())));

        Map<String, MetricInfo> dedup = new LinkedHashMap<>();
        for (String keyword : keywords) {
            float[] embedding = embeddingModel.embed(keyword);
            for (MetricInfo metricInfo : qdrantRepository.searchMetrics(embedding)) {
                dedup.putIfAbsent(metricInfo.id(), metricInfo);
            }
        }
        state.setRetrievedMetricInfos(new ArrayList<>(dedup.values()));
        sink.progress("指标召回", "success");
    }

    private void mergeRetrievedInfo(AgentState state, SseEventSink sink) {
        sink.progress("合并信息", "running");
        Map<String, ColumnInfo> columns = state.getRetrievedColumnInfos().stream()
                .collect(Collectors.toMap(ColumnInfo::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        for (MetricInfo metricInfo : state.getRetrievedMetricInfos()) {
            for (String columnId : metricInfo.relevantColumns()) {
                columns.computeIfAbsent(columnId, metaRepository::findColumnById);
            }
        }

        for (ValueInfo valueInfo : state.getRetrievedValueInfos()) {
            ColumnInfo columnInfo = columns.computeIfAbsent(valueInfo.columnId(), metaRepository::findColumnById);
            if (columnInfo == null) {
                continue;
            }
            List<Object> examples = new ArrayList<>(columnInfo.examples() == null ? List.of() : columnInfo.examples());
            if (!examples.contains(valueInfo.value())) {
                examples.add(valueInfo.value());
            }
            columns.put(columnInfo.id(), columnInfo.withExamples(examples));
        }

        Map<String, List<ColumnInfo>> tableToColumns = columns.values().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(ColumnInfo::tableId, LinkedHashMap::new, Collectors.toCollection(ArrayList::new)));

        for (Map.Entry<String, List<ColumnInfo>> entry : tableToColumns.entrySet()) {
            Set<String> existing = entry.getValue().stream().map(ColumnInfo::id).collect(Collectors.toSet());
            for (ColumnInfo keyColumn : metaRepository.findKeyColumnsByTableId(entry.getKey())) {
                if (existing.add(keyColumn.id())) {
                    entry.getValue().add(keyColumn);
                }
            }
        }

        List<Map<String, Object>> tableInfos = new ArrayList<>();
        for (Map.Entry<String, List<ColumnInfo>> entry : tableToColumns.entrySet()) {
            var table = metaRepository.findTableById(entry.getKey());
            if (table == null) {
                continue;
            }
            Map<String, Object> tableInfo = new LinkedHashMap<>();
            tableInfo.put("name", table.name());
            tableInfo.put("role", table.role());
            tableInfo.put("description", table.description());
            tableInfo.put("columns", entry.getValue().stream().map(this::toColumnPromptMap).toList());
            tableInfos.add(tableInfo);
        }
        state.setTableInfos(tableInfos);
        state.setMetricInfos(state.getRetrievedMetricInfos().stream().map(this::toMetricPromptMap).toList());
        sink.progress("合并信息", "success");
    }

    private void filterTable(AgentState state, SseEventSink sink) {
        sink.progress("过滤表信息", "running");
        Map<String, List<String>> selected = llmService.jsonStringListMap("filter_table_info", Map.of(
                "query", state.getQuery(),
                "table_infos", llmService.toYaml(state.getTableInfos())
        ));
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> tableInfo : state.getTableInfos()) {
            String tableName = String.valueOf(tableInfo.get("name"));
            if (!selected.containsKey(tableName)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> columns = (List<Map<String, Object>>) tableInfo.get("columns");
            Set<String> columnNames = new LinkedHashSet<>(selected.get(tableName));
            List<Map<String, Object>> selectedColumns = columns.stream()
                    .filter(column -> columnNames.contains(String.valueOf(column.get("name"))))
                    .toList();
            Map<String, Object> copy = new LinkedHashMap<>(tableInfo);
            copy.put("columns", selectedColumns);
            filtered.add(copy);
        }
        state.setTableInfos(filtered);
        sink.progress("过滤表信息", "success");
    }

    private void filterMetric(AgentState state, SseEventSink sink) {
        sink.progress("过滤指标信息", "running");
        List<String> selected = llmService.jsonStringList("filter_metric_info", Map.of(
                "query", state.getQuery(),
                "metric_infos", llmService.toYaml(state.getMetricInfos())
        ));
        Set<String> selectedNames = new LinkedHashSet<>(selected);
        state.setMetricInfos(state.getMetricInfos().stream()
                .filter(metric -> selectedNames.contains(String.valueOf(metric.get("name"))))
                .toList());
        sink.progress("过滤指标信息", "success");
    }

    private void addExtraContext(AgentState state, SseEventSink sink) {
        sink.progress("添加额外信息", "running");
        LocalDate today = LocalDate.now();
        state.setDateInfo(Map.of(
                "date", today.toString(),
                "weekday", today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                "quarter", "Q" + ((today.getMonthValue() - 1) / 3 + 1)
        ));
        state.setDbInfo(dwRepository.getDbInfo());
        sink.progress("添加额外信息", "success");
    }

    private void generateSql(AgentState state, SseEventSink sink) {
        sink.progress("生成SQL", "running");
        String sql = llmService.text("generate_sql", promptVariables(state));
        state.setSql(SqlGuard.cleanSql(sql));
        sink.progress("生成SQL", "success");
    }

    private void validateSql(AgentState state, SseEventSink sink) {
        sink.progress("验证SQL", "running");
        try {
            SqlGuard.assertReadOnlySingleStatement(state.getSql());
            dwRepository.validateSql(state.getSql());
            state.setError(null);
        } catch (Exception ex) {
            state.setError(ex.getMessage());
        }
        sink.progress("验证SQL", "success");
    }

    private void correctSql(AgentState state, SseEventSink sink) {
        sink.progress("校正SQL", "running");
        String sql = llmService.text("correct_sql", promptVariables(state));
        state.setSql(SqlGuard.cleanSql(sql));
        SqlGuard.assertReadOnlySingleStatement(state.getSql());
        sink.progress("校正SQL", "success");
    }

    private void executeSql(AgentState state, SseEventSink sink) {
        sink.progress("执行SQL", "running");
        List<Map<String, Object>> result = dwRepository.runSql(state.getSql());
        state.setResultRows(result);
        sink.progress("执行SQL", "success");
        sink.result(Map.of("sql", state.getSql(), "rows", result));
    }

    private Map<String, Object> promptVariables(AgentState state) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("table_infos", llmService.toYaml(state.getTableInfos()));
        variables.put("metric_infos", llmService.toYaml(state.getMetricInfos()));
        variables.put("date_info", llmService.toYaml(state.getDateInfo()));
        variables.put("db_info", llmService.toYaml(state.getDbInfo()));
        variables.put("query", state.getQuery());
        variables.put("sql", state.getSql());
        variables.put("error", state.getError());
        return variables;
    }

    private Map<String, Object> toColumnPromptMap(ColumnInfo columnInfo) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", columnInfo.name());
        map.put("type", columnInfo.type());
        map.put("role", columnInfo.role());
        map.put("examples", columnInfo.examples());
        map.put("description", columnInfo.description());
        map.put("alias", columnInfo.alias());
        return map;
    }

    private Map<String, Object> toMetricPromptMap(MetricInfo metricInfo) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", metricInfo.name());
        map.put("description", metricInfo.description());
        map.put("relevant_columns", metricInfo.relevantColumns());
        map.put("alias", metricInfo.alias());
        return map;
    }
}
