package com.seatbooking.model;

public enum BookingStatus {
    ACTIVE,      // seat reserved or occupied
    CANCELLED,   // cancelled by member
    COMPLETED,   // end-of-day completed normally
    RELEASED     // released early (vacation / ad-hoc)
}
