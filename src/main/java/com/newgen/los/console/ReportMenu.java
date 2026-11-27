package com.newgen.los.console;

import com.newgen.los.AppContext;
import com.newgen.los.model.ApplicationStatus;
import com.newgen.los.model.Product;
import com.newgen.los.model.ReportTable;

import java.time.LocalDate;

class ReportMenu {

    private final AppContext ctx;
    private final ConsoleIO io;

    ReportMenu(AppContext ctx, ConsoleIO io) {
        this.ctx = ctx;
        this.io = io;
    }

    void show() {
        while (true) {
            io.title("Reports");
            io.line(" 1. Application register");
            io.line(" 2. Product pipeline (product/status, branch/product)");
            io.line(" 3. Disbursements (daily / monthly / quarterly, branch wise)");
            io.line(" 4. Overdue reviews");
            io.line(" 5. Customers without disbursement");
            io.line(" 6. Repeat applicants");
            io.line(" 0. Back");
            int choice = io.choice("Select option: ", 6);
            if (choice == 0) {
                return;
            }
            ConsoleApp.guarded(io, () -> {
                switch (choice) {
                    case 1 -> {
                        String product = io.text("Product (blank for all)", false, null);
                        String status = io.text("Status (blank for all)", false, null);
                        print(ctx.reports().applicationRegister(product == null ? null : Product.from(product),
                                status == null ? null : ApplicationStatus.from(status)));
                    }
                    case 2 -> ctx.reports().productPipeline().forEach(this::print);
                    case 3 -> {
                        LocalDate today = ctx.clock().today();
                        LocalDate from = io.date("From date", true, today.withDayOfMonth(1));
                        LocalDate to = io.date("To date", true, today);
                        String period = io.text("Period (DAILY/MONTHLY/QUARTERLY)", true, "DAILY");
                        ctx.reports().disbursements(from, to, period).forEach(this::print);
                    }
                    case 4 -> print(ctx.reports().overdueReviews());
                    case 5 -> print(ctx.reports().customersWithoutDisbursement());
                    case 6 -> print(ctx.reports().repeatApplicants());
                    default -> { }
                }
            });
        }
    }

    private void print(ReportTable table) {
        TablePrinter.print(io.out(), table);
    }
}
