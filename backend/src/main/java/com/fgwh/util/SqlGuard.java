package com.fgwh.util;

import java.util.Locale;
import java.util.Set;

public final class SqlGuard {

    private static final Set<String> BLOCKED = Set.of(
            "insert", "update", "delete", "create", "alter", "drop", "truncate", "replace",
            "grant", "revoke", "merge", "call", "exec", "load", "outfile"
    );

    private SqlGuard() {
    }

    public static String cleanSql(String sql) {
        String cleaned = sql.trim();
        cleaned = cleaned.replaceFirst("^```(?:sql)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        if (cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        return cleaned;
    }

    public static void assertReadOnlySingleStatement(String sql) {
        String cleaned = cleanSql(sql);
        String lower = cleaned.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("select") && !lower.startsWith("with")) {
            throw new IllegalArgumentException("Only SELECT or WITH queries are allowed");
        }
        if (lower.contains(";")) {
            throw new IllegalArgumentException("Only one SQL statement is allowed");
        }
        for (String keyword : BLOCKED) {
            if (lower.matches("(?s).*\\b" + keyword + "\\b.*")) {
                throw new IllegalArgumentException("Blocked non-read-only SQL keyword: " + keyword);
            }
        }
    }
}
