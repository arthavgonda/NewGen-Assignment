package com.newgen.los.service;

import com.newgen.los.config.ProductRule;
import com.newgen.los.config.ProductRuleBook;
import com.newgen.los.model.Customer;
import com.newgen.los.model.EligibilityResult;
import com.newgen.los.model.LoanApplication;
import com.newgen.los.util.DateUtil;
import com.newgen.los.util.MoneyUtil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class EligibilityAssessor {

    private final ProductRuleBook ruleBook;

    public EligibilityAssessor(ProductRuleBook ruleBook) {
        this.ruleBook = ruleBook;
    }

    public EligibilityResult assess(Customer customer, LoanApplication app, LocalDate businessDate) {
        ProductRule rule = ruleBook.ruleFor(app.getProduct());
        List<String> failed = new ArrayList<>();

        LocalDate dob = customer.getDateOfBirth();
        if (dob.isAfter(businessDate)) {
            failed.add("Date of birth " + DateUtil.format(dob) + " is in the future");
        } else {
            int age = LoanCalculator.completedYears(dob, businessDate);
            if (age < ruleBook.minAge()) {
                failed.add("Applicant age " + age + " is below the minimum age of " + ruleBook.minAge());
            } else if (age > ruleBook.maxAge()) {
                failed.add("Applicant age " + age + " is above the maximum age of " + ruleBook.maxAge());
            }
        }

        BigDecimal amount = app.getRequestedAmount();
        if (amount.compareTo(rule.minAmount()) < 0 || amount.compareTo(rule.maxAmount()) > 0) {
            failed.add("Requested amount " + MoneyUtil.format(amount) + " is outside the " + rule.product()
                    + " range of " + MoneyUtil.format(rule.minAmount()) + " to " + MoneyUtil.format(rule.maxAmount()));
        }

        int tenure = app.getTenureMonths();
        if (tenure < rule.minTenure() || tenure > rule.maxTenure()) {
            failed.add("Tenure of " + tenure + " months is outside the " + rule.product() + " range of "
                    + rule.minTenure() + " to " + rule.maxTenure() + " months");
        }

        if (app.getMonthlyIncome().compareTo(rule.minIncome()) < 0) {
            failed.add("Monthly net income " + MoneyUtil.format(app.getMonthlyIncome()) + " is below the "
                    + rule.product() + " minimum of " + MoneyUtil.format(rule.minIncome()));
        }

        if (!rule.coBorrowerAllowed() && app.hasCoBorrower()) {
            failed.add("Co-borrower is not allowed for " + rule.product() + " loans");
        }

        BigDecimal emi = LoanCalculator.emi(amount, rule.annualRate(), tenure);
        BigDecimal obligations = app.getExistingObligations();
        BigDecimal foir = LoanCalculator.foir(obligations, emi, app.getMonthlyIncome());
        BigDecimal maxFoir = ruleBook.maxFoirPercent();
        if (!LoanCalculator.withinFoirLimit(obligations, emi, app.getMonthlyIncome(), maxFoir)) {
            BigDecimal shown = foir.compareTo(maxFoir) > 0 ? foir : obligations.add(emi)
                    .multiply(BigDecimal.valueOf(100)).divide(app.getMonthlyIncome(), 4, java.math.RoundingMode.HALF_UP);
            failed.add("FOIR of " + shown.toPlainString() + "% is above the allowed " + maxFoir.toPlainString() + "%");
        }

        return new EligibilityResult(rule.annualRate(), emi, foir, failed);
    }
}
