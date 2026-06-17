package com.fgwh.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class QueryService {

    private final DataAgentService dataAgentService;
    private final QueryRewriteService queryRewriteService;
    private final ConversationMemoryService memoryService;

    public QueryService(
            DataAgentService dataAgentService,
            QueryRewriteService queryRewriteService,
            ConversationMemoryService memoryService
    ) {
        this.dataAgentService = dataAgentService;
        this.queryRewriteService = queryRewriteService;
        this.memoryService = memoryService;
    }

    public SseEmitter query(String sessionId, String query) {
        SseEmitter emitter = new SseEmitter(0L);
        Thread.startVirtualThread(() -> {
            var sink = new SseEventSink(emitter);
            try {
                String rewrittenQuery = queryRewriteService.rewrite(sessionId, query);
                if (!rewrittenQuery.equals(query)) {
                    sink.rewrite(rewrittenQuery);
                }
                var state = dataAgentService.run(rewrittenQuery, sink);
                memoryService.save(sessionId, query, rewrittenQuery, state);
                emitter.complete();
            } catch (Exception ex) {
                sink.error(ex.getMessage());
                emitter.complete();
            }
        });
        return emitter;
    }
}
