package com.newgen.los.console;

import com.newgen.los.AppContext;
import com.newgen.los.config.ProductRule;
import com.newgen.los.model.ApplicationStatus;
import com.newgen.los.model.Branch;
import com.newgen.los.model.Customer;
import com.newgen.los.model.Disbursement;
import com.newgen.los.model.EligibilityResult;
import com.newgen.los.model.HistoryEntry;
import com.newgen.los.model.LoanApplication;
import com.newgen.los.model.Product;
import com.newgen.los.model.SystemAlert;
import com.newgen.los.service.LoanApplicationService.SubmissionResult;
import com.newgen.los.service.LoanCalculator;
import com.newgen.los.util.DateUtil;
import com.newgen.los.util.MoneyUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class ApplicationMenu {

    private final AppContext ctx;
    private final ConsoleIO io;
    private final ConsoleApp.Session session;

    ApplicationMenu(AppContext ctx, ConsoleIO io, ConsoleApp.Session session) {
        this.ctx = ctx;
        this.io = io;
        this.session = session;
    }

    void show() {
        while (true) {
            io.title("Loan applications");
            io.line(" 1. Create draft application");
            io.line(" 2. Edit draft");
            io.line(" 3. Submit draft for eligibility check");
            io.line(" 4. View application details and history");
            io.line(" 5. List applications");
            io.line(" 6. EMI / FOIR calculator");
            io.line(" 0. Back");
            int choice = io.choice("Select option: ", 6);
            if (choice == 0) {
                return;
            }
            ConsoleApp.guarded(io, () -> {
                switch (choice) {
                    case 1 -> create();
                    case 2 -> edit();
                    case 3 -> submit();
                    case 4 -> view(io.text("Application ID or number", true, null));
                    case 5 -> list();
                    case 6 -> calculator();
                    default -> { }
                }
            });
        }
    }

    private void create() {
        io.title("New draft application");
        long customerId = io.integer("Customer ID", true, null);
        Customer customer = ctx.customers().get(customerId);
        io.line("  Customer: " + customer.getName() + ", mobile " + customer.getMobile());
        LoanApplication app = new LoanApplication();
        app.setCustomerId(customerId);
        fill(app);
        LoanApplication saved = ctx.applications().createDraft(app, session.getUserCode());
        io.success("Draft " + saved.getApplicationNumber() + " created (ID " + saved.getApplicationId() + ")");
    }

    private void edit() {
        LoanApplication app = ctx.applications().find(io.text("Application ID or number", true, null));
        if (app.getStatus() != ApplicationStatus.DRAFT) {
            io.error(app.getApplicationNumber() + " is " + app.getStatus() + ", only drafts can be edited");
            return;
        }
        io.line("Press Enter to keep the current value.");
        fill(app);
        ctx.applications().updateDraft(app.getApplicationId(), app, session.getUserCode());
        io.success("Draft " + app.getApplicationNumber() + " updated");
    }

    private void fill(LoanApplication app) {
        String product = io.text("Product (PERSONAL/EDUCATION)", true,
                app.getProduct() == null ? null : app.getProduct().name());
        app.setProduct(Product.from(product));
        ProductRule rule = ctx.ruleBook().ruleFor(app.getProduct());
        io.line("  " + rule.product() + ": amount " + MoneyUtil.format(rule.minAmount()) + " - "
                + MoneyUtil.format(rule.maxAmount()) + ", tenure " + rule.minTenure() + "-" + rule.maxTenure()
                + " months, min income " + MoneyUtil.format(rule.minIncome()) + ", rate " + rule.annualRate() + "%");

        List<Branch> branches = ctx.applications().branches();
        io.line("  Branches: " + branches.stream().map(Branch::toString).reduce((a, b) -> a + ", " + b).orElse(""));
        app.setBranchCode(io.text("Branch code", true, app.getBranchCode()));
        app.setRequestedAmount(io.amount("Requested amount (INR)", true, app.getRequestedAmount()));
        app.setTenureMonths(io.integer("Tenure (months)", true, app.getTenureMonths()));
        app.setMonthlyIncome(io.amount("Monthly net income (INR)", true, app.getMonthlyIncome()));
        app.setExistingObligations(io.amount("Existing monthly obligations (INR)", true,
                app.getExistingObligations() == null ? BigDecimal.ZERO : app.getExistingObligations()));
        app.setCoBorrowerName(io.text("Co-borrower name (optional, '-' for none)", false, app.getCoBorrowerName()));
    }

    private void submit() {
        LoanApplication app = ctx.applications().find(io.text("Application ID or number", true, null));
        if (app.getStatus() != ApplicationStatus.DRAFT) {
            io.error(app.getApplicationNumber() + " is " + app.getStatus() + ", only drafts can be submitted");
            return;
        }
        showSummary(app);
        if (!io.confirm("Submit " + app.getApplicationNumber() + " for eligibility check?")) {
            return;
        }
        SubmissionResult result = ctx.applications().submit(app.getApplicationId(), session.getUserCode());
        printAssessment(result.assessment());
        LoanApplication after = result.application();
        if (after.getStatus() == ApplicationStatus.UNDER_REVIEW) {
            io.success(after.getApplicationNumber() + " is eligible and moved to UNDER_REVIEW");
        } else {
            io.error(after.getApplicationNumber() + " is not eligible and was moved to " + after.getStatus());
        }
    }

    void printAssessment(EligibilityResult r) {
        io.title("Eligibility assessment");
        io.field("Interest rate", r.annualRate().toPlainString() + "% p.a.");
        io.field("Proposed EMI", MoneyUtil.inr(r.emi()));
        io.field("FOIR", r.foirPercentage().toPlainString() + "%");
        io.field("Eligible", r.eligible() ? "YES" : "NO");
        if (!r.eligible()) {
            io.line("  Failed rules:");
            r.failedRules().forEach(rule -> io.line("    - " + rule));
        }
    }

    void showSummary(LoanApplication a) {
        io.title("Application " + a.getApplicationNumber() + "  [" + a.getStatus() + "]");
        io.field("Customer", a.getCustomerName() + " (ID " + a.getCustomerId() + ")");
        io.field("Product", a.getProduct());
        io.field("Branch", a.getBranchCode() + " - " + a.getBranchName());
        io.field("Requested amount", MoneyUtil.inr(a.getRequestedAmount()));
        io.field("Tenure", a.getTenureMonths() + " months");
        io.field("Monthly net income", MoneyUtil.inr(a.getMonthlyIncome()));
        io.field("Existing obligations", MoneyUtil.inr(a.getExistingObligations()));
        io.field("Co-borrower", a.hasCoBorrower() ? a.getCoBorrowerName() : "None");
        if (a.getSubmittedAt() != null) {
            io.field("Interest rate", a.getAnnualInterestRate() + "%");
            io.field("Proposed EMI", MoneyUtil.inr(a.getProposedEmi()));
            io.field("FOIR", a.getFoirPercentage() + "%");
            io.field("Eligible", Boolean.TRUE.equals(a.getEligible()) ? "Yes" : "No");
            io.field("Assessment", a.getEligibilityReasons());
            io.field("Submitted", DateUtil.format(a.getSubmittedAt()));
        }
    }

    void view(String reference) {
        LoanApplication a = ctx.applications().find(reference);
        showSummary(a);
        io.field("Created by", a.getCreatedBy() + " on " + DateUtil.format(a.getCreatedAt()));
        if (a.getReviewerCode() != null) {
            io.field("Reviewer", a.getReviewerCode() + " on " + DateUtil.format(a.getDecidedAt()));
        }
        if (a.getCreditApproverCode() != null) {
            io.field("Credit approver", a.getCreditApproverCode() + " on " + DateUtil.format(a.getCreditDecidedAt()));
        }
        if (a.getDecisionReason() != null) {
            io.field(a.getStatus() == ApplicationStatus.REJECTED ? "Rejection reason" : "Remarks", a.getDecisionReason());
        }
        if (a.getApprovedAmount() != null) {
            io.field("Sanctioned amount", MoneyUtil.inr(a.getApprovedAmount()));
        }
        if (a.getApplicationFormPath() != null) {
            io.field("Application form", a.getApplicationFormPath());
        }
        Optional<Disbursement> d = ctx.disbursements().findForApplication(a.getApplicationId());
        d.ifPresent(x -> io.field("Disbursement", x.getReference() + ", " + MoneyUtil.inr(x.getAmount())
                + " on " + DateUtil.format(x.getDisbursedAt())));
        for (SystemAlert alert : ctx.operations().alertsFor(a.getApplicationId())) {
            io.field("Alert", alert.getAlertType() + " - " + alert.getMessage());
        }

        List<List<String>> rows = new ArrayList<>();
        for (HistoryEntry h : ctx.applications().history(a.getApplicationId())) {
            rows.add(List.of(DateUtil.format(h.getPerformedAt()), h.getAction().name(),
                    h.getOldStatus() == null ? "" : h.getOldStatus().name(), h.getNewStatus().name(),
                    h.getPerformedBy(), h.getRemarks() == null ? "" : h.getRemarks()));
        }
        io.line("");
        io.line("History");
        TablePrinter.print(io.out(), List.of("When", "Action", "From", "To", "By", "Remarks"), rows);
    }

    private void list() {
        String status = io.text("Status filter (blank for all)", false, null);
        String product = io.text("Product filter (blank for all)", false, null);
        List<LoanApplication> apps = ctx.applications().list(
                status == null ? null : ApplicationStatus.from(status),
                product == null ? null : Product.from(product));
        printApplications(apps, false);
    }

    void printApplications(List<LoanApplication> apps, boolean showWaiting) {
        List<List<String>> rows = new ArrayList<>();
        for (LoanApplication a : apps) {
            List<String> row = new ArrayList<>(List.of(String.valueOf(a.getApplicationId()), a.getApplicationNumber(),
                    a.getCustomerName(), a.getProduct().name(), a.getBranchCode(),
                    MoneyUtil.format(a.getRequestedAmount()), String.valueOf(a.getTenureMonths()),
                    a.getProposedEmi() == null ? "" : MoneyUtil.format(a.getProposedEmi()), a.getStatus().name()));
            if (showWaiting) {
                row.add(a.getSubmittedAt() == null ? "" : String.valueOf(
                        java.time.temporal.ChronoUnit.DAYS.between(a.getSubmittedAt().toLocalDate(), ctx.clock().today())));
            }
            rows.add(row);
        }
        List<String> headers = new ArrayList<>(List.of("ID", "Number", "Customer", "Product", "Branch", "Amount",
                "Tenure", "EMI", "Status"));
        if (showWaiting) {
            headers.add("Days");
        }
        TablePrinter.print(io.out(), headers, rows);
    }

    private void calculator() {
        io.title("EMI / FOIR calculator");
        Product product = Product.from(io.text("Product (PERSONAL/EDUCATION)", true, "PERSONAL"));
        ProductRule rule = ctx.ruleBook().ruleFor(product);
        BigDecimal amount = io.amount("Loan amount", true, null);
        int tenure = io.integer("Tenure (months)", true, null);
        BigDecimal income = io.amount("Monthly net income", true, null);
        BigDecimal obligations = io.amount("Existing obligations", true, BigDecimal.ZERO);
        if (tenure <= 0 || income.signum() <= 0) {
            io.error("Tenure and income must be positive");
            return;
        }
        BigDecimal emi = LoanCalculator.emi(amount, rule.annualRate(), tenure);
        BigDecimal foir = LoanCalculator.foir(obligations, emi, income);
        boolean ok = LoanCalculator.withinFoirLimit(obligations, emi, income, ctx.ruleBook().maxFoirPercent());
        io.field("Rate", rule.annualRate() + "% p.a.");
        io.field("EMI", MoneyUtil.inr(emi));
        io.field("FOIR", foir.toPlainString() + "% " + (ok ? "(within limit)" : "(above limit)"));
    }
}
