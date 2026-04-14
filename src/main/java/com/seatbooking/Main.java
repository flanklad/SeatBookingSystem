package com.seatbooking;

import javafx.application.Application;

/**
 * Entry point.
 *
 * This class deliberately does NOT extend Application so that the fat JAR
 * (built via maven-shade-plugin) can be launched with  java -jar  without
 * triggering the "JavaFX runtime components are missing" guard that JavaFX
 * places on direct Application subclasses detected as MANIFEST main-class.
 *
 * Usage:
 *   mvn javafx:run                    (development — module-path managed by plugin)
 *   java -jar target/SeatBookingSystem.jar   (fat JAR — JavaFX natives bundled inside)
 *   deploy.bat                        (create Windows .exe installer via jpackage)
 */
public class Main {
    public static void main(String[] args) {
        Application.launch(SeatBookingApp.class, args);
    }
}
