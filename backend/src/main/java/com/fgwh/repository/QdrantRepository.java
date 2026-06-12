package com.fgwh.repository;

import com.fgwh.config.DataAgentProperties;
import com.fgwh.model.ColumnInfo;
import com.fgwh.model.MetricInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;

@Repository
public class QdrantRepository {

    private final DataAgentProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public QdrantRepository(DataAgentProperties properties, RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(properties.getQdrant().getBaseUrl()).build();
        this.objectMapper = objectMapper;
    }

    public List<ColumnInfo> searchColumns(float[] embedding) {
        return search(properties.getQdrant().getColumnCollection(), embedding).stream()
                .map(payload -> objectMapper.convertValue(payload, ColumnInfo.class))
                .toList();
    }

    public List<MetricInfo> searchMetrics(float[] embedding) {
        return search(properties.getQdrant().getMetricCollection(), embedding).stream()
                .map(payload -> objectMapper.convertValue(payload, MetricInfo.class))
                .toList();
    }

    private List<Map<String, Object>> search(String collection, float[] embedding) {
        Map<String, Object> request = Map.of(
                "vector", toList(embedding),
                "limit", properties.getQdrant().getLimit(),
                "score_threshold", properties.getQdrant().getScoreThreshold(),
                "with_payload", true
        );
        Map<String, Object> response = restClient.post()
                .uri("/collections/{collection}/points/search", collection)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> points = (List<Map<String, Object>>) response.getOrDefault("result", List.of());
        return points.stream()
                .map(point -> (Map<String, Object>) point.get("payload"))
                .toList();
    }

    private static List<Float> toList(float[] embedding) {
        java.util.ArrayList<Float> result = new java.util.ArrayList<>(embedding.length);
        for (float value : embedding) {
            result.add(value);
        }
        return result;
    }
}
