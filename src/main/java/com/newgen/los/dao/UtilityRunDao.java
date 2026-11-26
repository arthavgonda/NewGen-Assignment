package com.newgen.los.dao;

import com.newgen.los.model.UtilityRun;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class UtilityRunDao {

    public long start(Connection c, UtilityRun run) throws SQLException {
        String sql = "INSERT INTO utility_run (utility_name, trigger_type, business_date, started_at, result) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, run.getUtilityName());
            ps.setString(2, run.getTriggerType());
            Jdbc.setDate(ps, 3, run.getBusinessDate());
            Jdbc.setDateTime(ps, 4, run.getStartedAt());
            ps.setString(5, UtilityRun.RUNNING);
            ps.executeUpdate();
            return Jdbc.generatedKey(ps);
        }
    }

    public void finish(Connection c, UtilityRun run) throws SQLException {
        String sql = "UPDATE utility_run SET completed_at = ?, result = ?, applications_processed = ?, "
                + "alerts_created = ?, output_file = ?, error_message = ? WHERE run_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            Jdbc.setDateTime(ps, 1, run.getCompletedAt());
            ps.setString(2, run.getResult());
            ps.setInt(3, run.getApplicationsProcessed());
            ps.setInt(4, run.getAlertsCreated());
            Jdbc.setString(ps, 5, run.getOutputFile());
            String error = run.getErrorMessage();
            Jdbc.setString(ps, 6, error != null && error.length() > 1000 ? error.substring(0, 1000) : error);
            ps.setLong(7, run.getRunId());
            ps.executeUpdate();
        }
    }

    public List<UtilityRun> findRecent(Connection c, int limit) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM utility_run ORDER BY run_id DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<UtilityRun> list = new ArrayList<>();
                while (rs.next()) {
                    UtilityRun run = new UtilityRun();
                    run.setRunId(rs.getLong("run_id"));
                    run.setUtilityName(rs.getString("utility_name"));
                    run.setTriggerType(rs.getString("trigger_type"));
                    run.setBusinessDate(Jdbc.date(rs, "business_date"));
                    run.setStartedAt(Jdbc.dateTime(rs, "started_at"));
                    run.setCompletedAt(Jdbc.dateTime(rs, "completed_at"));
                    run.setResult(rs.getString("result"));
                    run.setApplicationsProcessed(rs.getInt("applications_processed"));
                    run.setAlertsCreated(rs.getInt("alerts_created"));
                    run.setOutputFile(rs.getString("output_file"));
                    run.setErrorMessage(rs.getString("error_message"));
                    list.add(run);
                }
                return list;
            }
        }
    }
}
