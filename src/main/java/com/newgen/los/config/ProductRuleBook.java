package com.newgen.los.config;

import com.newgen.los.exception.ValidationException;
import com.newgen.los.model.Product;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class ProductRuleBook {

    private final Path file;
    private final Map<Product, ProductRule> rules = new EnumMap<>(Product.class);
    private int minAge;
    private int maxAge;
    private BigDecimal maxFoirPercent;
    private FileTime loadedAt;

    public ProductRuleBook(Path file) {
        this.file = file;
        reload();
    }

    public synchronized ProductRule ruleFor(Product product) {
        refreshIfChanged();
        ProductRule rule = rules.get(product);
        if (rule == null) {
            throw new IllegalStateException("No rules configured for " + product);
        }
        return rule;
    }

    public synchronized List<ProductRule> allRules() {
        refreshIfChanged();
        return new ArrayList<>(rules.values());
    }

    public synchronized int minAge() {
        refreshIfChanged();
        return minAge;
    }

    public synchronized int maxAge() {
        refreshIfChanged();
        return maxAge;
    }

    public synchronized BigDecimal maxFoirPercent() {
        refreshIfChanged();
        return maxFoirPercent;
    }

    public synchronized void update(ProductRule rule) {
        validate(rule);
        rules.put(rule.product(), rule);
        save();
    }

    public synchronized void updateCommon(int newMinAge, int newMaxAge, BigDecimal newMaxFoir) {
        if (newMinAge <= 0 || newMaxAge < newMinAge) {
            throw new ValidationException("Age limits are not valid");
        }
        if (newMaxFoir == null || newMaxFoir.signum() <= 0 || newMaxFoir.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ValidationException("FOIR limit must be between 0 and 100");
        }
        minAge = newMinAge;
        maxAge = newMaxAge;
        maxFoirPercent = newMaxFoir;
        save();
    }

    private void validate(ProductRule rule) {
        List<String> errors = new ArrayList<>();
        if (rule.minAmount().signum() <= 0 || rule.maxAmount().compareTo(rule.minAmount()) < 0) {
            errors.add("Amount range is not valid");
        }
        if (rule.minTenure() <= 0 || rule.maxTenure() < rule.minTenure()) {
            errors.add("Tenure range is not valid");
        }
        if (rule.minIncome().signum() < 0) {
            errors.add("Minimum income cannot be negative");
        }
        if (rule.annualRate().signum() <= 0) {
            errors.add("Interest rate must be positive");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private void refreshIfChanged() {
        try {
            if (Files.exists(file) && !Files.getLastModifiedTime(file).equals(loadedAt)) {
                reload();
            }
        } catch (IOException ignored) {
            // keep whatever was loaded last
        }
    }

    public synchronized void reload() {
        Properties props = new Properties();
        try {
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    props.load(reader);
                }
                loadedAt = Files.getLastModifiedTime(file);
            } else {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("product-rules.properties")) {
                    if (in != null) {
                        props.load(in);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read product rules from " + file, e);
        }

        minAge = Integer.parseInt(props.getProperty("customer.min.age", "21").trim());
        maxAge = Integer.parseInt(props.getProperty("customer.max.age", "60").trim());
        maxFoirPercent = new BigDecimal(props.getProperty("foir.max.percent", "50").trim());
        for (Product product : Product.values()) {
            String p = product.name() + ".";
            rules.put(product, new ProductRule(product,
                    new BigDecimal(required(props, p + "min.amount")),
                    new BigDecimal(required(props, p + "max.amount")),
                    Integer.parseInt(required(props, p + "min.tenure")),
                    Integer.parseInt(required(props, p + "max.tenure")),
                    new BigDecimal(required(props, p + "min.income")),
                    new BigDecimal(required(props, p + "annual.rate")),
                    Boolean.parseBoolean(props.getProperty(p + "co.borrower.allowed", "true").trim())));
        }
    }

    private static String required(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing product rule " + key);
        }
        return value.trim();
    }

    private void save() {
        StringBuilder out = new StringBuilder();
        out.append("# Lending parameters. Business can change these at any time,\n");
        out.append("# the application re-reads this file when it is modified.\n\n");
        out.append("customer.min.age=").append(minAge).append('\n');
        out.append("customer.max.age=").append(maxAge).append('\n');
        out.append("foir.max.percent=").append(maxFoirPercent.toPlainString()).append('\n');
        for (ProductRule rule : rules.values()) {
            String p = rule.product().name() + ".";
            out.append('\n');
            out.append(p).append("min.amount=").append(rule.minAmount().toPlainString()).append('\n');
            out.append(p).append("max.amount=").append(rule.maxAmount().toPlainString()).append('\n');
            out.append(p).append("min.tenure=").append(rule.minTenure()).append('\n');
            out.append(p).append("max.tenure=").append(rule.maxTenure()).append('\n');
            out.append(p).append("min.income=").append(rule.minIncome().toPlainString()).append('\n');
            out.append(p).append("annual.rate=").append(rule.annualRate().toPlainString()).append('\n');
            out.append(p).append("co.borrower.allowed=").append(rule.coBorrowerAllowed()).append('\n');
        }
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                writer.write(out.toString());
            }
            loadedAt = Files.getLastModifiedTime(file);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save product rules to " + file, e);
        }
    }
}
