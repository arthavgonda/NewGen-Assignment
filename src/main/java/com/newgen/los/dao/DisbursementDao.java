package com.newgen.los.dao;

import com.newgen.los.model.Disbursement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

public class DisbursementDao {

    public long insert(Connection c, Disbursement d) throws SQLException {
        String sql = "INSERT INTO disbursement (application_id, reference, amount, disbursed_at, disbursed_by) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, d.getApplicationId());
            ps.setString(2, d.getReference());
            ps.setBigDecimal(3, d.getAmount());
            Jdbc.setDateTime(ps, 4, d.getDisbursedAt());
            ps.setString(5, d.getDisbursedBy());
            ps.executeUpdate();
            return Jdbc.generatedKey(ps);
        }
    }

    public Optional<Disbursement> findByApplication(Connection c, long applicationId) throws SQLException {
        return findOne(c, "SELECT * FROM disbursement WHERE application_id = ?", applicationId);
    }

    public Optional<Disbursement> findByReference(Connection c, String reference) throws SQLException {
        return findOne(c, "SELECT * FROM disbursement WHERE reference = ?", reference);
    }

    private Optional<Disbursement> findOne(Connection c, String sql, Object param) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Disbursement d = new Disbursement();
                d.setDisbursementId(rs.getLong("disbursement_id"));
                d.setApplicationId(rs.getLong("application_id"));
                d.setReference(rs.getString("reference"));
                d.setAmount(rs.getBigDecimal("amount"));
                d.setDisbursedAt(Jdbc.dateTime(rs, "disbursed_at"));
                d.setDisbursedBy(rs.getString("disbursed_by"));
                return Optional.of(d);
            }
        }
    }
}
