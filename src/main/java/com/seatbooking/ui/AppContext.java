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

    /** Single status-bar callback registered by MainWindowController. */
    private java.util.function.Consumer<String[]> statusCallback;

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

    /** Registers the status-bar callback (called by MainWindowController once). */
    public void setStatusCallback(java.util.function.Consumer<String[]> cb) {
        this.statusCallback = cb;
    }

    /**
     * Shows a status message in the main window's status bar.
     * Thread-safe — may be called from any thread.
     *
     * @param msg     human-readable message
     * @param success true → green success style; false → red error style
     */
    public void showStatus(String msg, boolean success) {
        if (statusCallback == null) return;
        String[] args = { msg, success ? "ok" : "err" };
        if (Platform.isFxApplicationThread()) {
            statusCallback.accept(args);
        } else {
            Platform.runLater(() -> statusCallback.accept(args));
        }
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
