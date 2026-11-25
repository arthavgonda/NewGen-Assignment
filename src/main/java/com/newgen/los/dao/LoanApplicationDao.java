package com.newgen.los.dao;

import com.newgen.los.model.ApplicationStatus;
import com.newgen.los.model.CoreBankingRecord;
import com.newgen.los.model.LoanApplication;
import com.newgen.los.model.Product;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class LoanApplicationDao {

    private static final String SELECT = "SELECT a.*, c.name AS customer_name FROM loan_application a "
            + "JOIN customer c ON c.customer_id = a.customer_id ";

    public long insert(Connection c, LoanApplication app) throws SQLException {
        String sql = "INSERT INTO loan_application (application_number, customer_id, product, branch_code, branch_name, "
                + "requested_amount, tenure_months, monthly_income, existing_monthly_obligations, co_borrower_name, "
                + "status, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, app.getApplicationNumber());
            ps.setLong(2, app.getCustomerId());
            ps.setString(3, app.getProduct().name());
            ps.setString(4, app.getBranchCode());
            ps.setString(5, app.getBranchName());
            ps.setBigDecimal(6, app.getRequestedAmount());
            ps.setInt(7, app.getTenureMonths());
            ps.setBigDecimal(8, app.getMonthlyIncome());
            ps.setBigDecimal(9, app.getExistingObligations());
            Jdbc.setString(ps, 10, app.getCoBorrowerName());
            ps.setString(11, app.getStatus().name());
            ps.setString(12, app.getCreatedBy());
            Jdbc.setDateTime(ps, 13, app.getCreatedAt());
            ps.executeUpdate();
            return Jdbc.generatedKey(ps);
        }
    }

    public void updateNumber(Connection c, long id, String number) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE loan_application SET application_number = ? WHERE application_id = ?")) {
            ps.setString(1, number);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public int updateDraft(Connection c, LoanApplication app) throws SQLException {
        String sql = "UPDATE loan_application SET product = ?, branch_code = ?, branch_name = ?, requested_amount = ?, "
                + "tenure_months = ?, monthly_income = ?, existing_monthly_obligations = ?, co_borrower_name = ?, "
                + "updated_at = ? WHERE application_id = ? AND status = 'DRAFT'";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, app.getProduct().name());
            ps.setString(2, app.getBranchCode());
            ps.setString(3, app.getBranchName());
            ps.setBigDecimal(4, app.getRequestedAmount());
            ps.setInt(5, app.getTenureMonths());
            ps.setBigDecimal(6, app.getMonthlyIncome());
            ps.setBigDecimal(7, app.getExistingObligations());
            Jdbc.setString(ps, 8, app.getCoBorrowerName());
            Jdbc.setDateTime(ps, 9, app.getUpdatedAt());
            ps.setLong(10, app.getApplicationId());
            return ps.executeUpdate();
        }
    }

    public int saveAssessment(Connection c, LoanApplication app) throws SQLException {
        String sql = "UPDATE loan_application SET annual_interest_rate = ?, proposed_emi = ?, foir_percentage = ?, "
                + "eligible = ?, eligibility_reasons = ?, submitted_at = ?, status = ?, updated_at = ? "
                + "WHERE application_id = ? AND status = 'DRAFT'";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            Jdbc.setDecimal(ps, 1, app.getAnnualInterestRate());
            Jdbc.setDecimal(ps, 2, app.getProposedEmi());
            Jdbc.setDecimal(ps, 3, app.getFoirPercentage());
            Jdbc.setBoolean(ps, 4, app.getEligible());
            Jdbc.setString(ps, 5, app.getEligibilityReasons());
            Jdbc.setDateTime(ps, 6, app.getSubmittedAt());
            ps.setString(7, app.getStatus().name());
            Jdbc.setDateTime(ps, 8, app.getSubmittedAt());
            ps.setLong(9, app.getApplicationId());
            return ps.executeUpdate();
        }
    }

    public int recordReviewDecision(Connection c, long id, ApplicationStatus from, ApplicationStatus to,
                                    String reviewer, String reason, LocalDateTime at) throws SQLException {
        String sql = "UPDATE loan_application SET status = ?, reviewer_code = ?, decision_reason = ?, decided_at = ?, "
                + "updated_at = ? WHERE application_id = ? AND status = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, to.name());
            ps.setString(2, reviewer);
            Jdbc.setString(ps, 3, reason);
            Jdbc.setDateTime(ps, 4, at);
            Jdbc.setDateTime(ps, 5, at);
            ps.setLong(6, id);
            ps.setString(7, from.name());
            return ps.executeUpdate();
        }
    }

    public int recordCreditDecision(Connection c, long id, ApplicationStatus to, String approver, String reason,
                                    BigDecimal approvedAmount, LocalDateTime at) throws SQLException {
        String sql = "UPDATE loan_application SET status = ?, credit_approver_code = ?, credit_decided_at = ?, "
                + "decision_reason = COALESCE(?, decision_reason), approved_amount = ?, updated_at = ? "
                + "WHERE application_id = ? AND status = 'CREDIT_APPROVAL'";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, to.name());
            ps.setString(2, approver);
            Jdbc.setDateTime(ps, 3, at);
            Jdbc.setString(ps, 4, reason);
            Jdbc.setDecimal(ps, 5, approvedAmount);
            Jdbc.setDateTime(ps, 6, at);
            ps.setLong(7, id);
            return ps.executeUpdate();
        }
    }

    public int changeStatus(Connection c, long id, ApplicationStatus from, ApplicationStatus to,
                            LocalDateTime at) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE loan_application SET status = ?, updated_at = ? WHERE application_id = ? AND status = ?")) {
            ps.setString(1, to.name());
            Jdbc.setDateTime(ps, 2, at);
            ps.setLong(3, id);
            ps.setString(4, from.name());
            return ps.executeUpdate();
        }
    }

    public void updateFormPath(Connection c, long id, String path) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE loan_application SET application_form_path = ? WHERE application_id = ?")) {
            ps.setString(1, path);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public Optional<LoanApplication> findById(Connection c, long id) throws SQLException {
        return first(query(c, SELECT + "WHERE a.application_id = ?", id));
    }

    public Optional<LoanApplication> findByIdForUpdate(Connection c, long id) throws SQLException {
        return first(query(c, SELECT + "WHERE a.application_id = ? FOR UPDATE OF a", id));
    }

    public Optional<LoanApplication> findByNumber(Connection c, String number) throws SQLException {
        return first(query(c, SELECT + "WHERE a.application_number = ?", number.trim().toUpperCase()));
    }

    public Optional<LoanApplication> findActive(Connection c, long customerId, Product product) throws SQLException {
        return first(query(c, SELECT + "WHERE a.customer_id = ? AND a.product = ? "
                + "AND a.status IN ('DRAFT', 'UNDER_REVIEW', 'CREDIT_APPROVAL', 'APPROVED')", customerId, product.name()));
    }

    public List<LoanApplication> findByCustomer(Connection c, long customerId) throws SQLException {
        return query(c, SELECT + "WHERE a.customer_id = ? ORDER BY a.application_id DESC", customerId);
    }

    public List<LoanApplication> findByStatus(Connection c, ApplicationStatus status) throws SQLException {
        return query(c, SELECT + "WHERE a.status = ? ORDER BY a.submitted_at, a.application_id", status.name());
    }

    public List<LoanApplication> search(Connection c, ApplicationStatus status, Product product, int limit)
            throws SQLException {
        String sql = SELECT + "WHERE (? IS NULL OR a.status = ?) AND (? IS NULL OR a.product = ?) "
                + "ORDER BY a.application_id DESC LIMIT ?";
        String s = status == null ? null : status.name();
        String p = product == null ? null : product.name();
        return query(c, sql, s, s, p, p, limit);
    }

    public Map<ApplicationStatus, Integer> countByStatus(Connection c) throws SQLException {
        Map<ApplicationStatus, Integer> counts = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus status : ApplicationStatus.values()) {
            counts.put(status, 0);
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT status, COUNT(*) AS cnt FROM loan_application GROUP BY status");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                counts.put(ApplicationStatus.valueOf(rs.getString("status")), rs.getInt("cnt"));
            }
        }
        return counts;
    }

    public List<CoreBankingRecord> findPendingCorePush(Connection c) throws SQLException {
        String sql = "SELECT a.application_id, a.application_number, a.customer_id, a.approved_amount, "
                + "c.name, c.address, c.date_of_birth, c.kyc_type, c.kyc_number, d.amount, d.disbursed_at "
                + "FROM loan_application a "
                + "JOIN customer c ON c.customer_id = a.customer_id "
                + "JOIN disbursement d ON d.application_id = a.application_id "
                + "WHERE a.status = 'DISBURSED' ORDER BY a.application_id";
        List<CoreBankingRecord> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                CoreBankingRecord r = new CoreBankingRecord();
                r.setApplicationId(rs.getLong("application_id"));
                r.setApplicationNumber(rs.getString("application_number"));
                r.setCustomerId(rs.getLong("customer_id"));
                r.setCustomerName(rs.getString("name"));
                r.setAddress(rs.getString("address"));
                r.setDateOfBirth(Jdbc.date(rs, "date_of_birth"));
                String kycType = rs.getString("kyc_type");
                String kycNumber = rs.getString("kyc_number");
                if (kycType != null || kycNumber != null) {
                    r.setKycDetails((kycType == null ? "" : kycType + ":") + (kycNumber == null ? "" : kycNumber));
                }
                r.setApprovedAmount(rs.getBigDecimal("approved_amount"));
                r.setDisbursedAmount(rs.getBigDecimal("amount"));
                r.setDisbursedDate(Jdbc.dateTime(rs, "disbursed_at").toLocalDate());
                list.add(r);
            }
        }
        return list;
    }

    private static Optional<LoanApplication> first(List<LoanApplication> list) {
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    private List<LoanApplication> query(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<LoanApplication> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        }
    }

    private LoanApplication map(ResultSet rs) throws SQLException {
        LoanApplication a = new LoanApplication();
        a.setApplicationId(rs.getLong("application_id"));
        a.setApplicationNumber(rs.getString("application_number"));
        a.setCustomerId(rs.getLong("customer_id"));
        a.setCustomerName(rs.getString("customer_name"));
        a.setProduct(Product.valueOf(rs.getString("product")));
        a.setBranchCode(rs.getString("branch_code"));
        a.setBranchName(rs.getString("branch_name"));
        a.setRequestedAmount(rs.getBigDecimal("requested_amount"));
        a.setTenureMonths(Jdbc.integer(rs, "tenure_months"));
        a.setMonthlyIncome(rs.getBigDecimal("monthly_income"));
        a.setExistingObligations(rs.getBigDecimal("existing_monthly_obligations"));
        a.setCoBorrowerName(rs.getString("co_borrower_name"));
        a.setAnnualInterestRate(rs.getBigDecimal("annual_interest_rate"));
        a.setProposedEmi(rs.getBigDecimal("proposed_emi"));
        a.setFoirPercentage(rs.getBigDecimal("foir_percentage"));
        a.setEligible(Jdbc.bool(rs, "eligible"));
        a.setEligibilityReasons(rs.getString("eligibility_reasons"));
        a.setStatus(ApplicationStatus.valueOf(rs.getString("status")));
        a.setApprovedAmount(rs.getBigDecimal("approved_amount"));
        a.setApplicationFormPath(rs.getString("application_form_path"));
        a.setCreatedBy(rs.getString("created_by"));
        a.setCreatedAt(Jdbc.dateTime(rs, "created_at"));
        a.setUpdatedAt(Jdbc.dateTime(rs, "updated_at"));
        a.setSubmittedAt(Jdbc.dateTime(rs, "submitted_at"));
        a.setDecidedAt(Jdbc.dateTime(rs, "decided_at"));
        a.setReviewerCode(rs.getString("reviewer_code"));
        a.setDecisionReason(rs.getString("decision_reason"));
        a.setCreditDecidedAt(Jdbc.dateTime(rs, "credit_decided_at"));
        a.setCreditApproverCode(rs.getString("credit_approver_code"));
        return a;
    }
}
