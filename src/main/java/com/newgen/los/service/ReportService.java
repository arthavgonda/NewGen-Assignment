package com.newgen.los.service;

import com.newgen.los.config.BusinessClock;
import com.newgen.los.dao.Database;
import com.newgen.los.dao.ReportDao;
import com.newgen.los.exception.ValidationException;
import com.newgen.los.model.ApplicationStatus;
import com.newgen.los.model.Product;
import com.newgen.los.model.ReportTable;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReportService {

    public static final Set<String> PERIODS = Set.of("DAILY", "MONTHLY", "QUARTERLY");

    private final Database db;
    private final ReportDao reportDao;
    private final BusinessClock clock;
    private final int overdueDays;

    public ReportService(Database db, ReportDao reportDao, BusinessClock clock, int overdueDays) {
        this.db = db;
        this.reportDao = reportDao;
        this.clock = clock;
        this.overdueDays = overdueDays;
    }

    public ReportTable applicationRegister(Product product, ApplicationStatus status) {
        Map<String, Object> params = new HashMap<>();
        params.put("product", product == null ? null : product.name());
        params.put("status", status == null ? null : status.name());
        return run("application_register", "1. Application register", params);
    }

    public List<ReportTable> productPipeline() {
        return List.of(
                run("pipeline_by_product_status", "2a. Product pipeline by status", Map.of()),
                run("pipeline_by_branch_product", "2b. Pipeline by branch and product", Map.of()));
    }

    public List<ReportTable> disbursements(LocalDate from, LocalDate to, String period) {
        if (from == null || to == null) {
            throw new ValidationException("From and to dates are required");
        }
        if (to.isBefore(from)) {
            throw new ValidationException("To date cannot be before from date");
        }
        String p = period == null ? "DAILY" : period.trim().toUpperCase();
        if (!PERIODS.contains(p)) {
            throw new ValidationException("Period must be DAILY, MONTHLY or QUARTERLY");
        }
        Map<String, Object> params = Map.of("from_date", from, "to_date", to, "period", p);
        String label = p.charAt(0) + p.substring(1).toLowerCase();
        return List.of(
                run("disbursement_summary", "3a. " + label + " disbursements", params),
                run("disbursement_by_branch", "3b. " + label + " disbursements by branch", params));
    }

    public ReportTable overdueReviews() {
        Map<String, Object> params = Map.of("business_date", clock.today(), "overdue_days", overdueDays);
        return run("overdue_reviews", "4. Overdue reviews (more than " + overdueDays + " days)", params);
    }

    public ReportTable customersWithoutDisbursement() {
        return run("customers_without_disbursement", "5. Customers without disbursement", Map.of());
    }

    public ReportTable repeatApplicants() {
        return run("repeat_applicants", "6. Repeat applicants", Map.of());
    }

    private ReportTable run(String query, String title, Map<String, Object> params) {
        return db.withConnection(c -> reportDao.run(c, query, title, params));
    }
}
