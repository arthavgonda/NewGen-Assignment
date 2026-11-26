package com.newgen.los.dao;

import com.newgen.los.model.AlertType;
import com.newgen.los.model.SystemAlert;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AlertDao {

    // the unique key on (application_id, alert_type) is what really stops duplicates
    public boolean insertIfAbsent(Connection c, long applicationId, AlertType type, String message,
                                  LocalDateTime at) throws SQLException {
        String sql = "INSERT INTO system_alert (application_id, alert_type, message, created_at) "
                + "SELECT ?, ?, ?, ? FROM DUAL WHERE NOT EXISTS "
                + "(SELECT 1 FROM system_alert WHERE application_id = ? AND alert_type = ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, applicationId);
            ps.setString(2, type.name());
            ps.setString(3, message);
            Jdbc.setDateTime(ps, 4, at);
            ps.setLong(5, applicationId);
            ps.setString(6, type.name());
            return ps.executeUpdate() == 1;
        }
    }

    public List<SystemAlert> findRecent(Connection c, int limit) throws SQLException {
        String sql = "SELECT s.*, a.application_number FROM system_alert s "
                + "JOIN loan_application a ON a.application_id = s.application_id "
                + "ORDER BY s.created_at DESC, s.alert_id DESC LIMIT ?";
        return query(c, sql, limit);
    }

    public List<SystemAlert> findByApplication(Connection c, long applicationId) throws SQLException {
        String sql = "SELECT s.*, a.application_number FROM system_alert s "
                + "JOIN loan_application a ON a.application_id = s.application_id "
                + "WHERE s.application_id = ? ORDER BY s.created_at";
        return query(c, sql, applicationId);
    }

    public int countAll(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM system_alert");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private List<SystemAlert> query(Connection c, String sql, Object param) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                List<SystemAlert> list = new ArrayList<>();
                while (rs.next()) {
                    SystemAlert alert = new SystemAlert();
                    alert.setAlertId(rs.getLong("alert_id"));
                    alert.setApplicationId(rs.getLong("application_id"));
                    alert.setApplicationNumber(rs.getString("application_number"));
                    alert.setAlertType(AlertType.valueOf(rs.getString("alert_type")));
                    alert.setMessage(rs.getString("message"));
                    alert.setCreatedAt(Jdbc.dateTime(rs, "created_at"));
                    list.add(alert);
                }
                return list;
            }
        }
    }
}
