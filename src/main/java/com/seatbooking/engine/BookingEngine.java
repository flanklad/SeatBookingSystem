package com.seatbooking.engine;

import com.seatbooking.model.*;
import com.seatbooking.rules.*;
import com.seatbooking.store.JsonDataStore;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates all booking operations:
 * book, cancel, check-in, release, auto-assign batch, block unbooked.
 */
public class BookingEngine {

    private final JsonDataStore   store;
    private final ScheduleEngine  schedule;
    private final RuleEngine      ruleEngine;

    public BookingEngine(JsonDataStore store, ScheduleEngine schedule) {
        this.store    = store;
        this.schedule = schedule;
        this.ruleEngine = new RuleEngine()
            .addRule(new HolidayRule())
            .addRule(new AfterThreePMRule())
            .addRule(new VacationRule())
            .addRule(new FloaterEligibilityRule(schedule))
            .addRule(new NonDesignatedDayRule(schedule));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Day initialisation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resets all seats to FREE and, if it is a batch day, auto-assigns
     * the 40 fixed seats to the scheduled batch members.
     * Safe to call multiple times (idempotent for the same date).
     */
    public void initializeDay(LocalDate date) {
        AppData data = store.getData();

        // Reset seat states
        data.getSeats().forEach(Seat::resetToFree);

        // Remove stale bookings for this date so we can recreate them cleanly
        data.getBookings().removeIf(b -> b.getDate().equals(date));

        int scheduledBatch = schedule.getScheduledBatch(date);
        if (scheduledBatch != 0 && !schedule.isHoliday(date)) {
            autoAssignBatchSeats(date, scheduledBatch);
        }
        store.save();
    }

    /** Auto-assigns the 40 fixed seats to the 40 members of the scheduled batch. */
    private void autoAssignBatchSeats(LocalDate date, int batchId) {
        AppData data = store.getData();

        List<Member> batchMembers = data.getMembers().stream()
            .filter(m -> {
                Optional<Squad> sq = schedule.getMemberSquad(m.getId());
                return sq.isPresent() && sq.get().getBatchId() == batchId;
            })
            .sorted(Comparator.comparingInt(Member::getHomeSeatNumber))
            .collect(Collectors.toList());

        for (Member member : batchMembers) {
            if (member.isOnVacationOn(date)) continue; // vacation: seat left free

            int seatNum = member.getHomeSeatNumber();
            Seat seat = getSeat(seatNum);
            if (seat != null && seat.isFree()) {
                seat.transitionTo(SeatState.RESERVED);
                seat.setAssignedMemberId(member.getId());
                data.getBookings().add(Booking.auto(member.getId(), seatNum, date));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Manual booking
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Books a seat for a member on a given date.
     * Returns a success message or throws IllegalArgumentException with the reason.
     */
    public Booking book(String memberId, int seatNumber, LocalDate date) {
        AppData data = store.getData();
        Member member = getMemberOrThrow(memberId);
        Seat seat     = getSeatOrThrow(seatNumber);

        // Validate rules
        Optional<String> violation = ruleEngine.validate(member, seat, date, store);
        violation.ifPresent(msg -> { throw new IllegalArgumentException(msg); });

        // Check seat availability
        if (!seat.isFree()) {
            throw new IllegalArgumentException(
                "Seat " + seatNumber + " is not free (current state: " + seat.getState() + ").");
        }

        // Check member doesn't already have a booking for this date
        boolean alreadyBooked = data.getBookings().stream()
            .anyMatch(b -> b.getMemberId().equals(memberId)
                        && b.getDate().equals(date)
                        && b.isActive());
        if (alreadyBooked) {
            throw new IllegalArgumentException(
                member.getName() + " already has an active booking on " + date + ".");
        }

        seat.transitionTo(SeatState.RESERVED);
        seat.setAssignedMemberId(memberId);
        Booking booking = Booking.manual(memberId, seatNumber, date);
        data.getBookings().add(booking);
        store.save();
        return booking;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cancel
    // ─────────────────────────────────────────────────────────────────────────

    public void cancel(String bookingId) {
        AppData data = store.getData();
        Booking booking = getBookingOrThrow(bookingId);
        if (!booking.isActive()) {
            throw new IllegalArgumentException("Booking " + bookingId + " is not active.");
        }

        booking.setStatus(BookingStatus.CANCELLED);

        Seat seat = getSeat(booking.getSeatNumber());
        if (seat != null && seat.getState() == SeatState.RESERVED) {
            seat.transitionTo(SeatState.FREE);
            seat.setAssignedMemberId(null);
        }
        store.save();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Check-in
    // ─────────────────────────────────────────────────────────────────────────

    public void checkIn(String bookingId) {
        AppData data = store.getData();
        Booking booking = getBookingOrThrow(bookingId);
        if (!booking.isActive()) {
            throw new IllegalArgumentException("Booking " + bookingId + " is not active.");
        }
        Seat seat = getSeatOrThrow(booking.getSeatNumber());
        if (seat.getState() != SeatState.RESERVED) {
            throw new IllegalStateException(
                "Seat " + seat.getSeatNumber() + " must be RESERVED to check in.");
        }
        seat.transitionTo(SeatState.OCCUPIED);
        store.save();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Release (vacation / early departure)
    // ─────────────────────────────────────────────────────────────────────────

    public void releaseSeat(String memberId, LocalDate date) {
        AppData data = store.getData();
        Booking booking = data.getBookings().stream()
            .filter(b -> b.getMemberId().equals(memberId)
                      && b.getDate().equals(date)
                      && b.isActive())
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "No active booking found for member " + memberId + " on " + date));

        Seat seat = getSeatOrThrow(booking.getSeatNumber());
        if (seat.getState() == SeatState.OCCUPIED) {
            seat.transitionTo(SeatState.RELEASED);
        } else if (seat.getState() == SeatState.RESERVED) {
            seat.transitionTo(SeatState.FREE);
        }
        seat.setAssignedMemberId(null);
        booking.setStatus(BookingStatus.RELEASED);
        store.save();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3 PM auto-block
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Blocks all FREE seats for the given date (called at / after 15:00).
     * Returns the count of seats blocked.
     */
    public int blockUnbookedSeats(LocalDate date) {
        if (schedule.isHoliday(date) || schedule.isWeekend(date)) return 0;

        int count = 0;
        for (Seat seat : store.getData().getSeats()) {
            if (seat.isFree()) {
                seat.transitionTo(SeatState.BLOCKED);
                count++;
            }
        }
        store.save();
        return count;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Vacation management
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sets a vacation period for a member and releases their active booking
     * if the booking falls within the vacation window.
     */
    public void setVacation(String memberId, LocalDate start, LocalDate end) {
        Member member = getMemberOrThrow(memberId);
        member.setOnVacation(true);
        member.setVacationStart(start);
        member.setVacationEnd(end);

        // Release any active bookings within the vacation period
        store.getData().getBookings().stream()
            .filter(b -> b.getMemberId().equals(memberId)
                      && b.isActive()
                      && !b.getDate().isBefore(start)
                      && !b.getDate().isAfter(end))
            .forEach(b -> {
                b.setStatus(BookingStatus.RELEASED);
                Seat s = getSeat(b.getSeatNumber());
                if (s != null) {
                    if (s.getState() == SeatState.OCCUPIED) s.transitionTo(SeatState.RELEASED);
                    else if (s.getState() == SeatState.RESERVED) s.transitionTo(SeatState.FREE);
                    s.setAssignedMemberId(null);
                }
            });

        store.save();
    }

    public void clearVacation(String memberId) {
        Member member = getMemberOrThrow(memberId);
        member.setOnVacation(false);
        member.setVacationStart(null);
        member.setVacationEnd(null);
        store.save();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Seat getSeat(int seatNumber) {
        return store.getData().getSeats().stream()
                    .filter(s -> s.getSeatNumber() == seatNumber)
                    .findFirst().orElse(null);
    }

    private Seat getSeatOrThrow(int seatNumber) {
        Seat s = getSeat(seatNumber);
        if (s == null) throw new IllegalArgumentException("Seat " + seatNumber + " not found.");
        return s;
    }

    public Member getMemberOrThrow(String memberId) {
        return store.getData().getMembers().stream()
                    .filter(m -> m.getId().equals(memberId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
    }

    private Booking getBookingOrThrow(String bookingId) {
        return store.getData().getBookings().stream()
                    .filter(b -> b.getId().equals(bookingId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
    }

    public List<Booking> getBookingsForDate(LocalDate date) {
        return store.getData().getBookings().stream()
                    .filter(b -> b.getDate().equals(date))
                    .collect(Collectors.toList());
    }

    public List<Booking> getActiveBookingsForMember(String memberId) {
        return store.getData().getBookings().stream()
                    .filter(b -> b.getMemberId().equals(memberId) && b.isActive())
                    .collect(Collectors.toList());
    }

    public ScheduleEngine getScheduleEngine() { return schedule; }
}
