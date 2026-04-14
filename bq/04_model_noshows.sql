-- ═══════════════════════════════════════════════════════════════════════════
--  BigQuery ML — No-Show / Cancellation Risk Prediction
--  Model  : LOGISTIC_REG  (binary classifier)
--  Predicts probability that a booking will be cancelled before check-in.
--  Requires: ≥ 50 bookings with a mix of ACTIVE and CANCELLED statuses.
-- ═══════════════════════════════════════════════════════════════════════════

-- ── STEP 1: Train logistic regression model ───────────────────────────────

CREATE OR REPLACE MODEL `seatbookingsystem-493310.seat_analytics.noshow_model`
OPTIONS (
  model_type         = 'LOGISTIC_REG',
  input_label_cols   = ['was_cancelled'],
  auto_class_weights = TRUE,      -- handles imbalanced ACTIVE vs CANCELLED
  max_iterations     = 30
) AS
SELECT
  booking_type,                   -- AUTO | MANUAL
  seat_type,                      -- FIXED | FLOATER
  day_of_week,                    -- MONDAY … FRIDAY
  CAST(fortnight_week AS STRING)  AS fortnight_week,
  CAST(batch_id AS STRING)        AS batch_id,
  CAST(squad_id AS STRING)        AS squad_id,
  IF(status = 'CANCELLED', 1, 0)  AS was_cancelled
FROM `seatbookingsystem-493310.seat_analytics.bookings`;


-- ── STEP 2: Evaluate accuracy on held-out data ────────────────────────────

SELECT
  precision,
  recall,
  f1_score,
  accuracy,
  roc_auc,
  log_loss
FROM ML.EVALUATE(MODEL `seatbookingsystem-493310.seat_analytics.noshow_model`);


-- ── STEP 3: Score all ACTIVE bookings — rank by cancellation risk ─────────

SELECT
  b.booking_id,
  b.member_name,
  b.squad_name,
  b.seat_number,
  b.booking_date,
  b.booking_type,
  b.day_of_week,
  ROUND(p.prob_cancelled * 100, 1)  AS cancellation_risk_pct,
  CASE
    WHEN p.prob_cancelled >= 0.6 THEN 'HIGH'
    WHEN p.prob_cancelled >= 0.3 THEN 'MEDIUM'
    ELSE 'LOW'
  END AS risk_level
FROM ML.PREDICT(
  MODEL `seatbookingsystem-493310.seat_analytics.noshow_model`,
  (
    SELECT
      booking_id,
      member_name,
      squad_name,
      seat_number,
      booking_date,
      booking_type,
      seat_type,
      day_of_week,
      CAST(fortnight_week AS STRING)  AS fortnight_week,
      CAST(batch_id AS STRING)        AS batch_id,
      CAST(squad_id AS STRING)        AS squad_id
    FROM `seatbookingsystem-493310.seat_analytics.bookings`
    WHERE status = 'ACTIVE'
  )
) p
-- Extract cancellation probability from the predicted label probabilities
CROSS JOIN UNNEST(p.predicted_was_cancelled_probs) AS prob_struct
WHERE prob_struct.label = 1
  RENAME prob_struct.prob AS prob_cancelled
ORDER BY cancellation_risk_pct DESC;


-- ── STEP 4: Feature importance — which factors drive cancellations ─────────

SELECT
  input,
  ROUND(attribution, 4) AS importance
FROM ML.GLOBAL_EXPLAIN(MODEL `seatbookingsystem-493310.seat_analytics.noshow_model`)
ORDER BY importance DESC;
