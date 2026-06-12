package com.fgwh.service;

import com.fgwh.model.AgentState;
import com.fgwh.model.ConversationTurn;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ConversationMemoryService {

    private static final int MAX_TURNS = 5;

    private final Map<String, Deque<ConversationTurn>> turnsBySession = new ConcurrentHashMap<>();

    public List<ConversationTurn> recentTurns(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return List.of();
        }
        Deque<ConversationTurn> turns = turnsBySession.get(sessionId);
        if (turns == null) {
            return List.of();
        }
        synchronized (turns) {
            return new ArrayList<>(turns);
        }
    }

    public void save(String sessionId, String userQuery, String rewrittenQuery, AgentState state) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        ConversationTurn turn = new ConversationTurn(
                sessionId,
                userQuery,
                rewrittenQuery,
                state.getSql(),
                summarize(state),
                Instant.now()
        );
        Deque<ConversationTurn> turns = turnsBySession.computeIfAbsent(sessionId, ignored -> new ArrayDeque<>());
        synchronized (turns) {
            turns.addLast(turn);
            while (turns.size() > MAX_TURNS) {
                turns.removeFirst();
            }
        }
    }

    public void clear(String sessionId) {
        if (StringUtils.hasText(sessionId)) {
            turnsBySession.remove(sessionId);
        }
    }

    public String formatHistory(String sessionId) {
        List<ConversationTurn> turns = recentTurns(sessionId);
        if (turns.isEmpty()) {
            return "无";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < turns.size(); i++) {
            ConversationTurn turn = turns.get(i);
            builder.append(i + 1).append(". 用户原问题：").append(turn.userQuery()).append('\n');
            builder.append("   理解为：").append(turn.rewrittenQuery()).append('\n');
            if (StringUtils.hasText(turn.resultSummary())) {
                builder.append("   结果摘要：").append(turn.resultSummary()).append('\n');
            }
            if (StringUtils.hasText(turn.sql())) {
                builder.append("   SQL：").append(turn.sql()).append('\n');
            }
        }
        return builder.toString();
    }

    private String summarize(AgentState state) {
        StringJoiner joiner = new StringJoiner("；");
        joiner.add("问题=" + state.getQuery());
        if (!state.getMetricInfos().isEmpty()) {
            joiner.add("指标=" + names(state.getMetricInfos()));
        }
        if (!state.getTableInfos().isEmpty()) {
            joiner.add("表=" + names(state.getTableInfos()));
        }
        if (StringUtils.hasText(state.getSql())) {
            joiner.add("SQL已生成");
        }
        joiner.add("返回行数=" + state.getResultRows().size());
        return joiner.toString();
    }

    private String names(List<Map<String, Object>> items) {
        return items.stream()
                .map(item -> String.valueOf(item.get("name")))
                .filter(StringUtils::hasText)
                .distinct()
                .toList()
                .toString();
    }
}
