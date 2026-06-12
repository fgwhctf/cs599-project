package com.fgwh.repository;

import com.fgwh.config.DataAgentProperties;
import com.fgwh.model.ValueInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;

@Repository
public class ElasticValueRepository {

    private final DataAgentProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ElasticValueRepository(DataAgentProperties properties, RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(properties.getElasticsearch().getBaseUrl()).build();
        this.objectMapper = objectMapper;
    }

    public List<ValueInfo> search(String keyword) {
        Map<String, Object> request = Map.of(
                "query", Map.of("match", Map.of("value", keyword)),
                "size", properties.getElasticsearch().getLimit(),
                "min_score", properties.getElasticsearch().getScoreThreshold()
        );
        Map<String, Object> response = restClient.post()
                .uri("/{index}/_search", properties.getElasticsearch().getValueIndex())
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        @SuppressWarnings("unchecked")
        Map<String, Object> hits = (Map<String, Object>) response.get("hits");
        if (hits == null) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) hits.getOrDefault("hits", List.of());
        return rows.stream()
                .map(hit -> (Map<String, Object>) hit.get("_source"))
                .map(source -> objectMapper.convertValue(source, ValueInfo.class))
                .toList();
    }
}
