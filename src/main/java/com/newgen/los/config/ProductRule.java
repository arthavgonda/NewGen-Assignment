package com.newgen.los.config;

import com.newgen.los.model.Product;

import java.math.BigDecimal;

public record ProductRule(Product product,
                          BigDecimal minAmount,
                          BigDecimal maxAmount,
                          int minTenure,
                          int maxTenure,
                          BigDecimal minIncome,
                          BigDecimal annualRate,
                          boolean coBorrowerAllowed) {
}
