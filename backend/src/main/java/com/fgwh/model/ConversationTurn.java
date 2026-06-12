package com.fgwh.model;

import java.time.Instant;

public record ConversationTurn(
        String sessionId,
        String userQuery,
        String rewrittenQuery,
        String sql,
        String resultSummary,
        Instant createdAt
) {
}
