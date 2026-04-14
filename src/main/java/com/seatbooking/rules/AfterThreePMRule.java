package com.seatbooking.rules;

import com.seatbooking.model.Member;
import com.seatbooking.model.Seat;
import com.seatbooking.store.JsonDataStore;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/**
 * After 15:00 (3 PM), same-day bookings are no longer accepted.
 * Members may still book for future dates.
 */
public class AfterThreePMRule implements BookingRule {

    private static final LocalTime CUTOFF = LocalTime.of(15, 0);

    @Override
    public Optional<String> validate(Member member, Seat seat, LocalDate date, JsonDataStore store) {
        if (date.equals(LocalDate.now()) && LocalTime.now().isAfter(CUTOFF)) {
            return Optional.of("Same-day bookings are closed after 15:00. Book for a future date.");
        }
        return Optional.empty();
    }
}
