package com.seatbooking.rules;

import com.seatbooking.model.Member;
import com.seatbooking.model.Seat;
import com.seatbooking.store.JsonDataStore;

import java.time.LocalDate;
import java.util.Optional;

/** Prevents a member from booking while on vacation. */
public class VacationRule implements BookingRule {

    @Override
    public Optional<String> validate(Member member, Seat seat, LocalDate date, JsonDataStore store) {
        if (member.isOnVacationOn(date)) {
            return Optional.of(member.getName() + " is on vacation on " + date + ". Cannot book.");
        }
        return Optional.empty();
    }
}
