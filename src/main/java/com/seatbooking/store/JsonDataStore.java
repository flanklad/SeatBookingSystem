package com.seatbooking.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.seatbooking.model.*;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes all application state to/from data/seatbooking.json.
 * Uses Jackson with JavaTimeModule for LocalDate / LocalDateTime support.
 */
public class JsonDataStore {

    private static final String DATA_DIR  = "data";
    private static final String DATA_FILE = DATA_DIR + "/seatbooking.json";

    private final ObjectMapper mapper;
    private AppData data;

    public JsonDataStore() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    // ---- public API ----

    public AppData getData() { return data; }

    public void loadOrInit() {
        File file = new File(DATA_FILE);
        if (file.exists()) {
            try {
                data = mapper.readValue(file, AppData.class);
                System.out.println("[Store] Loaded data from " + DATA_FILE);
                return;
            } catch (IOException e) {
                System.err.println("[Store] Failed to read " + DATA_FILE + ": " + e.getMessage());
            }
        }
        System.out.println("[Store] No data file found — loading sample data.");
        data = buildSampleData();
        save();
    }

    public void save() {
        try {
            File dir = new File(DATA_DIR);
            if (!dir.exists()) dir.mkdirs();
            mapper.writeValue(new File(DATA_FILE), data);
        } catch (IOException e) {
            System.err.println("[Store] Save failed: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sample data generator
    // ─────────────────────────────────────────────────────────────────────────

    private AppData buildSampleData() {
        AppData d = new AppData();

        // 50 seats
        List<Seat> seats = new ArrayList<>();
        for (int i = 1; i <= 50; i++) seats.add(new Seat(i));
        d.setSeats(seats);

        // 10 squads × 8 members = 80 members
        // Batch A = squads 1-5, Batch B = squads 6-10
        // Squad day assignment (no overlap with batch's primary days):
        //   Batch A squads  → THURSDAY  (Batch A primary days are Mon-Wed in week1)
        //   Batch B squads  → MONDAY    (Batch B primary days are Thu-Fri in week1)
        // Each squad gets a distinct squad day among the batch's off-days:
        //   Squads 1-5: Thu, Fri, Thu, Fri, Thu  (Thu/Fri = off for Batch A in week1)
        //   Squads 6-10: Mon, Tue, Wed, Mon, Tue  (Mon/Tue/Wed = off for Batch B in week1)

        DayOfWeek[] batchASquadDays = {
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,   DayOfWeek.THURSDAY
        };
        DayOfWeek[] batchBSquadDays = {
            DayOfWeek.MONDAY,  DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.MONDAY,  DayOfWeek.TUESDAY
        };

        List<Member>  members = new ArrayList<>();
        List<Squad>   squads  = new ArrayList<>();

        int memberCounter = 0;

        for (int squadId = 1; squadId <= 10; squadId++) {
            int batchId    = squadId <= 5 ? 1 : 2;
            DayOfWeek day  = batchId == 1
                ? batchASquadDays[squadId - 1]
                : batchBSquadDays[squadId - 6];

            String squadName = "Squad-" + squadId;
            List<String> memberIds = new ArrayList<>();

            for (int slot = 1; slot <= 8; slot++) {
                memberCounter++;
                String memberId   = "M" + String.format("%03d", memberCounter);
                String memberName = squadName + "-Member" + slot;

                // Home seat: seats 1-40, shared between Batch A & B on different days.
                // Each batch has 5 squads × 8 members = 40 members covering seats 1-40.
                // ((squadId-1) % 5) maps both squad 1 & 6 → block 0 (seats 1-8), etc.
                int homeSeat = ((squadId - 1) % 5) * 8 + slot;

                Member m = new Member(memberId, memberName, squadId,
                                      homeSeat, false, null, null);
                members.add(m);
                memberIds.add(memberId);
            }

            Squad squad = new Squad(squadId, squadName, batchId, day, memberIds);
            squads.add(squad);
        }

        d.setMembers(members);
        d.setSquads(squads);
        d.setBookings(new ArrayList<>());

        // 5 holidays: spread through the next few months from a fixed reference
        LocalDate ref = LocalDate.of(2024, 1, 15);
        List<LocalDate> holidays = List.of(
            LocalDate.of(2024, 1, 26),  // Republic Day (India)
            LocalDate.of(2024, 3, 25),  // Holi
            LocalDate.of(2024, 4, 14),  // Ambedkar Jayanti
            LocalDate.of(2024, 5,  1),  // Labour Day
            LocalDate.of(2024, 8, 15)   // Independence Day (India)
        );
        d.setHolidays(new ArrayList<>(holidays));

        return d;
    }
}
