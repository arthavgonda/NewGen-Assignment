package com.newgen.los.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;

public final class LoanCalculator {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MONTHS_X_PERCENT = BigDecimal.valueOf(1200);

    private LoanCalculator() {
    }

    // P * r * (1+r)^n / ((1+r)^n - 1) with r = rate / 12 / 100
    public static BigDecimal emi(BigDecimal principal, BigDecimal annualRatePercent, int months) {
        if (principal == null || annualRatePercent == null || months <= 0) {
            throw new IllegalArgumentException("Principal, rate and a positive tenure are required");
        }
        if (annualRatePercent.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        }
        BigDecimal r = annualRatePercent.divide(MONTHS_X_PERCENT, MC);
        BigDecimal growth = BigDecimal.ONE.add(r).pow(months, MC);
        BigDecimal emi = principal.multiply(r, MC).multiply(growth, MC)
                .divide(growth.subtract(BigDecimal.ONE), MC);
        return emi.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal foir(BigDecimal obligations, BigDecimal roundedEmi, BigDecimal monthlyIncome) {
        return obligations.add(roundedEmi)
                .multiply(HUNDRED)
                .divide(monthlyIncome, 2, RoundingMode.HALF_UP);
    }

    // compare without rounding, otherwise 50.004% would pass as 50.00%
    public static boolean withinFoirLimit(BigDecimal obligations, BigDecimal roundedEmi,
                                          BigDecimal monthlyIncome, BigDecimal maxPercent) {
        BigDecimal outgo = obligations.add(roundedEmi).multiply(HUNDRED);
        BigDecimal allowed = monthlyIncome.multiply(maxPercent);
        return outgo.compareTo(allowed) <= 0;
    }

    public static int completedYears(LocalDate dateOfBirth, LocalDate onDate) {
        return Period.between(dateOfBirth, onDate).getYears();
    }
}
