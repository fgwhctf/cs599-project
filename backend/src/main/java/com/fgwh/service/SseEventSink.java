package com.fgwh.service;

import java.io.IOException;
import java.util.Map;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class SseEventSink {

    private final SseEmitter emitter;

    public SseEventSink(SseEmitter emitter) {
        this.emitter = emitter;
    }

    public void progress(String step, String status) {
        send(Map.of("type", "progress", "step", step, "status", status));
    }

    public void result(Object data) {
        send(Map.of("type", "result", "data", data));
    }

    public void rewrite(String query) {
        send(Map.of("type", "rewrite", "query", query));
    }

    public void error(String message) {
        send(Map.of("type", "error", "message", message));
    }

    public void send(Object payload) {
        try {
            emitter.send(SseEmitter.event().data(payload));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to send SSE event", ex);
        }
    }
}
