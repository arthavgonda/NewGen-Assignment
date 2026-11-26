package com.newgen.los.service;

import com.newgen.los.dao.AlertDao;
import com.newgen.los.dao.CoreBankingDao;
import com.newgen.los.dao.DashboardDao;
import com.newgen.los.dao.Database;
import com.newgen.los.dao.UtilityRunDao;
import com.newgen.los.model.CoreBankingRecord;
import com.newgen.los.model.SystemAlert;
import com.newgen.los.model.UtilityRun;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class OperationsService {

    private final Database db;
    private final AlertDao alertDao;
    private final UtilityRunDao runDao;
    private final CoreBankingDao coreDao;
    private final DashboardDao dashboardDao = new DashboardDao();
    private final Path exportDir;

    public OperationsService(Database db, AlertDao alertDao, UtilityRunDao runDao, CoreBankingDao coreDao, Path exportDir) {
        this.db = db;
        this.alertDao = alertDao;
        this.runDao = runDao;
        this.coreDao = coreDao;
        this.exportDir = exportDir;
    }

    public List<SystemAlert> recentAlerts() {
        return db.withConnection(c -> alertDao.findRecent(c, 200));
    }

    public List<SystemAlert> alertsFor(long applicationId) {
        return db.withConnection(c -> alertDao.findByApplication(c, applicationId));
    }

    public int alertCount() {
        return db.withConnection(alertDao::countAll);
    }

    public List<UtilityRun> recentRuns() {
        return db.withConnection(c -> runDao.findRecent(c, 50));
    }

    public List<CoreBankingRecord> coreBankingRecords() {
        return db.withConnection(c -> coreDao.findRecent(c, 200));
    }

    public List<Path> exportFiles() {
        if (!Files.isDirectory(exportDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(exportDir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".csv"))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public Map<LocalDate, long[]> applicationsByDay(LocalDate from, LocalDate to) {
        return db.withConnection(c -> dashboardDao.applicationsByDay(c, from, to));
    }

    public Map<LocalDate, BigDecimal[]> moneyByDay(LocalDate from, LocalDate to) {
        return db.withConnection(c -> dashboardDao.moneyByDay(c, from, to));
    }

    public Map<String, BigDecimal> totals() {
        return db.withConnection(dashboardDao::totals);
    }

    public Path exportDir() {
        return exportDir;
    }
}
