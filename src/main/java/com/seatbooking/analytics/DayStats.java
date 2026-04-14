package com.seatbooking.analytics;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public class DayStats {

    private final LocalDate              date;
    private final int                    totalSeats;
    private final int                    bookedSeats;
    private final int                    occupiedSeats;
    private final int                    blockedSeats;
    private final int                    releasedSeats;
    private final int                    freeSeats;
    private final Map<Integer, Integer>  squadBookingCounts; // squadId -> count

    public DayStats(LocalDate date, int totalSeats, int bookedSeats, int occupiedSeats,
                    int blockedSeats, int releasedSeats, int freeSeats,
                    Map<Integer, Integer> squadBookingCounts) {
        this.date               = date;
        this.totalSeats         = totalSeats;
        this.bookedSeats        = bookedSeats;
        this.occupiedSeats      = occupiedSeats;
        this.blockedSeats       = blockedSeats;
        this.releasedSeats      = releasedSeats;
        this.freeSeats          = freeSeats;
        this.squadBookingCounts = new LinkedHashMap<>(squadBookingCounts);
    }

    public double getOccupancyPercent() {
        return totalSeats == 0 ? 0.0 : (bookedSeats + occupiedSeats) * 100.0 / totalSeats;
    }

    public int getWastedSeats() {
        return blockedSeats; // seats that were never booked but blocked
    }

    public LocalDate getDate()               { return date; }
    public int       getTotalSeats()         { return totalSeats; }
    public int       getBookedSeats()        { return bookedSeats; }
    public int       getOccupiedSeats()      { return occupiedSeats; }
    public int       getBlockedSeats()       { return blockedSeats; }
    public int       getReleasedSeats()      { return releasedSeats; }
    public int       getFreeSeats()          { return freeSeats; }
    public Map<Integer, Integer> getSquadBookingCounts() { return squadBookingCounts; }
}
