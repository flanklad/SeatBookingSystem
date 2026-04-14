package com.seatbooking.bq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.seatbooking.analytics.DayStats;
import com.seatbooking.analytics.UtilisationTracker;
import com.seatbooking.engine.ScheduleEngine;
import com.seatbooking.model.*;
import com.seatbooking.store.JsonDataStore;

import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Exports application data as NDJSON files ready for BigQuery load.
 *
 * Produces three files:
 *   bookings.ndjson    — one row per booking with member + schedule context
 *   daily_stats.ndjson — one row per day with utilisation metrics
 *   members.ndjson     — member / squad reference table
 *
 * Also writes an upload.bat (Windows) and upload.sh (Linux/macOS) to the
 * output directory so the user can push to BigQuery with a single command.
 */
public class BigQueryExporter {

    private static final String PROJECT  = "seatbookingsystem-493310";
    private static final String DATASET  = "seat_analytics";

    private final JsonDataStore      store;
    private final ScheduleEngine     schedule;
    private final UtilisationTracker analytics;
    private final ObjectMapper       mapper;

    public BigQueryExporter(JsonDataStore store, ScheduleEngine schedule, UtilisationTracker analytics) {
        this.store     = store;
        this.schedule  = schedule;
        this.analytics = analytics;
        this.mapper    = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Exports all tables to NDJSON files and writes upload scripts.
     *
     * @param outputDir directory to write files into (created if absent)
     * @return human-readable summary for display in the UI
     */
    public String export(String outputDir) throws IOException {
        File dir = new File(outputDir);
        dir.mkdirs();

        int bookings = exportBookings(new File(dir, "bookings.ndjson"));
        int stats    = exportDailyStats(new File(dir, "daily_stats.ndjson"));
        int members  = exportMembers(new File(dir, "members.ndjson"));

        writeUploadBat(dir);
        writeUploadSh(dir);

        return String.format(
            "Exported %d bookings, %d daily-stat rows, %d members → %s%n" +
            "Run %s\\upload.bat to push to BigQuery.",
            bookings, stats, members, dir.getAbsolutePath(), dir.getAbsolutePath());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NDJSON writers
    // ─────────────────────────────────────────────────────────────────────────

    private int exportBookings(File file) throws IOException {
        AppData data = store.getData();

        // Build lookup maps once
        Map<String, String>  memberName  = new HashMap<>();
        Map<String, Integer> squadId     = new HashMap<>();
        Map<String, String>  squadName   = new HashMap<>();
        Map<String, Integer> batchId     = new HashMap<>();
        for (Squad sq : data.getSquads()) {
            for (String mid : sq.getMemberIds()) {
                squadId.put(mid, sq.getId());
                squadName.put(mid, sq.getName());
                batchId.put(mid, sq.getBatchId());
            }
        }
        for (Member m : data.getMembers()) memberName.put(m.getId(), m.getName());

        Map<Integer, String> seatType = new HashMap<>();
        for (Seat s : data.getSeats()) seatType.put(s.getSeatNumber(), s.getType().name());

        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            for (Booking b : data.getBookings()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("booking_id",       b.getId());
                row.put("member_id",        b.getMemberId());
                row.put("member_name",      memberName.getOrDefault(b.getMemberId(), "Unknown"));
                row.put("squad_id",         squadId.getOrDefault(b.getMemberId(), 0));
                row.put("squad_name",       squadName.getOrDefault(b.getMemberId(), "Unknown"));
                row.put("batch_id",         batchId.getOrDefault(b.getMemberId(), 0));
                row.put("seat_number",      b.getSeatNumber());
                row.put("seat_type",        seatType.getOrDefault(b.getSeatNumber(), "FIXED"));
                row.put("booking_date",     b.getDate().toString());
                row.put("status",           b.getStatus().name());
                row.put("booking_type",     b.isAutoAssigned() ? "AUTO" : "MANUAL");
                row.put("day_of_week",      b.getDate().getDayOfWeek().name());
                row.put("fortnight_week",   schedule.getFortnightWeek(b.getDate()));
                row.put("scheduled_batch",  schedule.getScheduledBatch(b.getDate()));
                pw.println(mapper.writeValueAsString(row));
            }
        }
        return data.getBookings().size();
    }

    private int exportDailyStats(File file) throws IOException {
        List<LocalDate> dates = store.getData().getBookings().stream()
            .map(Booking::getDate)
            .distinct()
            .sorted()
            .collect(Collectors.toList());

        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            for (LocalDate date : dates) {
                DayStats s = analytics.statsForDate(date);
                List<Booking> day = store.getData().getBookings().stream()
                    .filter(b -> b.getDate().equals(date))
                    .collect(Collectors.toList());

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("stat_date",          date.toString());
                row.put("total_seats",         s.getTotalSeats());
                row.put("booked_seats",        s.getBookedSeats());
                row.put("occupied_seats",      s.getOccupiedSeats());
                row.put("blocked_seats",       s.getBlockedSeats());
                row.put("released_seats",      s.getReleasedSeats());
                row.put("free_seats",          s.getFreeSeats());
                row.put("occupancy_pct",       Math.round(s.getOccupancyPercent() * 10.0) / 10.0);
                row.put("total_bookings",      day.size());
                row.put("cancelled_bookings",  day.stream().filter(b -> b.getStatus() == BookingStatus.CANCELLED).count());
                row.put("auto_bookings",       day.stream().filter(Booking::isAutoAssigned).count());
                row.put("manual_bookings",     day.stream().filter(b -> !b.isAutoAssigned()).count());
                row.put("scheduled_batch",     schedule.getScheduledBatch(date));
                row.put("fortnight_week",      schedule.getFortnightWeek(date));
                row.put("day_of_week",         date.getDayOfWeek().name());
                pw.println(mapper.writeValueAsString(row));
            }
        }
        return dates.size();
    }

    private int exportMembers(File file) throws IOException {
        AppData data = store.getData();
        Map<String, Squad> memberSquad = new HashMap<>();
        for (Squad sq : data.getSquads())
            for (String mid : sq.getMemberIds())
                memberSquad.put(mid, sq);

        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            for (Member m : data.getMembers()) {
                Squad sq = memberSquad.get(m.getId());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("member_id",        m.getId());
                row.put("member_name",      m.getName());
                row.put("squad_id",         sq != null ? sq.getId()      : 0);
                row.put("squad_name",       sq != null ? sq.getName()    : "Unknown");
                row.put("batch_id",         sq != null ? sq.getBatchId() : 0);
                row.put("home_seat_number", m.getHomeSeatNumber());
                pw.println(mapper.writeValueAsString(row));
            }
        }
        return data.getMembers().size();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Upload scripts
    // ─────────────────────────────────────────────────────────────────────────

    private void writeUploadBat(File dir) throws IOException {
        File out = new File(dir, "upload.bat");
        try (PrintWriter pw = new PrintWriter(new FileWriter(out))) {
            pw.println("@echo off");
            pw.println("echo Uploading seat booking data to BigQuery...");
            pw.println("echo Project: " + PROJECT);
            pw.println();
            pw.println("bq load --source_format=NEWLINE_DELIMITED_JSON --replace ^");
            pw.println("  " + PROJECT + ":" + DATASET + ".bookings ^");
            pw.println("  bookings.ndjson");
            pw.println();
            pw.println("bq load --source_format=NEWLINE_DELIMITED_JSON --replace ^");
            pw.println("  " + PROJECT + ":" + DATASET + ".daily_stats ^");
            pw.println("  daily_stats.ndjson");
            pw.println();
            pw.println("bq load --source_format=NEWLINE_DELIMITED_JSON --replace ^");
            pw.println("  " + PROJECT + ":" + DATASET + ".members ^");
            pw.println("  members.ndjson");
            pw.println();
            pw.println("echo Done. Open BigQuery console to run ML model scripts.");
        }
    }

    private void writeUploadSh(File dir) throws IOException {
        File out = new File(dir, "upload.sh");
        try (PrintWriter pw = new PrintWriter(new FileWriter(out))) {
            pw.println("#!/usr/bin/env bash");
            pw.println("set -e");
            pw.println("echo 'Uploading to BigQuery project " + PROJECT + "'");
            pw.println();
            pw.println("bq load --source_format=NEWLINE_DELIMITED_JSON --replace \\");
            pw.println("  " + PROJECT + ":" + DATASET + ".bookings \\");
            pw.println("  bookings.ndjson");
            pw.println();
            pw.println("bq load --source_format=NEWLINE_DELIMITED_JSON --replace \\");
            pw.println("  " + PROJECT + ":" + DATASET + ".daily_stats \\");
            pw.println("  daily_stats.ndjson");
            pw.println();
            pw.println("bq load --source_format=NEWLINE_DELIMITED_JSON --replace \\");
            pw.println("  " + PROJECT + ":" + DATASET + ".members \\");
            pw.println("  members.ndjson");
            pw.println();
            pw.println("echo 'Done.'");
        }
    }
}
