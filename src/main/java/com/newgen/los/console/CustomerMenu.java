package com.newgen.los.console;

import com.newgen.los.AppContext;
import com.newgen.los.model.Customer;
import com.newgen.los.model.LoanApplication;
import com.newgen.los.service.LoanCalculator;
import com.newgen.los.util.DateUtil;
import com.newgen.los.util.MoneyUtil;

import java.util.ArrayList;
import java.util.List;

class CustomerMenu {

    private final AppContext ctx;
    private final ConsoleIO io;

    CustomerMenu(AppContext ctx, ConsoleIO io) {
        this.ctx = ctx;
        this.io = io;
    }

    void show() {
        while (true) {
            io.title("Customers");
            io.line(" 1. Create customer");
            io.line(" 2. Search customer (ID / name / mobile)");
            io.line(" 3. View customer and applications");
            io.line(" 4. Edit customer");
            io.line(" 0. Back");
            int choice = io.choice("Select option: ", 4);
            if (choice == 0) {
                return;
            }
            ConsoleApp.guarded(io, () -> {
                switch (choice) {
                    case 1 -> create();
                    case 2 -> search();
                    case 3 -> view();
                    case 4 -> edit();
                    default -> { }
                }
            });
        }
    }

    private void create() {
        io.title("New customer");
        Customer c = new Customer();
        fill(c);
        Customer saved = ctx.customers().create(c);
        io.success("Customer created with ID " + saved.getCustomerId());
    }

    private void edit() {
        long id = io.integer("Customer ID", true, null);
        Customer c = ctx.customers().get(id);
        io.line("Press Enter to keep the current value, '-' to clear an optional field.");
        fill(c);
        ctx.customers().update(c);
        io.success("Customer " + id + " updated");
    }

    private void fill(Customer c) {
        c.setName(io.text("Full name", true, c.getName()));
        c.setDateOfBirth(io.date("Date of birth", true, c.getDateOfBirth()));
        c.setMobile(io.text("Mobile (10 digits)", true, c.getMobile()));
        c.setEmail(io.text("Email (optional)", false, c.getEmail()));
        c.setAddress(io.text("Address", false, c.getAddress()));
        c.setKycType(io.text("KYC type (PAN/AADHAAR/PASSPORT/VOTER_ID/DRIVING_LICENCE)", false, c.getKycType()));
        if (c.getKycType() != null) {
            c.setKycNumber(io.text("KYC number", true, c.getKycNumber()));
        } else {
            c.setKycNumber(null);
        }
    }

    private void search() {
        io.line(" Search by: 1. ID  2. Name  3. Mobile  0. Show recent");
        int by = io.choice("Choice: ", 3);
        String mode = by == 1 ? "ID" : by == 3 ? "MOBILE" : "NAME";
        String value = io.text("Value", by != 0, null);
        List<Customer> found = ctx.customers().search(by == 0 ? null : mode, value);
        printList(found);
    }

    private void printList(List<Customer> customers) {
        List<List<String>> rows = new ArrayList<>();
        for (Customer c : customers) {
            rows.add(List.of(String.valueOf(c.getCustomerId()), c.getName(), DateUtil.format(c.getDateOfBirth()),
                    String.valueOf(LoanCalculator.completedYears(c.getDateOfBirth(), ctx.clock().today())),
                    c.getMobile(), c.getKycDetails() == null ? "" : c.getKycDetails()));
        }
        TablePrinter.print(io.out(), List.of("ID", "Name", "DOB", "Age", "Mobile", "KYC"), rows);
    }

    private void view() {
        long id = io.integer("Customer ID", true, null);
        Customer c = ctx.customers().get(id);
        io.title("Customer " + id);
        io.field("Name", c.getName());
        io.field("Date of birth", DateUtil.format(c.getDateOfBirth()));
        io.field("Age today", LoanCalculator.completedYears(c.getDateOfBirth(), ctx.clock().today()));
        io.field("Mobile", c.getMobile());
        io.field("Email", c.getEmail());
        io.field("Address", c.getAddress());
        io.field("KYC", c.getKycDetails());
        io.field("Created", DateUtil.format(c.getCreatedAt()));

        List<List<String>> rows = new ArrayList<>();
        for (LoanApplication a : ctx.applications().forCustomer(id)) {
            rows.add(List.of(a.getApplicationNumber(), a.getProduct().name(), MoneyUtil.format(a.getRequestedAmount()),
                    String.valueOf(a.getTenureMonths()), a.getStatus().name(), DateUtil.format(a.getCreatedAt())));
        }
        TablePrinter.print(io.out(), List.of("Application", "Product", "Amount", "Tenure", "Status", "Created"), rows);
    }
}
