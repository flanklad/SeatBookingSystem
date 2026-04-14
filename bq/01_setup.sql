-- ═══════════════════════════════════════════════════════════════════════════
--  Seat Booking System — BigQuery Setup
--  Project : seatbookingsystem-493310
--  Run once in the BigQuery console (or via `bq query --use_legacy_sql=false`)
-- ═══════════════════════════════════════════════════════════════════════════

-- 1. Create dataset
CREATE SCHEMA IF NOT EXISTS `seatbookingsystem-493310.seat_analytics`
OPTIONS (
  location       = 'US',
  description    = 'Seat booking analytics — fortnightly rotation system'
);

-- 2. Bookings fact table
CREATE TABLE IF NOT EXISTS `seatbookingsystem-493310.seat_analytics.bookings` (
  booking_id       STRING    NOT NULL,
  member_id        STRING,
  member_name      STRING,
  squad_id         INT64,
  squad_name       STRING,
  batch_id         INT64,
  seat_number      INT64,
  seat_type        STRING,   -- FIXED | FLOATER
  booking_date     DATE,
  status           STRING,   -- ACTIVE | CANCELLED | RELEASED
  booking_type     STRING,   -- AUTO | MANUAL
  day_of_week      STRING,
  fortnight_week   INT64,    -- 1 or 2
  scheduled_batch  INT64     -- 1 (Batch A) | 2 (Batch B) | 0 (weekend)
)
PARTITION BY booking_date
OPTIONS (description = 'One row per booking');

-- 3. Daily utilisation stats
CREATE TABLE IF NOT EXISTS `seatbookingsystem-493310.seat_analytics.daily_stats` (
  stat_date          DATE    NOT NULL,
  total_seats        INT64,
  booked_seats       INT64,
  occupied_seats     INT64,
  blocked_seats      INT64,
  released_seats     INT64,
  free_seats         INT64,
  occupancy_pct      FLOAT64,
  total_bookings     INT64,
  cancelled_bookings INT64,
  auto_bookings      INT64,
  manual_bookings    INT64,
  scheduled_batch    INT64,
  fortnight_week     INT64,
  day_of_week        STRING
)
PARTITION BY stat_date
OPTIONS (description = 'Daily seat utilisation snapshot');

-- 4. Members reference table
CREATE TABLE IF NOT EXISTS `seatbookingsystem-493310.seat_analytics.members` (
  member_id        STRING  NOT NULL,
  member_name      STRING,
  squad_id         INT64,
  squad_name       STRING,
  batch_id         INT64,
  home_seat_number INT64
)
OPTIONS (description = 'Member / squad reference');
