package com.fgwh.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SqlGuardTest {

    @Test
    void cleanSqlRemovesMarkdownFenceAndTrailingSemicolon() {
        String sql = """
                ```sql
                SELECT * FROM fact_order;
                ```
                """;

        assertEquals("SELECT * FROM fact_order", SqlGuard.cleanSql(sql));
    }

    @Test
    void allowsReadOnlySelectAndWithQueries() {
        assertDoesNotThrow(() -> SqlGuard.assertReadOnlySingleStatement("select * from fact_order"));
        assertDoesNotThrow(() -> SqlGuard.assertReadOnlySingleStatement(
                "with t as (select * from fact_order) select * from t"
        ));
    }

    @Test
    void rejectsNonReadOnlyStatement() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> SqlGuard.assertReadOnlySingleStatement("delete from fact_order")
        );
        assertEquals(SqlGuard.READ_ONLY_MESSAGE, ex.getMessage());
    }

    @Test
    void rejectsMultipleStatements() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> SqlGuard.assertReadOnlySingleStatement("select * from fact_order; select * from dim_product")
        );
        assertEquals(SqlGuard.READ_ONLY_MESSAGE, ex.getMessage());
    }

    @Test
    void rejectsDangerousKeywordInsideReadOnlyQuery() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> SqlGuard.assertReadOnlySingleStatement("select * from fact_order into outfile '/tmp/orders.csv'")
        );
        assertEquals(SqlGuard.READ_ONLY_MESSAGE, ex.getMessage());
    }

    @Test
    void rejectsWriteIntentBeforeSqlGeneration() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> SqlGuard.assertReadOnlyIntent("删除 fact_order 中金额为 0 的订单")
        );
        assertEquals(SqlGuard.READ_ONLY_MESSAGE, ex.getMessage());
    }
}
