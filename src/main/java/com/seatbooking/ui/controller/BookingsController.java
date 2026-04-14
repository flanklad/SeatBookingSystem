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
 * Shows bookings for the current simulation date (or all dates when toggled).
 * Supports live search, status filter, inline check-in / cancel, and new booking form.
 */
public class BookingsController {

    // Table
    @FXML private TableView<Booking>           table;
    @FXML private TableColumn<Booking, String> colId;
    @FXML private TableColumn<Booking, String> colMember;
    @FXML private TableColumn<Booking, String> colSeat;
    @FXML private TableColumn<Booking, String> colStatus;
    @FXML private TableColumn<Booking, String> colType;
    @FXML private TableColumn<Booking, String> colDate;

    // Filters
    @FXML private TextField          searchField;
    @FXML private ComboBox<String>   statusFilter;
    @FXML private CheckBox           showAllDates;
    @FXML private Label              lblCount;

    // Booking form
    @FXML private TextField  fldMemberId;
    @FXML private TextField  fldSeatNumber;
    @FXML private Label      lblError;

    private final AppContext               ctx   = AppContext.get();
    private final ObservableList<Booking>  all   = FXCollections.observableArrayList();
    private FilteredList<Booking>          filtered;

    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // Status filter options
        statusFilter.getItems().setAll("All Statuses", "ACTIVE", "CANCELLED", "RELEASED");
        statusFilter.setValue("All Statuses");
        statusFilter.setOnAction(e -> applyFilter());

        // Table columns
        colId.setCellValueFactory(d      -> new SimpleStringProperty(d.getValue().getId().substring(0, 8)));
        colMember.setCellValueFactory(d  -> new SimpleStringProperty(resolveName(d.getValue().getMemberId())));
        colSeat.setCellValueFactory(d    -> new SimpleStringProperty(String.valueOf(d.getValue().getSeatNumber())));
        colStatus.setCellValueFactory(d  -> new SimpleStringProperty(d.getValue().getStatus().name()));
        colType.setCellValueFactory(d    -> new SimpleStringProperty(d.getValue().isAutoAssigned() ? "AUTO" : "MANUAL"));
        colDate.setCellValueFactory(d    -> new SimpleStringProperty(d.getValue().getDate().toString()));

        // Colour-code status column
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
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

        // AUTO / MANUAL colour
        colType.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setStyle(""); return; }
                setText(s);
                setStyle("AUTO".equals(s) ? "-fx-text-fill: #89b4fa;" : "-fx-text-fill: #cba6f7;");
            }
        });

        // Filtered list wired to the table
        filtered = new FilteredList<>(all, b -> true);
        table.setItems(filtered);

        // Live search
        searchField.textProperty().addListener((obs, old, v) -> applyFilter());

        ctx.addRefreshListener(this::refresh);
        refresh();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data load + filter
    // ─────────────────────────────────────────────────────────────────────────

    private void refresh() {
        all.setAll(ctx.getStore().getData().getBookings());
        applyFilter();
        lblError.setText("");
    }

    @FXML
    private void onFilterChanged() { applyFilter(); }

    private void applyFilter() {
        LocalDate date       = ctx.getCurrentDate();
        boolean   allDates   = showAllDates.isSelected();
        String    search     = searchField.getText().toLowerCase();
        String    statusSel  = statusFilter.getValue();

        filtered.setPredicate(b -> {
            // Date filter
            if (!allDates && !b.getDate().equals(date)) return false;

            // Status filter
            if (!"All Statuses".equals(statusSel) && !b.getStatus().name().equals(statusSel)) return false;

            // Search filter
            if (!search.isBlank()) {
                String memberName = resolveName(b.getMemberId()).toLowerCase();
                String memberId   = b.getMemberId().toLowerCase();
                if (!memberName.contains(search) && !memberId.contains(search)) return false;
            }
            return true;
        });

        lblCount.setText(filtered.size() + " booking(s)");
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
            ctx.showStatus("Seat " + seatNum + " booked for " + resolveName(memberId) + ".", true);
        } catch (Exception e) {
            lblError.setText(e.getMessage());
            ctx.showStatus("Booking failed: " + e.getMessage(), false);
        }
    }

    @FXML
    private void doCancel() {
        Booking selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { lblError.setText("Select a booking to cancel."); return; }
        try {
            ctx.getEngine().cancel(selected.getId());
            ctx.fireRefresh();
            ctx.showStatus("Booking cancelled for " + resolveName(selected.getMemberId()) + ".", true);
        } catch (Exception e) {
            lblError.setText(e.getMessage());
            ctx.showStatus(e.getMessage(), false);
        }
    }

    @FXML
    private void doCheckIn() {
        Booking selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { lblError.setText("Select a booking to check in."); return; }
        try {
            ctx.getEngine().checkIn(selected.getId());
            ctx.fireRefresh();
            ctx.showStatus(resolveName(selected.getMemberId()) + " checked in to seat " + selected.getSeatNumber() + ".", true);
        } catch (Exception e) {
            lblError.setText(e.getMessage());
            ctx.showStatus(e.getMessage(), false);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private String resolveName(String memberId) {
        return ctx.getStore().getData().getMembers().stream()
            .filter(m -> m.getId().equals(memberId))
            .map(Member::getName)
            .findFirst().orElse(memberId);
    }
}
