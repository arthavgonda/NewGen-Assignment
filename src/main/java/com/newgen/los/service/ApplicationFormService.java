package com.newgen.los.service;

import com.newgen.los.model.Customer;
import com.newgen.los.model.LoanApplication;
import com.newgen.los.util.DateUtil;
import com.newgen.los.util.MoneyUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

public class ApplicationFormService {

    private final Path formsDir;

    public ApplicationFormService(Path formsDir) {
        this.formsDir = formsDir;
    }

    public Path generate(LoanApplication app, Customer customer, LocalDateTime generatedAt) throws IOException {
        Files.createDirectories(formsDir);
        Path file = formsDir.resolve(app.getApplicationNumber() + "_application_form.html");
        Files.writeString(file, render(app, customer, generatedAt), StandardCharsets.UTF_8);
        return file;
    }

    String render(LoanApplication app, Customer customer, LocalDateTime generatedAt) {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset=\"utf-8\"><title>Application Form ")
                .append(esc(app.getApplicationNumber())).append("</title><style>")
                .append("body{font-family:Georgia,'Times New Roman',serif;color:#222;margin:40px auto;max-width:760px}")
                .append("h1{font-size:22px;margin:0}h2{font-size:15px;border-bottom:1px solid #999;padding-bottom:4px;margin-top:28px}")
                .append("table{width:100%;border-collapse:collapse;font-size:14px}td{padding:5px 6px;border-bottom:1px dotted #bbb}")
                .append("td.k{width:40%;color:#555}.top{display:flex;justify-content:space-between;align-items:flex-end;")
                .append("border-bottom:2px solid #222;padding-bottom:10px}.sign{display:flex;justify-content:space-between;margin-top:60px}")
                .append(".sign div{border-top:1px solid #222;width:200px;text-align:center;padding-top:4px;font-size:13px}")
                .append("@media print{body{margin:10mm}}</style></head><body>");
        html.append("<div class=\"top\"><div><h1>Loan Application Form</h1><div>")
                .append(esc(app.getBranchName())).append(" (").append(esc(app.getBranchCode())).append(")</div></div>")
                .append("<div style=\"text-align:right\">No. <b>").append(esc(app.getApplicationNumber())).append("</b><br>")
                .append("Generated ").append(DateUtil.format(generatedAt)).append("</div></div>");

        html.append("<h2>Applicant</h2><table>");
        row(html, "Customer ID", String.valueOf(customer.getCustomerId()));
        row(html, "Name", customer.getName());
        row(html, "Date of birth", DateUtil.format(customer.getDateOfBirth()));
        row(html, "Mobile", customer.getMobile());
        row(html, "Email", customer.getEmail());
        row(html, "Address", customer.getAddress());
        row(html, "KYC", customer.getKycDetails());
        html.append("</table>");

        html.append("<h2>Loan details</h2><table>");
        row(html, "Product", app.getProduct().name());
        row(html, "Requested amount", MoneyUtil.inr(app.getRequestedAmount()));
        row(html, "Tenure", app.getTenureMonths() + " months");
        row(html, "Annual interest rate", app.getAnnualInterestRate() == null ? "" : app.getAnnualInterestRate().toPlainString() + "%");
        row(html, "Monthly net income", MoneyUtil.inr(app.getMonthlyIncome()));
        row(html, "Existing monthly obligations", MoneyUtil.inr(app.getExistingObligations()));
        row(html, "Co-borrower", app.hasCoBorrower() ? app.getCoBorrowerName() : "None");
        row(html, "Proposed EMI", MoneyUtil.inr(app.getProposedEmi()));
        row(html, "FOIR", app.getFoirPercentage() == null ? "" : app.getFoirPercentage().toPlainString() + "%");
        html.append("</table>");

        html.append("<h2>Approval</h2><table>");
        row(html, "Submitted on", DateUtil.format(app.getSubmittedAt()));
        row(html, "Reviewed by", app.getReviewerCode() + " on " + DateUtil.format(app.getDecidedAt()));
        row(html, "Credit approved by", app.getCreditApproverCode() + " on " + DateUtil.format(app.getCreditDecidedAt()));
        row(html, "Sanctioned amount", MoneyUtil.inr(app.getApprovedAmount()));
        row(html, "Remarks", app.getDecisionReason());
        html.append("</table>");

        html.append("<div class=\"sign\"><div>Applicant</div><div>Credit Officer</div></div></body></html>");
        return html.toString();
    }

    private static void row(StringBuilder html, String key, String value) {
        html.append("<tr><td class=\"k\">").append(esc(key)).append("</td><td>")
                .append(esc(value == null ? "" : value)).append("</td></tr>");
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
