#!/usr/bin/env bash
set -e
echo 'Uploading to BigQuery project seatbookingsystem-493310'

bq load --source_format=NEWLINE_DELIMITED_JSON --replace \
  seatbookingsystem-493310:seat_analytics.bookings \
  bookings.ndjson

bq load --source_format=NEWLINE_DELIMITED_JSON --replace \
  seatbookingsystem-493310:seat_analytics.daily_stats \
  daily_stats.ndjson

bq load --source_format=NEWLINE_DELIMITED_JSON --replace \
  seatbookingsystem-493310:seat_analytics.members \
  members.ndjson

echo 'Done.'
