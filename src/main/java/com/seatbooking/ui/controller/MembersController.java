package com.seatbooking.ui.controller;

import com.seatbooking.model.Booking;
import com.seatbooking.model.Member;
import com.seatbooking.model.Squad;
import com.seatbooking.ui.AppContext;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Team directory: all 80 employees with today's booking status and (admin-only) vacation management.
 */
public class MembersController {

    // Table
    @FXML private TableView<Member>           table;
    @FXML private TableColumn<Member, String> colId;
    @FXML private TableColumn<Member, String> colName;
    @FXML private TableColumn<Member, String> colSquad;
    @FXML private TableColumn<Member, String> colBatch;
    @FXML private TableColumn<Member, String> colSeat;
    @FXML private TableColumn<Member, String> colToday;
    @FXML private TableColumn<Member, String> colVacation;

    // Filters / summary
    @FXML private TextField        searchField;
    @FXML private ComboBox<String> batchFilter;
    @FXML private Label            lblTotal;
    @FXML private Label            lblPresent;
    @FXML private Label            lblVacation;
    @FXML private Label            lblAbsent;

    // Vacation form (admin only)
    @FXML private VBox       vacationCard;
    @FXML private TextField  vacMemberIdField;
    @FXML private DatePicker vacStartPicker;
    @FXML private DatePicker vacEndPicker;
    @FXML private Label      lblVacResult;

    private final AppContext              ctx      = AppContext.get();
    private final ObservableList<Member>  allEmps  = FXCollections.observableArrayList();
    private FilteredList<Member>          filtered;

    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // Column value factories
        colId.setCellValueFactory(d    -> new SimpleStringProperty(d.getValue().getId()));
        colName.setCellValueFactory(d  -> new SimpleStringProperty(d.getValue().getName()));
        colSquad.setCellValueFactory(d -> new SimpleStringProperty(resolveSquad(d.getValue())));
        colBatch.setCellValueFactory(d -> new SimpleStringProperty(resolveBatch(d.getValue())));
        colSeat.setCellValueFactory(d  -> new SimpleStringProperty(
            d.getValue().getHomeSeatNumber() > 0
                ? String.valueOf(d.getValue().getHomeSeatNumber()) : "—"));

        // Today's booking status column
        colToday.setCellValueFactory(d -> new SimpleStringProperty(todayStatus(d.getValue())));
        colToday.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setStyle(""); return; }
                setText(s);
                setStyle(switch (s) {
                    case "CHECKED IN"  -> "-fx-text-fill: #89b4fa;";  // blue
                    case "BOOKED"      -> "-fx-text-fill: #a6e3a1;";  // green
                    case "ON VACATION" -> "-fx-text-fill: #fab387;";  // orange
                    case "RELEASED"    -> "-fx-text-fill: #cba6f7;";  // purple
                    default            -> "-fx-text-fill: #6c7086;";  // muted grey
                });
            }
        });

        // Vacation column
        colVacation.setCellValueFactory(d -> {
            Member m = d.getValue();
            String vac = m.isOnVacation()
                ? m.getVacationStart() + " → " + m.getVacationEnd() : "—";
            return new SimpleStringProperty(vac);
        });
        colVacation.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setStyle(""); return; }
                setText(s);
                setStyle(s.equals("—") ? "-fx-text-fill: #6c7086;" : "-fx-text-fill: #fab387;");
            }
        });

        // Batch filter
        batchFilter.getItems().setAll("All Batches", "Batch A", "Batch B");
        batchFilter.setValue("All Batches");
        batchFilter.setOnAction(e -> applyFilter());

        // Filtered list
        filtered = new FilteredList<>(allEmps, m -> true);
        table.setItems(filtered);

        searchField.textProperty().addListener((obs, old, v) -> applyFilter());

        // Row click → pre-fill vacation form
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, m) -> {
            if (m != null && vacMemberIdField != null) vacMemberIdField.setText(m.getId());
        });

        // Vacation card: admins only
        vacationCard.setVisible(ctx.isAdmin());
        vacationCard.setManaged(ctx.isAdmin());

        ctx.addRefreshListener(this::refresh);
        refresh();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void refresh() {
        // Show only employees (exclude admin account)
        List<Member> employees = ctx.getStore().getData().getMembers().stream()
            .filter(m -> !m.isAdmin())
            .toList();
        allEmps.setAll(employees);
        applyFilter();
        if (lblVacResult != null) lblVacResult.setText("");
        updateSummary(employees);
    }

    private void applyFilter() {
        String search = searchField.getText().toLowerCase();
        String batch  = batchFilter.getValue();

        filtered.setPredicate(m -> {
            // Batch filter
            if ("Batch A".equals(batch) && !"A".equals(resolveBatch(m))) return false;
            if ("Batch B".equals(batch) && !"B".equals(resolveBatch(m))) return false;

            // Search
            if (!search.isBlank()) {
                return m.getName().toLowerCase().contains(search)
                    || m.getId().toLowerCase().contains(search)
                    || resolveSquad(m).toLowerCase().contains(search);
            }
            return true;
        });
    }

    private void updateSummary(List<Member> employees) {
        LocalDate date = ctx.getCurrentDate();
        long present   = employees.stream().filter(m -> {
            String s = todayStatus(m);
            return "CHECKED IN".equals(s) || "BOOKED".equals(s);
        }).count();
        long onVac   = employees.stream().filter(m -> "ON VACATION".equals(todayStatus(m))).count();
        long absent  = employees.size() - present - onVac;

        lblTotal.setText("Total: " + employees.size());
        lblPresent.setText("Present / Booked: " + present);
        lblVacation.setText("On Vacation: " + onVac);
        lblAbsent.setText("No Booking: " + absent);

        lblPresent.setStyle("-fx-text-fill: #a6e3a1;");
        lblVacation.setStyle("-fx-text-fill: #fab387;");
        lblAbsent.setStyle("-fx-text-fill: #6c7086;");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Today's status for a member on the current simulation date
    // ─────────────────────────────────────────────────────────────────────────

    private String todayStatus(Member m) {
        LocalDate date = ctx.getCurrentDate();
        if (m.isOnVacationOn(date)) return "ON VACATION";

        Optional<Booking> booking = ctx.getStore().getData().getBookings().stream()
            .filter(b -> b.getMemberId().equals(m.getId()) && b.getDate().equals(date))
            .filter(b -> b.isActive()
                || b.getStatus().name().equals("RELEASED"))
            .findFirst();

        if (booking.isEmpty()) return "NOT BOOKED";
        return switch (booking.get().getStatus()) {
            case OCCUPIED  -> "CHECKED IN";
            case ACTIVE    -> "BOOKED";
            case RELEASED  -> "RELEASED";
            default        -> "NOT BOOKED";
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Vacation actions (admin only)
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void setVacation() {
        if (!ctx.isAdmin()) { setVacResult("Admin access required.", false); return; }
        String memberId = vacMemberIdField.getText().trim();
        LocalDate start = vacStartPicker.getValue();
        LocalDate end   = vacEndPicker.getValue();
        if (memberId.isBlank() || start == null || end == null) {
            setVacResult("Fill in all fields.", false); return;
        }
        if (start.isAfter(end)) { setVacResult("Start must be before end.", false); return; }
        try {
            ctx.getEngine().setVacation(memberId, start, end);
            ctx.fireRefresh();
            setVacResult("Vacation set for " + memberId + ".", true);
        } catch (Exception e) {
            setVacResult(e.getMessage(), false);
        }
    }

    @FXML
    private void clearVacation() {
        if (!ctx.isAdmin()) { setVacResult("Admin access required.", false); return; }
        String memberId = vacMemberIdField.getText().trim();
        if (memberId.isBlank()) { setVacResult("Enter a Member ID.", false); return; }
        try {
            ctx.getEngine().clearVacation(memberId);
            ctx.fireRefresh();
            setVacResult("Vacation cleared for " + memberId + ".", true);
        } catch (Exception e) {
            setVacResult(e.getMessage(), false);
        }
    }

    private void setVacResult(String msg, boolean success) {
        lblVacResult.setText(msg);
        lblVacResult.setStyle("-fx-text-fill: " + (success ? "#a6e3a1" : "#f38ba8") + ";");
    }

    // ─────────────────────────────────────────────────────────────────────────

    private String resolveSquad(Member m) {
        return ctx.getStore().getData().getSquads().stream()
            .filter(sq -> sq.getMemberIds().contains(m.getId()))
            .map(Squad::getName).findFirst().orElse("—");
    }

    private String resolveBatch(Member m) {
        return ctx.getStore().getData().getSquads().stream()
            .filter(sq -> sq.getMemberIds().contains(m.getId()))
            .map(sq -> sq.getBatchId() == 1 ? "A" : "B")
            .findFirst().orElse("—");
    }
}
