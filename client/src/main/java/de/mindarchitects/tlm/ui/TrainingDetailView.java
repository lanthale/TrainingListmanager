package de.mindarchitects.tlm.ui;

import de.mindarchitects.tlm.Fmt;
import de.mindarchitects.tlm.api.Model.Invitation;
import de.mindarchitects.tlm.api.Model.Registration;
import de.mindarchitects.tlm.api.Model.TrainingDetail;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Rechte Seite: Kopfdaten, Aktiv-Schalter, Registrierungen und Einladungen eines Trainings. */
public final class TrainingDetailView extends VBox {

    private final Label title = new Label();
    private final Label meta = new Label();
    private final Label description = new Label();
    private final CheckBox active = new CheckBox("Registrierung offen");
    private final TableView<Registration> registrations = new TableView<>();
    private final TableView<Invitation> invitations = new TableView<>();
    private final Tab regTab = new Tab();
    private final Tab invTab = new Tab();
    private final Button inviteMore = new Button("Weitere einladen …");
    private final Button resend = new Button("Erneut senden …");

    private boolean updating;
    private TrainingDetail current;

    public TrainingDetailView(Consumer<Boolean> onActiveChanged, Runnable onInviteMore,
                              Consumer<List<Invitation>> onResend) {
        super(10);
        setPadding(new Insets(14));

        title.setStyle("-fx-font-size: 1.5em; -fx-font-weight: bold;");
        title.setWrapText(true);
        meta.setStyle("-fx-text-fill: -fx-mid-text-color;");
        description.setWrapText(true);
        active.selectedProperty().addListener((o, a, b) -> {
            if (!updating) onActiveChanged.accept(b);
        });

        registrations.getColumns().addAll(List.of(
                col("Vorname", Registration::firstName, 120),
                col("Nachname", Registration::lastName, 140),
                col("Job-Position", Registration::jobPosition, 150),
                col("Händler", Registration::dealer, 170),
                col("E-Mail", Registration::email, 200),
                col("Registriert am", r -> Fmt.dateTime(r.createdAt()), 130)));
        registrations.setPlaceholder(new Label("Noch keine Registrierungen"));
        registrations.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        invitations.getColumns().addAll(List.of(
                col("E-Mail", Invitation::email, 260),
                col("Gesendet", i -> Fmt.dateTime(i.sentAt()), 130),
                col("Registriert", i -> Fmt.dateTime(i.usedAt()), 130)));
        invitations.setPlaceholder(new Label("Keine Einladungen"));
        invitations.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        invitations.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        inviteMore.setOnAction(e -> onInviteMore.run());
        resend.setOnAction(e -> onResend.accept(List.copyOf(invitations.getSelectionModel().getSelectedItems())));
        resend.disableProperty().bind(invitations.getSelectionModel().selectedItemProperty().isNull());
        Label resendHint = new Label("Mehrfachauswahl möglich; bereits registrierte werden übersprungen.");
        resendHint.setStyle("-fx-text-fill: -fx-mid-text-color; -fx-font-size: 0.9em;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox invButtons = new HBox(8, inviteMore, resend, spacer, resendHint);

        VBox invBox = new VBox(8, invitations, invButtons);
        invBox.setPadding(new Insets(8, 0, 0, 0));
        VBox.setVgrow(invitations, Priority.ALWAYS);
        VBox regBox = new VBox(registrations);
        regBox.setPadding(new Insets(8, 0, 0, 0));
        VBox.setVgrow(registrations, Priority.ALWAYS);

        regTab.setContent(regBox);
        invTab.setContent(invBox);
        TabPane tabs = new TabPane(regTab, invTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        getChildren().addAll(title, meta, description, active, tabs);
        show(null);
    }

    public TrainingDetail current() {
        return current;
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
                invitations.getItems().clear();
                regTab.setText("Registrierungen");
                invTab.setText("Einladungen");
                return;
            }
            var t = d.training();
            title.setText(t.title());
            String when = Fmt.dateTime(t.startsAt());
            meta.setText(String.join("  ·  ", List.of(
                    when.isEmpty() ? "ohne Datum" : when + " Uhr",
                    Fmt.nz(t.location()).isEmpty() ? "ohne Ort" : t.location())));
            description.setText(Fmt.nz(t.description()));
            description.setManaged(!description.getText().isEmpty());
            active.setSelected(t.active());
            registrations.getItems().setAll(d.registrations());
            invitations.getItems().setAll(d.invitations());
            regTab.setText("Registrierungen (" + d.registrations().size() + ")");
            long sent = d.invitations().stream().filter(i -> i.sentAt() != null).count();
            invTab.setText("Einladungen (" + d.invitations().size() + ", " + sent + " gesendet)");
        } finally {
            updating = false;
        }
    }

    private static <S> TableColumn<S, String> col(String name, Function<S, String> getter, double width) {
        TableColumn<S, String> c = new TableColumn<>(name);
        c.setCellValueFactory(cd -> new ReadOnlyStringWrapper(Fmt.nz(getter.apply(cd.getValue()))));
        c.setPrefWidth(width);
        return c;
    }
}
