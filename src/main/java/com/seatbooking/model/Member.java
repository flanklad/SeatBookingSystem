package com.seatbooking.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

public class Member {

    private final String id;
    private final String name;
    private final int    squadId;
    private final int    homeSeatNumber; // fixed-seat assignment during batch days (1-40)
    private boolean      onVacation;
    private LocalDate    vacationStart;
    private LocalDate    vacationEnd;

    @JsonCreator
    public Member(@JsonProperty("id")             String id,
                  @JsonProperty("name")           String name,
                  @JsonProperty("squadId")        int squadId,
                  @JsonProperty("homeSeatNumber") int homeSeatNumber,
                  @JsonProperty("onVacation")     boolean onVacation,
                  @JsonProperty("vacationStart")  LocalDate vacationStart,
                  @JsonProperty("vacationEnd")    LocalDate vacationEnd) {
        this.id             = id;
        this.name           = name;
        this.squadId        = squadId;
        this.homeSeatNumber = homeSeatNumber;
        this.onVacation     = onVacation;
        this.vacationStart  = vacationStart;
        this.vacationEnd    = vacationEnd;
    }

    public boolean isOnVacationOn(LocalDate date) {
        if (!onVacation || vacationStart == null || vacationEnd == null) return false;
        return !date.isBefore(vacationStart) && !date.isAfter(vacationEnd);
    }

    // ---- getters ----
    public String    getId()             { return id; }
    public String    getName()           { return name; }
    public int       getSquadId()        { return squadId; }
    public int       getHomeSeatNumber() { return homeSeatNumber; }
    public boolean   isOnVacation()      { return onVacation; }
    public LocalDate getVacationStart()  { return vacationStart; }
    public LocalDate getVacationEnd()    { return vacationEnd; }

    // ---- setters ----
    public void setOnVacation(boolean onVacation)      { this.onVacation = onVacation; }
    public void setVacationStart(LocalDate vacationStart) { this.vacationStart = vacationStart; }
    public void setVacationEnd(LocalDate vacationEnd)    { this.vacationEnd = vacationEnd; }

    @Override
    public String toString() {
        return String.format("Member[%s|%s|squad=%d|seat=%d%s]",
            id, name, squadId, homeSeatNumber, onVacation ? "|VAC" : "");
    }
}
