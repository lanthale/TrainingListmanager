package de.mindarchitects.tlm;

import de.mindarchitects.tlm.InvitationService.TrainingInfo;
import de.mindarchitects.tlm.api.Model.Invitation;
import de.mindarchitects.tlm.api.Model.Training;
import de.mindarchitects.tlm.api.Model.TrainingDetail;
import de.mindarchitects.tlm.settings.Secrets;
import de.mindarchitects.tlm.settings.Settings;
import de.mindarchitects.tlm.ui.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

/** Hauptfenster: Trainingsliste links, Details rechts. */
public class App extends Application {

    private final Settings settings = new Settings();
    private final Secrets secrets = new Secrets();
    private final InvitationService service = new InvitationService(settings, secrets);

    private Stage stage;
    private final ListView<Training> list = new ListView<>();
    private final Label status = new Label();
    private TrainingDetailView detail;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;

        Button newBtn = new Button("Neues Training …");
        newBtn.setDefaultButton(true);
        newBtn.setOnAction(e -> newTraining());
        Button refresh = new Button("Aktualisieren");
        refresh.setOnAction(e -> reload());
        Button settingsBtn = new Button("Einstellungen …");
        settingsBtn.setOnAction(e -> openSettings());
        Region spacer = new Region();
        javafx.scene.layout.HBox.setHgrow(spacer, Priority.ALWAYS);
        ToolBar toolbar = new ToolBar(newBtn, refresh, spacer, settingsBtn);

        list.setCellFactory(lv -> new TrainingCell());
        list.setPlaceholder(new Label("Keine Trainings"));
        list.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> loadDetail(b));
        VBox left = new VBox(list);
        VBox.setVgrow(list, Priority.ALWAYS);
        left.setMinWidth(240);

        detail = new TrainingDetailView(this::setActive, this::inviteMore, this::resend);

        SplitPane split = new SplitPane(left, detail);
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(0.3);
        SplitPane.setResizableWithParent(left, false);

        status.setPadding(new Insets(4, 10, 4, 10));
        BorderPane root = new BorderPane(split, toolbar, null, status, null);

        stage.setTitle("TrainingListmanager");
        stage.setScene(new Scene(root, 1150, 720));
        stage.show();

        if (secrets.get(Secrets.API_KEY).isBlank()) {
            Platform.runLater(() -> {
                Fx.info(stage, "Willkommen",
                        "Bitte zuerst API-Schlüssel und SMTP-Zugang in den Einstellungen hinterlegen.");
                openSettings();
            });
        } else {
            reload();
        }
    }

    // ------------------------------------------------------------------ Laden

    private void reload() {
        Long selectedId = selectedTraining() == null ? null : selectedTraining().id();
        status.setText("Lade …");
        Fx.async(stage, () -> service.api().trainings(), trainings -> {
            list.getItems().setAll(trainings);
            status.setText(trainings.size() + " Training(s) · " + settings.apiUrl());
            if (selectedId != null) {
                trainings.stream().filter(t -> t.id() == selectedId).findFirst()
                        .ifPresent(t -> list.getSelectionModel().select(t));
            }
        });
    }

    private void loadDetail(Training t) {
        if (t == null) {
            detail.show(null);
            return;
        }
        Fx.async(stage, () -> service.api().training(t.id()), d -> {
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
        new NewTrainingDialog(stage, settings.mailSubject(), settings.mailBody()).showAndWait().ifPresent(d -> {
            rememberTemplate(d.subject(), d.body());
            ProgressDialog.run(stage, "Training anlegen & einladen",
                    p -> service.createTrainingAndInvite(d.title(), d.description(), d.startsAt(), d.location(),
                            d.emails(), d.subject(), d.body(), p),
                    this::reload);
        });
    }

    private void setActive(boolean active) {
        TrainingDetail d = detail.current();
        if (d == null) return;
        Fx.async(stage, () -> {
            service.api().setActive(d.training().id(), active);
            return null;
        }, r -> reload());
    }

    private void inviteMore() {
        TrainingDetail d = detail.current();
        if (d == null) return;
        new InviteDialog(stage, "Weitere Teilnehmer zu „" + d.training().title() + "“ einladen", true,
                settings.mailSubject(), settings.mailBody()).showAndWait().ifPresent(data -> {
            rememberTemplate(data.subject(), data.body());
            ProgressDialog.run(stage, "Einladungen senden",
                    p -> service.invite(d.training().id(), info(d), data.emails(), data.subject(), data.body(), p),
                    this::reload);
        });
    }

    private void resend(List<Invitation> selected) {
        TrainingDetail d = detail.current();
        if (d == null) return;
        List<Long> ids = selected.stream().filter(i -> i.usedAt() == null).map(Invitation::id).toList();
        if (ids.isEmpty()) {
            Fx.info(stage, "Nichts zu senden", "Die ausgewählten Teilnehmer haben sich bereits registriert.");
            return;
        }
        new InviteDialog(stage, ids.size() + " Einladung(en) erneut senden. Der bisherige Link wird dabei ungültig.",
                false, settings.mailSubject(), settings.mailBody()).showAndWait().ifPresent(data -> {
            rememberTemplate(data.subject(), data.body());
            ProgressDialog.run(stage, "Einladungen erneut senden",
                    p -> service.resend(info(d), ids, data.subject(), data.body(), p),
                    this::reload);
        });
    }

    private void rememberTemplate(String subject, String body) {
        settings.mailSubject(subject);
        settings.mailBody(body);
        settings.flush();
    }

    private static TrainingInfo info(TrainingDetail d) {
        var t = d.training();
        return new TrainingInfo(t.title(), Fmt.dateTime(t.startsAt()), Fmt.nz(t.location()));
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
                    + t.registered() + "/" + t.invited() + " registriert"
                    + (t.active() ? "" : " · geschlossen"));
            box.setOpacity(t.active() ? 1.0 : 0.55);
            setGraphic(box);
        }
    }
}
