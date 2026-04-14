package com.seatbooking.analytics;

import com.seatbooking.model.Booking;
import com.seatbooking.model.Seat;
import com.seatbooking.model.SeatState;
import com.seatbooking.model.Squad;
import com.seatbooking.store.JsonDataStore;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes utilisation analytics:
 *  - Daily occupancy %
 *  - Per-squad booking counts
 *  - Wasted (blocked) seats
 */
public class UtilisationTracker {

    private final JsonDataStore store;

    public UtilisationTracker(JsonDataStore store) {
        this.store = store;
    }

    /** Returns stats for the current in-memory seat state (today's snapshot). */
    public DayStats todayStats(LocalDate date) {
        List<Seat> seats = store.getData().getSeats();

        int total    = seats.size();
        int booked   = (int) seats.stream().filter(s -> s.getState() == SeatState.RESERVED).count();
        int occupied = (int) seats.stream().filter(s -> s.getState() == SeatState.OCCUPIED).count();
        int blocked  = (int) seats.stream().filter(s -> s.getState() == SeatState.BLOCKED).count();
        int released = (int) seats.stream().filter(s -> s.getState() == SeatState.RELEASED).count();
        int free     = (int) seats.stream().filter(Seat::isFree).count();

        Map<Integer, Integer> squadCounts = squadBookingCounts(date);

        return new DayStats(date, total, booked, occupied, blocked, released, free, squadCounts);
    }

    /** Returns stats derived from booking history for a past date. */
    public DayStats statsForDate(LocalDate date) {
        List<Booking> dayBookings = store.getData().getBookings().stream()
            .filter(b -> b.getDate().equals(date))
            .collect(Collectors.toList());

        int total    = 50;
        int booked   = (int) dayBookings.stream()
                           .filter(b -> b.getStatus() == com.seatbooking.model.BookingStatus.ACTIVE).count();
        int occupied = 0; // historical: OCCUPIED transitions aren't separately tracked in store
        int blocked  = 0; // we don't persist blocked state historically
        int released = (int) dayBookings.stream()
                           .filter(b -> b.getStatus() == com.seatbooking.model.BookingStatus.RELEASED).count();
        int free     = total - booked - released;

        Map<Integer, Integer> squadCounts = squadBookingCounts(date);

        return new DayStats(date, total, booked, occupied, blocked, released, free, squadCounts);
    }

    /** Maps each squad ID to how many bookings it has on the given date. */
    public Map<Integer, Integer> squadBookingCounts(LocalDate date) {
        List<Booking> dayBookings = store.getData().getBookings().stream()
            .filter(b -> b.getDate().equals(date) && b.isActive())
            .collect(Collectors.toList());

        Map<String, Integer> memberToSquad = new HashMap<>();
        for (Squad sq : store.getData().getSquads()) {
            for (String mid : sq.getMemberIds()) {
                memberToSquad.put(mid, sq.getId());
            }
        }

        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (int i = 1; i <= 10; i++) counts.put(i, 0);

        for (Booking b : dayBookings) {
            Integer squadId = memberToSquad.get(b.getMemberId());
            if (squadId != null) counts.merge(squadId, 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Returns utilisation stats over a date range (inclusive).
     * Only dates that have at least one booking are included.
     */
    public List<DayStats> range(LocalDate from, LocalDate to) {
        List<LocalDate> datesWithData = store.getData().getBookings().stream()
            .map(Booking::getDate)
            .filter(d -> !d.isBefore(from) && !d.isAfter(to))
            .distinct()
            .sorted()
            .collect(Collectors.toList());

        List<DayStats> result = new ArrayList<>();
        for (LocalDate d : datesWithData) {
            result.add(statsForDate(d));
        }
        return result;
    }

    /** Returns per-squad total booking counts over the given date range. */
    public Map<Integer, Integer> squadTotals(LocalDate from, LocalDate to) {
        Map<Integer, Integer> totals = new LinkedHashMap<>();
        for (int i = 1; i <= 10; i++) totals.put(i, 0);

        store.getData().getBookings().stream()
            .filter(b -> !b.getDate().isBefore(from) && !b.getDate().isAfter(to) && b.isActive())
            .forEach(b -> {
                store.getData().getSquads().stream()
                    .filter(sq -> sq.getMemberIds().contains(b.getMemberId()))
                    .findFirst()
                    .ifPresent(sq -> totals.merge(sq.getId(), 1, Integer::sum));
            });

        return totals;
    }
}
