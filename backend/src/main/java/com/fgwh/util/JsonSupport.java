package com.fgwh.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonSupport() {
    }

    public static <T> T read(String json, TypeReference<T> typeReference) {
        try {
            return OBJECT_MAPPER.readValue(json, typeReference);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid JSON: " + json, ex);
        }
    }

    public static String extractJson(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        int arrayStart = trimmed.indexOf('[');
        int objectStart = trimmed.indexOf('{');
        int start;
        char endChar;
        if (arrayStart >= 0 && (objectStart < 0 || arrayStart < objectStart)) {
            start = arrayStart;
            endChar = ']';
        } else {
            start = objectStart;
            endChar = '}';
        }
        if (start < 0) {
            throw new IllegalArgumentException("No JSON object or array found in response: " + text);
        }
        int end = trimmed.lastIndexOf(endChar);
        if (end < start) {
            throw new IllegalArgumentException("Incomplete JSON response: " + text);
        }
        return trimmed.substring(start, end + 1);
    }
}
