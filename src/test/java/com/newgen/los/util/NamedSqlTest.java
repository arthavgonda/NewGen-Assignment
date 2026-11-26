package com.newgen.los.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamedSqlTest {

    @Test
    void replacesNamedParametersInOrder() {
        NamedSql q = NamedSql.parse("SELECT * FROM t WHERE (:p IS NULL OR a = :p) AND b = :s;");
        assertEquals("SELECT * FROM t WHERE (? IS NULL OR a = ?) AND b = ?", q.sql());
        assertEquals(List.of("p", "p", "s"), q.parameters());
    }

    @Test
    void ignoresColonsInsideQuotes() {
        NamedSql q = NamedSql.parse("SELECT DATE_FORMAT(x, '%H:%i') FROM t WHERE d = :day");
        assertEquals(List.of("day"), q.parameters());
        assertTrue(q.sql().contains("'%H:%i'"));
    }

    @Test
    void allSixReportsArePresent() {
        Map<String, NamedSql> all = NamedSql.load("sql/reports.sql");
        for (String name : List.of("application_register", "pipeline_by_product_status", "pipeline_by_branch_product",
                "disbursement_summary", "disbursement_by_branch", "overdue_reviews",
                "customers_without_disbursement", "repeat_applicants")) {
            assertTrue(all.containsKey(name), name);
            assertFalse(all.get(name).sql().contains(":"), name);
        }
    }
}
