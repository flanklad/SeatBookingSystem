package com.seatbooking.ui;

import com.seatbooking.analytics.UtilisationTracker;
import com.seatbooking.engine.BookingEngine;
import com.seatbooking.engine.ScheduleEngine;
import com.seatbooking.scheduler.SeatBlockScheduler;
import com.seatbooking.store.JsonDataStore;
import javafx.application.Platform;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Singleton that owns all backend service objects and acts as a simple
 * event bus for UI refresh notifications.
 *
 * Must be initialised via {@link #init()} (in Application.init()) before
 * the scene graph is built.
 */
public class AppContext {

    private static AppContext INSTANCE;

    private final JsonDataStore       store;
    private final ScheduleEngine      schedule;
    private final BookingEngine       engine;
    private final UtilisationTracker  analytics;
    private final SeatBlockScheduler  scheduler;

    private LocalDate currentDate = LocalDate.now();

    /** All registered UI refresh callbacks. */
    private final List<Runnable> refreshListeners = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────────────────

    private AppContext() {
        store     = new JsonDataStore();
        store.loadOrInit();
        schedule  = new ScheduleEngine(store);
        engine    = new BookingEngine(store, schedule);
        analytics = new UtilisationTracker(store);
        scheduler = new SeatBlockScheduler(engine);
        // After the 3 PM auto-block, push a refresh to all open views.
        scheduler.setAfterBlockHook(this::fireRefresh);
        scheduler.start();
    }

    /** Call once from {@code Application.init()} before the scene graph starts. */
    public static void init() {
        if (INSTANCE == null) INSTANCE = new AppContext();
    }

    public static AppContext get() {
        if (INSTANCE == null) throw new IllegalStateException("AppContext.init() was not called.");
        return INSTANCE;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Refresh bus
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Registers a callback to be invoked whenever data changes.
     * Controllers call this in their {@code initialize()} method.
     */
    public void addRefreshListener(Runnable listener) {
        refreshListeners.add(listener);
    }

    /**
     * Notifies all registered listeners.  Thread-safe: always dispatches on
     * the JavaFX Application Thread so listeners can safely modify the scene.
     */
    public void fireRefresh() {
        if (Platform.isFxApplicationThread()) {
            refreshListeners.forEach(Runnable::run);
        } else {
            Platform.runLater(() -> refreshListeners.forEach(Runnable::run));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    public void shutdown() {
        scheduler.stop();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Accessors
    // ─────────────────────────────────────────────────────────────────────────

    public JsonDataStore       getStore()      { return store; }
    public ScheduleEngine      getSchedule()   { return schedule; }
    public BookingEngine       getEngine()     { return engine; }
    public UtilisationTracker  getAnalytics()  { return analytics; }
    public SeatBlockScheduler  getScheduler()  { return scheduler; }
    public LocalDate           getCurrentDate()             { return currentDate; }
    public void                setCurrentDate(LocalDate d)  { this.currentDate = d; }
}
