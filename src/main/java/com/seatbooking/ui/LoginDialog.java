package com.seatbooking.ui;

import com.seatbooking.auth.AuthService;
import com.seatbooking.model.Member;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.Optional;

/**
 * Reusable login dialog shown at startup and on logout.
 * Validates member ID + password, issues a JWT on success, and stores it in
 * AppContext before returning the authenticated Member.
 */
public class LoginDialog {

    public static Optional<Member> show() {
        Dialog<Member> dlg = new Dialog<>();
        dlg.setTitle("Seat Booking System — Login");
        dlg.setHeaderText("Sign in  (default password: \"password\")");

        TextField     idField  = new TextField();
        PasswordField pwField  = new PasswordField();
        idField.setPromptText("e.g. M000 (admin) or M001–M080");
        pwField.setPromptText("Password");

        Label errorLbl = new Label();
        errorLbl.setStyle("-fx-text-fill: #f38ba8;");

        VBox content = new VBox(8,
            new Label("Member ID:"), idField,
            new Label("Password:"),  pwField,
            errorLbl);
        content.setPadding(new Insets(20));
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okBtn = (Button) dlg.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            String id = idField.getText().trim();
            String pw = pwField.getText();

            Optional<Member> member = AppContext.get().getStore().getData().getMembers()
                .stream().filter(m -> m.getId().equalsIgnoreCase(id)).findFirst();

            if (member.isEmpty()) {
                errorLbl.setText("Member ID not found.");
                e.consume();
                return;
            }
            if (!AuthService.checkPassword(pw, member.get().getPasswordHash())) {
                errorLbl.setText("Incorrect password.");
                pwField.clear();
                e.consume();
            }
        });

        dlg.setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            String id = idField.getText().trim();
            return AppContext.get().getStore().getData().getMembers().stream()
                .filter(m -> m.getId().equalsIgnoreCase(id))
                .findFirst().orElse(null);
        });

        Optional<Member> result = dlg.showAndWait();

        // Issue a JWT and store it in AppContext on successful login
        result.ifPresent(m -> {
            String token = AuthService.generateToken(m);
            AppContext.get().setAuthToken(token);
        });

        return result;
    }
}
