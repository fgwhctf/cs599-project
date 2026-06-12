package com.fgwh.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record MetricInfo(
        String id,
        String name,
        String description,
        @JsonProperty("relevant_columns")
        List<String> relevantColumns,
        List<String> alias
) {
}
