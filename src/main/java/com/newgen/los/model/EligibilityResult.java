package com.newgen.los.model;

import java.math.BigDecimal;
import java.util.List;

public record EligibilityResult(BigDecimal annualRate,
                                BigDecimal emi,
                                BigDecimal foirPercentage,
                                List<String> failedRules) {

    public EligibilityResult {
        failedRules = List.copyOf(failedRules);
    }

    public boolean eligible() {
        return failedRules.isEmpty();
    }

    public String reasonsText() {
        return eligible() ? "All eligibility rules passed" : String.join("; ", failedRules);
    }
}
