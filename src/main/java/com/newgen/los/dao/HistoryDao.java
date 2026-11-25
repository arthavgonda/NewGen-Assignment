package com.newgen.los.dao;

import com.newgen.los.model.ApplicationStatus;
import com.newgen.los.model.HistoryAction;
import com.newgen.los.model.HistoryEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class HistoryDao {

    public void insert(Connection c, HistoryEntry entry) throws SQLException {
        String sql = "INSERT INTO application_history (application_id, action, old_status, new_status, performed_by, "
                + "performed_at, remarks) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, entry.getApplicationId());
            ps.setString(2, entry.getAction().name());
            Jdbc.setString(ps, 3, entry.getOldStatus() == null ? null : entry.getOldStatus().name());
            ps.setString(4, entry.getNewStatus().name());
            ps.setString(5, entry.getPerformedBy());
            Jdbc.setDateTime(ps, 6, entry.getPerformedAt());
            Jdbc.setString(ps, 7, truncate(entry.getRemarks(), 1000));
            ps.executeUpdate();
        }
    }

    public List<HistoryEntry> findByApplication(Connection c, long applicationId) throws SQLException {
        String sql = "SELECT h.*, a.application_number, cu.name AS customer_name FROM application_history h "
                + "JOIN loan_application a ON a.application_id = h.application_id "
                + "JOIN customer cu ON cu.customer_id = a.customer_id "
                + "WHERE h.application_id = ? ORDER BY h.performed_at, h.history_id";
        return query(c, sql, applicationId);
    }

    public List<HistoryEntry> findRecent(Connection c, int limit) throws SQLException {
        String sql = "SELECT h.*, a.application_number, cu.name AS customer_name FROM application_history h "
                + "JOIN loan_application a ON a.application_id = h.application_id "
                + "JOIN customer cu ON cu.customer_id = a.customer_id "
                + "ORDER BY h.performed_at DESC, h.history_id DESC LIMIT ?";
        return query(c, sql, limit);
    }

    private List<HistoryEntry> query(Connection c, String sql, Object param) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                List<HistoryEntry> list = new ArrayList<>();
                while (rs.next()) {
                    HistoryEntry h = new HistoryEntry();
                    h.setHistoryId(rs.getLong("history_id"));
                    h.setApplicationId(rs.getLong("application_id"));
                    h.setAction(HistoryAction.valueOf(rs.getString("action")));
                    String old = rs.getString("old_status");
                    h.setOldStatus(old == null ? null : ApplicationStatus.valueOf(old));
                    h.setNewStatus(ApplicationStatus.valueOf(rs.getString("new_status")));
                    h.setPerformedBy(rs.getString("performed_by"));
                    h.setPerformedAt(Jdbc.dateTime(rs, "performed_at"));
                    h.setRemarks(rs.getString("remarks"));
                    h.setApplicationNumber(rs.getString("application_number"));
                    h.setCustomerName(rs.getString("customer_name"));
                    list.add(h);
                }
                return list;
            }
        }
    }

    private static String truncate(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }
}
