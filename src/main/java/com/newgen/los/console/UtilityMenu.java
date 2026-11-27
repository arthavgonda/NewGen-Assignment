package com.newgen.los.console;

import com.newgen.los.AppContext;
import com.newgen.los.model.CoreBankingRecord;
import com.newgen.los.model.SystemAlert;
import com.newgen.los.model.UtilityRun;
import com.newgen.los.utility.CoreBankingPushUtility.PushResult;
import com.newgen.los.util.DateUtil;
import com.newgen.los.util.FailureSimulator;
import com.newgen.los.util.MoneyUtil;

import java.util.ArrayList;
import java.util.List;

class UtilityMenu {

    private final AppContext ctx;
    private final ConsoleIO io;

    UtilityMenu(AppContext ctx, ConsoleIO io) {
        this.ctx = ctx;
        this.io = io;
    }

    void show() {
        while (true) {
            io.title("Utilities");
            io.line(" 1. Run review monitor now (overdue alerts + CSV)");
            io.line(" 2. Run core banking push now");
            io.line(" 3. Recent utility runs");
            io.line(" 4. System alerts");
            io.line(" 5. NG_CORE_BANKING entries");
            io.line(" 6. Scheduler details");
            io.line(" 7. Rollback test (simulate a failure in the next transaction)");
            io.line(" 0. Back");
            int choice = io.choice("Select option: ", 7);
            if (choice == 0) {
                return;
            }
            ConsoleApp.guarded(io, () -> {
                switch (choice) {
                    case 1 -> runReview();
                    case 2 -> runPush();
                    case 3 -> runs();
                    case 4 -> alerts();
                    case 5 -> coreEntries();
                    case 6 -> schedule();
                    case 7 -> rollbackTest();
                    default -> { }
                }
            });
        }
    }

    private void runReview() {
        UtilityRun run = ctx.reviewMonitor().run("MANUAL");
        io.field("Result", run.getResult());
        io.field("Pending reviews", run.getApplicationsProcessed());
        io.field("New overdue alerts", run.getAlertsCreated());
        io.field("CSV file", run.getOutputFile());
        if (run.getErrorMessage() != null) {
            io.field("Error", run.getErrorMessage());
        }
    }

    private void runPush() {
        PushResult result = ctx.corePush().runOnce("MANUAL");
        io.field("Disbursed cases found", result.pending());
        io.field("Pushed and moved to EXIT", result.pushed());
        result.errors().forEach(io::error);
    }

    private void runs() {
        List<List<String>> rows = new ArrayList<>();
        for (UtilityRun r : ctx.operations().recentRuns()) {
            rows.add(List.of(String.valueOf(r.getRunId()), r.getUtilityName(), r.getTriggerType(),
                    DateUtil.format(r.getBusinessDate()), DateUtil.format(r.getStartedAt()), r.getResult(),
                    String.valueOf(r.getApplicationsProcessed()), String.valueOf(r.getAlertsCreated()),
                    r.getErrorMessage() == null ? "" : r.getErrorMessage()));
        }
        TablePrinter.print(io.out(), List.of("Run", "Utility", "Trigger", "Business date", "Started", "Result",
                "Processed", "Alerts", "Error"), rows);
    }

    private void alerts() {
        List<List<String>> rows = new ArrayList<>();
        for (SystemAlert a : ctx.operations().recentAlerts()) {
            rows.add(List.of(String.valueOf(a.getAlertId()), a.getApplicationNumber(), a.getAlertType().name(),
                    a.getMessage() == null ? "" : a.getMessage(), DateUtil.format(a.getCreatedAt())));
        }
        TablePrinter.print(io.out(), List.of("ID", "Application", "Type", "Message", "Created"), rows);
    }

    private void coreEntries() {
        List<List<String>> rows = new ArrayList<>();
        for (CoreBankingRecord r : ctx.operations().coreBankingRecords()) {
            rows.add(List.of(String.valueOf(r.getCoreId()), String.valueOf(r.getCustomerId()), r.getApplicationNumber(),
                    r.getCustomerName(), DateUtil.format(r.getDateOfBirth()),
                    r.getKycDetails() == null ? "" : r.getKycDetails(), MoneyUtil.format(r.getApprovedAmount()),
                    MoneyUtil.format(r.getDisbursedAmount()), DateUtil.format(r.getDisbursedDate())));
        }
        TablePrinter.print(io.out(), List.of("Core ID", "Cust ID", "Application", "Name", "DOB", "KYC", "Approved",
                "Disbursed", "Disb. date"), rows);
    }

    private void schedule() {
        io.field("Review monitor time", ctx.scheduler().reviewTime() + " every "
                + ctx.config().reviewUtilityIntervalHours() + " hour(s)");
        io.field("First scheduled run", DateUtil.format(ctx.scheduler().firstReviewRun()));
        io.field("Core push interval", ctx.scheduler().pushIntervalSeconds() + " seconds");
        io.field("Overdue after", ctx.config().reviewOverdueDays() + " calendar days");
        io.field("CSV folder", ctx.operations().exportDir().toAbsolutePath());
    }

    private void rollbackTest() {
        io.line(" The next transaction of the chosen type will fail half way and must roll back fully.");
        io.line(" 1. Submission   2. Review / credit decision   3. Disbursement   0. Cancel / disarm all");
        int choice = io.choice("Choice: ", 3);
        switch (choice) {
            case 1 -> FailureSimulator.arm(FailureSimulator.SUBMISSION);
            case 2 -> FailureSimulator.arm(FailureSimulator.DECISION);
            case 3 -> FailureSimulator.arm(FailureSimulator.DISBURSEMENT);
            default -> {
                FailureSimulator.disarmAll();
                io.success("All simulated failures cleared");
                return;
            }
        }
        io.success("Armed. Now perform that action, then check the application status and history.");
    }
}
