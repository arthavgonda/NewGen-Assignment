package com.newgen.los.console;

import com.newgen.los.AppContext;
import com.newgen.los.config.ProductRule;
import com.newgen.los.model.Product;
import com.newgen.los.util.DateUtil;
import com.newgen.los.util.MoneyUtil;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

class SettingsMenu {

    private final AppContext ctx;
    private final ConsoleIO io;
    private final ConsoleApp.Session session;

    SettingsMenu(AppContext ctx, ConsoleIO io, ConsoleApp.Session session) {
        this.ctx = ctx;
        this.io = io;
        this.session = session;
    }

    void show() {
        while (true) {
            io.title("Settings");
            io.line(" 1. Change business date");
            io.line(" 2. Reset business date to today");
            io.line(" 3. Change user code");
            io.line(" 4. View product rules");
            io.line(" 5. Edit product rule");
            io.line(" 6. Edit age and FOIR limits");
            io.line(" 0. Back");
            int choice = io.choice("Select option: ", 6);
            if (choice == 0) {
                return;
            }
            ConsoleApp.guarded(io, () -> {
                switch (choice) {
                    case 1 -> {
                        LocalDate date = io.date("New business date", true, ctx.clock().today());
                        ctx.clock().setBusinessDate(date);
                        io.success("Business date is now " + DateUtil.format(date));
                    }
                    case 2 -> {
                        ctx.clock().setBusinessDate(null);
                        io.success("Business date follows the system date (" + DateUtil.format(ctx.clock().today()) + ")");
                    }
                    case 3 -> {
                        session.setUserCode(io.text("User code", true, session.getUserCode()));
                        io.success("Acting as " + session.getUserCode());
                    }
                    case 4 -> printRules();
                    case 5 -> editRule();
                    case 6 -> editCommon();
                    default -> { }
                }
            });
        }
    }

    private void printRules() {
        List<List<String>> rows = new ArrayList<>();
        for (ProductRule r : ctx.ruleBook().allRules()) {
            rows.add(List.of(r.product().name(), MoneyUtil.format(r.minAmount()), MoneyUtil.format(r.maxAmount()),
                    r.minTenure() + " - " + r.maxTenure(), MoneyUtil.format(r.minIncome()),
                    r.annualRate().toPlainString() + "%", r.coBorrowerAllowed() ? "Yes" : "No"));
        }
        TablePrinter.print(io.out(), List.of("Product", "Min amount", "Max amount", "Tenure", "Min income", "Rate",
                "Co-borrower"), rows);
        io.line("  Age " + ctx.ruleBook().minAge() + " to " + ctx.ruleBook().maxAge()
                + " completed years, FOIR at most " + ctx.ruleBook().maxFoirPercent() + "%");
        io.line("  File: " + ctx.config().productRulesFile().toAbsolutePath());
    }

    private void editRule() {
        Product product = Product.from(io.text("Product (PERSONAL/EDUCATION)", true, null));
        ProductRule r = ctx.ruleBook().ruleFor(product);
        ProductRule updated = new ProductRule(product,
                io.amount("Minimum amount", true, r.minAmount()),
                io.amount("Maximum amount", true, r.maxAmount()),
                io.integer("Minimum tenure", true, r.minTenure()),
                io.integer("Maximum tenure", true, r.maxTenure()),
                io.amount("Minimum monthly income", true, r.minIncome()),
                io.amount("Annual interest rate %", true, r.annualRate()),
                io.text("Co-borrower allowed (true/false)", true, String.valueOf(r.coBorrowerAllowed()))
                        .equalsIgnoreCase("true"));
        ctx.ruleBook().update(updated);
        io.success(product + " rules saved. New submissions use them straight away.");
    }

    private void editCommon() {
        int min = io.integer("Minimum age", true, ctx.ruleBook().minAge());
        int max = io.integer("Maximum age", true, ctx.ruleBook().maxAge());
        var foir = io.amount("Maximum FOIR %", true, ctx.ruleBook().maxFoirPercent());
        ctx.ruleBook().updateCommon(min, max, foir);
        io.success("Limits saved");
    }
}
