package com.fgwh.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fgwh.model.AgentState;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConversationMemoryServiceTest {

    @Test
    void savesConversationTurnAndFormatsHistory() {
        ConversationMemoryService memoryService = new ConversationMemoryService();
        AgentState state = state("统计总 GMV", "select sum(amount) as gmv from fact_order");
        state.setMetricInfos(List.of(Map.of("name", "GMV")));
        state.setTableInfos(List.of(Map.of("name", "fact_order")));
        state.setResultRows(List.of(Map.of("gmv", 100)));

        memoryService.save("session-1", "统计总 GMV", "统计总 GMV", state);

        assertEquals(1, memoryService.recentTurns("session-1").size());
        String history = memoryService.formatHistory("session-1");
        assertTrue(history.contains("用户原问题：统计总 GMV"));
        assertTrue(history.contains("SQL：select sum(amount) as gmv from fact_order"));
        assertTrue(history.contains("指标=[GMV]"));
    }

    @Test
    void keepsOnlyMostRecentTenTurns() {
        ConversationMemoryService memoryService = new ConversationMemoryService();

        for (int i = 0; i < 11; i++) {
            memoryService.save("session-1", "q" + i, "rq" + i, state("rq" + i, "select " + i));
        }

        var turns = memoryService.recentTurns("session-1");
        assertEquals(10, turns.size());
        assertEquals("q1", turns.getFirst().userQuery());
        assertEquals("q10", turns.getLast().userQuery());
    }

    @Test
    void clearsConversation() {
        ConversationMemoryService memoryService = new ConversationMemoryService();
        memoryService.save("session-1", "q", "rq", state("rq", "select 1"));

        memoryService.clear("session-1");

        assertTrue(memoryService.recentTurns("session-1").isEmpty());
        assertEquals("无", memoryService.formatHistory("session-1"));
    }

    @Test
    void ignoresBlankSessionId() {
        ConversationMemoryService memoryService = new ConversationMemoryService();

        memoryService.save(" ", "q", "rq", state("rq", "select 1"));

        assertTrue(memoryService.recentTurns(" ").isEmpty());
        assertEquals("无", memoryService.formatHistory(" "));
    }

    private AgentState state(String query, String sql) {
        AgentState state = new AgentState(query);
        state.setSql(sql);
        return state;
    }
}
