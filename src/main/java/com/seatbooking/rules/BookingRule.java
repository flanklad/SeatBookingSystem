package com.seatbooking.rules;

import com.seatbooking.model.Member;
import com.seatbooking.model.Seat;
import com.seatbooking.store.JsonDataStore;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Pluggable, chainable booking validation rule.
 * Returns Optional.empty() when the booking is allowed,
 * or Optional.of("reason") when it must be rejected.
 */
@FunctionalInterface
public interface BookingRule {
    Optional<String> validate(Member member, Seat seat, LocalDate date, JsonDataStore store);
}
