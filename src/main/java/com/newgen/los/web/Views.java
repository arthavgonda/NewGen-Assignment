package com.newgen.los.web;

import com.newgen.los.model.Customer;
import com.newgen.los.model.CoreBankingRecord;
import com.newgen.los.model.Disbursement;
import com.newgen.los.model.EligibilityResult;
import com.newgen.los.model.HistoryEntry;
import com.newgen.los.model.LoanApplication;
import com.newgen.los.model.ReportTable;
import com.newgen.los.model.SystemAlert;
import com.newgen.los.model.UtilityRun;
import com.newgen.los.service.LoanCalculator;
import com.newgen.los.util.DateUtil;
import com.newgen.los.util.Display;
import com.newgen.los.util.MoneyUtil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Views {

    private Views() {
    }

    static Map<String, Object> customer(Customer c, LocalDate today) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("customerId", c.getCustomerId());
        m.put("name", c.getName());
        m.put("dateOfBirth", c.getDateOfBirth() == null ? null : c.getDateOfBirth().toString());
        m.put("dateOfBirthText", DateUtil.format(c.getDateOfBirth()));
        m.put("age", c.getDateOfBirth() == null || c.getDateOfBirth().isAfter(today) ? null
                : LoanCalculator.completedYears(c.getDateOfBirth(), today));
        m.put("mobile", c.getMobile());
        m.put("email", c.getEmail());
        m.put("address", c.getAddress());
        m.put("kycType", c.getKycType());
        m.put("kycNumber", c.getKycNumber());
        m.put("createdAt", DateUtil.format(c.getCreatedAt()));
        return m;
    }

    static Map<String, Object> application(LoanApplication a, LocalDate today) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("applicationId", a.getApplicationId());
        m.put("applicationNumber", a.getApplicationNumber());
        m.put("customerId", a.getCustomerId());
        m.put("customerName", a.getCustomerName());
        m.put("product", a.getProduct().name());
        m.put("branchCode", a.getBranchCode());
        m.put("branchName", a.getBranchName());
        m.put("requestedAmount", plain(a.getRequestedAmount()));
        m.put("requestedAmountText", MoneyUtil.format(a.getRequestedAmount()));
        m.put("tenureMonths", a.getTenureMonths());
        m.put("monthlyIncome", plain(a.getMonthlyIncome()));
        m.put("monthlyIncomeText", MoneyUtil.format(a.getMonthlyIncome()));
        m.put("existingObligations", plain(a.getExistingObligations()));
        m.put("existingObligationsText", MoneyUtil.format(a.getExistingObligations()));
        m.put("coBorrowerName", a.getCoBorrowerName());
        m.put("annualInterestRate", plain(a.getAnnualInterestRate()));
        m.put("proposedEmiText", MoneyUtil.format(a.getProposedEmi()));
        m.put("foirPercentage", plain(a.getFoirPercentage()));
        m.put("eligible", a.getEligible());
        m.put("eligibilityReasons", a.getEligibilityReasons());
        m.put("status", a.getStatus().name());
        m.put("statusLabel", a.getStatus().getLabel());
        m.put("approvedAmountText", MoneyUtil.format(a.getApprovedAmount()));
        m.put("hasApplicationForm", a.getApplicationFormPath() != null);
        m.put("createdBy", a.getCreatedBy());
        m.put("createdAt", DateUtil.format(a.getCreatedAt()));
        m.put("submittedAt", DateUtil.format(a.getSubmittedAt()));
        m.put("daysWaiting", a.getSubmittedAt() == null ? null
                : ChronoUnit.DAYS.between(a.getSubmittedAt().toLocalDate(), today));
        m.put("decidedAt", DateUtil.format(a.getDecidedAt()));
        m.put("reviewerCode", a.getReviewerCode());
        m.put("decisionReason", a.getDecisionReason());
        m.put("creditDecidedAt", DateUtil.format(a.getCreditDecidedAt()));
        m.put("creditApproverCode", a.getCreditApproverCode());
        return m;
    }

    static List<Map<String, Object>> applications(List<LoanApplication> apps, LocalDate today) {
        List<Map<String, Object>> list = new ArrayList<>();
        apps.forEach(a -> list.add(application(a, today)));
        return list;
    }

    static Map<String, Object> history(HistoryEntry h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("applicationId", h.getApplicationId());
        m.put("applicationNumber", h.getApplicationNumber());
        m.put("customerName", h.getCustomerName());
        m.put("action", h.getAction().name());
        m.put("oldStatus", h.getOldStatus() == null ? null : h.getOldStatus().name());
        m.put("newStatus", h.getNewStatus().name());
        m.put("performedBy", h.getPerformedBy());
        m.put("performedAt", DateUtil.format(h.getPerformedAt()));
        m.put("remarks", h.getRemarks());
        return m;
    }

    static Map<String, Object> disbursement(Disbursement d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reference", d.getReference());
        m.put("amountText", MoneyUtil.format(d.getAmount()));
        m.put("disbursedAt", DateUtil.format(d.getDisbursedAt()));
        m.put("disbursedBy", d.getDisbursedBy());
        return m;
    }

    static Map<String, Object> assessment(EligibilityResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("annualRate", plain(r.annualRate()));
        m.put("emiText", MoneyUtil.format(r.emi()));
        m.put("foirPercentage", plain(r.foirPercentage()));
        m.put("eligible", r.eligible());
        m.put("failedRules", r.failedRules());
        return m;
    }

    static Map<String, Object> alert(SystemAlert a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("alertId", a.getAlertId());
        m.put("applicationId", a.getApplicationId());
        m.put("applicationNumber", a.getApplicationNumber());
        m.put("alertType", a.getAlertType().name());
        m.put("message", a.getMessage());
        m.put("createdAt", DateUtil.format(a.getCreatedAt()));
        return m;
    }

    static Map<String, Object> run(UtilityRun r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("runId", r.getRunId());
        m.put("utilityName", r.getUtilityName());
        m.put("triggerType", r.getTriggerType());
        m.put("businessDate", DateUtil.format(r.getBusinessDate()));
        m.put("startedAt", DateUtil.format(r.getStartedAt()));
        m.put("completedAt", DateUtil.format(r.getCompletedAt()));
        m.put("result", r.getResult());
        m.put("applicationsProcessed", r.getApplicationsProcessed());
        m.put("alertsCreated", r.getAlertsCreated());
        m.put("outputFile", r.getOutputFile() == null ? null : java.nio.file.Path.of(r.getOutputFile()).getFileName().toString());
        m.put("errorMessage", r.getErrorMessage());
        return m;
    }

    static Map<String, Object> core(CoreBankingRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("coreId", r.getCoreId());
        m.put("customerId", r.getCustomerId());
        m.put("applicationId", r.getApplicationId());
        m.put("applicationNumber", r.getApplicationNumber());
        m.put("customerName", r.getCustomerName());
        m.put("address", r.getAddress());
        m.put("dateOfBirth", DateUtil.format(r.getDateOfBirth()));
        m.put("kycDetails", r.getKycDetails());
        m.put("approvedAmountText", MoneyUtil.format(r.getApprovedAmount()));
        m.put("disbursedAmountText", MoneyUtil.format(r.getDisbursedAmount()));
        m.put("disbursedDate", DateUtil.format(r.getDisbursedDate()));
        m.put("pushedAt", DateUtil.format(r.getPushedAt()));
        return m;
    }

    static Map<String, Object> report(ReportTable t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", t.getTitle());
        List<String> headers = new ArrayList<>();
        List<Boolean> numeric = new ArrayList<>();
        for (String c : t.getColumns()) {
            headers.add(Display.heading(c));
            numeric.add(false);
        }
        List<List<String>> rows = new ArrayList<>();
        for (List<Object> row : t.getRows()) {
            List<String> cells = new ArrayList<>();
            for (int i = 0; i < row.size(); i++) {
                if (row.get(i) instanceof Number) {
                    numeric.set(i, true);
                }
                cells.add(Display.cell(row.get(i)));
            }
            rows.add(cells);
        }
        m.put("columns", headers);
        m.put("numeric", numeric);
        m.put("rows", rows);
        return m;
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
