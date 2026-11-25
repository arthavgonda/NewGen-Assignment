package com.newgen.los.service;

import com.newgen.los.config.ProductRuleBook;
import com.newgen.los.model.Customer;
import com.newgen.los.model.EligibilityResult;
import com.newgen.los.model.LoanApplication;
import com.newgen.los.model.Product;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EligibilityAssessorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);

    @TempDir
    static Path dir;

    private static EligibilityAssessor assessor;

    @BeforeAll
    static void setUp() throws IOException {
        Path rules = dir.resolve("rules.properties");
        Files.copy(Path.of("config/product-rules.properties"), rules);
        assessor = new EligibilityAssessor(new ProductRuleBook(rules));
    }

    private static Customer customerAged(int years) {
        Customer c = new Customer();
        c.setName("Test");
        c.setDateOfBirth(TODAY.minusYears(years));
        return c;
    }

    private static LoanApplication app(Product product, String amount, int tenure, String income, String obligations) {
        LoanApplication a = new LoanApplication();
        a.setProduct(product);
        a.setRequestedAmount(new BigDecimal(amount));
        a.setTenureMonths(tenure);
        a.setMonthlyIncome(new BigDecimal(income));
        a.setExistingObligations(new BigDecimal(obligations));
        return a;
    }

    @ParameterizedTest(name = "age {0} eligible={1}")
    @CsvSource({"20,false", "21,true", "60,true", "61,false"})
    void ageBoundaries(int age, boolean eligible) {
        EligibilityResult r = assessor.assess(customerAged(age), app(Product.PERSONAL, "100000", 12, "30000", "5000"), TODAY);
        assertEquals(eligible, r.eligible(), r.reasonsText());
    }

    @Test
    void sixtyYearsAndElevenMonthsIsStillSixty() {
        Customer c = new Customer();
        c.setDateOfBirth(TODAY.minusYears(61).plusDays(1));
        assertTrue(assessor.assess(c, app(Product.PERSONAL, "100000", 12, "30000", "5000"), TODAY).eligible());
    }

    @Test
    void futureDateOfBirthFails() {
        Customer c = new Customer();
        c.setDateOfBirth(TODAY.plusDays(1));
        EligibilityResult r = assessor.assess(c, app(Product.PERSONAL, "100000", 12, "30000", "5000"), TODAY);
        assertFalse(r.eligible());
        assertTrue(r.failedRules().get(0).contains("future"));
    }

    @ParameterizedTest(name = "PERSONAL {0} for {1} months eligible={2}")
    @CsvSource({
            "49999.99,24,false", "50000,24,true", "500000,60,true", "500000.01,60,false",
            "100000,11,false", "100000,12,true", "100000,60,true", "100000,61,false"})
    void personalBoundaries(String amount, int tenure, boolean eligible) {
        EligibilityResult r = assessor.assess(customerAged(30), app(Product.PERSONAL, amount, tenure, "200000", "0"), TODAY);
        assertEquals(eligible, r.eligible(), r.reasonsText());
    }

    @ParameterizedTest(name = "EDUCATION {0} for {1} months eligible={2}")
    @CsvSource({
            "99999.99,24,false", "100000,24,true", "1000000,84,true", "1000000.01,84,false",
            "200000,11,false", "200000,12,true", "200000,84,true", "200000,85,false"})
    void educationBoundaries(String amount, int tenure, boolean eligible) {
        EligibilityResult r = assessor.assess(customerAged(30), app(Product.EDUCATION, amount, tenure, "500000", "0"), TODAY);
        assertEquals(eligible, r.eligible(), r.reasonsText());
    }

    @Test
    void minimumIncomeIsInclusive() {
        assertTrue(assessor.assess(customerAged(30), app(Product.PERSONAL, "50000", 60, "20000", "0"), TODAY).eligible());
        assertFalse(assessor.assess(customerAged(30), app(Product.PERSONAL, "50000", 60, "19999.99", "0"), TODAY).eligible());
        assertTrue(assessor.assess(customerAged(30), app(Product.EDUCATION, "100000", 84, "15000", "0"), TODAY).eligible());
    }

    @Test
    void foirExactlyFiftyPassesAndAboveFails() {
        assertTrue(assessor.assess(customerAged(30), app(Product.PERSONAL, "100000", 12, "30000", "6115.12"), TODAY).eligible());
        EligibilityResult above = assessor.assess(customerAged(30), app(Product.PERSONAL, "100000", 12, "30000", "6115.13"), TODAY);
        assertFalse(above.eligible());
        assertTrue(above.failedRules().get(0).startsWith("FOIR of 50.0000"), above.reasonsText());
    }

    @Test
    void workedExampleNumbersAreStored() {
        EligibilityResult r = assessor.assess(customerAged(30), app(Product.PERSONAL, "100000", 12, "30000", "7000"), TODAY);
        assertEquals(new BigDecimal("8884.88"), r.emi());
        assertEquals(new BigDecimal("52.95"), r.foirPercentage());
        assertEquals(new BigDecimal("12"), r.annualRate());
        assertFalse(r.eligible());
    }

    @Test
    void educationDoesNotAllowCoBorrower() {
        LoanApplication a = app(Product.EDUCATION, "200000", 36, "50000", "0");
        a.setCoBorrowerName("Parent");
        EligibilityResult r = assessor.assess(customerAged(25), a, TODAY);
        assertFalse(r.eligible());
        assertTrue(r.reasonsText().contains("Co-borrower"));

        LoanApplication p = app(Product.PERSONAL, "200000", 36, "50000", "0");
        p.setCoBorrowerName("Spouse");
        assertTrue(assessor.assess(customerAged(25), p, TODAY).eligible());
    }

    @Test
    void allFailedRulesAreReported() {
        EligibilityResult r = assessor.assess(customerAged(19), app(Product.PERSONAL, "600000", 72, "10000", "9000"), TODAY);
        assertEquals(5, r.failedRules().size(), r.reasonsText());
    }
}
