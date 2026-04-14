package com.seatbooking.ui.controller;

import com.seatbooking.model.Squad;
import com.seatbooking.ui.AppContext;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Administrative operations: day initialisation, vacation management, 3PM block.
 */
public class AdminController {

    // Panel 1 – Initialise day
    @FXML private DatePicker initDayPicker;
    @FXML private Label      lblInitResult;

    // Panel 2 – 3PM Block
    @FXML private DatePicker blockDayPicker;
    @FXML private Label      lblBlockResult;

    // Panel 3 – Vacation
    @FXML private ComboBox<String> memberCombo;
    @FXML private DatePicker       vacStartPicker;
    @FXML private DatePicker       vacEndPicker;
    @FXML private Label            lblVacResult;

    private final AppContext ctx = AppContext.get();

    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        LocalDate today = ctx.getCurrentDate();
        initDayPicker.setValue(today);
        blockDayPicker.setValue(today);

        // Populate member combo
        List<String> memberItems = ctx.getStore().getData().getMembers().stream()
            .map(m -> m.getId() + "  " + m.getName())
            .collect(Collectors.toList());
        memberCombo.getItems().setAll(memberItems);

        ctx.addRefreshListener(this::refresh);
    }

    private void refresh() {
        // Update date pickers to stay consistent with the simulation date
        LocalDate date = ctx.getCurrentDate();
        if (initDayPicker.getValue() == null)  initDayPicker.setValue(date);
        if (blockDayPicker.getValue() == null) blockDayPicker.setValue(date);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Actions
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void doInitDay() {
        LocalDate date = initDayPicker.getValue();
        if (date == null) { setResult(lblInitResult, "Select a date.", false); return; }
        if (ctx.getSchedule().isHoliday(date)) {
            String msg = date + " is a public holiday — cannot initialise.";
            setResult(lblInitResult, msg, false);
            ctx.showStatus(msg, false);
            return;
        }
        if (ctx.getSchedule().isWeekend(date)) {
            String msg = date + " is a weekend — no seats to assign.";
            setResult(lblInitResult, msg, false);
            ctx.showStatus(msg, false);
            return;
        }
        ctx.getEngine().initializeDay(date);
        ctx.fireRefresh();
        String msg = "Day initialised: " + date + "  [" + ctx.getSchedule().describeDayType(date) + "]";
        setResult(lblInitResult, msg, true);
        ctx.showStatus(msg, true);
    }

    @FXML
    private void doTriggerBlock() {
        LocalDate date = blockDayPicker.getValue();
        if (date == null) { setResult(lblBlockResult, "Select a date.", false); return; }
        int blocked = ctx.getScheduler().triggerNow(date);
        ctx.fireRefresh();
        String msg = "Blocked " + blocked + " seat(s) on " + date + ".";
        setResult(lblBlockResult, msg, true);
        ctx.showStatus(msg, true);
    }

    @FXML
    private void doSetVacation() {
        String selection = memberCombo.getValue();
        if (selection == null) { setResult(lblVacResult, "Select a member.", false); return; }
        String memberId = selection.split("\\s+")[0];
        LocalDate start = vacStartPicker.getValue();
        LocalDate end   = vacEndPicker.getValue();
        if (start == null || end == null) { setResult(lblVacResult, "Set both dates.", false); return; }
        if (start.isAfter(end)) { setResult(lblVacResult, "Start must be before end.", false); return; }
        try {
            ctx.getEngine().setVacation(memberId, start, end);
            ctx.fireRefresh();
            String msg = "Vacation set for " + memberId + " (" + start + " → " + end + ").";
            setResult(lblVacResult, msg, true);
            ctx.showStatus(msg, true);
        } catch (Exception e) {
            setResult(lblVacResult, e.getMessage(), false);
            ctx.showStatus(e.getMessage(), false);
        }
    }

    @FXML
    private void doClearVacation() {
        String selection = memberCombo.getValue();
        if (selection == null) { setResult(lblVacResult, "Select a member.", false); return; }
        String memberId = selection.split("\\s+")[0];
        try {
            ctx.getEngine().clearVacation(memberId);
            ctx.fireRefresh();
            String msg = "Vacation cleared for " + memberId + ".";
            setResult(lblVacResult, msg, true);
            ctx.showStatus(msg, true);
        } catch (Exception e) {
            setResult(lblVacResult, e.getMessage(), false);
            ctx.showStatus(e.getMessage(), false);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void setResult(Label label, String msg, boolean success) {
        label.setText(msg);
        label.setStyle("-fx-text-fill: " + (success ? "#a6e3a1" : "#f38ba8") + ";");
    }
}
