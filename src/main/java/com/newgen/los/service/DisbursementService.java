package com.newgen.los.service;

import com.newgen.los.config.BusinessClock;
import com.newgen.los.dao.Database;
import com.newgen.los.dao.DisbursementDao;
import com.newgen.los.dao.DuplicateKeyException;
import com.newgen.los.dao.HistoryDao;
import com.newgen.los.dao.LoanApplicationDao;
import com.newgen.los.exception.BusinessRuleException;
import com.newgen.los.exception.InvalidTransitionException;
import com.newgen.los.exception.NotFoundException;
import com.newgen.los.exception.ValidationException;
import com.newgen.los.model.ApplicationStatus;
import com.newgen.los.model.Disbursement;
import com.newgen.los.model.HistoryAction;
import com.newgen.los.model.HistoryEntry;
import com.newgen.los.model.LoanApplication;
import com.newgen.los.util.FailureSimulator;
import com.newgen.los.util.MoneyUtil;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class DisbursementService {

    private final Database db;
    private final LoanApplicationDao appDao;
    private final DisbursementDao disbursementDao;
    private final HistoryDao historyDao;
    private final BusinessClock clock;

    public DisbursementService(Database db, LoanApplicationDao appDao, DisbursementDao disbursementDao,
                               HistoryDao historyDao, BusinessClock clock) {
        this.db = db;
        this.appDao = appDao;
        this.disbursementDao = disbursementDao;
        this.historyDao = historyDao;
        this.clock = clock;
    }

    public Confirmation disburse(long applicationId, String reference, String userCode) {
        String user = UserCodes.clean(userCode);
        String ref = cleanReference(reference);
        try {
            return db.inTransaction(c -> {
                LoanApplication app = appDao.findByIdForUpdate(c, applicationId)
                        .orElseThrow(() -> new NotFoundException("Application " + applicationId + " not found"));
                Optional<Disbursement> existing = disbursementDao.findByApplication(c, applicationId);
                if (existing.isPresent()) {
                    throw new BusinessRuleException(app.getApplicationNumber() + " is already disbursed with reference "
                            + existing.get().getReference());
                }
                if (app.getStatus() != ApplicationStatus.APPROVED) {
                    throw new InvalidTransitionException("Only APPROVED applications can be disbursed. "
                            + app.getApplicationNumber() + " is " + app.getStatus());
                }
                WorkflowRules.ensure(app.getStatus(), ApplicationStatus.DISBURSED);

                LocalDateTime now = clock.now();
                String finalRef = ref != null ? ref : generateReference(app, now);
                if (disbursementDao.findByReference(c, finalRef).isPresent()) {
                    throw new BusinessRuleException("Disbursement reference " + finalRef + " is already used");
                }
                BigDecimal amount = app.getApprovedAmount();
                Disbursement d = new Disbursement();
                d.setApplicationId(applicationId);
                d.setReference(finalRef);
                d.setAmount(amount);
                d.setDisbursedAt(now);
                d.setDisbursedBy(user);
                disbursementDao.insert(c, d);

                FailureSimulator.check(FailureSimulator.DISBURSEMENT);

                int updated = appDao.changeStatus(c, applicationId, ApplicationStatus.APPROVED, ApplicationStatus.DISBURSED, now);
                if (updated != 1) {
                    throw new InvalidTransitionException("Application status changed while disbursing, try again");
                }
                historyDao.insert(c, new HistoryEntry(applicationId, HistoryAction.DISBURSED, ApplicationStatus.APPROVED,
                        ApplicationStatus.DISBURSED, user, now, "Ref " + finalRef + ", amount " + MoneyUtil.inr(amount)));
                return new Confirmation(app.getApplicationNumber(), app.getCustomerName(), amount, finalRef, now);
            });
        } catch (DuplicateKeyException e) {
            if (e.isFor("uk_disb_reference")) {
                throw new BusinessRuleException("Disbursement reference " + ref + " is already used");
            }
            if (e.isFor("uk_disb_application")) {
                throw new BusinessRuleException("Application is already disbursed");
            }
            throw e;
        }
    }

    public Optional<Disbursement> findForApplication(long applicationId) {
        return db.withConnection(c -> disbursementDao.findByApplication(c, applicationId));
    }

    private static String generateReference(LoanApplication app, LocalDateTime at) {
        return "DSB" + at.format(DateTimeFormatter.BASIC_ISO_DATE) + String.format("%06d", app.getApplicationId());
    }

    private static String cleanReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        String ref = reference.trim().toUpperCase();
        if (!ref.matches("[A-Z0-9-]{6,40}")) {
            throw new ValidationException("Reference must be 6 to 40 letters, digits or hyphens");
        }
        return ref;
    }

    public record Confirmation(String applicationNumber, String customerName, BigDecimal amount,
                               String reference, LocalDateTime disbursedAt) {
    }
}
