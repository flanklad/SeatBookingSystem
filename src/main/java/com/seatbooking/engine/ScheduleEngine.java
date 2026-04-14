package com.seatbooking.engine;

import com.seatbooking.model.Member;
import com.seatbooking.model.Squad;
import com.seatbooking.store.JsonDataStore;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Optional;

/**
 * Determines what type of day a given date is and which batch/squad is active.
 *
 * Fortnightly rotation (reference Monday = 2024-01-01):
 *   Fortnight Week 1: Batch A = Mon/Tue/Wed,  Batch B = Thu/Fri
 *   Fortnight Week 2: Batch A = Thu/Fri,      Batch B = Mon/Tue/Wed
 *
 * Week parity: even ISO-week-of-year → Fortnight Week 1; odd → Fortnight Week 2.
 */
public class ScheduleEngine {

    private static final LocalDate REFERENCE_MONDAY = LocalDate.of(2024, 1, 1);

    private final JsonDataStore store;

    public ScheduleEngine(JsonDataStore store) {
        this.store = store;
    }

    // ---- Fortnight logic ----

    /** Returns 1 or 2 indicating fortnight week for the given date. */
    public int getFortnightWeek(LocalDate date) {
        long weeksSinceRef = ChronoUnit.WEEKS.between(
            REFERENCE_MONDAY.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
            date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        );
        return (weeksSinceRef % 2 == 0) ? 1 : 2;
    }

    /**
     * Returns which batch (1 or 2) is scheduled on the given date.
     * Returns 0 if neither (e.g. weekend).
     */
    public int getScheduledBatch(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return 0;

        int week = getFortnightWeek(date);
        boolean isMWF = (dow == DayOfWeek.MONDAY || dow == DayOfWeek.TUESDAY || dow == DayOfWeek.WEDNESDAY);
        boolean isTF  = (dow == DayOfWeek.THURSDAY || dow == DayOfWeek.FRIDAY);

        if (week == 1) {
            if (isMWF) return 1; // Batch A
            if (isTF)  return 2; // Batch B
        } else {
            if (isTF)  return 1; // Batch A
            if (isMWF) return 2; // Batch B
        }
        return 0;
    }

    /** Returns the Squad whose squad day falls on this date, if any. */
    public Optional<Squad> getSquadDaySquad(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return store.getData().getSquads().stream()
                    .filter(s -> s.getSquadDay() == dow)
                    .findFirst();
    }

    /**
     * True when this date is a squad day AND the squad's batch is NOT scheduled
     * (so squad day rules take precedence).
     */
    public boolean isPureSquadDay(LocalDate date) {
        Optional<Squad> squad = getSquadDaySquad(date);
        if (squad.isEmpty()) return false;
        int scheduledBatch = getScheduledBatch(date);
        return scheduledBatch != squad.get().getBatchId();
    }

    public boolean isBatchDay(LocalDate date) {
        return getScheduledBatch(date) != 0;
    }

    public boolean isHoliday(LocalDate date) {
        return store.getData().getHolidays().contains(date);
    }

    public boolean isWeekend(LocalDate date) {
        DayOfWeek d = date.getDayOfWeek();
        return d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY;
    }

    /** Human-readable summary of a date's designation. */
    public String describeDayType(LocalDate date) {
        if (isHoliday(date)) return "HOLIDAY";
        if (isWeekend(date))  return "WEEKEND";
        if (isPureSquadDay(date)) {
            Squad s = getSquadDaySquad(date).orElseThrow();
            return "SQUAD DAY - " + s.getName();
        }
        int batch = getScheduledBatch(date);
        Optional<Squad> sd = getSquadDaySquad(date);
        String base = batch == 1 ? "BATCH A DAY" : batch == 2 ? "BATCH B DAY" : "REGULAR";
        if (sd.isPresent() && sd.get().getBatchId() == batch) {
            base += " + Squad Day (" + sd.get().getName() + ")";
        }
        return base;
    }

    /** Returns the batch ID (1 or 2) that a member belongs to. */
    public int getMemberBatch(String memberId) {
        return store.getData().getSquads().stream()
                    .filter(sq -> sq.getMemberIds().contains(memberId))
                    .mapToInt(Squad::getBatchId)
                    .findFirst()
                    .orElse(0);
    }

    /** Returns the squad of a member. */
    public Optional<Squad> getMemberSquad(String memberId) {
        return store.getData().getSquads().stream()
                    .filter(sq -> sq.getMemberIds().contains(memberId))
                    .findFirst();
    }

    /** True if the member's batch is the one scheduled on this date. */
    public boolean isMemberBatchDay(String memberId, LocalDate date) {
        int memberBatch    = getMemberBatch(memberId);
        int scheduledBatch = getScheduledBatch(date);
        return memberBatch != 0 && memberBatch == scheduledBatch;
    }

    /** True if today is the squad day for the member's squad (and not a conflicting batch day). */
    public boolean isMemberSquadDay(String memberId, LocalDate date) {
        Optional<Squad> memberSquad = getMemberSquad(memberId);
        if (memberSquad.isEmpty()) return false;
        Optional<Squad> daySquad = getSquadDaySquad(date);
        return daySquad.isPresent() && daySquad.get().getId() == memberSquad.get().getId();
    }
}
