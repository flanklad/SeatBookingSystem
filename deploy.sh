#!/usr/bin/env bash
# ============================================================
#  Seat Booking System — Linux / macOS Deployment
# ============================================================
set -e

echo "============================================================"
echo " Seat Booking System  —  Deployment"
echo "============================================================"

# Detect OS for jpackage type
OS=$(uname -s)
case "$OS" in
    Darwin*)  PKG_TYPE="dmg" ;;
    Linux*)   PKG_TYPE="deb" ;;
    *)        PKG_TYPE="app-image" ;;
esac

# Check jpackage
if ! command -v jpackage &>/dev/null; then
    echo "ERROR: jpackage not found. Install JDK 17+."
    exit 1
fi

echo ""
echo "[1/3] Building fat JAR..."
./mvnw clean package -q
echo "      OK — target/SeatBookingSystem.jar"

rm -rf dist && mkdir -p dist/input
cp target/SeatBookingSystem.jar dist/input/

echo ""
echo "[2/3] Running jpackage (type: $PKG_TYPE)..."
jpackage \
  --type "$PKG_TYPE" \
  --input dist/input \
  --name "SeatBookingSystem" \
  --main-jar SeatBookingSystem.jar \
  --main-class com.seatbooking.Main \
  --app-version 1.0.0 \
  --description "Seat Booking System" \
  --dest dist \
  --java-options "-Dfile.encoding=UTF-8"

echo ""
echo "[3/3] Package created in dist/"
ls dist/
echo ""
echo "============================================================"
echo " Deployment complete."
echo "============================================================"
