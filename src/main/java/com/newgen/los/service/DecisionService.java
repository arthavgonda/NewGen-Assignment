package com.newgen.los.service;

import com.newgen.los.config.BusinessClock;
import com.newgen.los.dao.CustomerDao;
import com.newgen.los.dao.Database;
import com.newgen.los.dao.HistoryDao;
import com.newgen.los.dao.LoanApplicationDao;
import com.newgen.los.exception.InvalidTransitionException;
import com.newgen.los.exception.LoanException;
import com.newgen.los.exception.NotFoundException;
import com.newgen.los.exception.ValidationException;
import com.newgen.los.model.ApplicationStatus;
import com.newgen.los.model.Customer;
import com.newgen.los.model.Decision;
import com.newgen.los.model.HistoryAction;
import com.newgen.los.model.HistoryEntry;
import com.newgen.los.model.LoanApplication;
import com.newgen.los.util.FailureSimulator;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DecisionService {

    private static final Logger LOG = Logger.getLogger(DecisionService.class.getName());

    private final Database db;
    private final LoanApplicationDao appDao;
    private final CustomerDao customerDao;
    private final HistoryDao historyDao;
    private final ApplicationFormService formService;
    private final BusinessClock clock;

    public DecisionService(Database db, LoanApplicationDao appDao, CustomerDao customerDao, HistoryDao historyDao,
                           ApplicationFormService formService, BusinessClock clock) {
        this.db = db;
        this.appDao = appDao;
        this.customerDao = customerDao;
        this.historyDao = historyDao;
        this.formService = formService;
        this.clock = clock;
    }

    public LoanApplication review(long id, Decision decision, String reason, String userCode) {
        String user = UserCodes.clean(userCode);
        String remarks = cleanReason(decision, reason);
        ApplicationStatus target = decision == Decision.APPROVE ? ApplicationStatus.CREDIT_APPROVAL : ApplicationStatus.REJECTED;
        db.inTransaction(c -> {
            LoanApplication app = appDao.findByIdForUpdate(c, id)
                    .orElseThrow(() -> new NotFoundException("Application " + id + " not found"));
            requireStatus(app, ApplicationStatus.UNDER_REVIEW, "reviewed");
            WorkflowRules.ensure(app.getStatus(), target);
            LocalDateTime now = clock.now();
            appDao.recordReviewDecision(c, id, ApplicationStatus.UNDER_REVIEW, target, user, remarks, now);
            historyDao.insert(c, new HistoryEntry(id,
                    decision == Decision.APPROVE ? HistoryAction.REVIEW_APPROVED : HistoryAction.REVIEW_REJECTED,
                    ApplicationStatus.UNDER_REVIEW, target, user, now, remarks));
            FailureSimulator.check(FailureSimulator.DECISION);
            return null;
        });
        return reload(id);
    }

    public LoanApplication creditDecision(long id, Decision decision, String reason, String userCode) {
        String user = UserCodes.clean(userCode);
        String remarks = cleanReason(decision, reason);
        ApplicationStatus target = decision == Decision.APPROVE ? ApplicationStatus.APPROVED : ApplicationStatus.REJECTED;
        db.inTransaction(c -> {
            LoanApplication app = appDao.findByIdForUpdate(c, id)
                    .orElseThrow(() -> new NotFoundException("Application " + id + " not found"));
            requireStatus(app, ApplicationStatus.CREDIT_APPROVAL, "credit approved");
            WorkflowRules.ensure(app.getStatus(), target);
            LocalDateTime now = clock.now();
            appDao.recordCreditDecision(c, id, target, user, remarks,
                    decision == Decision.APPROVE ? app.getRequestedAmount() : null, now);
            historyDao.insert(c, new HistoryEntry(id,
                    decision == Decision.APPROVE ? HistoryAction.CREDIT_APPROVED : HistoryAction.CREDIT_REJECTED,
                    ApplicationStatus.CREDIT_APPROVAL, target, user, now, remarks));
            FailureSimulator.check(FailureSimulator.DECISION);
            return null;
        });
        if (decision == Decision.APPROVE) {
            try {
                generateForm(id);
            } catch (LoanException e) {
                LOG.log(Level.WARNING, "Approval saved but application form could not be generated", e);
            }
        }
        return reload(id);
    }

    public Path generateForm(long id) {
        LoanApplication app = reload(id);
        if (app.getApprovedAmount() == null) {
            throw new InvalidTransitionException("Application form is generated only after credit approval");
        }
        Customer customer = db.withConnection(c -> customerDao.findById(c, app.getCustomerId()))
                .orElseThrow(() -> new NotFoundException("Customer not found"));
        LocalDateTime now = clock.now();
        try {
            Path file = formService.generate(app, customer, now);
            db.inTransaction(c -> {
                appDao.updateFormPath(c, id, file.toString());
                historyDao.insert(c, new HistoryEntry(id, HistoryAction.FORM_GENERATED, app.getStatus(), app.getStatus(),
                        UserCodes.SYSTEM, now, "Application form " + file.getFileName()));
                return null;
            });
            return file;
        } catch (IOException e) {
            throw new LoanException("Could not write application form: " + e.getMessage(), e);
        }
    }

    private LoanApplication reload(long id) {
        return db.withConnection(c -> appDao.findById(c, id))
                .orElseThrow(() -> new NotFoundException("Application " + id + " not found"));
    }

    private static void requireStatus(LoanApplication app, ApplicationStatus expected, String verb) {
        if (app.getStatus() != expected) {
            throw new InvalidTransitionException("Only " + expected + " applications can be " + verb + ". "
                    + app.getApplicationNumber() + " is " + app.getStatus());
        }
    }

    private static String cleanReason(Decision decision, String reason) {
        String text = reason == null ? null : reason.trim();
        if (decision == Decision.REJECT && (text == null || text.isEmpty())) {
            throw new ValidationException("Rejection reason is required");
        }
        if (text != null && text.length() > 500) {
            throw new ValidationException("Reason can be at most 500 characters");
        }
        return text == null || text.isEmpty() ? null : text;
    }
}
