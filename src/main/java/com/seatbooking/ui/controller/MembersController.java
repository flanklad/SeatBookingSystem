package com.seatbooking.ui.controller;

import com.seatbooking.model.Member;
import com.seatbooking.model.Squad;
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
 * Master list of all 80 members with vacation management.
 */
public class MembersController {

    // Table
    @FXML private TableView<Member>           table;
    @FXML private TableColumn<Member, String> colId;
    @FXML private TableColumn<Member, String> colName;
    @FXML private TableColumn<Member, String> colSquad;
    @FXML private TableColumn<Member, String> colBatch;
    @FXML private TableColumn<Member, String> colSeat;
    @FXML private TableColumn<Member, String> colVacation;

    @FXML private TextField  searchField;

    // Vacation form
    @FXML private TextField  vacMemberIdField;
    @FXML private DatePicker vacStartPicker;
    @FXML private DatePicker vacEndPicker;
    @FXML private Label      lblVacResult;

    private final AppContext              ctx = AppContext.get();
    private final ObservableList<Member>  all = FXCollections.observableArrayList();

    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        colId.setCellValueFactory(d       -> new SimpleStringProperty(d.getValue().getId()));
        colName.setCellValueFactory(d     -> new SimpleStringProperty(d.getValue().getName()));
        colSquad.setCellValueFactory(d    -> new SimpleStringProperty(resolveSquad(d.getValue())));
        colBatch.setCellValueFactory(d    -> new SimpleStringProperty(resolveBatch(d.getValue())));
        colSeat.setCellValueFactory(d     -> new SimpleStringProperty(String.valueOf(d.getValue().getHomeSeatNumber())));
        colVacation.setCellValueFactory(d -> {
            Member m = d.getValue();
            String vac = m.isOnVacation()
                ? m.getVacationStart() + " → " + m.getVacationEnd() : "—";
            return new SimpleStringProperty(vac);
        });

        // Colour vacation column
        colVacation.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setStyle(""); return; }
                setText(s);
                setStyle(s.equals("—") ? "" : "-fx-text-fill: #fab387;");
            }
        });

        FilteredList<Member> filtered = new FilteredList<>(all, m -> true);
        searchField.textProperty().addListener((obs, old, text) ->
            filtered.setPredicate(m -> text.isBlank()
                || m.getName().toLowerCase().contains(text.toLowerCase())
                || m.getId().toLowerCase().contains(text.toLowerCase())));
        table.setItems(filtered);

        // When a row is selected, pre-fill the vacation form
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, m) -> {
            if (m != null) vacMemberIdField.setText(m.getId());
        });

        ctx.addRefreshListener(this::refresh);
        refresh();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void refresh() {
        List<Member> members = ctx.getStore().getData().getMembers();
        all.setAll(members);
        lblVacResult.setText("");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Vacation actions
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void setVacation() {
        String memberId = vacMemberIdField.getText().trim();
        LocalDate start = vacStartPicker.getValue();
        LocalDate end   = vacEndPicker.getValue();

        if (memberId.isBlank() || start == null || end == null) {
            lblVacResult.setText("Fill in all fields.");
            lblVacResult.setStyle("-fx-text-fill: #f38ba8;");
            return;
        }
        if (start.isAfter(end)) {
            lblVacResult.setText("Start must be before end.");
            lblVacResult.setStyle("-fx-text-fill: #f38ba8;");
            return;
        }
        try {
            ctx.getEngine().setVacation(memberId, start, end);
            ctx.fireRefresh();
            lblVacResult.setText("Vacation set for " + memberId + ".");
            lblVacResult.setStyle("-fx-text-fill: #a6e3a1;");
        } catch (Exception e) {
            lblVacResult.setText(e.getMessage());
            lblVacResult.setStyle("-fx-text-fill: #f38ba8;");
        }
    }

    @FXML
    private void clearVacation() {
        String memberId = vacMemberIdField.getText().trim();
        if (memberId.isBlank()) { lblVacResult.setText("Enter a Member ID."); return; }
        try {
            ctx.getEngine().clearVacation(memberId);
            ctx.fireRefresh();
            lblVacResult.setText("Vacation cleared for " + memberId + ".");
            lblVacResult.setStyle("-fx-text-fill: #a6e3a1;");
        } catch (Exception e) {
            lblVacResult.setText(e.getMessage());
            lblVacResult.setStyle("-fx-text-fill: #f38ba8;");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private String resolveSquad(Member m) {
        return ctx.getStore().getData().getSquads().stream()
            .filter(sq -> sq.getMemberIds().contains(m.getId()))
            .map(Squad::getName).findFirst().orElse("?");
    }

    private String resolveBatch(Member m) {
        return ctx.getStore().getData().getSquads().stream()
            .filter(sq -> sq.getMemberIds().contains(m.getId()))
            .map(sq -> sq.getBatchId() == 1 ? "A" : "B")
            .findFirst().orElse("?");
    }
}
