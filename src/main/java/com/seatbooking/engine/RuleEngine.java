package com.seatbooking.engine;

import com.seatbooking.model.Member;
import com.seatbooking.model.Seat;
import com.seatbooking.rules.BookingRule;
import com.seatbooking.store.JsonDataStore;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Chains multiple BookingRules together.
 * All rules run; first violation is returned.
 */
public class RuleEngine {

    private final List<BookingRule> rules = new ArrayList<>();

    public RuleEngine addRule(BookingRule rule) {
        rules.add(rule);
        return this;
    }

    /**
     * Returns Optional.empty() if every rule passes,
     * or Optional.of("error message") on the first failure.
     */
    public Optional<String> validate(Member member, Seat seat,
                                     LocalDate date, JsonDataStore store) {
        for (BookingRule rule : rules) {
            Optional<String> result = rule.validate(member, seat, date, store);
            if (result.isPresent()) return result;
        }
        return Optional.empty();
    }
}
