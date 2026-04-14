package com.seatbooking.ui.controller;

import com.seatbooking.ui.AppContext;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Drives the outer shell: header labels, sidebar navigation, date picker,
 * and swaps sub-views into the centre StackPane.
 */
public class MainWindowController {

    @FXML private StackPane contentArea;
    @FXML private Label     lblDate;
    @FXML private Label     lblDayType;
    @FXML private Label     lblWeek;
    @FXML private DatePicker datePicker;

    private final AppContext            ctx        = AppContext.get();
    private final Map<String, Parent>   viewCache  = new HashMap<>();

    @FXML
    public void initialize() {
        datePicker.setValue(ctx.getCurrentDate());
        updateHeader();
        showSeatMap();                          // default view
        ctx.addRefreshListener(this::updateHeader);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Header update
    // ─────────────────────────────────────────────────────────────────────────

    private void updateHeader() {
        LocalDate date = ctx.getCurrentDate();
        lblDate.setText(date + "  (" + date.getDayOfWeek() + ")");
        lblDayType.setText(ctx.getSchedule().describeDayType(date));
        lblWeek.setText("Fortnight Week " + ctx.getSchedule().getFortnightWeek(date));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Date change
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void onDateChanged() {
        LocalDate selected = datePicker.getValue();
        if (selected != null) {
            ctx.setCurrentDate(selected);
            ctx.fireRefresh();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Navigation
    // ─────────────────────────────────────────────────────────────────────────

    @FXML public void showSeatMap()   { showView("SeatMap"); }
    @FXML public void showBookings()  { showView("Bookings"); }
    @FXML public void showAnalytics() { showView("Analytics"); }
    @FXML public void showMembers()   { showView("Members"); }
    @FXML public void showAdmin()     { showView("Admin"); }

    private void showView(String name) {
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
