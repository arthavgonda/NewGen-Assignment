package com.newgen.los.dao;

import com.newgen.los.model.CoreBankingRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class CoreBankingDao {

    public long insert(Connection c, CoreBankingRecord r) throws SQLException {
        String sql = "INSERT INTO NG_CORE_BANKING (customer_id, application_id, customer_name, address, date_of_birth, "
                + "kyc_details, approved_amount, disbursed_amount, disbursed_date, pushed_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, r.getCustomerId());
            ps.setLong(2, r.getApplicationId());
            ps.setString(3, r.getCustomerName());
            Jdbc.setString(ps, 4, r.getAddress());
            Jdbc.setDate(ps, 5, r.getDateOfBirth());
            Jdbc.setString(ps, 6, r.getKycDetails());
            ps.setBigDecimal(7, r.getApprovedAmount());
            ps.setBigDecimal(8, r.getDisbursedAmount());
            Jdbc.setDate(ps, 9, r.getDisbursedDate());
            Jdbc.setDateTime(ps, 10, r.getPushedAt());
            ps.executeUpdate();
            return Jdbc.generatedKey(ps);
        }
    }

    public List<CoreBankingRecord> findRecent(Connection c, int limit) throws SQLException {
        String sql = "SELECT k.*, a.application_number FROM NG_CORE_BANKING k "
                + "JOIN loan_application a ON a.application_id = k.application_id ORDER BY k.core_id DESC LIMIT ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<CoreBankingRecord> list = new ArrayList<>();
                while (rs.next()) {
                    CoreBankingRecord r = new CoreBankingRecord();
                    r.setCoreId(rs.getLong("core_id"));
                    r.setCustomerId(rs.getLong("customer_id"));
                    r.setApplicationId(rs.getLong("application_id"));
                    r.setApplicationNumber(rs.getString("application_number"));
                    r.setCustomerName(rs.getString("customer_name"));
                    r.setAddress(rs.getString("address"));
                    r.setDateOfBirth(Jdbc.date(rs, "date_of_birth"));
                    r.setKycDetails(rs.getString("kyc_details"));
                    r.setApprovedAmount(rs.getBigDecimal("approved_amount"));
                    r.setDisbursedAmount(rs.getBigDecimal("disbursed_amount"));
                    r.setDisbursedDate(Jdbc.date(rs, "disbursed_date"));
                    r.setPushedAt(Jdbc.dateTime(rs, "pushed_at"));
                    list.add(r);
                }
                return list;
            }
        }
    }

    public int countForApplication(Connection c, long applicationId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM NG_CORE_BANKING WHERE application_id = ?")) {
            ps.setLong(1, applicationId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
