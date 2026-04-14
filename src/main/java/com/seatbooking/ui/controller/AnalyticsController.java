package com.seatbooking.ui.controller;

import com.seatbooking.analytics.DayStats;
import com.seatbooking.model.Squad;
import com.seatbooking.ui.AppContext;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Utilisation analytics: today's stats + per-squad bar chart + range summary.
 */
public class AnalyticsController {

    // Stat labels
    @FXML private Label lblTotal;
    @FXML private Label lblReserved;
    @FXML private Label lblOccupied;
    @FXML private Label lblBlocked;
    @FXML private Label lblReleased;
    @FXML private Label lblFree;
    @FXML private Label lblOccupancy;
    @FXML private Label lblWasted;

    // Range summary
    @FXML private DatePicker fromPicker;
    @FXML private DatePicker toPicker;
    @FXML private Label      lblRangeDates;
    @FXML private Label      lblRangeAvg;
    @FXML private Label      lblRangeWasted;

    // Chart container (chart built programmatically)
    @FXML private VBox chartBox;

    private final AppContext ctx = AppContext.get();
    private BarChart<String, Number> chart;

    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        buildChart();
        LocalDate now = ctx.getCurrentDate();
        fromPicker.setValue(now.minusDays(30));
        toPicker.setValue(now);

        ctx.addRefreshListener(this::refresh);
        refresh();
    }

    private void buildChart() {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Squad");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Bookings");
        yAxis.setTickUnit(1);
        yAxis.setMinorTickVisible(false);

        chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("Per-Squad Bookings (Today)");
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setPrefHeight(260);
        VBox.setVgrow(chart, javafx.scene.layout.Priority.ALWAYS);
        chartBox.getChildren().add(chart);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void refresh() {
        LocalDate date = ctx.getCurrentDate();
        DayStats stats = ctx.getAnalytics().todayStats(date);

        lblTotal.setText(String.valueOf(stats.getTotalSeats()));
        lblReserved.setText(String.valueOf(stats.getBookedSeats()));
        lblOccupied.setText(String.valueOf(stats.getOccupiedSeats()));
        lblBlocked.setText(String.valueOf(stats.getBlockedSeats()));
        lblReleased.setText(String.valueOf(stats.getReleasedSeats()));
        lblFree.setText(String.valueOf(stats.getFreeSeats()));
        lblOccupancy.setText(String.format("%.1f%%", stats.getOccupancyPercent()));
        lblWasted.setText(String.valueOf(stats.getWastedSeats()));

        updateChart(stats.getSquadBookingCounts());
        refreshRange();
    }

    @SuppressWarnings("unchecked")
    private void updateChart(Map<Integer, Integer> counts) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        List<Squad> squads = ctx.getStore().getData().getSquads();

        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            String label = squads.stream()
                .filter(s -> s.getId() == e.getKey())
                .map(Squad::getName)
                .findFirst().orElse("Squad-" + e.getKey());
            series.getData().add(new XYChart.Data<>(label, e.getValue()));
        }
        chart.getData().setAll(series);

        // colour bars by batch
        for (XYChart.Data<String, Number> d : series.getData()) {
            String name = d.getXValue();
            int squadId = squads.stream()
                .filter(s -> s.getName().equals(name))
                .mapToInt(Squad::getId).findFirst().orElse(0);
            int batch = squads.stream()
                .filter(s -> s.getId() == squadId)
                .mapToInt(Squad::getBatchId).findFirst().orElse(1);
            if (d.getNode() != null) {
                d.getNode().setStyle("-fx-bar-fill: " + (batch == 1 ? "#89b4fa" : "#cba6f7") + ";");
            }
        }
    }

    @FXML
    private void refreshRange() {
        LocalDate from = fromPicker.getValue();
        LocalDate to   = toPicker.getValue();
        if (from == null || to == null || from.isAfter(to)) return;

        List<DayStats> range = ctx.getAnalytics().range(from, to);
        if (range.isEmpty()) {
            lblRangeDates.setText("0 days with data");
            lblRangeAvg.setText("—");
            lblRangeWasted.setText("—");
        } else {
            double avg    = range.stream().mapToDouble(DayStats::getOccupancyPercent).average().orElse(0);
            int    wasted = range.stream().mapToInt(DayStats::getWastedSeats).sum();
            lblRangeDates.setText(range.size() + " day(s) with booking data");
            lblRangeAvg.setText(String.format("%.1f%%", avg));
            lblRangeWasted.setText(wasted + " seat-days");
        }
    }
}
