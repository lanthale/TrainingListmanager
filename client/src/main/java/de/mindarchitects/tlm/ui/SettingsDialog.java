package de.mindarchitects.tlm.ui;

import org.kordamp.ikonli.materialdesign2.*;

import de.mindarchitects.tlm.api.ApiClient;
import de.mindarchitects.tlm.mail.MailTemplate;
import de.mindarchitects.tlm.mail.Mailer;
import de.mindarchitects.tlm.mail.QrCode;
import de.mindarchitects.tlm.settings.Secrets;
import de.mindarchitects.tlm.settings.Settings;
import de.mindarchitects.tlm.settings.Settings.SmtpSecurity;
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

/** Einstellungen: API-Zugang und SMTP. Geheimnisse gehen in den OS-Schlüsselbund. */
public final class SettingsDialog extends Dialog<ButtonType> {

    private final Settings settings;
    private final Secrets secrets;

    private final TextField apiUrl = new TextField();
    private final PasswordField apiKey = new PasswordField();
    private final TextField smtpHost = new TextField();
    private final Spinner<Integer> smtpPort = new Spinner<>(1, 65535, 587);
    private final ChoiceBox<SmtpSecurity> smtpSecurity = new ChoiceBox<>();
    private final TextField smtpUser = new TextField();
    private final PasswordField smtpPassword = new PasswordField();
    private final TextField fromName = new TextField();
    private final TextField fromAddress = new TextField();
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
        smtpHost.setText(settings.smtpHost());
        smtpPort.getValueFactory().setValue(settings.smtpPort());
        smtpPort.setEditable(true);
        smtpSecurity.getItems().setAll(SmtpSecurity.values());
        smtpSecurity.setValue(settings.smtpSecurity());
        smtpSecurity.valueProperty().addListener((o, a, b) -> {
            if (b == SmtpSecurity.SSL) smtpPort.getValueFactory().setValue(465);
            if (b == SmtpSecurity.STARTTLS) smtpPort.getValueFactory().setValue(587);
        });
        smtpUser.setText(settings.smtpUser());
        smtpPassword.setText(secrets.get(Secrets.SMTP_PASSWORD));
        fromName.setText(settings.fromName());
        fromAddress.setText(settings.fromAddress());
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
        Button testMail = Fx.button("Test-Mail an Absender senden", MaterialDesignE.EMAIL_CHECK);
        testMail.setOnAction(e -> {
            apply();
            Fx.async(window(), () -> {
                String link = "https://trainings.mind-architects.de/?t=test";
                try (Mailer m = new Mailer(settings, secrets.get(Secrets.SMTP_PASSWORD))) {
                    var mail = MailTemplate.render(settings.mailSubject(), settings.mailBody(),
                            "Test-Training", "01.01.2030 09:00", "Testort", link);
                    m.send(settings.fromAddress(), mail, QrCode.png(link, 440));
                }
                return null;
            }, r -> Fx.info(window(), "Test-Mail gesendet", "Bitte das Postfach " + settings.fromAddress() + " prüfen."));
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
        g.add(section("E-Mail-Versand (SMTP)"), 0, r++, 2, 1);
        g.addRow(r++, new Label("Server"), smtpHost);
        g.addRow(r++, new Label("Port / Sicherheit"), new HBox(8, smtpPort, smtpSecurity));
        g.addRow(r++, new Label("Benutzer"), smtpUser);
        g.addRow(r++, new Label("Passwort"), smtpPassword);
        g.addRow(r++, new Label("Absendername"), fromName);
        g.addRow(r++, new Label("Absenderadresse"), fromAddress);
        g.add(testMail, 1, r++);
        g.add(section("Software-Update"), 0, r++, 2, 1);
        Label version = new Label("Installierte Version: " + AppVersion.current());
        g.addRow(r++, version, checkUpdates);
        if (!secrets.isPersistent()) {
            Label warn = new Label("Kein Schlüsselbund verfügbar – Passwörter gelten nur bis zum Beenden.");
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
        settings.smtpHost(smtpHost.getText());
        settings.smtpPort(smtpPort.getValue());
        settings.smtpSecurity(smtpSecurity.getValue());
        settings.smtpUser(smtpUser.getText());
        settings.fromName(fromName.getText());
        settings.fromAddress(fromAddress.getText());
        settings.checkUpdates(checkUpdates.isSelected());
        settings.flush();
        secrets.set(Secrets.API_KEY, apiKey.getText().trim());
        secrets.set(Secrets.SMTP_PASSWORD, smtpPassword.getText());
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
