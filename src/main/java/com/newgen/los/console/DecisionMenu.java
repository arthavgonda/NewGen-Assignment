package com.newgen.los.console;

import com.newgen.los.AppContext;
import com.newgen.los.model.ApplicationStatus;
import com.newgen.los.model.Decision;
import com.newgen.los.model.LoanApplication;
import com.newgen.los.service.DisbursementService.Confirmation;
import com.newgen.los.util.DateUtil;
import com.newgen.los.util.MoneyUtil;

import java.nio.file.Path;
import java.util.List;

class DecisionMenu {

    private final AppContext ctx;
    private final ConsoleIO io;
    private final ConsoleApp.Session session;
    private final ApplicationMenu applications;

    DecisionMenu(AppContext ctx, ConsoleIO io, ConsoleApp.Session session, ApplicationMenu applications) {
        this.ctx = ctx;
        this.io = io;
        this.session = session;
        this.applications = applications;
    }

    void reviewQueue() {
        while (true) {
            io.title("Review queue");
            List<LoanApplication> queue = ctx.applications().byStatus(ApplicationStatus.UNDER_REVIEW);
            applications.printApplications(queue, true);
            io.line(" 1. Approve / reject an application");
            io.line(" 0. Back");
            if (io.choice("Select option: ", 1) == 0) {
                return;
            }
            ConsoleApp.guarded(io, () -> decide(true));
        }
    }

    void creditQueue() {
        while (true) {
            io.title("Credit approval queue");
            List<LoanApplication> queue = ctx.applications().byStatus(ApplicationStatus.CREDIT_APPROVAL);
            applications.printApplications(queue, false);
            io.line(" 1. Approve / reject an application");
            io.line(" 2. Regenerate application form");
            io.line(" 0. Back");
            int choice = io.choice("Select option: ", 2);
            if (choice == 0) {
                return;
            }
            ConsoleApp.guarded(io, () -> {
                if (choice == 1) {
                    decide(false);
                } else {
                    LoanApplication app = ctx.applications().find(io.text("Application ID or number", true, null));
                    Path form = ctx.decisions().generateForm(app.getApplicationId());
                    io.success("Application form written to " + form.toAbsolutePath());
                }
            });
        }
    }

    private void decide(boolean review) {
        LoanApplication app = ctx.applications().find(io.text("Application ID or number", true, null));
        applications.showSummary(app);
        String input = io.text("Decision (A = approve, R = reject)", true, null);
        Decision decision = Decision.from(input);
        String reason = io.text(decision == Decision.REJECT ? "Rejection reason" : "Remarks (optional)",
                decision == Decision.REJECT, null);
        LoanApplication after = review
                ? ctx.decisions().review(app.getApplicationId(), decision, reason, session.getUserCode())
                : ctx.decisions().creditDecision(app.getApplicationId(), decision, reason, session.getUserCode());
        io.success(after.getApplicationNumber() + " moved to " + after.getStatus());
        if (after.getStatus() == ApplicationStatus.APPROVED) {
            io.field("Sanctioned amount", MoneyUtil.inr(after.getApprovedAmount()));
            io.field("Tenure", after.getTenureMonths() + " months");
            if (after.getApplicationFormPath() != null) {
                io.field("Application form", Path.of(after.getApplicationFormPath()).toAbsolutePath());
            }
        }
    }

    void disbursementMenu() {
        while (true) {
            io.title("Disbursement");
            io.line(" 1. List approved applications");
            io.line(" 2. Disburse an application");
            io.line(" 3. View disbursement of an application");
            io.line(" 0. Back");
            int choice = io.choice("Select option: ", 3);
            if (choice == 0) {
                return;
            }
            ConsoleApp.guarded(io, () -> {
                switch (choice) {
                    case 1 -> applications.printApplications(ctx.applications().byStatus(ApplicationStatus.APPROVED), false);
                    case 2 -> disburse();
                    case 3 -> {
                        LoanApplication app = ctx.applications().find(io.text("Application ID or number", true, null));
                        ctx.disbursements().findForApplication(app.getApplicationId()).ifPresentOrElse(
                                d -> {
                                    io.field("Application", app.getApplicationNumber());
                                    io.field("Reference", d.getReference());
                                    io.field("Amount", MoneyUtil.inr(d.getAmount()));
                                    io.field("Disbursed at", DateUtil.format(d.getDisbursedAt()));
                                    io.field("Disbursed by", d.getDisbursedBy());
                                    io.field("Current status", app.getStatus());
                                },
                                () -> io.error("No disbursement for " + app.getApplicationNumber()));
                    }
                    default -> { }
                }
            });
        }
    }

    private void disburse() {
        LoanApplication app = ctx.applications().find(io.text("Application ID or number", true, null));
        io.field("Application", app.getApplicationNumber() + " [" + app.getStatus() + "]");
        io.field("Customer", app.getCustomerName());
        io.field("Amount", MoneyUtil.inr(app.getApprovedAmount() != null ? app.getApprovedAmount() : app.getRequestedAmount()));
        String reference = io.text("Disbursement reference (blank to auto-generate)", false, null);
        if (!io.confirm("Disburse now?")) {
            return;
        }
        Confirmation c = ctx.disbursements().disburse(app.getApplicationId(), reference, session.getUserCode());
        io.title("Disbursement confirmation");
        io.field("Application number", c.applicationNumber());
        io.field("Customer", c.customerName());
        io.field("Amount", MoneyUtil.inr(c.amount()));
        io.field("Reference", c.reference());
        io.field("Date", DateUtil.format(c.disbursedAt()));
        io.success("Disbursed. The core banking push utility will pick this up and move it to EXIT.");
    }
}
