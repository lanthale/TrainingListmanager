package de.mindarchitects.tlm.ui;

import org.kordamp.ikonli.materialdesign2.*;

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

/** Dialog: neues Training anlegen; der Server erzeugt dabei den QR-Code. */
public final class NewTrainingDialog extends Dialog<NewTrainingDialog.Data> {

    public record Data(String title, String description, LocalDateTime startsAt, String location) {}

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm");

    public NewTrainingDialog(Window owner) {
        initOwner(owner);
        setTitle("Neues Training");
        setHeaderText("Training anlegen – der QR-Code für die Registrierung wird automatisch erzeugt");
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

        VBox content = new VBox(14, grid);
        content.setPadding(new Insets(4));
        content.setPrefWidth(560);
        getDialogPane().setContent(content);

        ButtonType send = new ButtonType("Anlegen", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(send, ButtonType.CANCEL);
        Fx.decorate(getDialogPane().lookupButton(send), MaterialDesignC.CHECK);
        Fx.decorate(getDialogPane().lookupButton(ButtonType.CANCEL), MaterialDesignC.CLOSE);

        Node sendButton = getDialogPane().lookupButton(send);
        sendButton.addEventFilter(ActionEvent.ACTION, e -> {
            String error = null;
            if (title.getText().isBlank()) {
                error = "Bitte einen Titel eingeben.";
            } else if (date.getValue() != null && parseTime(time.getText()) == null) {
                error = "Uhrzeit bitte im Format HH:MM eingeben.";
            }
            if (error != null) {
                e.consume();
                Fx.error(getDialogPane().getScene().getWindow(), "Eingabe prüfen", error);
            }
        });

        setResultConverter(bt -> {
            if (bt != send) {
                return null;
            }
            LocalDateTime startsAt = date.getValue() == null ? null
                    : LocalDateTime.of(date.getValue(), parseTime(time.getText()));
            return new Data(title.getText().strip(), blankToNull(description.getText()), startsAt,
                    blankToNull(location.getText()));
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
