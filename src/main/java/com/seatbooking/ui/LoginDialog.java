package com.seatbooking.ui;

import com.seatbooking.model.Member;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.Optional;

/**
 * Reusable login dialog shown at startup and on logout.
 * Returns the authenticated Member or empty if the user cancelled.
 */
public class LoginDialog {

    public static Optional<Member> show() {
        Dialog<Member> dlg = new Dialog<>();
        dlg.setTitle("Seat Booking System — Login");
        dlg.setHeaderText("Enter your Member ID to continue");

        TextField idField  = new TextField();
        idField.setPromptText("e.g. M000 (admin) or M001–M080");
        Label errorLbl = new Label();
        errorLbl.setStyle("-fx-text-fill: #f38ba8;");

        VBox content = new VBox(8, new Label("Member ID:"), idField, errorLbl);
        content.setPadding(new Insets(20));
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Keep dialog open if the member ID is not found
        Button okBtn = (Button) dlg.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            String id = idField.getText().trim();
            boolean found = AppContext.get().getStore().getData().getMembers().stream()
                .anyMatch(m -> m.getId().equalsIgnoreCase(id));
            if (!found) {
                errorLbl.setText("Member ID not found. Try M001–M080.");
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

        return dlg.showAndWait();
    }
}
