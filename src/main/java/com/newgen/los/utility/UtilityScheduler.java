package com.newgen.los.utility;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UtilityScheduler {

    private static final Logger LOG = Logger.getLogger(UtilityScheduler.class.getName());

    private final ReviewMonitorUtility reviewMonitor;
    private final CoreBankingPushUtility corePush;
    private final LocalTime reviewTime;
    private final int reviewIntervalHours;
    private final int pushIntervalSeconds;
    private ScheduledExecutorService executor;
    private LocalDateTime firstReviewRun;

    public UtilityScheduler(ReviewMonitorUtility reviewMonitor, CoreBankingPushUtility corePush,
                            LocalTime reviewTime, int reviewIntervalHours, int pushIntervalSeconds) {
        this.reviewMonitor = reviewMonitor;
        this.corePush = corePush;
        this.reviewTime = reviewTime;
        this.reviewIntervalHours = reviewIntervalHours;
        this.pushIntervalSeconds = pushIntervalSeconds;
    }

    public synchronized void start() {
        if (executor != null) {
            return;
        }
        executor = Executors.newScheduledThreadPool(2, new NamedThreadFactory());
        LocalDateTime now = LocalDateTime.now().withNano(0);
        long delay = secondsUntil(reviewTime, now);
        firstReviewRun = now.plusSeconds(delay);
        executor.scheduleAtFixedRate(() -> safely("review monitor", () -> reviewMonitor.run("SCHEDULED")),
                delay, TimeUnit.HOURS.toSeconds(reviewIntervalHours), TimeUnit.SECONDS);
        executor.scheduleWithFixedDelay(() -> safely("core push", () -> corePush.runOnce("SCHEDULED")),
                5, pushIntervalSeconds, TimeUnit.SECONDS);
        LOG.info(() -> "Review monitor scheduled for " + firstReviewRun + " every " + reviewIntervalHours
                + "h, core push every " + pushIntervalSeconds + "s");
    }

    public synchronized void stop() {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        executor = null;
    }

    public LocalDateTime firstReviewRun() {
        return firstReviewRun;
    }

    public LocalTime reviewTime() {
        return reviewTime;
    }

    public int pushIntervalSeconds() {
        return pushIntervalSeconds;
    }

    static long secondsUntil(LocalTime time, LocalDateTime now) {
        LocalDateTime next = now.toLocalDate().atTime(time);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return Duration.between(now, next).getSeconds();
    }

    // an exception escaping a scheduled task cancels all future runs, so never let one out
    private static void safely(String name, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, name + " run failed", e);
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "utility-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}
