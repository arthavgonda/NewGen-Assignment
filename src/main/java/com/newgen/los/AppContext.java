package com.newgen.los;

import com.newgen.los.config.AppConfig;
import com.newgen.los.config.BusinessClock;
import com.newgen.los.config.ProductRuleBook;
import com.newgen.los.dao.AlertDao;
import com.newgen.los.dao.CoreBankingDao;
import com.newgen.los.dao.CustomerDao;
import com.newgen.los.dao.Database;
import com.newgen.los.dao.DisbursementDao;
import com.newgen.los.dao.HistoryDao;
import com.newgen.los.dao.LoanApplicationDao;
import com.newgen.los.dao.ReportDao;
import com.newgen.los.dao.UtilityRunDao;
import com.newgen.los.service.ApplicationFormService;
import com.newgen.los.service.CustomerService;
import com.newgen.los.service.DecisionService;
import com.newgen.los.service.DisbursementService;
import com.newgen.los.service.EligibilityAssessor;
import com.newgen.los.service.LoanApplicationService;
import com.newgen.los.service.OperationsService;
import com.newgen.los.service.ReportService;
import com.newgen.los.utility.CoreBankingPushUtility;
import com.newgen.los.utility.ReviewMonitorUtility;
import com.newgen.los.utility.UtilityScheduler;

public class AppContext {

    private final AppConfig config;
    private final BusinessClock clock;
    private final ProductRuleBook ruleBook;
    private final Database database;
    private final CustomerService customerService;
    private final LoanApplicationService applicationService;
    private final DecisionService decisionService;
    private final DisbursementService disbursementService;
    private final ReportService reportService;
    private final OperationsService operationsService;
    private final ReviewMonitorUtility reviewMonitor;
    private final CoreBankingPushUtility corePush;
    private final UtilityScheduler scheduler;

    public AppContext(AppConfig config) {
        this.config = config;
        this.clock = new BusinessClock(config.businessDate().orElse(null));
        this.ruleBook = new ProductRuleBook(config.productRulesFile());
        this.database = new Database(config.dbUrl(), config.dbUser(), config.dbPassword());

        CustomerDao customerDao = new CustomerDao();
        LoanApplicationDao appDao = new LoanApplicationDao();
        HistoryDao historyDao = new HistoryDao();
        DisbursementDao disbursementDao = new DisbursementDao();
        AlertDao alertDao = new AlertDao();
        UtilityRunDao runDao = new UtilityRunDao();
        CoreBankingDao coreDao = new CoreBankingDao();

        this.customerService = new CustomerService(database, customerDao, clock);
        this.applicationService = new LoanApplicationService(database, appDao, customerDao, historyDao,
                new EligibilityAssessor(ruleBook), clock, config.branches());
        this.decisionService = new DecisionService(database, appDao, customerDao, historyDao,
                new ApplicationFormService(config.formsDir()), clock);
        this.disbursementService = new DisbursementService(database, appDao, disbursementDao, historyDao, clock);
        this.reportService = new ReportService(database, new ReportDao(), clock, config.reviewOverdueDays());
        this.operationsService = new OperationsService(database, alertDao, runDao, coreDao, config.exportDir());
        this.reviewMonitor = new ReviewMonitorUtility(database, appDao, alertDao, runDao, clock,
                config.exportDir(), config.reviewOverdueDays());
        this.corePush = new CoreBankingPushUtility(database, appDao, coreDao, historyDao, runDao, clock);
        this.scheduler = new UtilityScheduler(reviewMonitor, corePush, config.reviewUtilityTime(),
                config.reviewUtilityIntervalHours(), config.corePushIntervalSeconds());
    }

    public AppConfig config() {
        return config;
    }

    public BusinessClock clock() {
        return clock;
    }

    public ProductRuleBook ruleBook() {
        return ruleBook;
    }

    public Database database() {
        return database;
    }

    public CustomerService customers() {
        return customerService;
    }

    public LoanApplicationService applications() {
        return applicationService;
    }

    public DecisionService decisions() {
        return decisionService;
    }

    public DisbursementService disbursements() {
        return disbursementService;
    }

    public ReportService reports() {
        return reportService;
    }

    public OperationsService operations() {
        return operationsService;
    }

    public ReviewMonitorUtility reviewMonitor() {
        return reviewMonitor;
    }

    public CoreBankingPushUtility corePush() {
        return corePush;
    }

    public UtilityScheduler scheduler() {
        return scheduler;
    }
}
