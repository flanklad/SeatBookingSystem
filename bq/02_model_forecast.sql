-- ═══════════════════════════════════════════════════════════════════════════
--  BigQuery ML — Seat Occupancy Forecasting
--  Model  : ARIMA_PLUS  (time-series with holiday & trend decomposition)
--  Predicts daily seat occupancy % for the next 30 working days.
--  Requires: ≥ 10 days of data in daily_stats.
-- ═══════════════════════════════════════════════════════════════════════════

-- ── STEP 1: Train the model ───────────────────────────────────────────────

CREATE OR REPLACE MODEL `seatbookingsystem-493310.seat_analytics.occupancy_forecast`
OPTIONS (
  model_type              = 'ARIMA_PLUS',
  time_series_timestamp_col = 'stat_date',
  time_series_data_col    = 'occupancy_pct',
  data_frequency          = 'DAILY',
  holiday_region          = 'IN',          -- Indian public holidays auto-handled
  decompose_time_series   = TRUE,
  clean_spikes_and_dips   = TRUE
) AS
SELECT
  stat_date,
  occupancy_pct
FROM `seatbookingsystem-493310.seat_analytics.daily_stats`
WHERE scheduled_batch > 0    -- exclude weekends (no batch scheduled)
ORDER BY stat_date;


-- ── STEP 2: Forecast next 30 days ─────────────────────────────────────────

SELECT
  forecast_timestamp                          AS forecast_date,
  ROUND(forecast_value, 1)                    AS predicted_occupancy_pct,
  ROUND(prediction_interval_lower_bound, 1)   AS lower_bound,
  ROUND(prediction_interval_upper_bound, 1)   AS upper_bound,
  CASE
    WHEN forecast_value >= 80 THEN 'HIGH'
    WHEN forecast_value >= 50 THEN 'MEDIUM'
    ELSE 'LOW'
  END AS demand_level
FROM ML.FORECAST(
  MODEL `seatbookingsystem-493310.seat_analytics.occupancy_forecast`,
  STRUCT(30 AS horizon, 0.9 AS confidence_level)
)
ORDER BY forecast_date;


-- ── STEP 3: Evaluate model quality ────────────────────────────────────────

SELECT
  mean_absolute_error,
  mean_squared_error,
  mean_absolute_percentage_error,
  symmetric_mean_absolute_percentage_error
FROM ML.EVALUATE(MODEL `seatbookingsystem-493310.seat_analytics.occupancy_forecast`);


-- ── STEP 4: Decompose trend + seasonality (for Looker Studio charts) ──────

SELECT
  time_series_timestamp  AS stat_date,
  time_series_data       AS actual_occupancy,
  trend,
  seasonal_period_yearly,
  seasonal_period_weekly,
  residual
FROM ML.EXPLAIN_FORECAST(
  MODEL `seatbookingsystem-493310.seat_analytics.occupancy_forecast`,
  STRUCT(30 AS horizon, 0.9 AS confidence_level)
)
ORDER BY stat_date;


-- ── STEP 5: Anomaly detection — days with abnormally high/low occupancy ───

SELECT
  time_series_timestamp AS anomaly_date,
  time_series_data      AS occupancy_pct,
  is_anomaly,
  lower_bound,
  upper_bound,
  ABS(time_series_data - (lower_bound + upper_bound) / 2) AS deviation
FROM ML.DETECT_ANOMALIES(
  MODEL `seatbookingsystem-493310.seat_analytics.occupancy_forecast`,
  STRUCT(0.9 AS anomaly_prob_threshold)
)
WHERE is_anomaly = TRUE
ORDER BY anomaly_date DESC;
