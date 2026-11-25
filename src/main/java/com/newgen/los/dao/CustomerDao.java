package com.newgen.los.dao;

import com.newgen.los.model.Customer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CustomerDao {

    public long insert(Connection c, Customer customer) throws SQLException {
        String sql = "INSERT INTO customer (name, date_of_birth, mobile, email, address, kyc_type, kyc_number, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, customer.getName());
            Jdbc.setDate(ps, 2, customer.getDateOfBirth());
            ps.setString(3, customer.getMobile());
            Jdbc.setString(ps, 4, customer.getEmail());
            Jdbc.setString(ps, 5, customer.getAddress());
            Jdbc.setString(ps, 6, customer.getKycType());
            Jdbc.setString(ps, 7, customer.getKycNumber());
            Jdbc.setDateTime(ps, 8, customer.getCreatedAt());
            ps.executeUpdate();
            return Jdbc.generatedKey(ps);
        }
    }

    public int update(Connection c, Customer customer) throws SQLException {
        String sql = "UPDATE customer SET name = ?, date_of_birth = ?, mobile = ?, email = ?, address = ?, "
                + "kyc_type = ?, kyc_number = ?, updated_at = ? WHERE customer_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, customer.getName());
            Jdbc.setDate(ps, 2, customer.getDateOfBirth());
            ps.setString(3, customer.getMobile());
            Jdbc.setString(ps, 4, customer.getEmail());
            Jdbc.setString(ps, 5, customer.getAddress());
            Jdbc.setString(ps, 6, customer.getKycType());
            Jdbc.setString(ps, 7, customer.getKycNumber());
            Jdbc.setDateTime(ps, 8, customer.getUpdatedAt());
            ps.setLong(9, customer.getCustomerId());
            return ps.executeUpdate();
        }
    }

    public Optional<Customer> findById(Connection c, long id) throws SQLException {
        return findOne(c, "SELECT * FROM customer WHERE customer_id = ?", id);
    }

    public Optional<Customer> findByMobile(Connection c, String mobile) throws SQLException {
        return findOne(c, "SELECT * FROM customer WHERE mobile = ?", mobile);
    }

    public List<Customer> searchByName(Connection c, String name) throws SQLException {
        String sql = "SELECT * FROM customer WHERE LOWER(name) LIKE ? ORDER BY name LIMIT 100";
        return findMany(c, sql, "%" + name.trim().toLowerCase() + "%");
    }

    public List<Customer> findRecent(Connection c, int limit) throws SQLException {
        return findMany(c, "SELECT * FROM customer ORDER BY customer_id DESC LIMIT ?", limit);
    }

    private Optional<Customer> findOne(Connection c, String sql, Object param) throws SQLException {
        List<Customer> list = findMany(c, sql, param);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    private List<Customer> findMany(Connection c, String sql, Object param) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                List<Customer> customers = new ArrayList<>();
                while (rs.next()) {
                    customers.add(map(rs));
                }
                return customers;
            }
        }
    }

    private Customer map(ResultSet rs) throws SQLException {
        Customer customer = new Customer();
        customer.setCustomerId(rs.getLong("customer_id"));
        customer.setName(rs.getString("name"));
        customer.setDateOfBirth(Jdbc.date(rs, "date_of_birth"));
        customer.setMobile(rs.getString("mobile"));
        customer.setEmail(rs.getString("email"));
        customer.setAddress(rs.getString("address"));
        customer.setKycType(rs.getString("kyc_type"));
        customer.setKycNumber(rs.getString("kyc_number"));
        customer.setCreatedAt(Jdbc.dateTime(rs, "created_at"));
        customer.setUpdatedAt(Jdbc.dateTime(rs, "updated_at"));
        return customer;
    }
}
