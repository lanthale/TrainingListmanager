package de.mindarchitects.tlm.ui;

import org.kordamp.ikonli.materialdesign2.*;

import de.mindarchitects.tlm.api.ApiClient;
import de.mindarchitects.tlm.settings.Secrets;
import de.mindarchitects.tlm.settings.Settings;
import de.mindarchitects.tlm.update.AppVersion;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Window;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/** Einstellungen: API-Zugang, Mail-Vorlage für die Anwesenheitsliste, Updates. Der API-Schlüssel geht in den OS-Schlüsselbund. */
public final class SettingsDialog extends Dialog<ButtonType> {

    private final Settings settings;
    private final Secrets secrets;

    private final TextField apiUrl = new TextField();
    private final PasswordField apiKey = new PasswordField();
    private final TextField reportRecipient = new TextField();
    private final TextField reportSubject = new TextField();
    private final TextArea reportBody = Fx.textArea("", 6);
    private final CheckBox checkUpdates = new CheckBox("Beim Start nach neuen Versionen suchen");

    public SettingsDialog(Window owner, Settings settings, Secrets secrets) {
        this.settings = settings;
        this.secrets = secrets;
        initOwner(owner);
        setTitle("Einstellungen");
        setHeaderText(null);
        setResizable(true);

        apiUrl.setText(settings.apiUrl());
        apiKey.setText(secrets.get(Secrets.API_KEY));
        reportRecipient.setText(settings.reportRecipient());
        reportRecipient.setPromptText("optional, z. B. empfaenger@firma.de");
        reportSubject.setText(settings.reportSubject());
        reportBody.setText(settings.reportBody());
        checkUpdates.setSelected(settings.checkUpdates());

        Button generate = Fx.button("Schlüssel erzeugen …", MaterialDesignK.KEY_PLUS);
        generate.setOnAction(e -> generateKey());
        Button testApi = Fx.button("API testen", MaterialDesignL.LAN_CONNECT);
        testApi.setOnAction(e -> {
            apply();
            Fx.async(window(), () -> {
                new ApiClient(settings.apiUrl(), secrets.get(Secrets.API_KEY)).ping();
                return null;
            }, r -> Fx.info(window(), "Verbindung OK", "Die API hat den Schlüssel akzeptiert."));
        });
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(8);
        g.setPadding(new Insets(10));
        ColumnConstraints c0 = new ColumnConstraints();
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        c1.setMinWidth(320);
        g.getColumnConstraints().addAll(c0, c1);

        int r = 0;
        g.add(section("Server-API"), 0, r++, 2, 1);
        g.addRow(r++, new Label("API-URL"), apiUrl);
        g.addRow(r++, new Label("API-Schlüssel"), new HBox(8, grow(apiKey), generate));
        g.add(testApi, 1, r++);
        g.add(section("Anwesenheitsliste per Mail weiterleiten"), 0, r++, 2, 1);
        g.addRow(r++, new Label("Empfänger"), reportRecipient);
        g.addRow(r++, new Label("Betreff"), reportSubject);
        g.addRow(r++, new Label("Text"), reportBody);
        Label placeholders = new Label("Platzhalter: {titel} {datum} {ort} {anzahl}. Die Mail öffnet sich als Entwurf "
                + "im Mailprogramm und wird erst dort gesendet.");
        placeholders.setWrapText(true);
        placeholders.setStyle("-fx-text-fill: -fx-mid-text-color; -fx-font-size: 0.9em;");
        g.add(placeholders, 1, r++);
        g.add(section("Software-Update"), 0, r++, 2, 1);
        Label version = new Label("Installierte Version: " + AppVersion.current());
        g.addRow(r++, version, checkUpdates);
        if (!secrets.isPersistent()) {
            Label warn = new Label("Kein Schlüsselbund verfügbar – der API-Schlüssel gilt nur bis zum Beenden.");
            warn.setStyle("-fx-text-fill: #b45309;");
            g.add(warn, 0, r, 2, 1);
        }

        getDialogPane().setContent(g);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                apply();
            }
            return bt;
        });
    }

    private void apply() {
        settings.apiUrl(apiUrl.getText());
        settings.reportRecipient(reportRecipient.getText());
        settings.reportSubject(reportSubject.getText().isBlank() ? Settings.DEFAULT_REPORT_SUBJECT : reportSubject.getText());
        settings.reportBody(reportBody.getText().isBlank() ? Settings.DEFAULT_REPORT_BODY : reportBody.getText());
        settings.checkUpdates(checkUpdates.isSelected());
        settings.flush();
        secrets.set(Secrets.API_KEY, apiKey.getText().trim());
    }

    /** Erzeugt einen neuen API-Schlüssel und zeigt den Hash für config.php an. */
    private void generateKey() {
        if (!apiKey.getText().isBlank()) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Es ist bereits ein Schlüssel eingetragen. Einen neuen erzeugen? "
                            + "Danach muss der neue Hash in config.php auf dem Server eingetragen werden.",
                    ButtonType.OK, ButtonType.CANCEL);
            confirm.initOwner(window());
            confirm.setHeaderText(null);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        String key = HexFormat.of().formatHex(raw);
        apiKey.setText(key);

        String hash;
        try {
            hash = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }

        TextField hashField = new TextField("'api_key_hash' => '" + hash + "',");
        hashField.setEditable(false);
        hashField.setPrefColumnCount(50);
        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.initOwner(window());
        info.setTitle("API-Schlüssel");
        info.setHeaderText("Diese Zeile in config.php auf dem Server eintragen:");
        info.getDialogPane().setContent(hashField);
        ButtonType copy = new ButtonType("Kopieren", ButtonBar.ButtonData.LEFT);
        info.getButtonTypes().setAll(copy, ButtonType.OK);
        Fx.decorate(info.getDialogPane().lookupButton(copy), MaterialDesignC.CONTENT_COPY);
        if (info.showAndWait().orElse(ButtonType.OK) == copy) {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(hashField.getText());
            Clipboard.getSystemClipboard().setContent(cc);
        }
    }

    private Window window() {
        return getDialogPane().getScene().getWindow();
    }

    private static Label section(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold; -fx-padding: 6 0 0 0;");
        return l;
    }

    private static <T extends Control> T grow(T c) {
        HBox.setHgrow(c, Priority.ALWAYS);
        c.setMaxWidth(Double.MAX_VALUE);
        return c;
    }
}
