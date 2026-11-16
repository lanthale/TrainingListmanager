package de.mindarchitects.tlm.ui;

import javafx.event.ActionEvent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.stage.Window;

import java.util.List;

/** Dialog: weitere Teilnehmer einladen (withEmails) oder Einladungen erneut senden. */
public final class InviteDialog extends Dialog<InviteDialog.Data> {

    public record Data(List<String> emails, String subject, String body) {}

    public InviteDialog(Window owner, String header, boolean withEmails, String subjectTemplate, String bodyTemplate) {
        initOwner(owner);
        setTitle(withEmails ? "Teilnehmer einladen" : "Einladungen erneut senden");
        setHeaderText(header);
        setResizable(true);

        MailComposer composer = new MailComposer(withEmails, subjectTemplate, bodyTemplate);
        composer.setPrefWidth(600);
        getDialogPane().setContent(composer);

        ButtonType send = new ButtonType("Senden", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(send, ButtonType.CANCEL);
        getDialogPane().lookupButton(send).addEventFilter(ActionEvent.ACTION, e -> {
            String error = composer.validate();
            if (error != null) {
                e.consume();
                Fx.error(getDialogPane().getScene().getWindow(), "Eingabe prüfen", error);
            }
        });

        setResultConverter(bt -> bt == send
                ? new Data(composer.recipients().valid(), composer.subject(), composer.body())
                : null);
    }
}
