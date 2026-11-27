package com.newgen.los.console;

import com.newgen.los.AppContext;
import com.newgen.los.exception.LoanException;
import com.newgen.los.exception.ValidationException;
import com.newgen.los.util.DateUtil;
import com.newgen.los.util.FailureSimulator;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ConsoleApp {

    private static final Logger LOG = Logger.getLogger(ConsoleApp.class.getName());

    private final AppContext ctx;
    private final ConsoleIO io = new ConsoleIO();
    private final Session session = new Session();
    private final CustomerMenu customerMenu;
    private final ApplicationMenu applicationMenu;
    private final DecisionMenu decisionMenu;
    private final ReportMenu reportMenu;
    private final UtilityMenu utilityMenu;
    private final SettingsMenu settingsMenu;

    public ConsoleApp(AppContext ctx) {
        this.ctx = ctx;
        this.customerMenu = new CustomerMenu(ctx, io);
        this.applicationMenu = new ApplicationMenu(ctx, io, session);
        this.decisionMenu = new DecisionMenu(ctx, io, session, applicationMenu);
        this.reportMenu = new ReportMenu(ctx, io);
        this.utilityMenu = new UtilityMenu(ctx, io);
        this.settingsMenu = new SettingsMenu(ctx, io, session);
    }

    public void run() {
        io.line("");
        io.line("Mini Loan Origination System");
        String user = io.text("Enter your user code", true, "MAKER01");
        session.setUserCode(user);

        try {
            while (true) {
                printMainMenu();
                int choice = io.choice("Select option: ", 8);
                if (choice == 0) {
                    io.line("Goodbye.");
                    return;
                }
                handle(choice);
            }
        } catch (ConsoleIO.EndOfInputException e) {
            io.line("");
        }
    }

    private void printMainMenu() {
        io.line("");
        io.line("==========================================================");
        io.line(" MAIN MENU   Business date: " + DateUtil.format(ctx.clock().today())
                + "   User: " + session.getUserCode());
        if (FailureSimulator.isArmed(FailureSimulator.SUBMISSION) || FailureSimulator.isArmed(FailureSimulator.DECISION)
                || FailureSimulator.isArmed(FailureSimulator.DISBURSEMENT)) {
            io.line(" ** rollback test armed, next matching transaction will fail **");
        }
        io.line("==========================================================");
        io.line(" 1. Customers");
        io.line(" 2. Loan applications");
        io.line(" 3. Review queue (UNDER_REVIEW)");
        io.line(" 4. Credit approval queue");
        io.line(" 5. Disbursement");
        io.line(" 6. Reports");
        io.line(" 7. Utilities");
        io.line(" 8. Settings");
        io.line(" 0. Exit");
    }

    private void handle(int choice) {
        Runnable action;
        switch (choice) {
            case 1 -> action = customerMenu::show;
            case 2 -> action = applicationMenu::show;
            case 3 -> action = decisionMenu::reviewQueue;
            case 4 -> action = decisionMenu::creditQueue;
            case 5 -> action = decisionMenu::disbursementMenu;
            case 6 -> action = reportMenu::show;
            case 7 -> action = utilityMenu::show;
            case 8 -> action = settingsMenu::show;
            default -> action = () -> { };
        }
        guarded(io, action);
    }

    static void guarded(ConsoleIO io, Runnable action) {
        try {
            action.run();
        } catch (ValidationException e) {
            io.error("Please correct the following:");
            e.getErrors().forEach(err -> io.line("      - " + err));
        } catch (LoanException | IllegalArgumentException e) {
            io.error(e.getMessage());
        } catch (ConsoleIO.EndOfInputException e) {
            throw e;
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Unexpected error", e);
            io.error("Unexpected error: " + e.getMessage());
        }
    }

    static class Session {
        private String userCode = "MAKER01";

        String getUserCode() {
            return userCode;
        }

        void setUserCode(String userCode) {
            this.userCode = userCode.trim().toUpperCase();
        }
    }
}
