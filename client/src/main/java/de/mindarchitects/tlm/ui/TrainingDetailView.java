package de.mindarchitects.tlm.ui;

import org.kordamp.ikonli.materialdesign2.*;

import de.mindarchitects.tlm.Fmt;
import de.mindarchitects.tlm.QrCode;
import de.mindarchitects.tlm.api.Model.Code;
import de.mindarchitects.tlm.api.Model.Registration;
import de.mindarchitects.tlm.api.Model.TrainingDetail;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

/** Rechte Seite: Kopfdaten, Aktionen, QR-Code und Registrierungen eines Trainings. */
public final class TrainingDetailView extends VBox {

    /** Aktionen, die das Hauptfenster ausführt. */
    public interface Actions {
        void setActive(boolean active);
        void exportExcel();
        void mailReport();
        void deleteTraining();
        void exportQr();
        void newCode();
    }

    private final Label title = new Label();
    private final Label meta = new Label();
    private final Label description = new Label();
    private final CheckBox active = new CheckBox("Registrierung offen");
    private final TableView<Registration> registrations = new TableView<>();
    private final Tab qrTab = new Tab("QR-Code");
    private final Tab regTab = new Tab();

    private final ImageView qrImage = new ImageView();
    private final TextField qrUrl = new TextField();
    private final Label qrInfo = new Label();
    private final Button exportQr = Fx.button("Als PNG exportieren …", MaterialDesignQ.QRCODE);
    private final Button copyLink = Fx.button("Link kopieren", MaterialDesignC.CONTENT_COPY);
    private final Button newCode = Fx.button("Neuen QR-Code erzeugen …", MaterialDesignR.REFRESH);

    private boolean updating;
    private TrainingDetail current;

    public TrainingDetailView(Actions actions) {
        super(10);
        setPadding(new Insets(14));

        title.setStyle("-fx-font-size: 1.5em; -fx-font-weight: bold;");
        title.setWrapText(true);
        meta.setStyle("-fx-text-fill: -fx-mid-text-color;");
        description.setWrapText(true);
        active.selectedProperty().addListener((o, a, b) -> {
            if (!updating) actions.setActive(b);
        });

        // --- QR-Code
        qrImage.setFitWidth(280);
        qrImage.setFitHeight(280);
        qrImage.setPreserveRatio(true);
        qrUrl.setEditable(false);
        qrUrl.setFocusTraversable(false);
        qrInfo.setWrapText(true);
        exportQr.setOnAction(e -> actions.exportQr());
        copyLink.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(qrUrl.getText());
            Clipboard.getSystemClipboard().setContent(cc);
        });
        newCode.setOnAction(e -> actions.newCode());
        Label qrHint = new Label("Den QR-Code ausdrucken oder per Beamer zeigen. Die Teilnehmer scannen ihn mit dem "
                + "Handy und tragen sich auf der Registrierungsseite ein. Ein neuer QR-Code macht den bisherigen ungültig.");
        qrHint.setWrapText(true);
        qrHint.setStyle("-fx-text-fill: -fx-mid-text-color;");
        VBox qrSide = new VBox(10, qrInfo, qrUrl, new HBox(8, exportQr, copyLink), newCode, qrHint);
        HBox.setHgrow(qrSide, Priority.ALWAYS);
        HBox qrBox = new HBox(18, qrImage, qrSide);
        qrBox.setPadding(new Insets(12, 0, 0, 0));
        qrBox.setAlignment(Pos.TOP_LEFT);
        qrTab.setContent(qrBox);

        // --- Registrierungen
        registrations.getColumns().addAll(List.of(
                col("Vorname", Registration::firstName, 130),
                col("Nachname", Registration::lastName, 150),
                col("Job-Position", Registration::jobPosition, 170),
                col("Händler", Registration::dealer, 190),
                col("Registriert am", r -> Fmt.dateTime(r.createdAt()), 130)));
        registrations.setPlaceholder(new Label("Noch keine Registrierungen"));
        registrations.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox regBox = new VBox(registrations);
        regBox.setPadding(new Insets(8, 0, 0, 0));
        VBox.setVgrow(registrations, Priority.ALWAYS);
        regTab.setContent(regBox);

        TabPane tabs = new TabPane(qrTab, regTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        // --- Aktionen
        Button export = Fx.button("Excel-Export …", MaterialDesignM.MICROSOFT_EXCEL);
        export.setOnAction(e -> actions.exportExcel());
        Button mail = Fx.button("Per Mail weiterleiten …", MaterialDesignE.EMAIL_EDIT_OUTLINE);
        mail.setOnAction(e -> actions.mailReport());
        Button delete = Fx.button("Training löschen …", MaterialDesignD.DELETE_OUTLINE);
        delete.setStyle("-fx-text-fill: #b91c1c;");
        delete.setOnAction(e -> actions.deleteTraining());
        Region headSpacer = new Region();
        HBox.setHgrow(headSpacer, Priority.ALWAYS);
        HBox activeRow = new HBox(8, active, headSpacer, export, mail, delete);
        activeRow.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(title, meta, description, activeRow, tabs);
        show(null);
    }

    public TrainingDetail current() {
        return current;
    }

    /** Wechselt auf den Tab mit dem QR-Code (z. B. nach dem Anlegen). */
    public void showQrTab() {
        qrTab.getTabPane().getSelectionModel().select(qrTab);
    }

    public void show(TrainingDetail d) {
        current = d;
        setDisable(d == null);
        updating = true;
        try {
            if (d == null) {
                title.setText("Kein Training ausgewählt");
                meta.setText("");
                description.setText("");
                active.setSelected(false);
                registrations.getItems().clear();
                regTab.setText("Registrierungen");
                showCode(null);
                return;
            }
            var t = d.training();
            title.setText(t.title());
            meta.setText(subtitle(t.startsAt(), t.location()));
            description.setText(Fmt.nz(t.description()));
            description.setManaged(!description.getText().isEmpty());
            active.setSelected(t.active());
            registrations.getItems().setAll(d.registrations());
            regTab.setText("Registrierungen (" + d.registrations().size() + ")");
            showCode(d.code());
        } finally {
            updating = false;
        }
    }

    private void showCode(Code code) {
        boolean has = code != null && code.url() != null;
        qrImage.setImage(has ? new Image(new ByteArrayInputStream(QrCode.png(code.url(), 560))) : null);
        qrUrl.setText(has ? code.url() : "");
        qrUrl.setVisible(has);
        exportQr.setDisable(!has);
        copyLink.setDisable(!has);
        newCode.setText(has ? "Neuen QR-Code erzeugen …" : "QR-Code erzeugen");
        if (!has) {
            qrInfo.setText("Für dieses Training gibt es noch keinen QR-Code (z. B. weil es vor der Umstellung "
                    + "angelegt wurde). Mit „QR-Code erzeugen“ einen anlegen.");
            qrInfo.setStyle("");
            return;
        }
        LocalDateTime exp = Fmt.parse(code.expiresAt());
        boolean expired = exp != null && exp.isBefore(LocalDateTime.now());
        qrInfo.setText(exp == null ? "Gültig ohne Ablaufdatum"
                : (expired ? "Abgelaufen seit " : "Gültig bis ") + Fmt.dateTime(exp) + " Uhr");
        qrInfo.setStyle(expired ? "-fx-text-fill: #b91c1c; -fx-font-weight: bold;" : "-fx-font-weight: bold;");
    }

    /** "01.12.2026 09:00 Uhr · München" für Kopfzeile und QR-Plakat. */
    public static String subtitle(String startsAt, String location) {
        String when = Fmt.dateTime(startsAt);
        return String.join("  ·  ", List.of(
                when.isEmpty() ? "ohne Datum" : when + " Uhr",
                Fmt.nz(location).isEmpty() ? "ohne Ort" : location));
    }

    private static <S> TableColumn<S, String> col(String name, Function<S, String> getter, double width) {
        TableColumn<S, String> c = new TableColumn<>(name);
        c.setCellValueFactory(cd -> new ReadOnlyStringWrapper(Fmt.nz(getter.apply(cd.getValue()))));
        c.setPrefWidth(width);
        return c;
    }
}
