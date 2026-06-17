package com.fgwh.util;

import java.util.Locale;
import java.util.Set;

public final class SqlGuard {

    public static final String READ_ONLY_MESSAGE = "只能执行只读SQL";

    private static final Set<String> BLOCKED = Set.of(
            "insert", "update", "delete", "create", "alter", "drop", "truncate", "replace",
            "grant", "revoke", "merge", "call", "exec", "load", "outfile"
    );

    private static final Set<String> WRITE_INTENT_WORDS = Set.of(
            "插入", "新增", "添加", "删除", "移除", "更新", "修改", "改写", "创建", "建表",
            "清空", "截断", "授权", "撤销授权", "导出", "写入"
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
            throw new IllegalArgumentException(READ_ONLY_MESSAGE);
        }
        if (lower.contains(";")) {
            throw new IllegalArgumentException(READ_ONLY_MESSAGE);
        }
        for (String keyword : BLOCKED) {
            if (lower.matches("(?s).*\\b" + keyword + "\\b.*")) {
                throw new IllegalArgumentException(READ_ONLY_MESSAGE);
            }
        }
    }

    public static void assertReadOnlyIntent(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : BLOCKED) {
            if (lower.matches("(?s).*\\b" + keyword + "\\b.*")) {
                throw new IllegalArgumentException(READ_ONLY_MESSAGE);
            }
        }
        for (String word : WRITE_INTENT_WORDS) {
            if (text.contains(word)) {
                throw new IllegalArgumentException(READ_ONLY_MESSAGE);
            }
        }
    }
}
