package com.seatbooking.rules;

import com.seatbooking.model.Member;
import com.seatbooking.model.Seat;
import com.seatbooking.store.JsonDataStore;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Prevents any booking (floater or fixed) on weekends (Saturday or Sunday).
 * Checked first in the rule chain so the error message is clear.
 */
public class WeekendBookingRule implements BookingRule {

    @Override
    public Optional<String> validate(Member member, Seat seat, LocalDate date, JsonDataStore store) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return Optional.of("Bookings are not allowed on weekends (" + dow + " " + date + ").");
        }
        return Optional.empty();
    }
}
