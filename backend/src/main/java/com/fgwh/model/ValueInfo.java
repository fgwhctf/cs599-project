package com.fgwh.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ValueInfo(String id, String value, @JsonProperty("column_id") String columnId) {
}
