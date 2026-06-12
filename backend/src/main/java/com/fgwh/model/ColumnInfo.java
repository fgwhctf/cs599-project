package com.fgwh.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

public record ColumnInfo(
        String id,
        String name,
        String type,
        String role,
        List<Object> examples,
        String description,
        List<String> alias,
        @JsonProperty("table_id")
        String tableId
) {
    public ColumnInfo withExamples(List<Object> newExamples) {
        return new ColumnInfo(id, name, type, role, new ArrayList<>(newExamples), description, alias, tableId);
    }
}
