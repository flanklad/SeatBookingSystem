package com.seatbooking.ui.controller;

import com.seatbooking.model.*;
import com.seatbooking.ui.AppContext;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shows all 50 seats as coloured interactive tiles.
 * Fixed seats 1-40 are laid out in a 5×8 grid (one row per squad).
 * Floater seats 41-50 are in a single row below.
 */
public class SeatMapController {

    @FXML private GridPane seatGrid;
    @FXML private HBox     floaterRow;
    @FXML private Label    lblFree;
    @FXML private Label    lblReserved;
    @FXML private Label    lblOccupied;
    @FXML private Label    lblBlocked;
    @FXML private Label    lblReleased;

    private final AppContext            ctx       = AppContext.get();
    private final Map<Integer, StackPane> tiles   = new HashMap<>();

    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        buildTiles();
        ctx.addRefreshListener(this::refresh);
        refresh();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tile construction
    // ─────────────────────────────────────────────────────────────────────────

    private void buildTiles() {
        // Fixed seats 1-40 in 5 rows × 8 columns
        for (int seatNum = 1; seatNum <= 40; seatNum++) {
            StackPane tile = makeTile(seatNum);
            tiles.put(seatNum, tile);
            int row = (seatNum - 1) / 8;
            int col = (seatNum - 1) % 8;
            seatGrid.add(tile, col, row);
        }
        // Floater seats 41-50
        for (int seatNum = 41; seatNum <= 50; seatNum++) {
            StackPane tile = makeTile(seatNum);
            tiles.put(seatNum, tile);
            floaterRow.getChildren().add(tile);
        }
    }

    private StackPane makeTile(int seatNum) {
        Label numLbl   = new Label(String.valueOf(seatNum));
        numLbl.getStyleClass().add("seat-num");
        Label stateLbl = new Label("F");
        stateLbl.getStyleClass().add("seat-state");

        VBox inner = new VBox(2, numLbl, stateLbl);
        inner.setAlignment(Pos.CENTER);

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
        long free = 0, res = 0, occ = 0, blk = 0, rel = 0;

        for (Seat seat : seats) {
            StackPane tile = tiles.get(seat.getSeatNumber());
            if (tile == null) continue;

            // update style class
            tile.getStyleClass().removeIf(c -> c.startsWith("seat-") && !c.equals("seat-tile"));
            String sc = switch (seat.getState()) {
                case FREE     -> "seat-free";
                case RESERVED -> "seat-reserved";
                case OCCUPIED -> "seat-occupied";
                case BLOCKED  -> "seat-blocked";
                case RELEASED -> "seat-released";
            };
            tile.getStyleClass().add(sc);

            // update inner state label
            VBox inner = (VBox) tile.getChildren().get(0);
            Label stateLbl = (Label) inner.getChildren().get(1);
            stateLbl.setText(seat.getState().name().substring(0, 1));

            // tooltip with member info
            String memberInfo = seat.getAssignedMemberId() == null ? "—"
                : ctx.getStore().getData().getMembers().stream()
                    .filter(m -> m.getId().equals(seat.getAssignedMemberId()))
                    .map(Member::getName).findFirst()
                    .orElse(seat.getAssignedMemberId());
            Tooltip.install(tile, new Tooltip(
                "Seat " + seat.getSeatNumber()
                + " [" + seat.getType() + "]\n"
                + "State: " + seat.getState() + "\n"
                + "Member: " + memberInfo));

            // counters
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

    // ─────────────────────────────────────────────────────────────────────────
    // Button actions (FXML)
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void initDay() {
        LocalDate date = ctx.getCurrentDate();
        if (ctx.getSchedule().isHoliday(date)) {
            showAlert("Cannot initialise: " + date + " is a public holiday.");
            return;
        }
        ctx.getEngine().initializeDay(date);
        ctx.fireRefresh();
        showInfo("Day initialised for " + date
            + "\n" + ctx.getSchedule().describeDayType(date));
    }

    @FXML
    private void triggerBlock() {
        int blocked = ctx.getScheduler().triggerNow(ctx.getCurrentDate());
        ctx.fireRefresh();
        showInfo("Blocked " + blocked + " unbooked seat(s) on " + ctx.getCurrentDate() + ".");
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
                MenuItem ci = new MenuItem("Check In");
                ci.setOnAction(e -> doCheckIn(seat));
                MenuItem cancel = new MenuItem("Cancel Booking");
                cancel.setOnAction(e -> doCancel(seat));
                menu.getItems().addAll(ci, cancel);
            }
            case OCCUPIED -> {
                MenuItem release = new MenuItem("Release Seat");
                release.setOnAction(e -> doRelease(seat));
                menu.getItems().add(release);
            }
            default -> {
                MenuItem info = new MenuItem(
                    "Seat " + seatNum + " — " + seat.getState() + " (no action)");
                info.setDisable(true);
                menu.getItems().add(info);
            }
        }
        StackPane tile = tiles.get(seatNum);
        menu.show(tile, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Booking dialog
    // ─────────────────────────────────────────────────────────────────────────

    private void showBookingDialog(Seat seat) {
        Dialog<String> dlg = new Dialog<>();
        dlg.setTitle("Book Seat " + seat.getSeatNumber());
        dlg.setHeaderText("Enter Member ID to book Seat " + seat.getSeatNumber()
            + " on " + ctx.getCurrentDate());

        TextField memberField = new TextField();
        memberField.setPromptText("e.g. M001");

        VBox body = new VBox(8, new Label("Member ID:"), memberField);
        body.setPadding(new Insets(12));
        dlg.getDialogPane().setContent(body);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.setResultConverter(bt -> bt == ButtonType.OK ? memberField.getText() : null);

        dlg.showAndWait().ifPresent(id -> {
            if (id == null || id.isBlank()) return;
            try {
                ctx.getEngine().book(id.trim(), seat.getSeatNumber(), ctx.getCurrentDate());
                ctx.fireRefresh();
            } catch (Exception ex) {
                showAlert("Booking failed:\n" + ex.getMessage());
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // State operations
    // ─────────────────────────────────────────────────────────────────────────

    private void doCheckIn(Seat seat) {
        Optional<Booking> b = findActiveBookingForSeat(seat.getSeatNumber());
        if (b.isEmpty()) { showAlert("No active booking found for seat " + seat.getSeatNumber()); return; }
        try {
            ctx.getEngine().checkIn(b.get().getId());
            ctx.fireRefresh();
        } catch (Exception ex) { showAlert(ex.getMessage()); }
    }

    private void doCancel(Seat seat) {
        Optional<Booking> b = findActiveBookingForSeat(seat.getSeatNumber());
        if (b.isEmpty()) { showAlert("No active booking found for seat " + seat.getSeatNumber()); return; }
        try {
            ctx.getEngine().cancel(b.get().getId());
            ctx.fireRefresh();
        } catch (Exception ex) { showAlert(ex.getMessage()); }
    }

    private void doRelease(Seat seat) {
        Optional<Booking> b = findActiveBookingForSeat(seat.getSeatNumber());
        if (b.isEmpty()) { showAlert("No active booking found for seat " + seat.getSeatNumber()); return; }
        try {
            ctx.getEngine().releaseSeat(b.get().getMemberId(), ctx.getCurrentDate());
            ctx.fireRefresh();
        } catch (Exception ex) { showAlert(ex.getMessage()); }
    }

    private Optional<Booking> findActiveBookingForSeat(int seatNum) {
        LocalDate date = ctx.getCurrentDate();
        return ctx.getStore().getData().getBookings().stream()
            .filter(b -> b.getSeatNumber() == seatNum
                      && b.getDate().equals(date)
                      && b.isActive())
            .findFirst();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void showAlert(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait();
    }

    private void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait();
    }
}
