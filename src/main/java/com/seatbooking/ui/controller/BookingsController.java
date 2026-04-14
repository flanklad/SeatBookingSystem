package com.seatbooking.ui.controller;

import com.seatbooking.model.Booking;
import com.seatbooking.model.BookingStatus;
import com.seatbooking.model.Member;
import com.seatbooking.ui.AppContext;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Shows all bookings for the current simulation date.
 * Supports inline book / cancel / check-in actions.
 */
public class BookingsController {

    // Table
    @FXML private TableView<Booking>                    table;
    @FXML private TableColumn<Booking, String>          colId;
    @FXML private TableColumn<Booking, String>          colMember;
    @FXML private TableColumn<Booking, String>          colSeat;
    @FXML private TableColumn<Booking, String>          colStatus;
    @FXML private TableColumn<Booking, String>          colType;
    @FXML private TableColumn<Booking, String>          colDate;

    // Booking form
    @FXML private TextField  fldMemberId;
    @FXML private TextField  fldSeatNumber;
    @FXML private Label      lblError;

    private final AppContext            ctx = AppContext.get();
    private final ObservableList<Booking> items = FXCollections.observableArrayList();

    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        colId.setCellValueFactory(d     -> new SimpleStringProperty(d.getValue().getId().substring(0, 8)));
        colMember.setCellValueFactory(d -> new SimpleStringProperty(resolveName(d.getValue().getMemberId())));
        colSeat.setCellValueFactory(d   -> new SimpleStringProperty(String.valueOf(d.getValue().getSeatNumber())));
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus().name()));
        colType.setCellValueFactory(d   -> new SimpleStringProperty(d.getValue().isAutoAssigned() ? "AUTO" : "MANUAL"));
        colDate.setCellValueFactory(d   -> new SimpleStringProperty(d.getValue().getDate().toString()));

        // Colour-code status column
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setStyle(""); return; }
                setText(s);
                setStyle(switch (s) {
                    case "ACTIVE"    -> "-fx-text-fill: #a6e3a1;";
                    case "CANCELLED" -> "-fx-text-fill: #f38ba8;";
                    case "RELEASED"  -> "-fx-text-fill: #fab387;";
                    default          -> "-fx-text-fill: #cdd6f4;";
                });
            }
        });

        table.setItems(items);
        ctx.addRefreshListener(this::refresh);
        refresh();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Refresh
    // ─────────────────────────────────────────────────────────────────────────

    private void refresh() {
        LocalDate date = ctx.getCurrentDate();
        List<Booking> bookings = ctx.getEngine().getBookingsForDate(date);
        items.setAll(bookings);
        lblError.setText("");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Actions
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void doBook() {
        String memberId = fldMemberId.getText().trim();
        String seatStr  = fldSeatNumber.getText().trim();
        if (memberId.isBlank() || seatStr.isBlank()) {
            lblError.setText("Member ID and seat number are required.");
            return;
        }
        int seatNum;
        try { seatNum = Integer.parseInt(seatStr); }
        catch (NumberFormatException e) { lblError.setText("Invalid seat number."); return; }

        try {
            ctx.getEngine().book(memberId, seatNum, ctx.getCurrentDate());
            ctx.fireRefresh();
            fldMemberId.clear();
            fldSeatNumber.clear();
        } catch (Exception e) {
            lblError.setText(e.getMessage());
        }
    }

    @FXML
    private void doCancel() {
        Booking selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { lblError.setText("Select a booking to cancel."); return; }
        try {
            ctx.getEngine().cancel(selected.getId());
            ctx.fireRefresh();
        } catch (Exception e) { lblError.setText(e.getMessage()); }
    }

    @FXML
    private void doCheckIn() {
        Booking selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { lblError.setText("Select a booking to check in."); return; }
        try {
            ctx.getEngine().checkIn(selected.getId());
            ctx.fireRefresh();
        } catch (Exception e) { lblError.setText(e.getMessage()); }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private String resolveName(String memberId) {
        return ctx.getStore().getData().getMembers().stream()
            .filter(m -> m.getId().equals(memberId))
            .map(Member::getName)
            .findFirst().orElse(memberId);
    }
}
