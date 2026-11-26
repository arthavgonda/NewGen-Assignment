package com.newgen.los.service;

import com.newgen.los.config.BusinessClock;
import com.newgen.los.dao.CustomerDao;
import com.newgen.los.dao.Database;
import com.newgen.los.dao.DuplicateKeyException;
import com.newgen.los.dao.HistoryDao;
import com.newgen.los.dao.LoanApplicationDao;
import com.newgen.los.exception.BusinessRuleException;
import com.newgen.los.exception.InvalidTransitionException;
import com.newgen.los.exception.NotFoundException;
import com.newgen.los.exception.ValidationException;
import com.newgen.los.model.ApplicationStatus;
import com.newgen.los.model.Branch;
import com.newgen.los.model.Customer;
import com.newgen.los.model.EligibilityResult;
import com.newgen.los.model.HistoryAction;
import com.newgen.los.model.HistoryEntry;
import com.newgen.los.model.LoanApplication;
import com.newgen.los.model.Product;
import com.newgen.los.util.FailureSimulator;
import com.newgen.los.util.MoneyUtil;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class LoanApplicationService {

    private final Database db;
    private final LoanApplicationDao appDao;
    private final CustomerDao customerDao;
    private final HistoryDao historyDao;
    private final EligibilityAssessor assessor;
    private final BusinessClock clock;
    private final List<Branch> branches;

    public LoanApplicationService(Database db, LoanApplicationDao appDao, CustomerDao customerDao, HistoryDao historyDao,
                                  EligibilityAssessor assessor, BusinessClock clock, List<Branch> branches) {
        this.db = db;
        this.appDao = appDao;
        this.customerDao = customerDao;
        this.historyDao = historyDao;
        this.assessor = assessor;
        this.clock = clock;
        this.branches = List.copyOf(branches);
    }

    public List<Branch> branches() {
        return branches;
    }

    public LoanApplication createDraft(LoanApplication input, String userCode) {
        String user = UserCodes.clean(userCode);
        validateInputs(input);
        applyBranch(input);
        LocalDateTime now = clock.now();
        input.setStatus(ApplicationStatus.DRAFT);
        input.setCreatedBy(user);
        input.setCreatedAt(now);
        input.setApplicationNumber("TMP-" + System.nanoTime());
        try {
            long id = db.inTransaction(c -> {
                Customer customer = customerDao.findById(c, input.getCustomerId())
                        .orElseThrow(() -> new NotFoundException("Customer " + input.getCustomerId() + " not found"));
                ensureNoOtherActive(c, customer.getCustomerId(), input.getProduct(), null);
                long newId = appDao.insert(c, input);
                String number = applicationNumber(newId);
                appDao.updateNumber(c, newId, number);
                historyDao.insert(c, new HistoryEntry(newId, HistoryAction.CREATED, null, ApplicationStatus.DRAFT,
                        user, now, input.getProduct() + " application for " + MoneyUtil.inr(input.getRequestedAmount())));
                return newId;
            });
            return get(id);
        } catch (DuplicateKeyException e) {
            if (e.isFor("uk_app_active_product")) {
                throw activeExists(input.getProduct());
            }
            throw e;
        }
    }

    public LoanApplication updateDraft(long id, LoanApplication changes, String userCode) {
        String user = UserCodes.clean(userCode);
        validateInputs(changes);
        applyBranch(changes);
        try {
            db.inTransaction(c -> {
                LoanApplication current = lockForUpdate(c, id);
                if (current.getStatus() != ApplicationStatus.DRAFT) {
                    throw new InvalidTransitionException("Only DRAFT applications can be edited. "
                            + current.getApplicationNumber() + " is " + current.getStatus());
                }
                if (changes.getProduct() != current.getProduct()) {
                    ensureNoOtherActive(c, current.getCustomerId(), changes.getProduct(), id);
                }
                String diff = describeChanges(current, changes);
                changes.setApplicationId(id);
                changes.setUpdatedAt(clock.now());
                appDao.updateDraft(c, changes);
                historyDao.insert(c, new HistoryEntry(id, HistoryAction.UPDATED, ApplicationStatus.DRAFT,
                        ApplicationStatus.DRAFT, user, changes.getUpdatedAt(), diff.isEmpty() ? "No changes" : diff));
                return null;
            });
        } catch (DuplicateKeyException e) {
            if (e.isFor("uk_app_active_product")) {
                throw activeExists(changes.getProduct());
            }
            throw e;
        }
        return get(id);
    }

    // ineligible cases still pass through UNDER_REVIEW so the history shows DRAFT -> UNDER_REVIEW -> REJECTED
    public SubmissionResult submit(long id, String userCode) {
        String user = UserCodes.clean(userCode);
        EligibilityResult result = db.inTransaction(c -> {
            LoanApplication app = lockForUpdate(c, id);
            if (app.getStatus() != ApplicationStatus.DRAFT) {
                throw new InvalidTransitionException("Only DRAFT applications can be submitted. "
                        + app.getApplicationNumber() + " is " + app.getStatus());
            }
            WorkflowRules.ensure(app.getStatus(), ApplicationStatus.UNDER_REVIEW);
            validateInputs(app);
            Customer customer = customerDao.findById(c, app.getCustomerId())
                    .orElseThrow(() -> new NotFoundException("Customer " + app.getCustomerId() + " not found"));

            LocalDateTime now = clock.now();
            EligibilityResult assessment = assessor.assess(customer, app, now.toLocalDate());
            app.setAnnualInterestRate(assessment.annualRate());
            app.setProposedEmi(assessment.emi());
            app.setFoirPercentage(assessment.foirPercentage());
            app.setEligible(assessment.eligible());
            app.setEligibilityReasons(assessment.reasonsText());
            app.setSubmittedAt(now);
            app.setStatus(ApplicationStatus.UNDER_REVIEW);
            appDao.saveAssessment(c, app);
            historyDao.insert(c, new HistoryEntry(id, HistoryAction.SUBMITTED, ApplicationStatus.DRAFT,
                    ApplicationStatus.UNDER_REVIEW, user, now, "EMI " + MoneyUtil.format(assessment.emi())
                    + ", FOIR " + assessment.foirPercentage().toPlainString() + "%"));

            if (!assessment.eligible()) {
                WorkflowRules.ensure(ApplicationStatus.UNDER_REVIEW, ApplicationStatus.REJECTED);
                appDao.recordReviewDecision(c, id, ApplicationStatus.UNDER_REVIEW, ApplicationStatus.REJECTED,
                        UserCodes.SYSTEM, truncate(assessment.reasonsText(), 500), now);
                historyDao.insert(c, new HistoryEntry(id, HistoryAction.AUTO_REJECTED, ApplicationStatus.UNDER_REVIEW,
                        ApplicationStatus.REJECTED, UserCodes.SYSTEM, now, assessment.reasonsText()));
            }
            FailureSimulator.check(FailureSimulator.SUBMISSION);
            return assessment;
        });
        return new SubmissionResult(get(id), result);
    }

    public LoanApplication get(long id) {
        return db.withConnection(c -> appDao.findById(c, id))
                .orElseThrow(() -> new NotFoundException("Application " + id + " not found"));
    }

    // accepts the numeric id or the LA... number
    public LoanApplication find(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new ValidationException("Application id or number is required");
        }
        String ref = reference.trim();
        if (ref.matches("\\d+")) {
            return get(Long.parseLong(ref));
        }
        return db.withConnection(c -> appDao.findByNumber(c, ref))
                .orElseThrow(() -> new NotFoundException("Application " + ref + " not found"));
    }

    public List<HistoryEntry> history(long id) {
        return db.withConnection(c -> historyDao.findByApplication(c, id));
    }

    public List<HistoryEntry> recentActivity(int limit) {
        return db.withConnection(c -> historyDao.findRecent(c, limit));
    }

    public List<LoanApplication> list(ApplicationStatus status, Product product) {
        return db.withConnection(c -> appDao.search(c, status, product, 500));
    }

    public List<LoanApplication> byStatus(ApplicationStatus status) {
        return db.withConnection(c -> appDao.findByStatus(c, status));
    }

    public List<LoanApplication> forCustomer(long customerId) {
        return db.withConnection(c -> appDao.findByCustomer(c, customerId));
    }

    public Map<ApplicationStatus, Integer> statusCounts() {
        return db.withConnection(appDao::countByStatus);
    }

    private LoanApplication lockForUpdate(Connection c, long id) throws SQLException {
        return appDao.findByIdForUpdate(c, id)
                .orElseThrow(() -> new NotFoundException("Application " + id + " not found"));
    }

    private void ensureNoOtherActive(Connection c, long customerId, Product product, Long ignoreId) throws SQLException {
        Optional<LoanApplication> active = appDao.findActive(c, customerId, product);
        if (active.isPresent() && !active.get().getApplicationId().equals(ignoreId)) {
            throw new BusinessRuleException("Customer already has an active " + product + " application "
                    + active.get().getApplicationNumber() + " (" + active.get().getStatus() + ")");
        }
    }

    private static BusinessRuleException activeExists(Product product) {
        return new BusinessRuleException("Customer already has an active " + product + " application");
    }

    private String applicationNumber(long id) {
        return String.format("LA%d%06d", clock.today().getYear(), id);
    }

    private void applyBranch(LoanApplication app) {
        String code = app.getBranchCode() == null ? "" : app.getBranchCode().trim().toUpperCase();
        Branch branch = branches.stream().filter(b -> b.code().equalsIgnoreCase(code)).findFirst()
                .orElseThrow(() -> new ValidationException("Unknown branch code " + code));
        app.setBranchCode(branch.code());
        app.setBranchName(branch.name());
    }

    static void validateInputs(LoanApplication app) {
        List<String> errors = new ArrayList<>();
        if (app.getCustomerId() == null) {
            errors.add("Customer is required");
        }
        if (app.getProduct() == null) {
            errors.add("Product is required");
        }
        if (app.getBranchCode() == null || app.getBranchCode().isBlank()) {
            errors.add("Branch is required");
        }
        if (app.getRequestedAmount() == null) {
            errors.add("Requested amount is required");
        } else if (app.getRequestedAmount().signum() <= 0) {
            errors.add("Requested amount must be positive");
        } else if (app.getRequestedAmount().scale() > 2) {
            errors.add("Requested amount can have at most 2 decimals");
        }
        if (app.getTenureMonths() == null) {
            errors.add("Tenure is required");
        } else if (app.getTenureMonths() <= 0) {
            errors.add("Tenure must be a positive whole number of months");
        }
        if (app.getMonthlyIncome() == null) {
            errors.add("Monthly net income is required");
        } else if (app.getMonthlyIncome().signum() <= 0) {
            errors.add("Monthly net income must be positive");
        }
        if (app.getExistingObligations() == null) {
            app.setExistingObligations(BigDecimal.ZERO);
        } else if (app.getExistingObligations().signum() < 0) {
            errors.add("Existing monthly obligations cannot be negative");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        app.setRequestedAmount(MoneyUtil.scale(app.getRequestedAmount()));
        app.setMonthlyIncome(MoneyUtil.scale(app.getMonthlyIncome()));
        app.setExistingObligations(MoneyUtil.scale(app.getExistingObligations()));
        if (app.getCoBorrowerName() != null && app.getCoBorrowerName().isBlank()) {
            app.setCoBorrowerName(null);
        }
    }

    private static String describeChanges(LoanApplication before, LoanApplication after) {
        List<String> parts = new ArrayList<>();
        if (before.getProduct() != after.getProduct()) {
            parts.add("product " + before.getProduct() + " -> " + after.getProduct());
        }
        if (!Objects.equals(before.getBranchCode(), after.getBranchCode())) {
            parts.add("branch " + before.getBranchCode() + " -> " + after.getBranchCode());
        }
        if (before.getRequestedAmount().compareTo(after.getRequestedAmount()) != 0) {
            parts.add("amount " + MoneyUtil.format(before.getRequestedAmount()) + " -> "
                    + MoneyUtil.format(after.getRequestedAmount()));
        }
        if (!Objects.equals(before.getTenureMonths(), after.getTenureMonths())) {
            parts.add("tenure " + before.getTenureMonths() + " -> " + after.getTenureMonths());
        }
        if (before.getMonthlyIncome().compareTo(after.getMonthlyIncome()) != 0) {
            parts.add("income " + MoneyUtil.format(before.getMonthlyIncome()) + " -> "
                    + MoneyUtil.format(after.getMonthlyIncome()));
        }
        if (before.getExistingObligations().compareTo(after.getExistingObligations()) != 0) {
            parts.add("obligations " + MoneyUtil.format(before.getExistingObligations()) + " -> "
                    + MoneyUtil.format(after.getExistingObligations()));
        }
        if (!Objects.equals(before.getCoBorrowerName(), after.getCoBorrowerName())) {
            parts.add("co-borrower " + (after.getCoBorrowerName() == null ? "removed" : after.getCoBorrowerName()));
        }
        return String.join(", ", parts);
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }

    public record SubmissionResult(LoanApplication application, EligibilityResult assessment) {
    }
}
