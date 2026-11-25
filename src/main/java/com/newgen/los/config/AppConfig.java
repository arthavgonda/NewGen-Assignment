package com.newgen.los.config;

import com.newgen.los.model.Branch;
import com.newgen.los.util.DateUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

public class AppConfig {

    private final Properties props;

    public AppConfig(Properties props) {
        this.props = props;
    }

    // env vars LOS_DB_URL / LOS_DB_USER / LOS_DB_PASSWORD override the file
    public static AppConfig load() {
        Properties props = new Properties();
        Path file = Path.of(System.getProperty("los.config", "config/application.properties"));
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                props.load(reader);
            } catch (IOException e) {
                throw new IllegalStateException("Could not read " + file, e);
            }
        } else {
            try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream("application.properties")) {
                if (in != null) {
                    props.load(in);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Could not read default configuration", e);
            }
        }
        Path local = Path.of("config/application-local.properties");
        if (Files.exists(local)) {
            try (Reader reader = Files.newBufferedReader(local, StandardCharsets.UTF_8)) {
                props.load(reader);
            } catch (IOException e) {
                throw new IllegalStateException("Could not read " + local, e);
            }
        }
        override(props, "db.url", "LOS_DB_URL");
        override(props, "db.user", "LOS_DB_USER");
        override(props, "db.password", "LOS_DB_PASSWORD");
        return new AppConfig(props);
    }

    private static void override(Properties props, String key, String env) {
        String value = System.getenv(env);
        if (value != null && !value.isBlank()) {
            props.setProperty(key, value);
        }
    }

    public String get(String key, String defaultValue) {
        String value = props.getProperty(key);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    public int getInt(String key, int defaultValue) {
        return Integer.parseInt(get(key, String.valueOf(defaultValue)));
    }

    public String dbUrl() {
        return get("db.url", "jdbc:mysql://localhost:3306/loan_origination?createDatabaseIfNotExist=true");
    }

    public String dbUser() {
        return get("db.user", "root");
    }

    public String dbPassword() {
        return props.getProperty("db.password", "");
    }

    public Optional<LocalDate> businessDate() {
        return Optional.ofNullable(DateUtil.parse(get("business.date", null)));
    }

    public boolean webEnabled() {
        return Boolean.parseBoolean(get("web.enabled", "true"));
    }

    public int webPort() {
        return getInt("web.port", 8080);
    }

    public LocalTime reviewUtilityTime() {
        return LocalTime.parse(get("review.utility.time", "09:00"));
    }

    public int reviewUtilityIntervalHours() {
        return getInt("review.utility.interval.hours", 24);
    }

    public int reviewOverdueDays() {
        return getInt("review.overdue.days", 2);
    }

    public int corePushIntervalSeconds() {
        return getInt("core.push.interval.seconds", 30);
    }

    public Path exportDir() {
        return Path.of(get("export.dir", "exports"));
    }

    public Path formsDir() {
        return Path.of(get("forms.dir", "application-forms"));
    }

    public Path productRulesFile() {
        return Path.of(get("product.rules.file", "config/product-rules.properties"));
    }

    public List<Branch> branches() {
        List<Branch> branches = new ArrayList<>();
        for (String entry : get("branches", "NOI001:Noida Sector 62").split(",")) {
            String[] parts = entry.split(":", 2);
            if (parts.length == 2) {
                branches.add(new Branch(parts[0].trim(), parts[1].trim()));
            }
        }
        return branches;
    }
}
