package com.newgen.los.utility;

import com.newgen.los.config.BusinessClock;
import com.newgen.los.dao.AlertDao;
import com.newgen.los.dao.Database;
import com.newgen.los.dao.LoanApplicationDao;
import com.newgen.los.dao.UtilityRunDao;
import com.newgen.los.model.AlertType;
import com.newgen.los.model.ApplicationStatus;
import com.newgen.los.model.LoanApplication;
import com.newgen.los.model.UtilityRun;
import com.newgen.los.util.CsvWriter;
import com.newgen.los.util.DateUtil;
import com.newgen.los.util.MoneyUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReviewMonitorUtility {

    public static final String NAME = "REVIEW_MONITOR";
    private static final Logger LOG = Logger.getLogger(ReviewMonitorUtility.class.getName());

    private final Database db;
    private final LoanApplicationDao appDao;
    private final AlertDao alertDao;
    private final UtilityRunDao runDao;
    private final BusinessClock clock;
    private final Path exportDir;
    private final int overdueDays;
    private final ReentrantLock lock = new ReentrantLock();

    public ReviewMonitorUtility(Database db, LoanApplicationDao appDao, AlertDao alertDao, UtilityRunDao runDao,
                                BusinessClock clock, Path exportDir, int overdueDays) {
        this.db = db;
        this.appDao = appDao;
        this.alertDao = alertDao;
        this.runDao = runDao;
        this.clock = clock;
        this.exportDir = exportDir;
        this.overdueDays = overdueDays;
    }

    public UtilityRun run(String trigger) {
        lock.lock();
        try {
            return doRun(trigger);
        } finally {
            lock.unlock();
        }
    }

    private UtilityRun doRun(String trigger) {
        LocalDate businessDate = clock.today();
        UtilityRun run = new UtilityRun();
        run.setUtilityName(NAME);
        run.setTriggerType(trigger);
        run.setBusinessDate(businessDate);
        run.setStartedAt(clock.now());
        run.setRunId(db.inTransaction(c -> runDao.start(c, run)));

        try {
            List<LoanApplication> pending = db.withConnection(c -> appDao.findByStatus(c, ApplicationStatus.UNDER_REVIEW));
            int created = db.inTransaction(c -> {
                int count = 0;
                for (LoanApplication app : pending) {
                    long days = daysWaiting(app, businessDate);
                    if (days > overdueDays) {
                        String message = app.getApplicationNumber() + " waiting " + days + " days for review";
                        if (alertDao.insertIfAbsent(c, app.getApplicationId(), AlertType.REVIEW_OVERDUE, message, clock.now())) {
                            count++;
                        }
                    }
                }
                return count;
            });
            Path csv = exportCsv(pending, businessDate);

            run.setApplicationsProcessed(pending.size());
            run.setAlertsCreated(created);
            run.setOutputFile(csv.toString());
            run.setResult(UtilityRun.SUCCESS);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.SEVERE, "Review monitor failed", e);
            run.setResult(UtilityRun.FAILED);
            run.setErrorMessage(e.getMessage());
        }
        run.setCompletedAt(clock.now());
        db.inTransaction(c -> {
            runDao.finish(c, run);
            return null;
        });
        LOG.info(() -> "Review monitor " + run.getResult() + ": " + run.getApplicationsProcessed()
                + " pending, " + run.getAlertsCreated() + " new alerts");
        return run;
    }

    long daysWaiting(LoanApplication app, LocalDate businessDate) {
        return ChronoUnit.DAYS.between(app.getSubmittedAt().toLocalDate(), businessDate);
    }

    private Path exportCsv(List<LoanApplication> pending, LocalDate businessDate) throws IOException {
        Path file = exportDir.resolve("pending_reviews_" + businessDate.format(DateUtil.FILE_DATE) + ".csv");
        try (CsvWriter csv = new CsvWriter(file)) {
            csv.writeRow(List.of("ApplicationNumber", "CustomerName", "Product", "Branch", "RequestedAmount",
                    "ProposedEMI", "SubmittedOn", "DaysWaiting", "OverdueFlag"));
            for (LoanApplication app : pending) {
                long days = daysWaiting(app, businessDate);
                csv.writeRow(List.of(app.getApplicationNumber(), app.getCustomerName(), app.getProduct(),
                        app.getBranchCode(), MoneyUtil.format(app.getRequestedAmount()),
                        MoneyUtil.format(app.getProposedEmi()), DateUtil.format(app.getSubmittedAt().toLocalDate()),
                        days, days > overdueDays ? "Yes" : "No"));
            }
        }
        return file;
    }
}
