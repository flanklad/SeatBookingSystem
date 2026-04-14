package com.seatbooking.ui.controller;

import com.seatbooking.model.*;
import com.seatbooking.ui.AppContext;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Shows all 50 seats as coloured interactive tiles.
 * Fixed seats 1–40 are laid out in a 5×8 grid (one row per squad for the
 * scheduled batch; squad labels appear in column 0).
 * Floater seats 41–50 are in a single row below.
 */
public class SeatMapController {

    @FXML private GridPane seatGrid;
    @FXML private HBox     floaterRow;
    @FXML private Label    lblFree;
    @FXML private Label    lblReserved;
    @FXML private Label    lblOccupied;
    @FXML private Label    lblBlocked;
    @FXML private Label    lblReleased;
    @FXML private ComboBox<String> filterBatch;

    private final AppContext              ctx     = AppContext.get();
    private final Map<Integer, StackPane> tiles   = new HashMap<>();

    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        filterBatch.getItems().setAll("All Batches", "Batch A (1–5)", "Batch B (6–10)");
        filterBatch.setValue("All Batches");
        filterBatch.setOnAction(e -> refresh());

        buildTiles();
        ctx.addRefreshListener(this::refresh);
        refresh();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tile construction
    // ─────────────────────────────────────────────────────────────────────────

    private void buildTiles() {
        // Column 0: squad row labels; columns 1–8: seat tiles
        seatGrid.getColumnConstraints().clear();
        ColumnConstraints labelCol = new ColumnConstraints(90);
        labelCol.setHalignment(javafx.geometry.HPos.LEFT);
        seatGrid.getColumnConstraints().add(labelCol);
        for (int c = 0; c < 8; c++) {
            ColumnConstraints cc = new ColumnConstraints(62);
            cc.setHalignment(javafx.geometry.HPos.CENTER);
            seatGrid.getColumnConstraints().add(cc);
        }

        // Squad row labels (rows 0–4)
        List<Squad> squads = ctx.getStore().getData().getSquads();
        for (int row = 0; row < 5; row++) {
            int squadIdx = row; // squads 1–5 for the first 5 rows (batch A home seats)
            String squadName = squads.stream()
                .filter(s -> s.getId() == squadIdx + 1)
                .map(Squad::getName)
                .findFirst().orElse("Squad " + (squadIdx + 1));
            Label lbl = new Label(squadName);
            lbl.getStyleClass().add("squad-row-label");
            lbl.setMaxWidth(Double.MAX_VALUE);
            GridPane.setValignment(lbl, javafx.geometry.VPos.CENTER);
            seatGrid.add(lbl, 0, row);
        }

        // Fixed seats 1–40 in 5 rows × 8 columns (shifted right by 1 for label column)
        for (int seatNum = 1; seatNum <= 40; seatNum++) {
            StackPane tile = makeTile(seatNum);
            tiles.put(seatNum, tile);
            int row = (seatNum - 1) / 8;
            int col = (seatNum - 1) % 8 + 1; // +1 for the squad label column
            seatGrid.add(tile, col, row);
        }
        // Floater seats 41–50
        for (int seatNum = 41; seatNum <= 50; seatNum++) {
            StackPane tile = makeTile(seatNum);
            tiles.put(seatNum, tile);
            floaterRow.getChildren().add(tile);
        }
    }

    private StackPane makeTile(int seatNum) {
        Label numLbl   = new Label(String.valueOf(seatNum));
        numLbl.getStyleClass().add("seat-num");
        Label memberLbl = new Label("");
        memberLbl.getStyleClass().add("seat-member");

        VBox inner = new VBox(2, numLbl, memberLbl);
        inner.setAlignment(Pos.CENTER);
        inner.setMaxWidth(Double.MAX_VALUE);

        StackPane tile = new StackPane(inner);
        tile.getStyleClass().addAll("seat-tile", "seat-free");
        tile.setOnMouseClicked(e -> onSeatClick(seatNum));
        Tooltip.install(tile, new Tooltip("Seat " + seatNum));
        return tile;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Refresh
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void refresh() {
        List<Seat> seats = ctx.getStore().getData().getSeats();
        String filter = filterBatch.getValue();
        long free = 0, res = 0, occ = 0, blk = 0, rel = 0;

        for (Seat seat : seats) {
            StackPane tile = tiles.get(seat.getSeatNumber());
            if (tile == null) continue;

            // Batch visibility filter (only for fixed seats 1–40)
            if (seat.getSeatNumber() <= 40) {
                boolean visible = true;
                if ("Batch A (1–5)".equals(filter)) {
                    visible = seat.getSeatNumber() <= 40 && isBatchASeat(seat.getSeatNumber());
                } else if ("Batch B (6–10)".equals(filter)) {
                    visible = seat.getSeatNumber() <= 40 && !isBatchASeat(seat.getSeatNumber());
                }
                tile.setVisible(visible);
                tile.setManaged(visible);
            }

            // CSS state class
            tile.getStyleClass().removeIf(c -> c.startsWith("seat-") && !c.equals("seat-tile"));
            String sc = switch (seat.getState()) {
                case FREE     -> "seat-free";
                case RESERVED -> "seat-reserved";
                case OCCUPIED -> "seat-occupied";
                case BLOCKED  -> "seat-blocked";
                case RELEASED -> "seat-released";
            };
            tile.getStyleClass().add(sc);

            // Member name on tile
            VBox inner = (VBox) tile.getChildren().get(0);
            Label memberLbl = (Label) inner.getChildren().get(1);
            if (seat.getAssignedMemberId() != null) {
                String name = ctx.getStore().getData().getMembers().stream()
                    .filter(m -> m.getId().equals(seat.getAssignedMemberId()))
                    .map(m -> firstName(m.getName()))
                    .findFirst().orElse("?");
                memberLbl.setText(name);
            } else {
                memberLbl.setText(seat.getState() == SeatState.FREE ? "free" :
                                  seat.getState() == SeatState.BLOCKED ? "—" : "");
            }

            // Tooltip
            String memberInfo = seat.getAssignedMemberId() == null ? "—"
                : ctx.getStore().getData().getMembers().stream()
                    .filter(m -> m.getId().equals(seat.getAssignedMemberId()))
                    .map(Member::getName).findFirst()
                    .orElse(seat.getAssignedMemberId());
            Tooltip.install(tile, new Tooltip(
                "Seat " + seat.getSeatNumber()
                + " [" + seat.getType() + "]"
                + "\nState: " + seat.getState()
                + "\nMember: " + memberInfo));

            switch (seat.getState()) {
                case FREE     -> free++;
                case RESERVED -> res++;
                case OCCUPIED -> occ++;
                case BLOCKED  -> blk++;
                case RELEASED -> rel++;
            }
        }
        lblFree.setText("Free: " + free);
        lblReserved.setText("Reserved: " + res);
        lblOccupied.setText("Occupied: " + occ);
        lblBlocked.setText("Blocked: " + blk);
        lblReleased.setText("Released: " + rel);
    }

    /** Returns true if the fixed seat (1–40) belongs to Batch A squads (1–5). */
    private boolean isBatchASeat(int seatNum) {
        // Squads 1–5 own seats 1–40; formula: squad = (seatNum-1)/8 + 1
        int squadId = (seatNum - 1) / 8 + 1;
        return squadId <= 5;
    }

    /** Returns the first name (or at most 8 chars) for display on a tile. */
    private String firstName(String fullName) {
        if (fullName == null) return "";
        String first = fullName.contains(" ") ? fullName.substring(0, fullName.indexOf(' ')) : fullName;
        return first.length() > 8 ? first.substring(0, 7) + "…" : first;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Toolbar actions
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void initDay() {
        LocalDate date = ctx.getCurrentDate();
        if (ctx.getSchedule().isHoliday(date)) {
            ctx.showStatus("Cannot initialise: " + date + " is a public holiday.", false);
            return;
        }
        ctx.getEngine().initializeDay(date);
        ctx.fireRefresh();
        ctx.showStatus("Day initialised for " + date + "  [" + ctx.getSchedule().describeDayType(date) + "]", true);
    }

    @FXML
    private void triggerBlock() {
        int blocked = ctx.getScheduler().triggerNow(ctx.getCurrentDate());
        ctx.fireRefresh();
        ctx.showStatus("Blocked " + blocked + " unbooked seat(s) on " + ctx.getCurrentDate() + ".", true);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Seat click → context menu
    // ─────────────────────────────────────────────────────────────────────────

    private void onSeatClick(int seatNum) {
        Seat seat = ctx.getStore().getData().getSeats().stream()
            .filter(s -> s.getSeatNumber() == seatNum)
            .findFirst().orElse(null);
        if (seat == null) return;

        ContextMenu menu = new ContextMenu();
        switch (seat.getState()) {
            case FREE -> {
                MenuItem book = new MenuItem("Book Seat " + seatNum + "…");
                book.setOnAction(e -> showBookingDialog(seat));
                menu.getItems().add(book);
            }
            case RESERVED -> {
                MenuItem ci     = new MenuItem("Check In");
                MenuItem cancel = new MenuItem("Cancel Booking");
                ci.setOnAction(e -> doCheckIn(seat));
                cancel.setOnAction(e -> doCancel(seat));
                menu.getItems().addAll(ci, cancel);
            }
            case OCCUPIED -> {
                MenuItem release = new MenuItem("Release Seat");
                release.setOnAction(e -> doRelease(seat));
                menu.getItems().add(release);
            }
            default -> {
                MenuItem info = new MenuItem("Seat " + seatNum + " — " + seat.getState() + " (no action)");
                info.setDisable(true);
                menu.getItems().add(info);
            }
        }
        StackPane tile = tiles.get(seatNum);
        menu.show(tile, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Booking dialog — searchable member list
    // ─────────────────────────────────────────────────────────────────────────

    private void showBookingDialog(Seat seat) {
        Dialog<String> dlg = new Dialog<>();
        dlg.setTitle("Book Seat " + seat.getSeatNumber());
        dlg.getDialogPane().getStylesheets().add(
            getClass().getResource("/com/seatbooking/ui/dark-theme.css").toExternalForm());

        // Search field
        TextField search = new TextField();
        search.setPromptText("Search by name or ID…");
        search.getStyleClass().add("search-field");

        // Member list
        List<Member> allMembers = ctx.getStore().getData().getMembers();
        ObservableList<Member> memberItems = FXCollections.observableArrayList(allMembers);
        FilteredList<Member> filtered = new FilteredList<>(memberItems, m -> true);
        search.textProperty().addListener((obs, old, text) ->
            filtered.setPredicate(m -> text.isBlank()
                || m.getName().toLowerCase().contains(text.toLowerCase())
                || m.getId().toLowerCase().contains(text.toLowerCase())));

        ListView<Member> listView = new ListView<>(filtered);
        listView.setPrefHeight(240);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Member m, boolean empty) {
                super.updateItem(m, empty);
                if (empty || m == null) { setText(null); setStyle(""); return; }
                Optional<Squad> sq = ctx.getSchedule().getMemberSquad(m.getId());
                String squadInfo = sq.map(s -> "  [" + s.getName() + "  Batch " + (s.getBatchId() == 1 ? "A" : "B") + "]").orElse("");
                String vacInfo   = m.isOnVacationOn(ctx.getCurrentDate()) ? "  ✈ on vacation" : "";
                setText(m.getId() + "  " + m.getName() + squadInfo + vacInfo);
                setStyle(m.isOnVacationOn(ctx.getCurrentDate()) ? "-fx-text-fill: #fab387;" : "");
            }
        });

        Label infoLbl = new Label("Booking Seat " + seat.getSeatNumber()
            + "  [" + seat.getType() + "]  on  " + ctx.getCurrentDate());
        infoLbl.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 12px;");

        VBox content = new VBox(10, infoLbl, search, listView);
        content.setPadding(new Insets(14));
        content.setPrefWidth(460);

        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okBtn = (Button) dlg.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setDisable(true);
        listView.getSelectionModel().selectedItemProperty().addListener(
            (obs, old, m) -> okBtn.setDisable(m == null));
        // Double-click to confirm
        listView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && !okBtn.isDisable()) {
                okBtn.fire();
            }
        });

        dlg.setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            Member m = listView.getSelectionModel().getSelectedItem();
            return m != null ? m.getId() : null;
        });

        dlg.showAndWait().ifPresent(id -> {
            if (id == null) return;
            try {
                ctx.getEngine().book(id, seat.getSeatNumber(), ctx.getCurrentDate());
                ctx.fireRefresh();
                String name = allMembers.stream().filter(m -> m.getId().equals(id))
                    .map(Member::getName).findFirst().orElse(id);
                ctx.showStatus("Seat " + seat.getSeatNumber() + " booked for " + name + ".", true);
            } catch (Exception ex) {
                ctx.showStatus("Booking failed: " + ex.getMessage(), false);
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // State operations
    // ─────────────────────────────────────────────────────────────────────────

    private void doCheckIn(Seat seat) {
        Optional<Booking> b = findActiveBookingForSeat(seat.getSeatNumber());
        if (b.isEmpty()) { ctx.showStatus("No active booking for seat " + seat.getSeatNumber() + ".", false); return; }
        try {
            ctx.getEngine().checkIn(b.get().getId());
            ctx.fireRefresh();
            ctx.showStatus("Seat " + seat.getSeatNumber() + " checked in.", true);
        } catch (Exception ex) { ctx.showStatus(ex.getMessage(), false); }
    }

    private void doCancel(Seat seat) {
        Optional<Booking> b = findActiveBookingForSeat(seat.getSeatNumber());
        if (b.isEmpty()) { ctx.showStatus("No active booking for seat " + seat.getSeatNumber() + ".", false); return; }
        try {
            ctx.getEngine().cancel(b.get().getId());
            ctx.fireRefresh();
            ctx.showStatus("Booking for seat " + seat.getSeatNumber() + " cancelled.", true);
        } catch (Exception ex) { ctx.showStatus(ex.getMessage(), false); }
    }

    private void doRelease(Seat seat) {
        Optional<Booking> b = findActiveBookingForSeat(seat.getSeatNumber());
        if (b.isEmpty()) { ctx.showStatus("No active booking for seat " + seat.getSeatNumber() + ".", false); return; }
        try {
            ctx.getEngine().releaseSeat(b.get().getMemberId(), ctx.getCurrentDate());
            ctx.fireRefresh();
            ctx.showStatus("Seat " + seat.getSeatNumber() + " released.", true);
        } catch (Exception ex) { ctx.showStatus(ex.getMessage(), false); }
    }

    private Optional<Booking> findActiveBookingForSeat(int seatNum) {
        LocalDate date = ctx.getCurrentDate();
        return ctx.getStore().getData().getBookings().stream()
            .filter(b -> b.getSeatNumber() == seatNum
                      && b.getDate().equals(date)
                      && b.isActive())
            .findFirst();
    }
}
