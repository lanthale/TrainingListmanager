package de.mindarchitects.tlm.ui;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Dialog: neues Training mit Teilnehmerliste und Mailtext. */
public final class NewTrainingDialog extends Dialog<NewTrainingDialog.Data> {

    public record Data(String title, String description, LocalDateTime startsAt, String location,
                       List<String> emails, String subject, String body) {}

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm");

    public NewTrainingDialog(Window owner, String subjectTemplate, String bodyTemplate) {
        initOwner(owner);
        setTitle("Neues Training");
        setHeaderText("Training anlegen und Einladungen versenden");
        setResizable(true);

        TextField title = new TextField();
        DatePicker date = new DatePicker(LocalDate.now().plusWeeks(2));
        TextField time = new TextField("09:00");
        time.setPrefColumnCount(5);
        TextField location = new TextField();
        TextArea description = Fx.textArea("", 3);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        ColumnConstraints c0 = new ColumnConstraints();
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(c0, c1);
        grid.addRow(0, new Label("Titel*"), title);
        grid.addRow(1, new Label("Datum / Uhrzeit"), new HBox(8, date, time));
        grid.addRow(2, new Label("Ort"), location);
        grid.addRow(3, new Label("Beschreibung"), description);

        MailComposer composer = new MailComposer(true, subjectTemplate, bodyTemplate);

        VBox content = new VBox(14, grid, new Separator(), composer);
        content.setPadding(new Insets(4));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefSize(640, 640);
        getDialogPane().setContent(scroll);

        ButtonType send = new ButtonType("Anlegen & senden", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(send, ButtonType.CANCEL);

        Node sendButton = getDialogPane().lookupButton(send);
        sendButton.addEventFilter(ActionEvent.ACTION, e -> {
            String error = null;
            if (title.getText().isBlank()) {
                error = "Bitte einen Titel eingeben.";
            } else if (date.getValue() != null && parseTime(time.getText()) == null) {
                error = "Uhrzeit bitte im Format HH:MM eingeben.";
            } else {
                error = composer.validate();
            }
            if (error != null) {
                e.consume();
                Fx.error(getDialogPane().getScene().getWindow(), "Eingabe prüfen", error);
                return;
            }
            int n = composer.recipients().valid().size();
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Training anlegen und " + n + " Einladung(en) jetzt versenden?", ButtonType.OK, ButtonType.CANCEL);
            confirm.initOwner(getDialogPane().getScene().getWindow());
            confirm.setHeaderText(null);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                e.consume();
            }
        });

        setResultConverter(bt -> {
            if (bt != send) {
                return null;
            }
            LocalDateTime startsAt = date.getValue() == null ? null
                    : LocalDateTime.of(date.getValue(), parseTime(time.getText()));
            return new Data(title.getText().strip(), blankToNull(description.getText()), startsAt,
                    blankToNull(location.getText()), composer.recipients().valid(),
                    composer.subject(), composer.body());
        });
    }

    private static LocalTime parseTime(String s) {
        try {
            return LocalTime.parse(s.strip(), TIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
