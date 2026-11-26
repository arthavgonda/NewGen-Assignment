package com.newgen.los.utility;

import com.newgen.los.config.BusinessClock;
import com.newgen.los.dao.CoreBankingDao;
import com.newgen.los.dao.Database;
import com.newgen.los.dao.HistoryDao;
import com.newgen.los.dao.LoanApplicationDao;
import com.newgen.los.dao.UtilityRunDao;
import com.newgen.los.model.ApplicationStatus;
import com.newgen.los.model.CoreBankingRecord;
import com.newgen.los.model.HistoryAction;
import com.newgen.los.model.HistoryEntry;
import com.newgen.los.model.UtilityRun;
import com.newgen.los.service.WorkflowRules;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CoreBankingPushUtility {

    public static final String NAME = "CORE_BANKING_PUSH";
    private static final String SYSTEM_USER = "SYSTEM";
    private static final Logger LOG = Logger.getLogger(CoreBankingPushUtility.class.getName());

    private final Database db;
    private final LoanApplicationDao appDao;
    private final CoreBankingDao coreDao;
    private final HistoryDao historyDao;
    private final UtilityRunDao runDao;
    private final BusinessClock clock;
    private final ReentrantLock lock = new ReentrantLock();

    public CoreBankingPushUtility(Database db, LoanApplicationDao appDao, CoreBankingDao coreDao, HistoryDao historyDao,
                                  UtilityRunDao runDao, BusinessClock clock) {
        this.db = db;
        this.appDao = appDao;
        this.coreDao = coreDao;
        this.historyDao = historyDao;
        this.runDao = runDao;
        this.clock = clock;
    }

    public PushResult runOnce(String trigger) {
        if (!lock.tryLock()) {
            return new PushResult(0, 0, List.of("Another push is already running"));
        }
        try {
            LocalDateTime started = clock.now();
            List<CoreBankingRecord> pending = db.withConnection(appDao::findPendingCorePush);
            int pushed = 0;
            List<String> errors = new ArrayList<>();
            for (CoreBankingRecord record : pending) {
                try {
                    if (pushOne(record)) {
                        pushed++;
                    }
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "Core push failed for " + record.getApplicationNumber(), e);
                    errors.add(record.getApplicationNumber() + ": " + e.getMessage());
                }
            }
            if (!pending.isEmpty() || "MANUAL".equals(trigger)) {
                saveRun(trigger, started, pushed, errors);
            }
            return new PushResult(pending.size(), pushed, errors);
        } finally {
            lock.unlock();
        }
    }

    private boolean pushOne(CoreBankingRecord record) {
        return db.inTransaction(c -> {
            LocalDateTime now = clock.now();
            long id = record.getApplicationId();
            WorkflowRules.ensure(ApplicationStatus.DISBURSED, ApplicationStatus.CORE_BANKING_PUSH);
            // conditional update locks the row; 0 means someone else already took this case
            if (appDao.changeStatus(c, id, ApplicationStatus.DISBURSED, ApplicationStatus.CORE_BANKING_PUSH, now) == 0) {
                return false;
            }
            record.setPushedAt(now);
            long coreId = coreDao.insert(c, record);
            historyDao.insert(c, new HistoryEntry(id, HistoryAction.CORE_BANKING_PUSH, ApplicationStatus.DISBURSED,
                    ApplicationStatus.CORE_BANKING_PUSH, SYSTEM_USER, now, "NG_CORE_BANKING entry " + coreId));

            WorkflowRules.ensure(ApplicationStatus.CORE_BANKING_PUSH, ApplicationStatus.EXIT);
            appDao.changeStatus(c, id, ApplicationStatus.CORE_BANKING_PUSH, ApplicationStatus.EXIT, now);
            historyDao.insert(c, new HistoryEntry(id, HistoryAction.EXITED, ApplicationStatus.CORE_BANKING_PUSH,
                    ApplicationStatus.EXIT, SYSTEM_USER, now, "Moved to EXIT after core banking push"));
            return true;
        });
    }

    private void saveRun(String trigger, LocalDateTime started, int pushed, List<String> errors) {
        try {
            UtilityRun run = new UtilityRun();
            run.setUtilityName(NAME);
            run.setTriggerType(trigger);
            run.setBusinessDate(started.toLocalDate());
            run.setStartedAt(started);
            run.setRunId(db.inTransaction(c -> runDao.start(c, run)));
            run.setApplicationsProcessed(pushed);
            run.setResult(errors.isEmpty() ? UtilityRun.SUCCESS : UtilityRun.FAILED);
            run.setErrorMessage(errors.isEmpty() ? null : String.join("; ", errors));
            run.setCompletedAt(clock.now());
            db.inTransaction(c -> {
                runDao.finish(c, run);
                return null;
            });
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Could not record core push run", e);
        }
    }

    public record PushResult(int pending, int pushed, List<String> errors) {
    }
}
