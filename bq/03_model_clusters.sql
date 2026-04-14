-- ═══════════════════════════════════════════════════════════════════════════
--  BigQuery ML — Member Attendance Behaviour Clustering
--  Model  : K-MEANS  (unsupervised grouping of members by booking patterns)
--  Groups members into 4 clusters automatically labelled after training.
--  Requires: members with ≥ 2 bookings.
-- ═══════════════════════════════════════════════════════════════════════════

-- ── STEP 1: Feature view — per-member booking stats ───────────────────────

CREATE OR REPLACE VIEW `seatbookingsystem-493310.seat_analytics.member_features` AS
SELECT
  b.member_id,
  ANY_VALUE(b.member_name)                                          AS member_name,
  ANY_VALUE(b.squad_name)                                           AS squad_name,
  ANY_VALUE(b.batch_id)                                             AS batch_id,

  COUNT(*)                                                          AS total_bookings,

  -- Reliability: what fraction of bookings stayed ACTIVE (not cancelled)
  COUNTIF(b.status = 'ACTIVE')  / COUNT(*)                         AS retention_rate,
  COUNTIF(b.status = 'CANCELLED') / COUNT(*)                       AS cancellation_rate,

  -- Independence: fraction booked manually (vs system auto-assignment)
  COUNTIF(b.booking_type = 'MANUAL') / COUNT(*)                    AS manual_booking_rate,

  -- Flexibility: fraction on floater seats
  COUNTIF(b.seat_type = 'FLOATER') / COUNT(*)                      AS floater_usage_rate,

  -- Schedule adherence: fraction booked on their batch's designated days
  COUNTIF(b.scheduled_batch = b.batch_id) / COUNT(*)               AS batch_day_adherence,

  -- Frequency: bookings per fortnight week (spread)
  COUNT(DISTINCT b.booking_date) / NULLIF(
    DATE_DIFF(MAX(b.booking_date), MIN(b.booking_date), WEEK) + 1, 0
  )                                                                 AS bookings_per_week

FROM `seatbookingsystem-493310.seat_analytics.bookings` b
GROUP BY b.member_id
HAVING COUNT(*) >= 2;


-- ── STEP 2: Train K-MEANS clustering model ───────────────────────────────

CREATE OR REPLACE MODEL `seatbookingsystem-493310.seat_analytics.member_clusters`
OPTIONS (
  model_type           = 'KMEANS',
  num_clusters         = 4,
  standardize_features = TRUE,
  kmeans_init_method   = 'KMEANS++'
) AS
SELECT
  total_bookings,
  retention_rate,
  cancellation_rate,
  manual_booking_rate,
  floater_usage_rate,
  batch_day_adherence,
  bookings_per_week
FROM `seatbookingsystem-493310.seat_analytics.member_features`;


-- ── STEP 3: Assign every member to a cluster ──────────────────────────────

SELECT
  f.member_id,
  f.member_name,
  f.squad_name,
  f.batch_id,
  p.CENTROID_ID                                     AS cluster_id,
  f.total_bookings,
  ROUND(f.cancellation_rate * 100, 1)               AS cancellation_pct,
  ROUND(f.retention_rate * 100, 1)                  AS retention_pct,
  ROUND(f.manual_booking_rate * 100, 1)             AS manual_pct,
  ROUND(f.batch_day_adherence * 100, 1)             AS adherence_pct,
  ROUND(f.bookings_per_week, 2)                     AS weekly_frequency,

  -- Descriptive cluster labels (adjust after inspecting centroid values)
  CASE p.CENTROID_ID
    WHEN 1 THEN 'Regular Attenders'
    WHEN 2 THEN 'Occasional Bookers'
    WHEN 3 THEN 'High Cancellation Risk'
    WHEN 4 THEN 'Flexible / Floater Users'
  END AS cluster_label

FROM ML.PREDICT(
  MODEL `seatbookingsystem-493310.seat_analytics.member_clusters`,
  TABLE `seatbookingsystem-493310.seat_analytics.member_features`
) p
JOIN `seatbookingsystem-493310.seat_analytics.member_features` f
  ON p.member_id = f.member_id
ORDER BY cluster_id, cancellation_rate DESC;


-- ── STEP 4: Cluster summary — average behaviour per cluster ───────────────

SELECT
  p.CENTROID_ID              AS cluster_id,
  COUNT(*)                   AS member_count,
  ROUND(AVG(f.total_bookings), 1)          AS avg_bookings,
  ROUND(AVG(f.cancellation_rate) * 100, 1) AS avg_cancellation_pct,
  ROUND(AVG(f.manual_booking_rate) * 100, 1) AS avg_manual_pct,
  ROUND(AVG(f.batch_day_adherence) * 100, 1) AS avg_adherence_pct,
  ROUND(AVG(f.floater_usage_rate) * 100, 1)  AS avg_floater_pct
FROM ML.PREDICT(
  MODEL `seatbookingsystem-493310.seat_analytics.member_clusters`,
  TABLE `seatbookingsystem-493310.seat_analytics.member_features`
) p
JOIN `seatbookingsystem-493310.seat_analytics.member_features` f
  ON p.member_id = f.member_id
GROUP BY cluster_id
ORDER BY cluster_id;


-- ── STEP 5: Inspect centroids ─────────────────────────────────────────────

SELECT
  centroid_id,
  feature,
  ROUND(numerical_value, 4) AS centroid_value
FROM ML.CENTROIDS(MODEL `seatbookingsystem-493310.seat_analytics.member_clusters`)
ORDER BY centroid_id, feature;
