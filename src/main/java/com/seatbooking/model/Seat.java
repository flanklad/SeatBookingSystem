package com.seatbooking.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Seat {

    private final int seatNumber;
    private final SeatType type;
    private SeatState state;
    private String assignedMemberId; // null when FREE/BLOCKED

    @JsonCreator
    public Seat(@JsonProperty("seatNumber") int seatNumber,
                @JsonProperty("type")       SeatType type,
                @JsonProperty("state")      SeatState state,
                @JsonProperty("assignedMemberId") String assignedMemberId) {
        this.seatNumber       = seatNumber;
        this.type             = type;
        this.state            = state;
        this.assignedMemberId = assignedMemberId;
    }

    public Seat(int seatNumber) {
        this.seatNumber = seatNumber;
        this.type       = seatNumber <= 40 ? SeatType.FIXED : SeatType.FLOATER;
        this.state      = SeatState.FREE;
    }

    // ---- state machine ----

    public void transitionTo(SeatState newState) {
        if (!state.canTransitionTo(newState)) {
            throw new IllegalStateException(
                "Seat " + seatNumber + ": invalid transition " + state + " -> " + newState);
        }
        this.state = newState;
    }

    public void resetToFree() {
        this.state            = SeatState.FREE;
        this.assignedMemberId = null;
    }

    // ---- getters / setters ----

    public int getSeatNumber()        { return seatNumber; }
    public SeatType getType()         { return type; }
    public SeatState getState()       { return state; }
    public String getAssignedMemberId() { return assignedMemberId; }

    public void setState(SeatState state)                 { this.state = state; }
    public void setAssignedMemberId(String memberId)      { this.assignedMemberId = memberId; }

    @JsonIgnore public boolean isFree()     { return state == SeatState.FREE; }
    @JsonIgnore public boolean isFloater()  { return type  == SeatType.FLOATER; }
    @JsonIgnore public boolean isFixed()    { return type  == SeatType.FIXED; }

    @Override
    public String toString() {
        return String.format("Seat[%2d|%s|%s|%s]",
            seatNumber, type, state,
            assignedMemberId == null ? "-" : assignedMemberId);
    }
}
