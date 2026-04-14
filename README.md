# Seat Booking System

A fortnightly seat rotation and booking management system built with Java 17 and JavaFX 17. Manages 50 seats across 10 squads in two rotating batches, with a rule engine, 3 PM auto-block scheduler, utilisation analytics, and a dark-themed GUI.

---

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [Domain Model](#domain-model)
- [Rotation Logic](#rotation-logic)
- [Rule Engine](#rule-engine)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Running the Application](#running-the-application)
- [Building a Fat JAR](#building-a-fat-jar)
- [Deploying as a Windows Executable](#deploying-as-a-windows-executable)
- [GUI Overview](#gui-overview)
- [Data Persistence](#data-persistence)
- [Tech Stack](#tech-stack)

---

## Features

- **50 seats**: seats 1–40 are fixed home seats, seats 41–50 are floater seats
- **10 squads / 80 members**: 8 members per squad, split into 2 batches of 5 squads each
- **Fortnightly rotation**: Batch A and Batch B alternate between Mon–Wed and Thu–Fri each week
- **Seat state machine**: FREE → RESERVED → OCCUPIED → RELEASED, FREE → BLOCKED (with guarded transitions)
- **Pluggable rule engine**: holiday blocking, vacation blocking, after-3PM restriction, floater eligibility, non-designated-day enforcement
- **3 PM auto-block scheduler**: unreserved seats are automatically blocked at 3 PM daily using a background `ScheduledExecutorService`
- **Utilisation analytics**: daily seat stats, per-squad booking counts bar chart, and date-range summaries
- **JavaFX dark-themed GUI**: Catppuccin Mocha palette, 6 views — Seat Map, Bookings, Analytics, Members, Admin, Main Window
- **JSON persistence**: all state saved to `data/seatbooking.json` via Jackson; sample data auto-generated on first run
- **Fat JAR & Windows installer**: single runnable JAR via Maven Shade; `deploy.bat` / `deploy.sh` build a self-contained native app via `jpackage`

---

## Architecture

```
com.seatbooking
├── Main.java                  Entry point (does NOT extend Application — fat-JAR trick)
├── SeatBookingApp.java        JavaFX Application subclass; wires UI lifecycle
│
├── model/                     Pure domain objects
│   ├── Seat.java              State machine (SeatState enum + canTransitionTo guard)
│   ├── Member.java
│   ├── Squad.java
│   ├── Booking.java
│   ├── AppData.java           Root JSON object
│   └── enums/                 SeatState, SeatType, BookingStatus
│
├── engine/
│   ├── ScheduleEngine.java    Fortnightly rotation — which batch/squad is active on a date
│   ├── BookingEngine.java     CRUD for bookings; drives seat state transitions
│   └── RuleEngine.java        Chains BookingRule implementations; returns first violation
│
├── rules/                     Pluggable rule implementations
│   ├── BookingRule.java       @FunctionalInterface returning Optional<String>
│   ├── HolidayRule.java
│   ├── AfterThreePMRule.java
│   ├── VacationRule.java
│   ├── FloaterEligibilityRule.java
│   └── NonDesignatedDayRule.java
│
├── store/
│   └── JsonDataStore.java     Jackson read/write to data/seatbooking.json
│
├── scheduler/
│   └── SeatBlockScheduler.java  3 PM daily auto-block via ScheduledExecutorService
│
├── analytics/
│   ├── UtilisationTracker.java
│   └── DayStats.java
│
├── cli/
│   └── Dashboard.java         ASCII seat map (legacy CLI, kept for headless use)
│
└── ui/
    ├── AppContext.java                     Singleton wiring all services; fireRefresh() → FX thread
    └── controller/
        ├── MainWindowController.java       Sidebar nav + lazy view loading
        ├── SeatMapController.java          50-tile seat map grid
        ├── BookingsController.java         Booking CRUD table
        ├── AnalyticsController.java        Stats labels + bar chart + date-range picker
        ├── MembersController.java          Member list with squad filter
        └── AdminController.java            Holiday/vacation management, manual date control
```

FXML views live at `src/main/resources/com/seatbooking/ui/`.

---

## Domain Model

### Seats

| Range    | Type    | Description                                      |
|----------|---------|--------------------------------------------------|
| 1 – 40   | FIXED   | Home seats assigned to squads by formula         |
| 41 – 50  | FLOATER | Can be booked by any eligible member             |

**Home seat formula**: `((squadId - 1) % 5) * 8 + slot`  
Both Batch A (squads 1–5) and Batch B (squads 6–10) map to seats 1–40 on their respective batch days, so the physical seats are shared on different days.

### Seat States

```
FREE ──→ RESERVED ──→ OCCUPIED ──→ RELEASED
 └──→ BLOCKED ──────────────────→ FREE
```

All transitions are guarded by `SeatState.canTransitionTo()`.

### Members & Squads

- 10 squads, 8 members each = 80 members total
- Each squad belongs to either Batch 1 or Batch 2
- Each member has an optional vacation flag and is linked to a home seat

---

## Rotation Logic

Reference Monday: **2024-01-01**

Fortnightly week is determined by counting weeks since the reference date:
- Even count → **Fortnight Week 1**
- Odd count  → **Fortnight Week 2**

| Fortnight Week | Batch A (squads 1–5) | Batch B (squads 6–10) |
|----------------|----------------------|-----------------------|
| Week 1         | Mon / Tue / Wed      | Thu / Fri             |
| Week 2         | Thu / Fri            | Mon / Tue / Wed       |

Weekends are unscheduled. Each squad also has a dedicated **Squad Day** (Batch A → Thu/Fri, Batch B → Mon/Tue/Wed) for squad-specific activities.

---

## Rule Engine

Booking validation runs through a chain of `BookingRule` instances (each is a `@FunctionalInterface` returning `Optional<String>`). The first non-empty result blocks the booking with the violation message.

| Rule                     | Description                                               |
|--------------------------|-----------------------------------------------------------|
| `HolidayRule`            | Blocks bookings on public holidays                        |
| `AfterThreePMRule`       | Blocks new bookings after 3 PM on the same day            |
| `VacationRule`           | Blocks bookings for members marked as on vacation         |
| `FloaterEligibilityRule` | Restricts floater seats to eligible members only          |
| `NonDesignatedDayRule`   | Prevents booking on days outside the member's batch days  |

New rules can be added by implementing `BookingRule` and registering with `RuleEngine`.

---

## Project Structure

```
SeatBookingSystem/
├── src/
│   ├── main/
│   │   ├── java/com/seatbooking/      Source code
│   │   └── resources/com/seatbooking/ui/
│   │       ├── *.fxml                 View definitions
│   │       └── dark-theme.css         Catppuccin Mocha stylesheet
├── data/                              Auto-created; holds seatbooking.json (gitignored)
├── deploy.bat                         Windows packaging script (jpackage)
├── deploy.sh                          Linux/macOS packaging script
├── pom.xml
└── mvnw / mvnw.cmd                    Maven wrapper
```

---

## Prerequisites

| Tool          | Version  | Notes                                      |
|---------------|----------|--------------------------------------------|
| JDK           | 17+      | JDK 21 recommended (includes jpackage)     |
| Maven         | 3.8+     | Or use the included `mvnw` wrapper         |
| IntelliJ IDEA | Any      | Run configuration included in `.idea/`     |

> JavaFX is bundled as Maven dependencies — no separate JavaFX SDK installation required.

---

## Running the Application

### Option 1 — IntelliJ IDEA (recommended)

A pre-configured run configuration is included at `.idea/runConfigurations/Run_Seat_Booking_GUI.xml`.

1. Open the project in IntelliJ IDEA
2. Select **"Run Seat Booking GUI"** from the run configurations dropdown
3. Click the green **▶** button

The configuration automatically sets the required JavaFX module-path VM options.

### Option 2 — Maven

```bash
mvn javafx:run
```

### Option 3 — Fat JAR

```bash
mvn clean package
java -jar target/SeatBookingSystem.jar
```

---

## Building a Fat JAR

```bash
mvn clean package
```

Output: `target/SeatBookingSystem.jar`

The fat JAR bundles all dependencies including JavaFX Windows natives (`.dll` files) via the win-classifier JARs. The Maven Shade plugin:
- Merges `META-INF/services/` entries with `ServicesResourceTransformer`
- Excludes `module-info.class` to prevent class-loading conflicts
- Sets `com.seatbooking.Main` as the manifest main class

---

## Deploying as a Windows Executable

Requires **JDK 17+** (for `jpackage`) on PATH.

```bat
deploy.bat
```

This will:
1. Build the fat JAR via Maven
2. Stage it into `dist/input/`
3. Run `jpackage --type exe` to produce `dist/SeatBookingSystem-1.0.0.exe`

> If WiX Toolset is not installed, the script automatically falls back to `--type app-image`, producing `dist/SeatBookingSystem/SeatBookingSystem.exe` — a self-contained folder with a bundled JRE. No Java installation required on the target machine.

For Linux/macOS:
```bash
./deploy.sh
```
Produces a `.deb` on Linux or `.dmg` on macOS.

---

## GUI Overview

| View       | Description                                                                 |
|------------|-----------------------------------------------------------------------------|
| Seat Map   | Visual 50-tile grid with colour-coded seat states; click to book/release    |
| Bookings   | Tabular list of all bookings; filter by date, member, or status             |
| Analytics  | Today's seat stats, per-squad bar chart, date-range occupancy summary       |
| Members    | Member directory with squad filter and vacation status toggle               |
| Admin      | Manage public holidays, vacation dates, and simulate date changes           |

### Seat Tile Colours (Catppuccin Mocha)

| State    | Colour     |
|----------|------------|
| Free     | Dark green |
| Reserved | Dark amber |
| Occupied | Dark blue  |
| Blocked  | Dark red   |
| Released | Dark grey  |

---

## Data Persistence

All application state is stored in `data/seatbooking.json` (created automatically on first run).

On first launch, sample data is generated:
- 10 squads, 80 members
- 5 Indian public holidays pre-loaded
- All 50 seats initialised to FREE

The file is written after every booking operation. To reset to a clean state, delete `data/seatbooking.json` and restart.

---

## Tech Stack

| Component     | Technology                        |
|---------------|-----------------------------------|
| Language      | Java 17                           |
| UI Framework  | JavaFX 17.0.6                     |
| UI Layout     | FXML + CSS (Catppuccin Mocha)     |
| JSON storage  | Jackson 2.16.1 + JavaTimeModule   |
| Build tool    | Maven 3 + Maven Shade Plugin      |
| Packaging     | jpackage (JDK 21)                 |
| Scheduler     | ScheduledExecutorService (daemon) |
