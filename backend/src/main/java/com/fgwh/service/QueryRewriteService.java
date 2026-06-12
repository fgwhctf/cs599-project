package com.fgwh.service;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class QueryRewriteService {

    private final ConversationMemoryService memoryService;
    private final LlmService llmService;

    public QueryRewriteService(ConversationMemoryService memoryService, LlmService llmService) {
        this.memoryService = memoryService;
        this.llmService = llmService;
    }

    public String rewrite(String sessionId, String query) {
        String history = memoryService.formatHistory(sessionId);
        if ("无".equals(history)) {
            return query;
        }
        String rewritten = llmService.text("rewrite_query", Map.of(
                "history", history,
                "query", query
        ));
        rewritten = cleanup(rewritten);
        return StringUtils.hasText(rewritten) ? rewritten : query;
    }

    private String cleanup(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```[a-zA-Z]*\\s*", "");
            cleaned = cleaned.replaceFirst("\\s*```$", "");
        }
        return cleaned.trim().replaceAll("^理解为[:：]\\s*", "");
    }
}
