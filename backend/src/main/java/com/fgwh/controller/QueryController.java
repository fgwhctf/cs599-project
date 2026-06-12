package com.fgwh.controller;

import com.fgwh.service.ConversationMemoryService;
import com.fgwh.service.QueryService;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class QueryController {

    private final QueryService queryService;
    private final ConversationMemoryService memoryService;

    public QueryController(QueryService queryService, ConversationMemoryService memoryService) {
        this.queryService = queryService;
        this.memoryService = memoryService;
    }

    @PostMapping(path = "/api/query", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter query(@RequestBody QueryRequest request) {
        if (request == null || !StringUtils.hasText(request.query())) {
            throw new IllegalArgumentException("query must not be blank");
        }
        return queryService.query(request.sessionId(), request.query());
    }

    @DeleteMapping("/api/conversations/{sessionId}")
    public void clearConversation(@PathVariable String sessionId) {
        memoryService.clear(sessionId);
    }

    public record QueryRequest(String sessionId, String query) {
    }
}
