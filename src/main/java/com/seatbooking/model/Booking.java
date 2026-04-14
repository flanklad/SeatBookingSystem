package com.seatbooking.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class Booking {

    private final String        id;
    private final String        memberId;
    private final int           seatNumber;
    private final LocalDate     date;
    private       BookingStatus status;
    private final LocalDateTime createdAt;
    private final boolean       autoAssigned; // true = system-generated batch assignment

    @JsonCreator
    public Booking(@JsonProperty("id")           String id,
                   @JsonProperty("memberId")     String memberId,
                   @JsonProperty("seatNumber")   int seatNumber,
                   @JsonProperty("date")         LocalDate date,
                   @JsonProperty("status")       BookingStatus status,
                   @JsonProperty("createdAt")    LocalDateTime createdAt,
                   @JsonProperty("autoAssigned") boolean autoAssigned) {
        this.id           = id;
        this.memberId     = memberId;
        this.seatNumber   = seatNumber;
        this.date         = date;
        this.status       = status;
        this.createdAt    = createdAt;
        this.autoAssigned = autoAssigned;
    }

    /** Factory for a manual booking. */
    public static Booking manual(String memberId, int seatNumber, LocalDate date) {
        return new Booking(UUID.randomUUID().toString(), memberId, seatNumber,
                           date, BookingStatus.ACTIVE, LocalDateTime.now(), false);
    }

    /** Factory for a batch auto-assignment. */
    public static Booking auto(String memberId, int seatNumber, LocalDate date) {
        return new Booking(UUID.randomUUID().toString(), memberId, seatNumber,
                           date, BookingStatus.ACTIVE, LocalDateTime.now(), true);
    }

    // ---- getters ----
    public String        getId()          { return id; }
    public String        getMemberId()    { return memberId; }
    public int           getSeatNumber()  { return seatNumber; }
    public LocalDate     getDate()        { return date; }
    public BookingStatus getStatus()      { return status; }
    public LocalDateTime getCreatedAt()   { return createdAt; }
    public boolean       isAutoAssigned() { return autoAssigned; }

    // ---- setters ----
    public void setStatus(BookingStatus status) { this.status = status; }

    public boolean isActive() { return status == BookingStatus.ACTIVE; }

    @Override
    public String toString() {
        return String.format("Booking[%s|member=%s|seat=%d|%s|%s|%s]",
            id, memberId, seatNumber, date, status, autoAssigned ? "AUTO" : "MANUAL");
    }
}
