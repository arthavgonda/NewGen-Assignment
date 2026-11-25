package com.newgen.los.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyUtilTest {

    @Test
    void indianGrouping() {
        assertEquals("0.00", MoneyUtil.format(BigDecimal.ZERO));
        assertEquals("999.00", MoneyUtil.format(new BigDecimal("999")));
        assertEquals("8,884.88", MoneyUtil.format(new BigDecimal("8884.88")));
        assertEquals("50,000.00", MoneyUtil.format(new BigDecimal("50000")));
        assertEquals("1,00,000.00", MoneyUtil.format(new BigDecimal("100000")));
        assertEquals("10,00,000.00", MoneyUtil.format(new BigDecimal("1000000")));
        assertEquals("1,23,45,678.50", MoneyUtil.format(new BigDecimal("12345678.5")));
        assertEquals("12,34,56,78,901.00", MoneyUtil.format(new BigDecimal("12345678901")));
        assertEquals("-1,00,000.00", MoneyUtil.format(new BigDecimal("-100000")));
    }

    @Test
    void roundsHalfUp() {
        assertEquals("10.01", MoneyUtil.format(new BigDecimal("10.005")));
    }

    @Test
    void parsesFormattedInput() {
        assertEquals(new BigDecimal("100000"), MoneyUtil.parse("1,00,000"));
        assertEquals(new BigDecimal("2500.50"), MoneyUtil.parse("INR 2,500.50"));
        assertThrows(IllegalArgumentException.class, () -> MoneyUtil.parse("abc"));
    }
}
