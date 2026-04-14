package com.seatbooking.ui.controller;

import com.seatbooking.analytics.DayStats;
import com.seatbooking.model.Booking;
import com.seatbooking.model.Member;
import com.seatbooking.model.Squad;
import com.seatbooking.ui.AppContext;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Dashboard home view — daily stats, weekly schedule, recent bookings,
 * upcoming holidays, and quick-action buttons.
 */
public class DashboardController {

    @FXML private Label  lblWelcome;
    @FXML private Label  lblSubtitle;

    // Stat cards
    @FXML private Label  lblBookingsToday;
    @FXML private Label  lblBookingsDesc;
    @FXML private Label  lblFreeSeats;
    @FXML private Label  lblFreeDesc;
    @FXML private Label  lblOccupancy;
    @FXML private Label  lblOccDesc;
    @FXML private Label  lblOnVacation;
    @FXML private Label  lblVacDesc;

    // Week schedule
    @FXML private HBox   weekRow;
    @FXML private Label  lblWeekRange;

    // Today detail
    @FXML private Label  lblBatchToday;
    @FXML private Label  lblFortnightWeek;
    @FXML private Label  lblSquadDay;
    @FXML private Label  lblBlocked;

    // Recent bookings table
    @FXML private TableView<Booking>           recentTable;
    @FXML private TableColumn<Booking, String> colRMember;
    @FXML private TableColumn<Booking, String> colRSeat;
    @FXML private TableColumn<Booking, String> colRStatus;
    @FXML private TableColumn<Booking, String> colRDate;

    // Holidays list
    @FXML private VBox holidayList;

    private final AppContext                   ctx   = AppContext.get();
    private final ObservableList<Booking>      recents = FXCollections.observableArrayList();
    private static final DateTimeFormatter     FMT   = DateTimeFormatter.ofPattern("dd MMM");

    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        setupRecentTable();
        ctx.addRefreshListener(this::refresh);
        refresh();
    }

    private void setupRecentTable() {
        colRMember.setCellValueFactory(d -> new SimpleStringProperty(
            resolveName(d.getValue().getMemberId())));
        colRSeat.setCellValueFactory(d -> new SimpleStringProperty(
            String.valueOf(d.getValue().getSeatNumber())));
        colRStatus.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getStatus().name()));
        colRDate.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getDate().format(FMT)));

        colRStatus.setCellFactory(col -> new TableCell<>() {
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

        recentTable.setItems(recents);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Refresh
    // ─────────────────────────────────────────────────────────────────────────

    private void refresh() {
        LocalDate date = ctx.getCurrentDate();
        DayStats stats = ctx.getAnalytics().todayStats(date);

        // Welcome header
        lblWelcome.setText("Good morning — " + date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")));
        lblSubtitle.setText(ctx.getSchedule().describeDayType(date));

        // Stat cards
        int bookings = stats.getBookedSeats() + stats.getOccupiedSeats();
        lblBookingsToday.setText(String.valueOf(bookings));
        lblBookingsDesc.setText("reserved + occupied");
        lblFreeSeats.setText(String.valueOf(stats.getFreeSeats()));
        lblFreeDesc.setText("seats available");
        lblOccupancy.setText(String.format("%.0f%%", stats.getOccupancyPercent()));
        lblOccDesc.setText(stats.getOccupiedSeats() + " checked in");

        long onVac = ctx.getStore().getData().getMembers().stream()
            .filter(m -> m.isOnVacationOn(date)).count();
        lblOnVacation.setText(String.valueOf(onVac));
        lblVacDesc.setText("member(s) away");

        // Today detail
        int batch = ctx.getSchedule().getScheduledBatch(date);
        lblBatchToday.setText(batch == 0 ? "—" : "Batch " + (batch == 1 ? "A" : "B") + "  (squads " + (batch == 1 ? "1–5" : "6–10") + ")");
        lblFortnightWeek.setText("Week " + ctx.getSchedule().getFortnightWeek(date));
        lblSquadDay.setText(ctx.getSchedule().getSquadDaySquad(date)
            .map(s -> s.getName()).orElse("—"));
        lblBlocked.setText(String.valueOf(stats.getBlockedSeats()));

        // Week schedule
        buildWeekSchedule(date);

        // Recent bookings (last 10 across all dates)
        List<Booking> all = ctx.getStore().getData().getBookings();
        List<Booking> sorted = all.stream()
            .sorted(Comparator.comparing(Booking::getDate).reversed())
            .limit(10)
            .collect(Collectors.toList());
        recents.setAll(sorted);

        // Upcoming holidays
        buildHolidayList(date);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Week schedule cells
    // ─────────────────────────────────────────────────────────────────────────

    private void buildWeekSchedule(LocalDate date) {
        weekRow.getChildren().clear();

        LocalDate monday = date.with(DayOfWeek.MONDAY);
        lblWeekRange.setText("w/c " + monday.format(FMT));

        for (int i = 0; i < 5; i++) {
            LocalDate day = monday.plusDays(i);
            weekRow.getChildren().add(makeWeekCell(day, day.equals(date)));
        }
    }

    private VBox makeWeekCell(LocalDate day, boolean isToday) {
        String dayName = day.getDayOfWeek().name().substring(0, 3);
        String dateStr = day.format(FMT);
        int batch = ctx.getSchedule().getScheduledBatch(day);
        boolean holiday = ctx.getSchedule().isHoliday(day);

        String batchLabel;
        String styleClass;
        if (holiday) {
            batchLabel = "HOLIDAY";
            styleClass = "week-cell-holiday";
        } else if (batch == 1) {
            batchLabel = "BATCH A";
            styleClass = "week-cell-batchA";
        } else if (batch == 2) {
            batchLabel = "BATCH B";
            styleClass = "week-cell-batchB";
        } else {
            batchLabel = "—";
            styleClass = "week-cell-weekend";
        }

        Label lblDay   = new Label(dayName);
        Label lblDate  = new Label(dateStr);
        Label lblBatch = new Label(batchLabel);

        lblDay.getStyleClass().add("week-cell-day");
        lblDate.getStyleClass().add("week-cell-date");
        lblBatch.getStyleClass().add("week-cell-batch");

        VBox cell = new VBox(4, lblDay, lblDate, lblBatch);
        cell.setAlignment(Pos.CENTER);
        cell.setPadding(new Insets(10, 14, 10, 14));
        cell.getStyleClass().addAll("week-cell", styleClass);
        HBox.setHgrow(cell, Priority.ALWAYS);

        if (isToday) {
            cell.getStyleClass().add("week-cell-today");
        }

        // Tooltip: squads active
        String squadInfo = ctx.getStore().getData().getSquads().stream()
            .filter(s -> s.getBatchId() == batch)
            .map(Squad::getName)
            .collect(Collectors.joining(", "));
        if (!squadInfo.isEmpty()) {
            Tooltip.install(cell, new Tooltip("Active: " + squadInfo));
        }

        return cell;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Upcoming holidays list
    // ─────────────────────────────────────────────────────────────────────────

    private void buildHolidayList(LocalDate today) {
        holidayList.getChildren().clear();

        List<LocalDate> upcoming = ctx.getStore().getData().getHolidays().stream()
            .filter(h -> !h.isBefore(today))
            .sorted()
            .limit(5)
            .collect(Collectors.toList());

        if (upcoming.isEmpty()) {
            Label none = new Label("No upcoming holidays.");
            none.getStyleClass().add("label-muted");
            holidayList.getChildren().add(none);
            return;
        }

        for (LocalDate h : upcoming) {
            long daysAway = today.until(h).getDays();
            String suffix = daysAway == 0 ? "Today"
                          : daysAway == 1 ? "Tomorrow"
                          : "in " + daysAway + " days";
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            Label dateLbl = new Label(h.format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
            dateLbl.setStyle("-fx-text-fill: #f38ba8; -fx-font-weight: bold;");
            Label suffixLbl = new Label("(" + suffix + ")");
            suffixLbl.getStyleClass().add("label-muted");
            row.getChildren().addAll(dateLbl, suffixLbl);
            holidayList.getChildren().add(row);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Quick actions
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void initToday() {
        LocalDate date = ctx.getCurrentDate();
        if (ctx.getSchedule().isHoliday(date)) {
            ctx.showStatus(date + " is a holiday — cannot initialise.", false);
            return;
        }
        if (ctx.getSchedule().isWeekend(date)) {
            ctx.showStatus(date + " is a weekend — no seats to assign.", false);
            return;
        }
        ctx.getEngine().initializeDay(date);
        ctx.fireRefresh();
        ctx.showStatus("Day initialised for " + date + "  [" + ctx.getSchedule().describeDayType(date) + "]", true);
    }

    @FXML
    private void blockToday() {
        int n = ctx.getScheduler().triggerNow(ctx.getCurrentDate());
        ctx.fireRefresh();
        ctx.showStatus("Blocked " + n + " unbooked seat(s).", true);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private String resolveName(String memberId) {
        return ctx.getStore().getData().getMembers().stream()
            .filter(m -> m.getId().equals(memberId))
            .map(Member::getName)
            .findFirst().orElse(memberId);
    }
}
