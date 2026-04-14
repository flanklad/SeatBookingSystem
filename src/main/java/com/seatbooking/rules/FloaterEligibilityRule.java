package com.seatbooking.rules;

import com.seatbooking.engine.ScheduleEngine;
import com.seatbooking.model.Member;
import com.seatbooking.model.Seat;
import com.seatbooking.model.SeatType;
import com.seatbooking.store.JsonDataStore;

import java.time.LocalDate;
import java.util.Optional;

/**
 * On batch days: only the scheduled batch may use FIXED seats.
 * Off-batch members are restricted to FLOATER seats (41-50).
 */
public class FloaterEligibilityRule implements BookingRule {

    private final ScheduleEngine schedule;

    public FloaterEligibilityRule(ScheduleEngine schedule) {
        this.schedule = schedule;
    }

    @Override
    public Optional<String> validate(Member member, Seat seat, LocalDate date, JsonDataStore store) {
        int scheduledBatch = schedule.getScheduledBatch(date);
        if (scheduledBatch == 0) return Optional.empty(); // not a batch day

        int memberBatch = schedule.getMemberBatch(member.getId());
        if (memberBatch == scheduledBatch) return Optional.empty(); // member's batch is in

        // Off-batch member: may only book floater seats
        if (seat.getType() == SeatType.FIXED) {
            return Optional.of(
                "Seat " + seat.getSeatNumber() + " is a fixed seat reserved for Batch "
                + (scheduledBatch == 1 ? "A" : "B") + " today. "
                + "Off-batch members may only book floater seats (41-50).");
        }
        return Optional.empty();
    }
}
