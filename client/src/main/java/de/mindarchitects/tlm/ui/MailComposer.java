package de.mindarchitects.tlm.ui;

import de.mindarchitects.tlm.mail.EmailList;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/** Eingabebereich für Empfänger (optional), Betreff und Mailtext. */
public final class MailComposer extends VBox {

    private final TextArea emails;
    private final Label count = new Label();
    private final TextField subject = new TextField();
    private final TextArea body;

    public MailComposer(boolean withEmails, String subjectTemplate, String bodyTemplate) {
        super(6);
        if (withEmails) {
            emails = Fx.textArea("", 6);
            emails.setPromptText("E-Mail-Adressen einfügen – eine pro Zeile oder getrennt durch Komma/Semikolon.\n"
                    + "Auch „Max Muster <max@firma.de>“ wird erkannt.");
            emails.textProperty().addListener((o, a, b) -> updateCount());
            getChildren().addAll(new Label("Teilnehmer (E-Mail-Adressen)"), emails, count);
            updateCount();
        } else {
            emails = null;
        }
        subject.setText(subjectTemplate);
        body = Fx.textArea(bodyTemplate, 12);
        Label hint = new Label("Platzhalter: {titel} {datum} {ort} {link} {qr}");
        hint.setStyle("-fx-text-fill: -fx-mid-text-color; -fx-font-size: 0.9em;");
        getChildren().addAll(new Label("Betreff"), subject, new Label("Mailtext"), body, hint);
    }

    private void updateCount() {
        EmailList.Result r = recipients();
        String text = r.valid().size() + " gültige Adresse(n)";
        if (!r.invalid().isEmpty()) {
            text += ", " + r.invalid().size() + " ungültig: " + String.join(", ", r.invalid());
            count.setStyle("-fx-text-fill: #b91c1c;");
        } else {
            count.setStyle("");
        }
        count.setText(text);
    }

    public EmailList.Result recipients() {
        return EmailList.parse(emails == null ? "" : emails.getText());
    }

    public String subject() {
        return subject.getText();
    }

    public String body() {
        return body.getText();
    }

    /** Liefert eine Fehlermeldung oder null, wenn die Eingaben passen. */
    public String validate() {
        if (emails != null) {
            EmailList.Result r = recipients();
            if (r.valid().isEmpty()) {
                return "Bitte mindestens eine gültige E-Mail-Adresse eingeben.";
            }
            if (!r.invalid().isEmpty()) {
                return "Ungültige Einträge: " + String.join(", ", r.invalid());
            }
        }
        if (subject().isBlank()) {
            return "Bitte einen Betreff eingeben.";
        }
        if (!body().contains("{link}") && !body().contains("{qr}")) {
            return "Der Mailtext muss {link} oder {qr} enthalten, sonst erhalten die Teilnehmer keinen Zugang.";
        }
        return null;
    }
}
