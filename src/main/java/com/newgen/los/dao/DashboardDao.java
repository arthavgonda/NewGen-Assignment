package com.newgen.los.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public class DashboardDao {

    public Map<LocalDate, long[]> applicationsByDay(Connection c, LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT DATE(created_at) AS d, COUNT(*) AS cnt, "
                + "SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) AS rejected "
                + "FROM loan_application WHERE DATE(created_at) BETWEEN ? AND ? GROUP BY DATE(created_at)";
        Map<LocalDate, long[]> out = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, from);
            ps.setObject(2, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getObject("d", LocalDate.class), new long[] {rs.getLong("cnt"), rs.getLong("rejected")});
                }
            }
        }
        return out;
    }

    public Map<LocalDate, BigDecimal[]> moneyByDay(Connection c, LocalDate from, LocalDate to) throws SQLException {
        Map<LocalDate, BigDecimal[]> out = new LinkedHashMap<>();
        String requested = "SELECT DATE(submitted_at) AS d, SUM(requested_amount) AS amt FROM loan_application "
                + "WHERE submitted_at IS NOT NULL AND DATE(submitted_at) BETWEEN ? AND ? GROUP BY DATE(submitted_at)";
        String disbursed = "SELECT DATE(disbursed_at) AS d, SUM(amount) AS amt FROM disbursement "
                + "WHERE DATE(disbursed_at) BETWEEN ? AND ? GROUP BY DATE(disbursed_at)";
        fill(c, requested, from, to, out, 0);
        fill(c, disbursed, from, to, out, 1);
        return out;
    }

    private void fill(Connection c, String sql, LocalDate from, LocalDate to, Map<LocalDate, BigDecimal[]> out, int slot)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, from);
            ps.setObject(2, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BigDecimal[] pair = out.computeIfAbsent(rs.getObject("d", LocalDate.class),
                            k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
                    pair[slot] = rs.getBigDecimal("amt");
                }
            }
        }
    }

    public Map<String, BigDecimal> totals(Connection c) throws SQLException {
        String sql = "SELECT "
                + "(SELECT COALESCE(SUM(requested_amount), 0) FROM loan_application WHERE submitted_at IS NOT NULL) AS requested, "
                + "(SELECT COALESCE(SUM(approved_amount), 0) FROM loan_application WHERE approved_amount IS NOT NULL) AS sanctioned, "
                + "(SELECT COALESCE(SUM(amount), 0) FROM disbursement) AS disbursed, "
                + "(SELECT COALESCE(SUM(approved_amount), 0) FROM loan_application WHERE status = 'APPROVED') AS pending, "
                + "(SELECT COALESCE(SUM(requested_amount), 0) FROM loan_application WHERE status IN ('UNDER_REVIEW', 'CREDIT_APPROVAL')) AS in_review";
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            for (String k : new String[] {"requested", "sanctioned", "disbursed", "pending", "in_review"}) {
                out.put(k, rs.getBigDecimal(k));
            }
        }
        return out;
    }
}
