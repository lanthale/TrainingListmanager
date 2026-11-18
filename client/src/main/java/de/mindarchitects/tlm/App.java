package de.mindarchitects.tlm;

import org.kordamp.ikonli.materialdesign2.*;

import de.mindarchitects.tlm.api.ApiClient;
import de.mindarchitects.tlm.api.ApiException;
import de.mindarchitects.tlm.api.Model.Registration;
import de.mindarchitects.tlm.api.Model.Training;
import de.mindarchitects.tlm.api.Model.TrainingDetail;
import de.mindarchitects.tlm.settings.Secrets;
import de.mindarchitects.tlm.settings.Settings;
import de.mindarchitects.tlm.ui.*;
import de.mindarchitects.tlm.update.AppVersion;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Hauptfenster: Trainingsliste links, Details rechts. */
public class App extends Application {

    private final Settings settings = new Settings();
    private final Secrets secrets = new Secrets();

    private Stage stage;
    private final ListView<Training> list = new ListView<>();
    private final Label status = new Label();
    private TrainingDetailView detail;

    /** Abstand der automatischen Aktualisierung (neue Registrierungen erscheinen ohne Klick). */
    private static final double AUTO_REFRESH_SECONDS = 10;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private boolean autoRefreshRunning;
    /** Während die Liste im Hintergrund aktualisiert wird, kein erneutes Laden durch Auswahl-Ereignisse. */
    private boolean suppressSelection;

    private record Refresh(List<Training> trainings, TrainingDetail detail) {}

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;

        Button newBtn = Fx.button("Neues Training …", MaterialDesignC.CALENDAR_PLUS);
        newBtn.setDefaultButton(true);
        newBtn.setOnAction(e -> newTraining());
        Button refresh = Fx.button("Aktualisieren", MaterialDesignR.REFRESH);
        refresh.setOnAction(e -> reload());
        Button settingsBtn = Fx.button("Einstellungen …", MaterialDesignC.COG);
        settingsBtn.setOnAction(e -> openSettings());
        Button updateBtn = Fx.button("Nach Updates suchen", MaterialDesignU.UPDATE);
        updateBtn.setOnAction(e -> UpdateDialog.check(stage, settings, getHostServices(), true));
        Region spacer = new Region();
        javafx.scene.layout.HBox.setHgrow(spacer, Priority.ALWAYS);
        // MINDArchitects-Logo (weiß) dunkel eingefärbt für die helle Werkzeugleiste
        javafx.scene.image.ImageView logo = new javafx.scene.image.ImageView(
                new javafx.scene.image.Image(App.class.getResourceAsStream("/icons/logo.png")));
        logo.setFitHeight(52);
        logo.setPreserveRatio(true);
        logo.setSmooth(true);
        logo.setEffect(new javafx.scene.effect.ColorAdjust(0, 0, -1, 0));
        logo.setMouseTransparent(true);
        ToolBar toolbar = new ToolBar(newBtn, refresh, spacer, updateBtn, settingsBtn);
        toolbar.setMinHeight(64);
        // Logo genau in der Fenstermitte über der Werkzeugleiste (unabhängig von der Breite der Buttons)
        javafx.scene.layout.StackPane top = new javafx.scene.layout.StackPane(toolbar, logo);

        list.setCellFactory(lv -> new TrainingCell());
        list.setPlaceholder(new Label("Keine Trainings"));
        list.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (!suppressSelection) loadDetail(b);
        });
        VBox left = new VBox(list);
        VBox.setVgrow(list, Priority.ALWAYS);
        left.setMinWidth(240);

        detail = new TrainingDetailView(new TrainingDetailView.Actions() {
            @Override public void setActive(boolean active) { App.this.setActive(active); }
            @Override public void exportExcel() { App.this.exportExcel(); }
            @Override public void mailReport() { App.this.mailReport(); }
            @Override public void deleteTraining() { App.this.deleteTraining(); }
            @Override public void exportQr() { App.this.exportQr(); }
            @Override public void newCode() { App.this.newCode(); }
        });

        SplitPane split = new SplitPane(left, detail);
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(0.3);
        SplitPane.setResizableWithParent(left, false);

        status.setPadding(new Insets(4, 10, 4, 10));
        BorderPane root = new BorderPane(split, top, null, status, null);

        for (int size : new int[]{16, 32, 64, 128, 256}) {
            var in = App.class.getResourceAsStream("/icons/app-" + size + ".png");
            if (in != null) {
                stage.getIcons().add(new javafx.scene.image.Image(in));
            }
        }
        stage.setTitle("TrainingListmanager " + AppVersion.current());
        stage.setScene(new Scene(root, 1150, 720));
        stage.show();

        Timeline poll = new Timeline(new KeyFrame(javafx.util.Duration.seconds(AUTO_REFRESH_SECONDS), e -> autoRefresh()));
        poll.setCycleCount(Animation.INDEFINITE);
        poll.play();

        // Still im Hintergrund nach einer neuen Version suchen (nicht in der Entwicklungsversion)
        UpdateDialog.check(stage, settings, getHostServices(), false);

        // Nach dem Update auf die Version ohne Mailversand: altes SMTP-Passwort aus dem Schlüsselbund entfernen
        if (settings.removedSmtp()) {
            secrets.set(Secrets.LEGACY_SMTP_PASSWORD, "");
        }

        if (apiKey().isBlank()) {
            Platform.runLater(() -> {
                Fx.info(stage, "Willkommen", "Bitte zuerst den API-Schlüssel in den Einstellungen hinterlegen.");
                openSettings();
            });
        } else {
            reload();
        }
    }

    // ------------------------------------------------------------------ Laden

    private ApiClient api() {
        return new ApiClient(apiUrl(), apiKey());
    }

    private String apiUrl() {
        String override = AppVersion.isDev() ? System.getProperty("tlm.apiUrl") : null;
        return override != null ? override : settings.apiUrl();
    }

    /**
     * API-Schlüssel aus dem Schlüsselbund. Nur in der Entwicklungsversion (mvn javafx:run) lassen sich
     * URL und Schlüssel zum Testen gegen einen lokalen Server mit -Dtlm.apiUrl / -Dtlm.apiKey überschreiben.
     */
    private String apiKey() {
        if (AppVersion.isDev() && System.getProperty("tlm.apiUrl") != null) {
            return System.getProperty("tlm.apiKey", "");
        }
        return secrets.get(Secrets.API_KEY);
    }

    private void reload() {
        reload(selectedTraining() == null ? null : selectedTraining().id(), false);
    }

    /** Lädt die Liste neu und wählt das Training selectedId aus (qrTab: danach den QR-Code zeigen). */
    private void reload(Long selectedId, boolean qrTab) {
        status.setText("Lade …");
        Fx.async(stage, () -> api().trainings(), trainings -> {
            list.getItems().setAll(trainings);
            status.setText(statusText(trainings.size()));
            if (selectedId != null) {
                trainings.stream().filter(t -> t.id() == selectedId).findFirst()
                        .ifPresent(t -> list.getSelectionModel().select(t));
                if (qrTab) {
                    detail.showQrTab();
                }
            }
        });
    }

    private String statusText(int count) {
        return count + " Training(s) · Stand " + LocalTime.now().format(CLOCK) + " · " + apiUrl();
    }

    /**
     * Holt Liste und ausgewähltes Training still im Hintergrund neu, damit neue Registrierungen
     * ohne Klick erscheinen. Auswahl, Tab und Tabellen bleiben dabei unverändert.
     */
    private void autoRefresh() {
        if (autoRefreshRunning || apiKey().isBlank()) {
            return;
        }
        autoRefreshRunning = true;
        Long selectedId = selectedTraining() == null ? null : selectedTraining().id();
        Fx.asyncQuiet(() -> {
            ApiClient api = api();
            List<Training> trainings = api.trainings();
            TrainingDetail d = null;
            if (selectedId != null && trainings.stream().anyMatch(t -> t.id() == selectedId)) {
                try {
                    d = api.training(selectedId);
                } catch (ApiException e) {
                    d = null; // z. B. gerade gelöscht
                }
            }
            return new Refresh(trainings, d);
        }, r -> {
            autoRefreshRunning = false;
            applyRefresh(selectedId, r);
        }, ex -> {
            autoRefreshRunning = false;
            status.setText("Automatische Aktualisierung fehlgeschlagen: " + ex.getMessage());
        });
    }

    private void applyRefresh(Long selectedId, Refresh r) {
        // Liste: nur geänderte Einträge ersetzen, Auswahl beibehalten
        suppressSelection = true;
        try {
            var items = list.getItems();
            boolean sameIds = items.size() == r.trainings().size();
            for (int i = 0; sameIds && i < items.size(); i++) {
                sameIds = items.get(i).id() == r.trainings().get(i).id();
            }
            if (sameIds) {
                for (int i = 0; i < items.size(); i++) {
                    if (!items.get(i).equals(r.trainings().get(i))) {
                        items.set(i, r.trainings().get(i));
                    }
                }
            } else {
                items.setAll(r.trainings());
            }
            if (selectedId != null) {
                r.trainings().stream().filter(t -> t.id() == selectedId).findFirst()
                        .ifPresentOrElse(t -> list.getSelectionModel().select(t), () -> {
                            list.getSelectionModel().clearSelection();
                            detail.show(null);
                        });
            }
        } finally {
            suppressSelection = false;
        }
        String text = statusText(r.trainings().size());

        // Details: nur neu anzeigen, wenn sich etwas geändert hat und noch dasselbe Training gewählt ist
        TrainingDetail current = detail.current();
        Training sel = selectedTraining();
        if (r.detail() != null && sel != null && sel.id() == r.detail().training().id()
                && !r.detail().equals(current)) {
            Set<Long> known = current == null || current.training().id() != sel.id() ? Set.of()
                    : current.registrations().stream().map(Registration::id).collect(Collectors.toSet());
            boolean sameTraining = current != null && current.training().id() == sel.id();
            List<Registration> added = !sameTraining ? List.of()
                    : r.detail().registrations().stream().filter(x -> !known.contains(x.id())).toList();
            detail.show(r.detail());
            if (!added.isEmpty()) {
                text = "Neu registriert: " + added.stream()
                        .map(x -> x.firstName() + " " + x.lastName()).collect(Collectors.joining(", "))
                        + " · " + text;
            }
        }
        status.setText(text);
    }

    private void loadDetail(Training t) {
        if (t == null) {
            detail.show(null);
            return;
        }
        Fx.async(stage, () -> api().training(t.id()), d -> {
            if (selectedTraining() != null && selectedTraining().id() == d.training().id()) {
                detail.show(d);
            }
        });
    }

    private Training selectedTraining() {
        return list.getSelectionModel().getSelectedItem();
    }

    // --------------------------------------------------------------- Aktionen

    private void openSettings() {
        new SettingsDialog(stage, settings, secrets).showAndWait()
                .filter(bt -> bt == ButtonType.OK)
                .ifPresent(bt -> reload());
    }

    private void newTraining() {
        new NewTrainingDialog(stage).showAndWait().ifPresent(d ->
                Fx.async(stage, () -> api().createTraining(d.title(), d.description(), d.startsAt(), d.location()),
                        created -> {
                            status.setText("Training „" + d.title() + "“ angelegt, QR-Code erzeugt.");
                            reload(created.id(), true);
                        }));
    }

    private void setActive(boolean active) {
        TrainingDetail d = detail.current();
        if (d == null) return;
        Fx.async(stage, () -> {
            api().setActive(d.training().id(), active);
            return null;
        }, r -> reload());
    }

    /** QR-Code mit Titel und Datum als PNG speichern (zum Ausdrucken oder für den Beamer). */
    private void exportQr() {
        TrainingDetail d = detail.current();
        if (d == null || d.code() == null) return;
        File file = chooseSaveFile("QR-Code als PNG speichern", "PNG-Bild (*.png)", "*.png",
                "QR_" + ExcelExport.suggestedFileName(d.training()).replaceFirst("\\.xlsx$", ".png"));
        if (file == null) return;
        var t = d.training();
        Fx.async(stage, () -> {
            Files.write(file.toPath(), QrCode.poster(d.code().url(), t.title(),
                    TrainingDetailView.subtitle(t.startsAt(), t.location())));
            return null;
        }, r -> {
            status.setText("QR-Code gespeichert: " + file.getName());
            getHostServices().showDocument(file.toURI().toString());
        });
    }

    private void newCode() {
        TrainingDetail d = detail.current();
        if (d == null) return;
        if (d.code() != null && !Fx.confirm(stage, "Neuen QR-Code erzeugen?",
                "Der bisherige QR-Code wird sofort ungültig. Bereits ausgedruckte Codes funktionieren dann nicht mehr. "
                        + "Die vorhandenen Registrierungen bleiben erhalten.", "Neuen Code erzeugen")) {
            return;
        }
        Fx.async(stage, () -> api().createCode(d.training().id()), code -> {
            status.setText("Neuer QR-Code erzeugt.");
            reload(d.training().id(), true);
        });
    }

    /** Training mit allen Teilnehmerdaten endgültig löschen (z. B. nach dem Weiterleiten der Liste). */
    private void deleteTraining() {
        TrainingDetail d = detail.current();
        if (d == null) return;
        var t = d.training();
        int n = d.registrations().size();
        if (!Fx.confirm(stage, "Training „" + t.title() + "“ löschen?",
                "Das Training wird mit " + n + " Registrierung(en) und dem QR-Code endgültig vom Server gelöscht. "
                        + "Das kann nicht rückgängig gemacht werden.\n\n"
                        + "Tipp: Vorher die Anwesenheitsliste exportieren oder per Mail weiterleiten.",
                "Endgültig löschen")) {
            return;
        }
        Fx.async(stage, () -> {
            api().deleteTraining(t.id());
            return null;
        }, r -> {
            status.setText("Training „" + t.title() + "“ gelöscht.");
            list.getSelectionModel().clearSelection();
            detail.show(null);
            reload(null, false);
        });
    }

    /** Anwesenheitsliste als Excel erzeugen und als Mail-Entwurf im Mailprogramm öffnen (nicht senden). */
    private void mailReport() {
        TrainingDetail d = detail.current();
        if (d == null) return;
        status.setText("Erstelle Mail-Entwurf …");
        Fx.async(stage, () -> {
            TrainingDetail fresh = api().training(d.training().id());
            // Temporäre Datei, wird beim Beenden gelöscht (Mail übernimmt die Anlage in den Entwurf)
            Path dir = Files.createTempDirectory("tlm-mail");
            dir.toFile().deleteOnExit();
            Path file = dir.resolve(ExcelExport.suggestedFileName(fresh.training()));
            ExcelExport.write(fresh, file);
            file.toFile().deleteOnExit();
            var t = fresh.training();
            String subject = fill(settings.reportSubject(), fresh);
            String body = fill(settings.reportBody(), fresh);
            MailDraft.Result result = MailDraft.open(settings.reportRecipient(), subject, body, file);
            return new Object[]{result, body, t.title()};
        }, r -> {
            MailDraft.Result result = (MailDraft.Result) r[0];
            if (result == MailDraft.Result.ATTACHMENT_ONLY) {
                ClipboardContent cc = new ClipboardContent();
                cc.putString((String) r[1]);
                Clipboard.getSystemClipboard().setContent(cc);
                Fx.info(stage, "Mail mit Anlage geöffnet",
                        "Apple Mail hat eine neue Nachricht mit der Anwesenheitsliste geöffnet. Betreff und Empfänger "
                                + "bitte dort eintragen; der Text liegt in der Zwischenablage.\n\n"
                                + "Für die vollständige Vorbelegung unter Systemeinstellungen → Datenschutz & Sicherheit → "
                                + "Automation erlauben, dass TrainingListmanager „Mail“ steuert.");
            }
            status.setText("Mail-Entwurf für „" + r[2] + "“ geöffnet – bitte im Mailprogramm prüfen und senden.");
        });
    }

    /** Platzhalter {titel} {datum} {ort} {anzahl} ersetzen. */
    private static String fill(String template, TrainingDetail d) {
        var t = d.training();
        String date = Fmt.dateTime(t.startsAt());
        return template
                .replace("{titel}", Fmt.nz(t.title()))
                .replace("{datum}", date.isEmpty() ? "ohne Datum" : date + " Uhr")
                .replace("{ort}", Fmt.nz(t.location()).isEmpty() ? "ohne Ort" : t.location())
                .replace("{anzahl}", String.valueOf(d.registrations().size()));
    }

    private File chooseSaveFile(String title, String filterName, String pattern, String initialName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(filterName, pattern));
        chooser.setInitialFileName(initialName);
        String lastDir = settings.exportDir();
        if (!lastDir.isEmpty() && new File(lastDir).isDirectory()) {
            chooser.setInitialDirectory(new File(lastDir));
        }
        File file = chooser.showSaveDialog(stage);
        if (file == null) return null;
        String ext = pattern.substring(1);
        if (!file.getName().toLowerCase().endsWith(ext)) {
            file = new File(file.getParentFile(), file.getName() + ext);
        }
        settings.exportDir(file.getParent());
        settings.flush();
        return file;
    }

    private void exportExcel() {
        TrainingDetail d = detail.current();
        if (d == null) return;
        File file = chooseSaveFile("Teilnehmer als Excel speichern", "Excel-Arbeitsmappe (*.xlsx)", "*.xlsx",
                ExcelExport.suggestedFileName(d.training()));
        if (file == null) return;
        File target = file;
        status.setText("Exportiere …");
        // Aktuellen Stand vom Server holen, damit die Liste vollständig ist
        Fx.async(stage, () -> {
            TrainingDetail fresh = api().training(d.training().id());
            ExcelExport.write(fresh, target.toPath());
            return fresh;
        }, fresh -> {
            status.setText(fresh.registrations().size() + " Teilnehmer exportiert nach " + target.getName());
            getHostServices().showDocument(target.toURI().toString());
        });
    }

    // ------------------------------------------------------------------ Zelle

    private static final class TrainingCell extends ListCell<Training> {
        private final Label title = new Label();
        private final Label sub = new Label();
        private final VBox box = new VBox(2, title, sub);

        TrainingCell() {
            title.setStyle("-fx-font-weight: bold;");
            sub.setStyle("-fx-font-size: 0.9em;");
            box.setPadding(new Insets(4, 2, 4, 2));
        }

        @Override
        protected void updateItem(Training t, boolean empty) {
            super.updateItem(t, empty);
            if (empty || t == null) {
                setGraphic(null);
                return;
            }
            title.setText(t.title());
            String date = Fmt.dateTime(t.startsAt());
            sub.setText((date.isEmpty() ? "" : date + " · ")
                    + t.registered() + " registriert"
                    + (t.active() ? "" : " · geschlossen"));
            box.setOpacity(t.active() ? 1.0 : 0.55);
            setGraphic(box);
        }
    }
}
