package com.seatbooking.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.DayOfWeek;
import java.util.List;

public class Squad {

    private final int         id;
    private final String      name;
    private final int         batchId;   // 1 = Batch A, 2 = Batch B
    private final DayOfWeek   squadDay;  // weekly designated squad day
    private final List<String> memberIds;

    @JsonCreator
    public Squad(@JsonProperty("id")        int id,
                 @JsonProperty("name")      String name,
                 @JsonProperty("batchId")   int batchId,
                 @JsonProperty("squadDay")  DayOfWeek squadDay,
                 @JsonProperty("memberIds") List<String> memberIds) {
        this.id        = id;
        this.name      = name;
        this.batchId   = batchId;
        this.squadDay  = squadDay;
        this.memberIds = memberIds;
    }

    public int          getId()        { return id; }
    public String       getName()      { return name; }
    public int          getBatchId()   { return batchId; }
    public DayOfWeek    getSquadDay()  { return squadDay; }
    public List<String> getMemberIds() { return memberIds; }

    @Override
    public String toString() {
        return String.format("Squad[%d|%s|batch=%d|day=%s]", id, name, batchId, squadDay);
    }
}
