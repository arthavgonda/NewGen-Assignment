package com.newgen.los.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoanCalculatorTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void workedExampleEmi() {
        assertEquals(bd("8884.88"), LoanCalculator.emi(bd("100000"), bd("12"), 12));
    }

    @Test
    void workedExampleFoirPasses() {
        BigDecimal emi = LoanCalculator.emi(bd("100000"), bd("12"), 12);
        assertEquals(bd("46.28"), LoanCalculator.foir(bd("5000"), emi, bd("30000")));
        assertTrue(LoanCalculator.withinFoirLimit(bd("5000"), emi, bd("30000"), bd("50")));
    }

    @Test
    void workedExampleFoirFails() {
        BigDecimal emi = LoanCalculator.emi(bd("100000"), bd("12"), 12);
        assertEquals(bd("52.95"), LoanCalculator.foir(bd("7000"), emi, bd("30000")));
        assertFalse(LoanCalculator.withinFoirLimit(bd("7000"), emi, bd("30000"), bd("50")));
    }

    @Test
    void foirExactlyFiftyIsAllowed() {
        // 8884.88 + 6115.12 = 15000, half of 30000
        BigDecimal emi = bd("8884.88");
        assertEquals(bd("50.00"), LoanCalculator.foir(bd("6115.12"), emi, bd("30000")));
        assertTrue(LoanCalculator.withinFoirLimit(bd("6115.12"), emi, bd("30000"), bd("50")));
    }

    @Test
    void foirOnePaiseAboveFiftyIsRejectedEvenThoughItRoundsToFifty() {
        BigDecimal emi = bd("8884.88");
        assertEquals(bd("50.00"), LoanCalculator.foir(bd("6115.13"), emi, bd("30000")));
        assertFalse(LoanCalculator.withinFoirLimit(bd("6115.13"), emi, bd("30000"), bd("50")));
    }

    @Test
    void educationEmi() {
        assertEquals(bd("10623.52"), LoanCalculator.emi(bd("500000"), bd("10"), 60));
    }

    @Test
    void completedYearsCountsOnlyFullYears() {
        LocalDate on = LocalDate.of(2026, 11, 24);
        assertEquals(20, LoanCalculator.completedYears(LocalDate.of(2005, 11, 25), on));
        assertEquals(21, LoanCalculator.completedYears(LocalDate.of(2005, 11, 24), on));
        assertEquals(60, LoanCalculator.completedYears(LocalDate.of(1965, 11, 25), on));
        assertEquals(61, LoanCalculator.completedYears(LocalDate.of(1965, 11, 24), on));
    }
}
