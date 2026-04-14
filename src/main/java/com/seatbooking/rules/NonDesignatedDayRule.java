package com.seatbooking.rules;

import com.seatbooking.engine.ScheduleEngine;
import com.seatbooking.model.Member;
import com.seatbooking.model.Seat;
import com.seatbooking.model.SeatType;
import com.seatbooking.model.Squad;
import com.seatbooking.store.JsonDataStore;

import java.time.LocalDate;
import java.util.Optional;

/**
 * On a squad day: the designated squad may book any of the 50 seats.
 * Non-squad members may only use FLOATER seats.
 *
 * This rule only fires on pure squad days (batch not also in office).
 */
public class NonDesignatedDayRule implements BookingRule {

    private final ScheduleEngine schedule;

    public NonDesignatedDayRule(ScheduleEngine schedule) {
        this.schedule = schedule;
    }

    @Override
    public Optional<String> validate(Member member, Seat seat, LocalDate date, JsonDataStore store) {
        if (!schedule.isPureSquadDay(date)) return Optional.empty();

        Squad daySquad = schedule.getSquadDaySquad(date).orElseThrow();
        boolean isMemberInSquad = daySquad.getMemberIds().contains(member.getId());

        if (!isMemberInSquad && seat.getType() == SeatType.FIXED) {
            return Optional.of(
                "Today is a Squad Day for " + daySquad.getName()
                + ". Non-squad members may only book floater seats (41-50).");
        }
        return Optional.empty();
    }
}
