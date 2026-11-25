package com.newgen.los.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

public class BusinessClock {

    private final AtomicReference<LocalDate> fixedDate = new AtomicReference<>();

    public BusinessClock() {
    }

    public BusinessClock(LocalDate date) {
        fixedDate.set(date);
    }

    public LocalDate today() {
        LocalDate date = fixedDate.get();
        return date != null ? date : LocalDate.now();
    }

    public LocalDateTime now() {
        return LocalDateTime.of(today(), LocalTime.now().truncatedTo(ChronoUnit.SECONDS));
    }

    public void setBusinessDate(LocalDate date) {
        fixedDate.set(date);
    }

    public boolean isUsingSystemDate() {
        return fixedDate.get() == null;
    }
}
