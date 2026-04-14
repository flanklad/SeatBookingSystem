package com.seatbooking.scheduler;

import com.seatbooking.engine.BookingEngine;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Schedules the daily 15:00 auto-block of unbooked seats.
 * Uses a single-thread ScheduledExecutorService.
 */
public class SeatBlockScheduler {

    private static final LocalTime THREE_PM = LocalTime.of(15, 0);

    private final BookingEngine            engine;
    private final ScheduledExecutorService executor;
    private       ScheduledFuture<?>       future;
    private       Runnable                 afterBlockHook; // optional UI refresh callback

    public SeatBlockScheduler(BookingEngine engine) {
        this.engine   = engine;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "seat-block-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /** Starts the scheduler; fires every day at 15:00. */
    public void start() {
        long delaySeconds = computeInitialDelay();
        long periodSeconds = TimeUnit.DAYS.toSeconds(1);

        future = executor.scheduleAtFixedRate(
            this::runBlock,
            delaySeconds,
            periodSeconds,
            TimeUnit.SECONDS
        );

        System.out.printf("[Scheduler] 3PM auto-block scheduled (first run in %d min %d sec)%n",
            delaySeconds / 60, delaySeconds % 60);
    }

    public void stop() {
        if (future != null) future.cancel(false);
        executor.shutdownNow();
    }

    /** Computes seconds until the next 15:00 today (or tomorrow if past 15:00). */
    private long computeInitialDelay() {
        LocalDateTime now     = LocalDateTime.now();
        LocalDateTime next3PM = now.toLocalDate().atTime(THREE_PM);
        if (!now.isBefore(next3PM)) {
            next3PM = next3PM.plusDays(1);
        }
        return ChronoUnit.SECONDS.between(now, next3PM);
    }

    private void runBlock() {
        LocalDate today = LocalDate.now();
        try {
            int blocked = engine.blockUnbookedSeats(today);
            System.out.printf("[Scheduler] %s 15:00 — blocked %d unbooked seat(s).%n", today, blocked);
            if (afterBlockHook != null) afterBlockHook.run();
        } catch (Exception e) {
            System.err.println("[Scheduler] Error during auto-block: " + e.getMessage());
        }
    }

    /** Optional hook invoked after a block run (use Platform.runLater in the hook if needed). */
    public void setAfterBlockHook(Runnable hook) { this.afterBlockHook = hook; }

    /** Manual trigger for simulation / testing from the CLI. */
    public int triggerNow(LocalDate date) {
        int blocked = engine.blockUnbookedSeats(date);
        System.out.printf("[Scheduler] Manual trigger: blocked %d seat(s) on %s.%n", blocked, date);
        return blocked;
    }
}
