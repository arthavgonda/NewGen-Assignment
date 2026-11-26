package com.newgen.los.utility;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UtilitySchedulerTest {

    @Test
    void waitsUntilLaterToday() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 24, 8, 30);
        assertEquals(30 * 60, UtilityScheduler.secondsUntil(LocalTime.of(9, 0), now));
    }

    @Test
    void rollsOverToTomorrowOncePassed() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 24, 9, 0);
        assertEquals(24 * 3600, UtilityScheduler.secondsUntil(LocalTime.of(9, 0), now));
    }
}
