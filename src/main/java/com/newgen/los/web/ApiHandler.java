package com.newgen.los.web;

import com.newgen.los.AppContext;
import com.newgen.los.config.ProductRule;
import com.newgen.los.exception.BusinessRuleException;
import com.newgen.los.exception.InvalidTransitionException;
import com.newgen.los.exception.LoanException;
import com.newgen.los.exception.NotFoundException;
import com.newgen.los.exception.ValidationException;
import com.newgen.los.model.ApplicationStatus;
import com.newgen.los.model.Branch;
import com.newgen.los.model.Customer;
import com.newgen.los.model.Decision;
import com.newgen.los.model.LoanApplication;
import com.newgen.los.model.Product;
import com.newgen.los.model.ReportTable;
import com.newgen.los.service.DisbursementService.Confirmation;
import com.newgen.los.service.LoanApplicationService.SubmissionResult;
import com.newgen.los.service.LoanCalculator;
import com.newgen.los.utility.CoreBankingPushUtility.PushResult;
import com.newgen.los.util.DateUtil;
import com.newgen.los.util.FailureSimulator;
import com.newgen.los.util.MoneyUtil;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

class ApiHandler {

    private static final Logger LOG = Logger.getLogger(ApiHandler.class.getName());

    private final AppContext ctx;

    ApiHandler(AppContext ctx) {
        this.ctx = ctx;
    }

    void handle(HttpExchange exchange, String path) throws IOException {
        Request req = new Request(exchange, path);
        try {
            Object result = route(req, exchange);
            if (result != null) {
                Responses.json(exchange, 200, result);
            }
        } catch (HttpError e) {
            Responses.error(exchange, e.status(), e.getMessage(), List.of());
        } catch (ValidationException e) {
            Responses.error(exchange, 400, "Please correct the following", e.getErrors());
        } catch (NotFoundException e) {
            Responses.error(exchange, 404, e.getMessage(), List.of());
        } catch (InvalidTransitionException | BusinessRuleException e) {
            Responses.error(exchange, 409, e.getMessage(), List.of());
        } catch (FailureSimulator.SimulatedFailureException e) {
            Responses.error(exchange, 500, e.getMessage(), List.of());
        } catch (IllegalArgumentException | LoanException e) {
            Responses.error(exchange, 400, e.getMessage(), List.of());
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "API error on " + path, e);
            Responses.error(exchange, 500, "Something went wrong: " + e.getMessage(), List.of());
        }
    }

    private Object route(Request req, HttpExchange exchange) throws IOException {
        String root = req.part(0);
        if (root == null) {
            throw new HttpError(404, "Not found");
        }
        String method = req.method();
        switch (root) {
            case "meta":
                return meta();
            case "dashboard":
                return dashboard(req);
            case "activity": {
                List<Map<String, Object>> list = new ArrayList<>();
                ctx.applications().recentActivity(25).forEach(h -> list.add(Views.history(h)));
                return list;
            }
            case "customers":
                return customers(req, method);
            case "applications":
                return applications(req, method, exchange);
            case "calculator":
                return calculator(req);
            case "reports":
                return report(req);
            case "utilities":
                return utilities(req, method, exchange);
            case "settings":
                return settings(req, method);
            default:
                throw new HttpError(404, "Not found");
        }
    }

    private LocalDate today() {
        return ctx.clock().today();
    }

    private Map<String, Object> meta() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("businessDate", today().toString());
        m.put("businessDateText", DateUtil.format(today()));
        m.put("usingSystemDate", ctx.clock().isUsingSystemDate());
        List<Map<String, String>> branches = new ArrayList<>();
        for (Branch b : ctx.applications().branches()) {
            branches.add(Map.of("code", b.code(), "name", b.name()));
        }
        m.put("branches", branches);
        List<String> statuses = new ArrayList<>();
        for (ApplicationStatus s : ApplicationStatus.values()) {
            statuses.add(s.name());
        }
        m.put("statuses", statuses);
        m.put("products", List.of("PERSONAL", "EDUCATION"));
        m.put("kycTypes", List.of("PAN", "AADHAAR", "PASSPORT", "VOTER_ID", "DRIVING_LICENCE"));
        m.put("rules", rules());
        return m;
    }

    private Map<String, Object> dashboard(Request req) {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        ctx.applications().statusCounts().forEach((k, v) -> counts.put(k.name(), v));
        m.put("counts", counts);
        m.put("alerts", ctx.operations().alertCount());
        m.put("recent", Views.applications(ctx.applications().list(null, null).stream().limit(8).toList(), today()));
        m.put("overdue", Views.report(ctx.reports().overdueReviews()));
        m.put("nextReviewRun", DateUtil.format(ctx.scheduler().firstReviewRun()));
        long count = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (List<Object> row : ctx.reports().disbursements(today().withDayOfMonth(1), today(), "MONTHLY").get(0).getRows()) {
            count += ((Number) row.get(1)).longValue();
            total = total.add((BigDecimal) row.get(2));
        }
        m.put("monthDisbursedCount", count);
        m.put("monthDisbursedText", MoneyUtil.format(total));
        m.put("monthName", today().getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH));

        Map<String, Object> totals = new LinkedHashMap<>();
        ctx.operations().totals().forEach((k, v) -> totals.put(k, MoneyUtil.format(v)));
        m.put("totals", totals);

        String daysParam = req.query("days");
        int days = daysParam != null && daysParam.matches("\\d{1,3}") ? Math.max(7, Math.min(180, Integer.parseInt(daysParam))) : 30;
        LocalDate from = today().minusDays(days - 1);
        Map<LocalDate, long[]> apps = ctx.operations().applicationsByDay(from, today());
        Map<LocalDate, BigDecimal[]> money = ctx.operations().moneyByDay(from, today());
        List<Map<String, Object>> trend = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(today()); d = d.plusDays(1)) {
            long[] a = apps.getOrDefault(d, new long[] {0, 0});
            BigDecimal[] mny = money.getOrDefault(d, new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", d.toString());
            day.put("applications", a[0]);
            day.put("rejected", a[1]);
            day.put("requested", mny[0].toPlainString());
            day.put("disbursed", mny[1].toPlainString());
            trend.add(day);
        }
        m.put("trend", trend);
        return m;
    }

    private Object customers(Request req, String method) {
        if (req.parts() == 1) {
            if (method.equals("GET")) {
                List<Map<String, Object>> list = new ArrayList<>();
                for (Customer c : ctx.customers().search(req.query("by"), req.query("q"))) {
                    list.add(Views.customer(c, today()));
                }
                return list;
            }
            if (method.equals("POST")) {
                Customer saved = ctx.customers().create(customerFrom(req, new Customer()));
                return Views.customer(saved, today());
            }
        } else if (req.parts() == 2) {
            long id = req.idPart(1);
            if (method.equals("GET")) {
                Map<String, Object> m = new LinkedHashMap<>(Views.customer(ctx.customers().get(id), today()));
                m.put("applications", Views.applications(ctx.applications().forCustomer(id), today()));
                return m;
            }
            if (method.equals("PUT")) {
                Customer c = ctx.customers().get(id);
                return Views.customer(ctx.customers().update(customerFrom(req, c)), today());
            }
        }
        throw new HttpError(405, "Method not allowed");
    }

    private Customer customerFrom(Request req, Customer c) {
        c.setName(req.str("name"));
        c.setDateOfBirth(req.date("dateOfBirth"));
        c.setMobile(req.str("mobile"));
        c.setEmail(req.str("email"));
        c.setAddress(req.str("address"));
        c.setKycType(req.str("kycType"));
        c.setKycNumber(req.str("kycNumber"));
        return c;
    }

    private Object applications(Request req, String method, HttpExchange exchange) throws IOException {
        if (req.parts() == 1) {
            if (method.equals("GET")) {
                String status = req.query("status");
                String product = req.query("product");
                return Views.applications(ctx.applications().list(
                        status == null ? null : ApplicationStatus.from(status),
                        product == null ? null : Product.from(product)), today());
            }
            if (method.equals("POST")) {
                LoanApplication app = applicationFrom(req, new LoanApplication());
                if (req.str("customerId") == null || !req.str("customerId").matches("\\d+")) {
                    throw new ValidationException("Select a customer");
                }
                app.setCustomerId(Long.parseLong(req.str("customerId")));
                return Views.application(ctx.applications().createDraft(app, req.userCode()), today());
            }
            throw new HttpError(405, "Method not allowed");
        }

        long id = req.part(1).matches("\\d+") ? req.idPart(1) : ctx.applications().find(req.part(1)).getApplicationId();
        String action = req.part(2);
        if (action == null) {
            if (method.equals("GET")) {
                return applicationDetail(id);
            }
            if (method.equals("PUT")) {
                LoanApplication current = ctx.applications().get(id);
                LoanApplication changes = applicationFrom(req, new LoanApplication());
                changes.setCustomerId(current.getCustomerId());
                return Views.application(ctx.applications().updateDraft(id, changes, req.userCode()), today());
            }
            throw new HttpError(405, "Method not allowed");
        }

        if (action.equals("form") && method.equals("GET")) {
            LoanApplication app = ctx.applications().get(id);
            if (app.getApplicationFormPath() == null || !Files.exists(Path.of(app.getApplicationFormPath()))) {
                throw new HttpError(404, "Application form has not been generated");
            }
            Responses.file(exchange, Path.of(app.getApplicationFormPath()), "text/html; charset=utf-8", false);
            return null;
        }
        if (!method.equals("POST")) {
            throw new HttpError(405, "Method not allowed");
        }
        switch (action) {
            case "submit": {
                SubmissionResult r = ctx.applications().submit(id, req.userCode());
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("application", Views.application(r.application(), today()));
                m.put("assessment", Views.assessment(r.assessment()));
                return m;
            }
            case "review":
                return Views.application(ctx.decisions().review(id, Decision.from(req.str("decision")),
                        req.str("reason"), req.userCode()), today());
            case "credit":
                return Views.application(ctx.decisions().creditDecision(id, Decision.from(req.str("decision")),
                        req.str("reason"), req.userCode()), today());
            case "regenerate-form":
                ctx.decisions().generateForm(id);
                return Map.of("ok", true);
            case "disburse": {
                Confirmation c = ctx.disbursements().disburse(id, req.str("reference"), req.userCode());
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("applicationNumber", c.applicationNumber());
                m.put("customerName", c.customerName());
                m.put("amountText", MoneyUtil.format(c.amount()));
                m.put("reference", c.reference());
                m.put("disbursedAt", DateUtil.format(c.disbursedAt()));
                return m;
            }
            default:
                throw new HttpError(404, "Not found");
        }
    }

    private Map<String, Object> applicationDetail(long id) {
        LoanApplication app = ctx.applications().get(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("application", Views.application(app, today()));
        m.put("customer", Views.customer(ctx.customers().get(app.getCustomerId()), today()));
        List<Map<String, Object>> history = new ArrayList<>();
        ctx.applications().history(id).forEach(h -> history.add(Views.history(h)));
        m.put("history", history);
        m.put("disbursement", ctx.disbursements().findForApplication(id).map(Views::disbursement).orElse(null));
        List<Map<String, Object>> alerts = new ArrayList<>();
        ctx.operations().alertsFor(id).forEach(a -> alerts.add(Views.alert(a)));
        m.put("alerts", alerts);
        return m;
    }

    private LoanApplication applicationFrom(Request req, LoanApplication app) {
        app.setProduct(Product.from(req.str("product")));
        app.setBranchCode(req.str("branchCode"));
        app.setRequestedAmount(req.decimal("requestedAmount"));
        app.setTenureMonths(req.wholeNumber("tenureMonths"));
        app.setMonthlyIncome(req.decimal("monthlyIncome"));
        app.setExistingObligations(req.decimal("existingObligations"));
        app.setCoBorrowerName(req.str("coBorrowerName"));
        return app;
    }

    private Map<String, Object> calculator(Request req) {
        Product product = Product.from(req.query("product"));
        ProductRule rule = ctx.ruleBook().ruleFor(product);
        BigDecimal amount = MoneyUtil.parse(req.query("amount"));
        String tenureText = req.query("tenure");
        BigDecimal income = MoneyUtil.parse(req.query("income"));
        BigDecimal obligations = req.query("obligations") == null ? BigDecimal.ZERO : MoneyUtil.parse(req.query("obligations"));
        if (amount == null || amount.signum() <= 0 || tenureText == null || !tenureText.matches("\\d+")
                || Integer.parseInt(tenureText) <= 0) {
            throw new ValidationException("Enter amount and tenure");
        }
        int tenure = Integer.parseInt(tenureText);
        BigDecimal emi = LoanCalculator.emi(amount, rule.annualRate(), tenure);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("annualRate", rule.annualRate().toPlainString());
        m.put("emiText", MoneyUtil.format(emi));
        if (income != null && income.signum() > 0) {
            m.put("foirPercentage", LoanCalculator.foir(obligations, emi, income).toPlainString());
            m.put("withinLimit", LoanCalculator.withinFoirLimit(obligations, emi, income, ctx.ruleBook().maxFoirPercent()));
        }
        return m;
    }

    private Object report(Request req) {
        String name = req.part(1);
        if (name == null) {
            throw new HttpError(404, "Not found");
        }
        List<ReportTable> tables;
        switch (name) {
            case "register": {
                String product = req.query("product");
                String status = req.query("status");
                tables = List.of(ctx.reports().applicationRegister(product == null ? null : Product.from(product),
                        status == null ? null : ApplicationStatus.from(status)));
                break;
            }
            case "pipeline":
                tables = ctx.reports().productPipeline();
                break;
            case "disbursements": {
                LocalDate from = DateUtil.parse(req.query("from"));
                LocalDate to = DateUtil.parse(req.query("to"));
                tables = ctx.reports().disbursements(from == null ? today().withDayOfMonth(1) : from,
                        to == null ? today() : to, req.query("period"));
                break;
            }
            case "overdue":
                tables = List.of(ctx.reports().overdueReviews());
                break;
            case "no-disbursement":
                tables = List.of(ctx.reports().customersWithoutDisbursement());
                break;
            case "repeat":
                tables = List.of(ctx.reports().repeatApplicants());
                break;
            default:
                throw new HttpError(404, "Unknown report");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        tables.forEach(t -> out.add(Views.report(t)));
        return out;
    }

    private Object utilities(Request req, String method, HttpExchange exchange) throws IOException {
        String what = req.part(1);
        if (what == null) {
            throw new HttpError(404, "Not found");
        }
        switch (what) {
            case "review-monitor":
                requirePost(method);
                return Views.run(ctx.reviewMonitor().run("MANUAL"));
            case "core-push": {
                requirePost(method);
                PushResult r = ctx.corePush().runOnce("MANUAL");
                return Map.of("pending", r.pending(), "pushed", r.pushed(), "errors", r.errors());
            }
            case "runs": {
                List<Map<String, Object>> list = new ArrayList<>();
                ctx.operations().recentRuns().forEach(r -> list.add(Views.run(r)));
                return list;
            }
            case "alerts": {
                List<Map<String, Object>> list = new ArrayList<>();
                ctx.operations().recentAlerts().forEach(a -> list.add(Views.alert(a)));
                return list;
            }
            case "core-banking": {
                List<Map<String, Object>> list = new ArrayList<>();
                ctx.operations().coreBankingRecords().forEach(r -> list.add(Views.core(r)));
                return list;
            }
            case "exports": {
                if (req.part(2) != null) {
                    String fileName = req.part(2);
                    if (!fileName.matches("[A-Za-z0-9_.-]+\\.csv")) {
                        throw new HttpError(400, "Invalid file name");
                    }
                    Path file = ctx.operations().exportDir().resolve(fileName);
                    if (!Files.exists(file)) {
                        throw new HttpError(404, "File not found");
                    }
                    Responses.file(exchange, file, "text/csv; charset=utf-8", true);
                    return null;
                }
                List<Map<String, Object>> list = new ArrayList<>();
                for (Path p : ctx.operations().exportFiles()) {
                    list.add(Map.of("name", p.getFileName().toString(), "size", Files.size(p)));
                }
                return list;
            }
            case "schedule": {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("reviewTime", ctx.scheduler().reviewTime().toString());
                m.put("intervalHours", ctx.config().reviewUtilityIntervalHours());
                m.put("nextReviewRun", DateUtil.format(ctx.scheduler().firstReviewRun()));
                m.put("pushIntervalSeconds", ctx.scheduler().pushIntervalSeconds());
                m.put("overdueDays", ctx.config().reviewOverdueDays());
                return m;
            }
            case "simulate-failure": {
                requirePost(method);
                String point = req.str("point");
                if (point == null) {
                    FailureSimulator.disarmAll();
                    return Map.of("armed", "");
                }
                String p = point.toUpperCase();
                if (!List.of(FailureSimulator.SUBMISSION, FailureSimulator.DECISION, FailureSimulator.DISBURSEMENT).contains(p)) {
                    throw new ValidationException("Unknown failure point " + point);
                }
                FailureSimulator.arm(p);
                return Map.of("armed", p);
            }
            default:
                throw new HttpError(404, "Not found");
        }
    }

    private Object settings(Request req, String method) {
        String what = req.part(1);
        if ("business-date".equals(what)) {
            if (method.equals("PUT")) {
                String value = req.str("date");
                ctx.clock().setBusinessDate(value == null ? null : DateUtil.parse(value));
            }
            return meta();
        }
        if ("rules".equals(what)) {
            if (method.equals("PUT")) {
                String product = req.part(2);
                if (product == null) {
                    Integer minAge = req.wholeNumber("minAge");
                    Integer maxAge = req.wholeNumber("maxAge");
                    if (minAge == null || maxAge == null) {
                        throw new ValidationException("Age limits are required");
                    }
                    ctx.ruleBook().updateCommon(minAge, maxAge, req.decimal("maxFoir"));
                } else {
                    Integer minTenure = req.wholeNumber("minTenure");
                    Integer maxTenure = req.wholeNumber("maxTenure");
                    BigDecimal minAmount = req.decimal("minAmount");
                    BigDecimal maxAmount = req.decimal("maxAmount");
                    BigDecimal minIncome = req.decimal("minIncome");
                    BigDecimal rate = req.decimal("annualRate");
                    if (minTenure == null || maxTenure == null || minAmount == null || maxAmount == null
                            || minIncome == null || rate == null) {
                        throw new ValidationException("All product rule fields are required");
                    }
                    ctx.ruleBook().update(new ProductRule(Product.from(product), minAmount, maxAmount, minTenure,
                            maxTenure, minIncome, rate, "true".equalsIgnoreCase(req.str("coBorrowerAllowed"))));
                }
            }
            return rules();
        }
        throw new HttpError(404, "Not found");
    }

    private Map<String, Object> rules() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("minAge", ctx.ruleBook().minAge());
        m.put("maxAge", ctx.ruleBook().maxAge());
        m.put("maxFoir", ctx.ruleBook().maxFoirPercent().toPlainString());
        List<Map<String, Object>> products = new ArrayList<>();
        for (ProductRule r : ctx.ruleBook().allRules()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("product", r.product().name());
            p.put("minAmount", r.minAmount().toPlainString());
            p.put("maxAmount", r.maxAmount().toPlainString());
            p.put("minTenure", r.minTenure());
            p.put("maxTenure", r.maxTenure());
            p.put("minIncome", r.minIncome().toPlainString());
            p.put("annualRate", r.annualRate().toPlainString());
            p.put("coBorrowerAllowed", r.coBorrowerAllowed());
            products.add(p);
        }
        m.put("products", products);
        return m;
    }

    private static void requirePost(String method) {
        if (!method.equals("POST")) {
            throw new HttpError(405, "Method not allowed");
        }
    }
}
