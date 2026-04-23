package com.seatbooking;

import com.seatbooking.model.Member;
import com.seatbooking.ui.AppContext;
import com.seatbooking.ui.LoginDialog;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class SeatBookingApp extends Application {

    /**
     * Runs on the launcher thread BEFORE start() — safe place to initialise
     * non-JavaFX singletons (file I/O, scheduler, etc.).
     */
    @Override
    public void init() {
        AppContext.init();
    }

    @Override
    public void start(Stage stage) throws Exception {
        Member loggedIn = LoginDialog.show().orElse(null);
        if (loggedIn == null) {
            Platform.exit();
            return;
        }
        AppContext.get().setCurrentUser(loggedIn);

        Parent root = FXMLLoader.load(
            getClass().getResource("/com/seatbooking/ui/MainWindow.fxml"));

        Scene scene = new Scene(root, 1280, 800);
        scene.getStylesheets().add(
            getClass().getResource("/com/seatbooking/ui/dark-theme.css").toExternalForm());

        stage.setTitle("Seat Booking System");
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.setScene(scene);
        stage.show();
    }

    /** Called when the window is closed — stop background threads cleanly. */
    @Override
    public void stop() {
        AppContext.get().shutdown();
    }
}
