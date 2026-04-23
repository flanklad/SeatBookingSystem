package com.seatbooking.model;

public enum BookingStatus {
    ACTIVE,      // seat reserved — booked but not yet checked in
    OCCUPIED,    // member has checked in
    CANCELLED,   // cancelled by member or admin
    COMPLETED,   // end-of-day completed normally
    RELEASED     // released early (vacation / ad-hoc)
}
