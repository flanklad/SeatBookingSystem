package com.seatbooking.rules;

import com.seatbooking.model.Member;
import com.seatbooking.model.Seat;
import com.seatbooking.store.JsonDataStore;

import java.time.LocalDate;
import java.util.Optional;

/** Rejects bookings on declared public holidays. */
public class HolidayRule implements BookingRule {

    @Override
    public Optional<String> validate(Member member, Seat seat, LocalDate date, JsonDataStore store) {
        if (store.getData().getHolidays().contains(date)) {
            return Optional.of("Booking not allowed on holiday: " + date);
        }
        return Optional.empty();
    }
}
