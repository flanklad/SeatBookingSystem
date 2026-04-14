package com.seatbooking.model;

/**
 * State machine states for a seat.
 * Valid transitions:
 *   FREE -> RESERVED  (on booking)
 *   FREE -> BLOCKED   (after 3PM auto-block)
 *   RESERVED -> OCCUPIED  (check-in)
 *   RESERVED -> FREE      (cancellation)
 *   OCCUPIED -> RELEASED  (vacation / early release)
 *   OCCUPIED -> FREE      (day-end reset)
 *   BLOCKED  -> FREE      (daily reset)
 *   RELEASED -> FREE      (daily reset)
 */
public enum SeatState {
    FREE, RESERVED, OCCUPIED, BLOCKED, RELEASED;

    public boolean canTransitionTo(SeatState target) {
        return switch (this) {
            case FREE     -> target == RESERVED || target == BLOCKED;
            case RESERVED -> target == OCCUPIED || target == FREE;
            case OCCUPIED -> target == RELEASED || target == FREE;
            case BLOCKED  -> target == FREE;
            case RELEASED -> target == FREE;
        };
    }
}
