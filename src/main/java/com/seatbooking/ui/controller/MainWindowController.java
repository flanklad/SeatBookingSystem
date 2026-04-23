package com.seatbooking.ui.controller;

import com.seatbooking.model.Member;
import com.seatbooking.ui.AppContext;
import com.seatbooking.ui.LoginDialog;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Drives the outer shell: header labels, sidebar navigation, date picker,
 * prev/next day buttons, status bar, and swaps sub-views into the centre StackPane.
 */
public class MainWindowController {

    @FXML private StackPane contentArea;
    @FXML private Label     lblDate;
    @FXML private Label     lblDayType;
    @FXML private Label     lblWeek;
    @FXML private Label     lblUser;
    @FXML private DatePicker datePicker;

    // Nav buttons (needed to manage active state)
    @FXML private Button btnDashboard;
    @FXML private Button btnSeatMap;
    @FXML private Button btnBookings;
    @FXML private Button btnAnalytics;
    @FXML private Button btnMembers;
    @FXML private Button btnAdmin;

    // Status bar
    @FXML private HBox  statusBar;
    @FXML private Label lblStatus;

    private final AppContext            ctx        = AppContext.get();
    private final Map<String, Parent>   viewCache  = new HashMap<>();
    private Button                      activeNav  = null;
    private Timeline                    statusFade;

    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        datePicker.setValue(ctx.getCurrentDate());
        updateHeader();
        updateUserBadge();
        ctx.addRefreshListener(this::updateHeader);

        // Register status-bar callback
        ctx.setStatusCallback(args -> showStatusBar(args[0], "ok".equals(args[1])));

        showDashboard();   // default view on launch
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Header
    // ─────────────────────────────────────────────────────────────────────────

    private void updateHeader() {
        LocalDate date = ctx.getCurrentDate();
        lblDate.setText(date + "  (" + date.getDayOfWeek() + ")");
        lblDayType.setText(ctx.getSchedule().describeDayType(date));
        lblWeek.setText("Fortnight Week " + ctx.getSchedule().getFortnightWeek(date));
    }

    private void updateUserBadge() {
        if (ctx.getCurrentUser() == null) return;
        boolean admin = ctx.isAdmin();
        lblUser.setText(ctx.getCurrentUser().getName() + "  [" + (admin ? "ADMIN" : "EMPLOYEE") + "]");
        lblUser.setStyle(admin
            ? "-fx-text-fill: #f38ba8; -fx-font-weight: bold;"   // red  for admin
            : "-fx-text-fill: #a6e3a1; -fx-font-weight: bold;"); // green for employee

        // Admin panel is only visible to admins
        btnAdmin.setVisible(admin);
        btnAdmin.setManaged(admin);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Date navigation
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void onDateChanged() {
        LocalDate selected = datePicker.getValue();
        if (selected != null) {
            ctx.setCurrentDate(selected);
            ctx.getEngine().initializeDayIfNeeded(selected);
            ctx.fireRefresh();
        }
    }

    @FXML
    private void prevDay() {
        LocalDate d = ctx.getCurrentDate().minusDays(1);
        ctx.setCurrentDate(d);
        datePicker.setValue(d);
        ctx.getEngine().initializeDayIfNeeded(d);
        ctx.fireRefresh();
    }

    @FXML
    private void nextDay() {
        LocalDate d = ctx.getCurrentDate().plusDays(1);
        ctx.setCurrentDate(d);
        datePicker.setValue(d);
        ctx.getEngine().initializeDayIfNeeded(d);
        ctx.fireRefresh();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Status bar
    // ─────────────────────────────────────────────────────────────────────────

    private void showStatusBar(String msg, boolean success) {
        lblStatus.setText(msg);
        lblStatus.setStyle(success
            ? "-fx-text-fill: #a6e3a1;"
            : "-fx-text-fill: #f38ba8;");
        statusBar.setOpacity(1.0);

        // Cancel any pending fade
        if (statusFade != null) statusFade.stop();

        // Fade out after 4 seconds
        statusFade = new Timeline(
            new KeyFrame(Duration.seconds(4),  new KeyValue(statusBar.opacityProperty(), 1.0)),
            new KeyFrame(Duration.seconds(5.5), new KeyValue(statusBar.opacityProperty(), 0.0))
        );
        statusFade.setOnFinished(e -> lblStatus.setText("Ready"));
        statusFade.play();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Navigation
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void logout() {
        Member newUser = LoginDialog.show().orElse(null);
        if (newUser == null) return;          // user cancelled — stay logged in
        ctx.setCurrentUser(newUser);
        updateUserBadge();
        ctx.fireRefresh();
        showDashboard();                      // return to dashboard after login
    }

    @FXML public void showDashboard()  { showView("Dashboard",  btnDashboard); }
    @FXML public void showSeatMap()    { showView("SeatMap",    btnSeatMap); }
    @FXML public void showBookings()   { showView("Bookings",   btnBookings); }
    @FXML public void showAnalytics()  { showView("Analytics",  btnAnalytics); }
    @FXML public void showMembers()    { showView("Members",    btnMembers); }
    @FXML public void showAdmin()      { showView("Admin",      btnAdmin); }

    private void showView(String name, Button navBtn) {
        // Update active nav button style
        List<Button> allNavBtns = List.of(
            btnDashboard, btnSeatMap, btnBookings, btnAnalytics, btnMembers, btnAdmin);
        for (Button b : allNavBtns) {
            b.getStyleClass().remove("nav-btn-active");
        }
        if (navBtn != null) {
            navBtn.getStyleClass().add("nav-btn-active");
        }
        activeNav = navBtn;

        try {
            if (!viewCache.containsKey(name)) {
                Parent view = FXMLLoader.load(
                    getClass().getResource("/com/seatbooking/ui/" + name + ".fxml"));
                viewCache.put(name, view);
            }
            contentArea.getChildren().setAll(viewCache.get(name));
        } catch (IOException e) {
            Label err = new Label("Could not load view '" + name + "': " + e.getMessage());
            err.setStyle("-fx-text-fill: #f38ba8; -fx-font-size: 14px;");
            contentArea.getChildren().setAll(err);
        }
    }
}
