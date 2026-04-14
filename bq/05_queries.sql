-- ═══════════════════════════════════════════════════════════════════════════
--  Seat Booking System — BigQuery Analytics Queries
--  Use these in the BigQuery console or connect to Looker Studio.
-- ═══════════════════════════════════════════════════════════════════════════


-- ────────────────────────────────────────────────────────────────────────────
-- A. DAILY UTILISATION OVERVIEW
-- ────────────────────────────────────────────────────────────────────────────

-- Occupancy trend by day of week
SELECT
  day_of_week,
  ROUND(AVG(occupancy_pct), 1)        AS avg_occupancy,
  ROUND(MAX(occupancy_pct), 1)        AS max_occupancy,
  ROUND(MIN(occupancy_pct), 1)        AS min_occupancy,
  COUNT(*)                            AS days_observed
FROM `seatbookingsystem-493310.seat_analytics.daily_stats`
GROUP BY day_of_week
ORDER BY CASE day_of_week
  WHEN 'MONDAY'    THEN 1
  WHEN 'TUESDAY'   THEN 2
  WHEN 'WEDNESDAY' THEN 3
  WHEN 'THURSDAY'  THEN 4
  WHEN 'FRIDAY'    THEN 5
END;


-- Occupancy by fortnight week (Week 1 vs Week 2)
SELECT
  fortnight_week,
  ROUND(AVG(occupancy_pct), 1)  AS avg_occupancy,
  SUM(total_bookings)           AS total_bookings,
  SUM(cancelled_bookings)       AS total_cancellations
FROM `seatbookingsystem-493310.seat_analytics.daily_stats`
GROUP BY fortnight_week
ORDER BY fortnight_week;


-- Seats wasted (blocked without ever being booked) by date
SELECT
  stat_date,
  blocked_seats                         AS wasted_seats,
  total_seats - blocked_seats - booked_seats - occupied_seats AS utilised_seats,
  ROUND(occupancy_pct, 1)               AS occupancy_pct
FROM `seatbookingsystem-493310.seat_analytics.daily_stats`
ORDER BY stat_date DESC
LIMIT 30;


-- ────────────────────────────────────────────────────────────────────────────
-- B. SQUAD & BATCH PERFORMANCE
-- ────────────────────────────────────────────────────────────────────────────

-- Bookings per squad — all time
SELECT
  squad_id,
  squad_name,
  batch_id,
  COUNT(*)                                            AS total_bookings,
  COUNTIF(status = 'ACTIVE')                          AS active,
  COUNTIF(status = 'CANCELLED')                       AS cancelled,
  ROUND(COUNTIF(status = 'CANCELLED') / COUNT(*) * 100, 1) AS cancellation_rate_pct
FROM `seatbookingsystem-493310.seat_analytics.bookings`
GROUP BY squad_id, squad_name, batch_id
ORDER BY total_bookings DESC;


-- Batch A vs Batch B comparison
SELECT
  batch_id,
  CASE batch_id WHEN 1 THEN 'Batch A' ELSE 'Batch B' END AS batch_name,
  COUNT(*)                                             AS total_bookings,
  COUNTIF(booking_type = 'MANUAL')                    AS manual_bookings,
  ROUND(COUNTIF(status = 'CANCELLED') / COUNT(*) * 100, 1) AS cancellation_pct,
  ROUND(AVG(seat_number), 1)                          AS avg_seat_number
FROM `seatbookingsystem-493310.seat_analytics.bookings`
GROUP BY batch_id
ORDER BY batch_id;


-- Week-over-week booking trend per squad
SELECT
  DATE_TRUNC(booking_date, WEEK)  AS week_start,
  squad_name,
  COUNT(*)                        AS bookings
FROM `seatbookingsystem-493310.seat_analytics.bookings`
WHERE status != 'CANCELLED'
GROUP BY week_start, squad_name
ORDER BY week_start, squad_name;


-- ────────────────────────────────────────────────────────────────────────────
-- C. MEMBER-LEVEL ANALYTICS
-- ────────────────────────────────────────────────────────────────────────────

-- Top 10 most reliable members (highest retention, lowest cancellation)
SELECT
  member_id,
  member_name,
  squad_name,
  COUNT(*)                                               AS total_bookings,
  COUNTIF(status = 'ACTIVE')                             AS active_bookings,
  ROUND(COUNTIF(status = 'CANCELLED') / COUNT(*) * 100, 1) AS cancellation_pct,
  COUNTIF(seat_type = 'FLOATER')                         AS floater_bookings
FROM `seatbookingsystem-493310.seat_analytics.bookings`
GROUP BY member_id, member_name, squad_name
HAVING COUNT(*) >= 3
ORDER BY cancellation_pct ASC, total_bookings DESC
LIMIT 10;


-- Members who have never checked-in on time (always RESERVED, never OCCUPIED)
SELECT
  member_id,
  member_name,
  squad_name,
  COUNT(*) AS total_bookings
FROM `seatbookingsystem-493310.seat_analytics.bookings`
GROUP BY member_id, member_name, squad_name
HAVING COUNTIF(status = 'ACTIVE') = COUNT(*)   -- all bookings still ACTIVE
   AND COUNT(*) >= 3
ORDER BY total_bookings DESC;


-- Floater seat demand — which members rely on floaters most
SELECT
  member_id,
  member_name,
  squad_name,
  COUNTIF(seat_type = 'FLOATER')                                  AS floater_bookings,
  COUNT(*)                                                         AS total_bookings,
  ROUND(COUNTIF(seat_type = 'FLOATER') / COUNT(*) * 100, 1)       AS floater_pct
FROM `seatbookingsystem-493310.seat_analytics.bookings`
GROUP BY member_id, member_name, squad_name
HAVING floater_bookings > 0
ORDER BY floater_pct DESC;


-- ────────────────────────────────────────────────────────────────────────────
-- D. SEAT UTILISATION
-- ────────────────────────────────────────────────────────────────────────────

-- Hot seats: most frequently booked seats
SELECT
  seat_number,
  seat_type,
  COUNT(*)                          AS total_bookings,
  COUNTIF(status = 'ACTIVE')        AS active_bookings,
  COUNTIF(status = 'CANCELLED')     AS cancellations,
  COUNT(DISTINCT member_id)         AS unique_members
FROM `seatbookingsystem-493310.seat_analytics.bookings`
GROUP BY seat_number, seat_type
ORDER BY total_bookings DESC
LIMIT 15;


-- Cold seats: least booked (potential re-assignment candidates)
SELECT
  seat_number,
  seat_type,
  COUNT(*) AS total_bookings
FROM `seatbookingsystem-493310.seat_analytics.bookings`
GROUP BY seat_number, seat_type
ORDER BY total_bookings ASC
LIMIT 10;


-- Floater seat demand by day of week (are there enough floaters?)
SELECT
  day_of_week,
  COUNT(*)                              AS floater_bookings,
  COUNT(DISTINCT booking_date)          AS days_observed,
  ROUND(COUNT(*) / COUNT(DISTINCT booking_date), 1) AS avg_daily_floater_demand
FROM `seatbookingsystem-493310.seat_analytics.bookings`
WHERE seat_type = 'FLOATER'
GROUP BY day_of_week
ORDER BY avg_daily_floater_demand DESC;


-- ────────────────────────────────────────────────────────────────────────────
-- E. OPERATIONS DASHBOARD (single-query summary — use in Looker Studio)
-- ────────────────────────────────────────────────────────────────────────────

SELECT
  -- Date window
  MIN(stat_date)                          AS first_date,
  MAX(stat_date)                          AS last_date,
  COUNT(DISTINCT stat_date)               AS total_days,

  -- Booking summary
  SUM(total_bookings)                     AS all_time_bookings,
  SUM(cancelled_bookings)                 AS all_time_cancellations,
  ROUND(SUM(cancelled_bookings) / NULLIF(SUM(total_bookings), 0) * 100, 1)
                                          AS overall_cancellation_pct,

  -- Seat efficiency
  ROUND(AVG(occupancy_pct), 1)            AS avg_occupancy_pct,
  ROUND(AVG(SAFE_DIVIDE(blocked_seats, total_seats)) * 100, 1)
                                          AS avg_waste_pct,

  -- Auto vs Manual
  ROUND(SUM(auto_bookings) / NULLIF(SUM(total_bookings), 0) * 100, 1)
                                          AS auto_booking_pct

FROM `seatbookingsystem-493310.seat_analytics.daily_stats`;
