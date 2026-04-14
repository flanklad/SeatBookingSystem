package com.seatbooking.cli;

import com.seatbooking.analytics.DayStats;
import com.seatbooking.analytics.UtilisationTracker;
import com.seatbooking.engine.BookingEngine;
import com.seatbooking.engine.ScheduleEngine;
import com.seatbooking.model.*;
import com.seatbooking.scheduler.SeatBlockScheduler;
import com.seatbooking.store.JsonDataStore;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ASCII terminal dashboard for the Seat Booking System.
 */
public class Dashboard {

    private static final String RESET  = "\u001B[0m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED    = "\u001B[31m";
    private static final String CYAN   = "\u001B[36m";
    private static final String BLUE   = "\u001B[34m";
    private static final String BOLD   = "\u001B[1m";
    private static final String DIM    = "\u001B[2m";

    private final JsonDataStore      store;
    private final BookingEngine      engine;
    private final ScheduleEngine     schedule;
    private final UtilisationTracker analytics;
    private final SeatBlockScheduler scheduler;
    private final Scanner            sc;

    private LocalDate simulatedDate;

    public Dashboard(JsonDataStore store) {
        this.store     = store;
        this.schedule  = new ScheduleEngine(store);
        this.engine    = new BookingEngine(store, schedule);
        this.analytics = new UtilisationTracker(store);
        this.scheduler = new SeatBlockScheduler(engine);
        this.sc        = new Scanner(System.in);
        this.simulatedDate = LocalDate.now();
    }

    public void run() {
        scheduler.start();
        printBanner();

        boolean running = true;
        while (running) {
            printMainMenu();
            String choice = prompt("Choice").trim();
            switch (choice) {
                case "1"  -> showSeatMap();
                case "2"  -> doBook();
                case "3"  -> doCancel();
                case "4"  -> doCheckIn();
                case "5"  -> doRelease();
                case "6"  -> doVacation();
                case "7"  -> showBookings();
                case "8"  -> showAnalytics();
                case "9"  -> doInitDay();
                case "10" -> doManualBlock();
                case "11" -> doChangeDate();
                case "12" -> listMembers();
                case "0"  -> running = false;
                default   -> println(RED + "Unknown option." + RESET);
            }
        }

        scheduler.stop();
        println(CYAN + "Goodbye!" + RESET);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Banner & Menu
    // ─────────────────────────────────────────────────────────────────────────

    private void printBanner() {
        println(CYAN + BOLD);
        println("╔══════════════════════════════════════════════════════════════╗");
        println("║          SEAT BOOKING SYSTEM  v1.0                          ║");
        println("║  50 Seats | 10 Squads | 80 Members | Fortnightly Rotation   ║");
        println("╚══════════════════════════════════════════════════════════════╝" + RESET);
    }

    private void printMainMenu() {
        String dayType = schedule.describeDayType(simulatedDate);
        int fw = schedule.getFortnightWeek(simulatedDate);
        println("\n" + BOLD + "Date: " + simulatedDate
            + " (" + simulatedDate.getDayOfWeek() + ") | " + dayType
            + " | Fortnight Week " + fw + RESET);
        println(DIM + "─────────────────────────────────────────────────────────" + RESET);
        println("  [1]  View Seat Map");
        println("  [2]  Book a Seat");
        println("  [3]  Cancel Booking");
        println("  [4]  Check In (RESERVED → OCCUPIED)");
        println("  [5]  Release Seat (vacation / early departure)");
        println("  [6]  Set / Clear Vacation");
        println("  [7]  View Bookings for Date");
        println("  [8]  Utilisation Analytics");
        println("  [9]  Initialise Day (auto-assign batch seats)");
        println("  [10] Trigger 3PM Block (simulate)");
        println("  [11] Change Simulation Date");
        println("  [12] List Members");
        println("  [0]  Exit");
        println(DIM + "─────────────────────────────────────────────────────────" + RESET);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Seat Map
    // ─────────────────────────────────────────────────────────────────────────

    private void showSeatMap() {
        List<Seat> seats = store.getData().getSeats();
        println("\n" + BOLD + CYAN + "╔════════════════════════════════════════════════════════╗" + RESET);
        println(BOLD + CYAN + "║               SEAT MAP  —  " + simulatedDate + "                  ║" + RESET);
        println(BOLD + CYAN + "╠════════════════════════════════════════════════════════╣" + RESET);
        println(BOLD + CYAN + "║  Legend: " + GREEN + "[F]FREE " + YELLOW + "[R]RESERVED " + BLUE + "[O]OCCUPIED "
            + RED + "[B]BLOCKED " + DIM + "[X]RELEASED" + RESET + BOLD + CYAN + "   ║" + RESET);
        println(BOLD + CYAN + "╠════════════════════════════════════════════════════════╣" + RESET);
        println(BOLD + "║  FIXED SEATS (1–40)                                    ║" + RESET);
        println(BOLD + CYAN + "╠════════════════════════════════════════════════════════╣" + RESET);

        // Print fixed seats in rows of 8 (matching squad blocks)
        for (int row = 0; row < 5; row++) {
            StringBuilder line = new StringBuilder("║  ");
            for (int col = 0; col < 8; col++) {
                int seatNum = row * 8 + col + 1;
                Seat s = getSeatByNumber(seats, seatNum);
                line.append(formatSeat(s));
                if (col < 7) line.append(" ");
            }
            // pad to width
            String raw = stripAnsi(line.toString());
            int padNeeded = 57 - raw.length();
            line.append(" ".repeat(Math.max(0, padNeeded)));
            println(line + RESET + BOLD + CYAN + "║" + RESET);
        }

        println(BOLD + CYAN + "╠════════════════════════════════════════════════════════╣" + RESET);
        println(BOLD + "║  FLOATER SEATS (41–50)                                 ║" + RESET);
        println(BOLD + CYAN + "╠════════════════════════════════════════════════════════╣" + RESET);

        StringBuilder floaterLine = new StringBuilder("║  ");
        for (int seatNum = 41; seatNum <= 50; seatNum++) {
            Seat s = getSeatByNumber(seats, seatNum);
            floaterLine.append(formatSeat(s));
            if (seatNum < 50) floaterLine.append(" ");
        }
        String rawF = stripAnsi(floaterLine.toString());
        int padF = 57 - rawF.length();
        floaterLine.append(" ".repeat(Math.max(0, padF)));
        println(floaterLine + RESET + BOLD + CYAN + "║" + RESET);

        println(BOLD + CYAN + "╚════════════════════════════════════════════════════════╝" + RESET);

        // Summary
        List<Seat> s = store.getData().getSeats();
        long free  = s.stream().filter(Seat::isFree).count();
        long res   = s.stream().filter(x -> x.getState() == SeatState.RESERVED).count();
        long occ   = s.stream().filter(x -> x.getState() == SeatState.OCCUPIED).count();
        long blk   = s.stream().filter(x -> x.getState() == SeatState.BLOCKED).count();
        long rel   = s.stream().filter(x -> x.getState() == SeatState.RELEASED).count();
        println(String.format("  Summary: " + GREEN + "Free=%d " + YELLOW + "Reserved=%d "
            + BLUE + "Occupied=%d " + RED + "Blocked=%d " + DIM + "Released=%d" + RESET,
            free, res, occ, blk, rel));
    }

    private String formatSeat(Seat s) {
        if (s == null) return "     ";
        String num = String.format("%2d", s.getSeatNumber());
        return switch (s.getState()) {
            case FREE     -> GREEN  + "[" + num + ":F]" + RESET;
            case RESERVED -> YELLOW + "[" + num + ":R]" + RESET;
            case OCCUPIED -> BLUE   + "[" + num + ":O]" + RESET;
            case BLOCKED  -> RED    + "[" + num + ":B]" + RESET;
            case RELEASED -> DIM    + "[" + num + ":X]" + RESET;
        };
    }

    private Seat getSeatByNumber(List<Seat> seats, int n) {
        return seats.stream().filter(s -> s.getSeatNumber() == n).findFirst().orElse(null);
    }

    /** Strips ANSI codes for length calculation. */
    private String stripAnsi(String s) {
        return s.replaceAll("\u001B\\[[;\\d]*m", "");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Book
    // ─────────────────────────────────────────────────────────────────────────

    private void doBook() {
        println("\n--- BOOK A SEAT ---");
        String memberId = prompt("Member ID (e.g. M001)").trim();
        String seatStr  = prompt("Seat number (1-50)").trim();
        String dateStr  = prompt("Date [" + simulatedDate + "]").trim();

        LocalDate date = dateStr.isEmpty() ? simulatedDate : parseDate(dateStr);
        if (date == null) return;

        int seatNumber;
        try { seatNumber = Integer.parseInt(seatStr); }
        catch (NumberFormatException e) { println(RED + "Invalid seat number." + RESET); return; }

        try {
            Booking b = engine.book(memberId, seatNumber, date);
            println(GREEN + "Booked! Booking ID: " + b.getId() + RESET);
        } catch (Exception e) {
            println(RED + "Error: " + e.getMessage() + RESET);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cancel
    // ─────────────────────────────────────────────────────────────────────────

    private void doCancel() {
        println("\n--- CANCEL BOOKING ---");
        showActiveBookingsForDate(simulatedDate);
        String bookingId = prompt("Booking ID to cancel").trim();
        try {
            engine.cancel(bookingId);
            println(GREEN + "Booking cancelled." + RESET);
        } catch (Exception e) {
            println(RED + "Error: " + e.getMessage() + RESET);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Check-in
    // ─────────────────────────────────────────────────────────────────────────

    private void doCheckIn() {
        println("\n--- CHECK IN ---");
        showActiveBookingsForDate(simulatedDate);
        String bookingId = prompt("Booking ID to check in").trim();
        try {
            engine.checkIn(bookingId);
            println(GREEN + "Checked in — seat is now OCCUPIED." + RESET);
        } catch (Exception e) {
            println(RED + "Error: " + e.getMessage() + RESET);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Release
    // ─────────────────────────────────────────────────────────────────────────

    private void doRelease() {
        println("\n--- RELEASE SEAT ---");
        String memberId = prompt("Member ID").trim();
        String dateStr  = prompt("Date [" + simulatedDate + "]").trim();
        LocalDate date  = dateStr.isEmpty() ? simulatedDate : parseDate(dateStr);
        if (date == null) return;

        try {
            engine.releaseSeat(memberId, date);
            println(GREEN + "Seat released." + RESET);
        } catch (Exception e) {
            println(RED + "Error: " + e.getMessage() + RESET);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Vacation
    // ─────────────────────────────────────────────────────────────────────────

    private void doVacation() {
        println("\n--- VACATION MANAGEMENT ---");
        println("  [1] Set vacation");
        println("  [2] Clear vacation");
        String opt = prompt("Option").trim();
        String memberId = prompt("Member ID").trim();

        if ("1".equals(opt)) {
            String start = prompt("Start date (YYYY-MM-DD)").trim();
            String end   = prompt("End date   (YYYY-MM-DD)").trim();
            LocalDate s  = parseDate(start);
            LocalDate e  = parseDate(end);
            if (s == null || e == null) return;
            engine.setVacation(memberId, s, e);
            println(GREEN + "Vacation set: " + s + " to " + e + RESET);
        } else {
            engine.clearVacation(memberId);
            println(GREEN + "Vacation cleared." + RESET);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bookings list
    // ─────────────────────────────────────────────────────────────────────────

    private void showBookings() {
        println("\n--- BOOKINGS FOR " + simulatedDate + " ---");
        showActiveBookingsForDate(simulatedDate);
    }

    private void showActiveBookingsForDate(LocalDate date) {
        List<Booking> bookings = engine.getBookingsForDate(date);
        if (bookings.isEmpty()) {
            println(DIM + "  (no bookings for " + date + ")" + RESET);
            return;
        }
        println(String.format("  %-38s %-10s %-6s %-10s %-8s",
            "Booking ID", "Member", "Seat", "Status", "Type"));
        println(DIM + "  " + "─".repeat(80) + RESET);
        for (Booking b : bookings) {
            String memberName = store.getData().getMembers().stream()
                .filter(m -> m.getId().equals(b.getMemberId()))
                .map(Member::getName)
                .findFirst().orElse(b.getMemberId());
            println(String.format("  %-38s %-10s %-6d %-10s %-8s",
                b.getId(), memberName, b.getSeatNumber(),
                b.getStatus(), b.isAutoAssigned() ? "AUTO" : "MANUAL"));
        }
        println("  Total: " + bookings.size() + " booking(s)");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Analytics
    // ─────────────────────────────────────────────────────────────────────────

    private void showAnalytics() {
        println("\n" + BOLD + CYAN + "╔══════════════════════════════════════════╗" + RESET);
        println(BOLD + CYAN + "║         UTILISATION ANALYTICS            ║" + RESET);
        println(BOLD + CYAN + "╚══════════════════════════════════════════╝" + RESET);

        println("\n--- TODAY (" + simulatedDate + ") ---");
        DayStats today = analytics.todayStats(simulatedDate);
        printDayStats(today);

        println("\n--- PER-SQUAD BREAKDOWN ---");
        Map<Integer, Integer> counts = today.getSquadBookingCounts();
        List<Squad> squads = store.getData().getSquads();
        println(String.format("  %-12s %-8s %-8s %-6s", "Squad", "Batch", "Booked", "Bar"));
        println(DIM + "  " + "─".repeat(45) + RESET);
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            Squad sq = squads.stream().filter(s -> s.getId() == e.getKey()).findFirst().orElse(null);
            String name  = sq != null ? sq.getName()  : "Squad-" + e.getKey();
            String batch = sq != null ? (sq.getBatchId() == 1 ? "A" : "B") : "?";
            int    cnt   = e.getValue();
            String bar   = GREEN + "█".repeat(cnt) + RESET;
            println(String.format("  %-12s %-8s %-8d %s", name, batch, cnt, bar));
        }

        println("\n--- RANGE STATS (last 30 days) ---");
        LocalDate from = simulatedDate.minusDays(30);
        List<DayStats> range = analytics.range(from, simulatedDate);
        if (range.isEmpty()) {
            println(DIM + "  No historical booking data in range." + RESET);
        } else {
            double avgOcc = range.stream().mapToDouble(DayStats::getOccupancyPercent).average().orElse(0);
            int   wasted  = range.stream().mapToInt(DayStats::getWastedSeats).sum();
            println(String.format("  Dates with data : %d", range.size()));
            println(String.format("  Avg occupancy   : %.1f%%", avgOcc));
            println(String.format("  Total wasted    : %d seat-days", wasted));
        }
    }

    private void printDayStats(DayStats s) {
        println(String.format("  Total seats  : %d", s.getTotalSeats()));
        println(String.format("  Reserved     : " + YELLOW + "%d" + RESET, s.getBookedSeats()));
        println(String.format("  Occupied     : " + BLUE   + "%d" + RESET, s.getOccupiedSeats()));
        println(String.format("  Blocked      : " + RED    + "%d" + RESET, s.getBlockedSeats()));
        println(String.format("  Released     : " + DIM    + "%d" + RESET, s.getReleasedSeats()));
        println(String.format("  Free         : " + GREEN  + "%d" + RESET, s.getFreeSeats()));
        println(String.format("  Occupancy %%  : " + BOLD   + "%.1f%%" + RESET, s.getOccupancyPercent()));
        println(String.format("  Wasted seats : " + RED    + "%d" + RESET, s.getWastedSeats()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Day init
    // ─────────────────────────────────────────────────────────────────────────

    private void doInitDay() {
        println("\n--- INITIALISE DAY ---");
        String dateStr = prompt("Date [" + simulatedDate + "]").trim();
        LocalDate date = dateStr.isEmpty() ? simulatedDate : parseDate(dateStr);
        if (date == null) return;

        if (schedule.isHoliday(date)) {
            println(RED + "Cannot initialise: " + date + " is a holiday." + RESET);
            return;
        }
        engine.initializeDay(date);
        println(GREEN + "Day initialised for " + date + " (" + schedule.describeDayType(date) + ")." + RESET);
        showSeatMap();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Manual 3PM block
    // ─────────────────────────────────────────────────────────────────────────

    private void doManualBlock() {
        println("\n--- SIMULATE 3PM AUTO-BLOCK ---");
        String dateStr = prompt("Date [" + simulatedDate + "]").trim();
        LocalDate date = dateStr.isEmpty() ? simulatedDate : parseDate(dateStr);
        if (date == null) return;

        int blocked = scheduler.triggerNow(date);
        println(GREEN + "Blocked " + blocked + " seat(s) on " + date + "." + RESET);
        showSeatMap();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Change simulation date
    // ─────────────────────────────────────────────────────────────────────────

    private void doChangeDate() {
        println("\n--- CHANGE SIMULATION DATE ---");
        String dateStr = prompt("New date (YYYY-MM-DD)").trim();
        LocalDate d = parseDate(dateStr);
        if (d != null) {
            simulatedDate = d;
            println(GREEN + "Simulation date set to " + simulatedDate + RESET);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // List members
    // ─────────────────────────────────────────────────────────────────────────

    private void listMembers() {
        println("\n--- MEMBERS ---");
        println(String.format("  %-6s %-22s %-10s %-6s %-6s %-12s",
            "ID", "Name", "Squad", "Batch", "Seat", "Vacation"));
        println(DIM + "  " + "─".repeat(70) + RESET);

        List<Member> members = store.getData().getMembers();
        List<Squad>  squads  = store.getData().getSquads();

        for (Member m : members) {
            Squad sq = squads.stream()
                .filter(s -> s.getMemberIds().contains(m.getId()))
                .findFirst().orElse(null);
            String batch = sq != null ? (sq.getBatchId() == 1 ? "A" : "B") : "?";
            String vac   = m.isOnVacation()
                ? m.getVacationStart() + " to " + m.getVacationEnd() : "-";
            println(String.format("  %-6s %-22s %-10s %-6s %-6d %-12s",
                m.getId(), m.getName(),
                sq != null ? sq.getName() : "?",
                batch, m.getHomeSeatNumber(), vac));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────────────────────────────────────

    private String prompt(String label) {
        System.out.print(CYAN + "  > " + label + ": " + RESET);
        return sc.nextLine();
    }

    private void println(String s) {
        System.out.println(s);
    }

    private LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            println(RED + "Invalid date format. Use YYYY-MM-DD." + RESET);
            return null;
        }
    }
}
