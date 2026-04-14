@echo off
setlocal

echo ============================================================
echo  Seat Booking System  —  Windows Deployment
echo ============================================================

REM ── Locate jpackage (requires JDK 14+) ─────────────────────────────────
where jpackage >nul 2>&1
if errorlevel 1 (
    echo ERROR: jpackage not found on PATH.
    echo Make sure JDK 17+ bin\ is on your PATH.
    echo e.g.  set PATH=C:\Program Files\Java\jdk-17\bin;%%PATH%%
    exit /b 1
)

REM ── Build fat JAR ───────────────────────────────────────────────────────
echo.
echo [1/3] Building fat JAR...
call mvnw.cmd clean package -q
if errorlevel 1 (
    echo ERROR: Maven build failed.
    exit /b 1
)
echo       OK — target\SeatBookingSystem.jar

REM ── Create dist folder ──────────────────────────────────────────────────
if exist dist rmdir /s /q dist
mkdir dist

REM ── Stage clean input (fat JAR only) ────────────────────────────────────
if exist dist rmdir /s /q dist
mkdir dist\input
copy target\SeatBookingSystem.jar dist\input\ >nul

REM ── Run jpackage ────────────────────────────────────────────────────────
echo.
echo [2/3] Running jpackage (creates a self-contained Windows installer)...
echo       This bundles the JRE — no Java needed on the target machine.

jpackage ^
  --type exe ^
  --input dist\input ^
  --name "SeatBookingSystem" ^
  --main-jar SeatBookingSystem.jar ^
  --main-class com.seatbooking.Main ^
  --app-version 1.0.0 ^
  --description "Seat Booking System — fortnightly rotation scheduler" ^
  --dest dist ^
  --win-shortcut ^
  --win-menu ^
  --win-menu-group "SeatBookingSystem" ^
  --java-options "-Dfile.encoding=UTF-8"

if errorlevel 1 (
    echo.
    echo NOTE: EXE installer failed. Trying app-image instead...
    echo ^(WiX Toolset is required for .exe; install from https://wixtoolset.org/^)
    echo.
    jpackage ^
      --type app-image ^
      --input dist\input ^
      --name "SeatBookingSystem" ^
      --main-jar SeatBookingSystem.jar ^
      --main-class com.seatbooking.Main ^
      --dest dist ^
      --java-options "-Dfile.encoding=UTF-8"
    if errorlevel 1 (
        echo ERROR: jpackage app-image also failed. See output above.
        exit /b 1
    )
    echo.
    echo [3/3] App image created: dist\SeatBookingSystem\
    echo       Run: dist\SeatBookingSystem\SeatBookingSystem.exe
) else (
    echo.
    echo [3/3] Installer created: dist\SeatBookingSystem-1.0.0.exe
    echo       Double-click the installer to deploy on any Windows machine.
)

echo.
echo ============================================================
echo  Deployment complete.
echo ============================================================
endlocal
