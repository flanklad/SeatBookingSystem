package com.seatbooking.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Root object serialised to / from the JSON data file.
 */
public class AppData {

    private List<Seat>      seats;
    private List<Member>    members;
    private List<Squad>     squads;
    private List<Booking>   bookings;
    private List<LocalDate> holidays;

    public AppData() {
        this.seats    = new ArrayList<>();
        this.members  = new ArrayList<>();
        this.squads   = new ArrayList<>();
        this.bookings = new ArrayList<>();
        this.holidays = new ArrayList<>();
    }

    @JsonCreator
    public AppData(@JsonProperty("seats")    List<Seat>      seats,
                   @JsonProperty("members")  List<Member>    members,
                   @JsonProperty("squads")   List<Squad>     squads,
                   @JsonProperty("bookings") List<Booking>   bookings,
                   @JsonProperty("holidays") List<LocalDate> holidays) {
        this.seats    = seats    != null ? seats    : new ArrayList<>();
        this.members  = members  != null ? members  : new ArrayList<>();
        this.squads   = squads   != null ? squads   : new ArrayList<>();
        this.bookings = bookings != null ? bookings : new ArrayList<>();
        this.holidays = holidays != null ? holidays : new ArrayList<>();
    }

    public List<Seat>      getSeats()    { return seats; }
    public List<Member>    getMembers()  { return members; }
    public List<Squad>     getSquads()   { return squads; }
    public List<Booking>   getBookings() { return bookings; }
    public List<LocalDate> getHolidays() { return holidays; }

    public void setSeats(List<Seat>      s) { this.seats    = s; }
    public void setMembers(List<Member>  m) { this.members  = m; }
    public void setSquads(List<Squad>    q) { this.squads   = q; }
    public void setBookings(List<Booking>b) { this.bookings = b; }
    public void setHolidays(List<LocalDate>h){ this.holidays = h; }
}
