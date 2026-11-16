package de.mindarchitects.tlm.ui;

import org.kordamp.ikonli.materialdesign2.*;

import de.mindarchitects.tlm.settings.Settings;
import de.mindarchitects.tlm.update.AppVersion;
import de.mindarchitects.tlm.update.UpdateService;
import de.mindarchitects.tlm.update.UpdateService.Release;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.Locale;

/** Ablauf „Update verfügbar“: Hinweis mit Änderungen, Download mit Fortschritt, Installer starten. */
public final class UpdateDialog {
    private UpdateDialog() {}

    /**
     * Sucht nach Updates. interactive=true: Meldungen auch bei „aktuell“ und Fehlern
     * (Menübefehl); false: still im Hintergrund (Programmstart), übersprungene Versionen ausgenommen.
     */
    public static void check(Window owner, Settings settings, HostServices host, boolean interactive) {
        UpdateService service = new UpdateService();
        if (!interactive && (AppVersion.isDev() || !settings.checkUpdates())) {
            return;
        }
        Fx.asyncQuiet(() -> service.checkForUpdate(), update -> {
            if (update.isEmpty()) {
                if (interactive) {
                    Fx.info(owner, "Kein Update verfügbar",
                            "Sie verwenden die aktuelle Version " + AppVersion.current() + ".");
                }
                return;
            }
            Release r = update.get();
            if (!interactive && r.version().equals(settings.skippedVersion())) {
                return;
            }
            show(owner, settings, host, service, r);
        }, ex -> {
            if (interactive) {
                Fx.error(owner, "Update-Prüfung fehlgeschlagen", ex);
            }
        });
    }

    private static void show(Window owner, Settings settings, HostServices host, UpdateService service, Release r) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.initOwner(owner);
        a.setTitle("Software-Update");
        a.setHeaderText("Version " + r.version() + " ist verfügbar (installiert: " + AppVersion.current() + ")");

        TextArea notes = Fx.textArea(r.notes().isBlank() ? "Keine Versionshinweise." : r.notes(), 10);
        notes.setEditable(false);
        boolean installable = r.asset() != null;
        String info = installable
                ? "Installer: " + r.asset().name() + " (" + mb(r.asset().size()) + ")"
                : "Für dieses System gibt es keinen Installer. Das Release kann auf GitHub geöffnet werden.";
        Label infoLabel = new Label(info);
        infoLabel.setWrapText(true);
        a.getDialogPane().setContent(new VBox(8, new Label("Änderungen:"), notes, infoLabel));
        a.getDialogPane().setPrefWidth(560);
        a.setResizable(true);

        ButtonType install = new ButtonType(installable ? "Herunterladen & installieren" : "Auf GitHub öffnen",
                ButtonBar.ButtonData.OK_DONE);
        ButtonType skip = new ButtonType("Diese Version überspringen", ButtonBar.ButtonData.LEFT);
        ButtonType later = new ButtonType("Später", ButtonBar.ButtonData.CANCEL_CLOSE);
        a.getButtonTypes().setAll(skip, later, install);
        Fx.decorate(a.getDialogPane().lookupButton(install),
                installable ? MaterialDesignD.DOWNLOAD : MaterialDesignO.OPEN_IN_NEW);
        Fx.decorate(a.getDialogPane().lookupButton(skip), MaterialDesignS.SKIP_NEXT);
        Fx.decorate(a.getDialogPane().lookupButton(later), MaterialDesignC.CLOCK_OUTLINE);

        ButtonType choice = a.showAndWait().orElse(later);
        if (choice == skip) {
            settings.skippedVersion(r.version());
            settings.flush();
        } else if (choice == install) {
            if (!installable) {
                host.showDocument(r.pageUrl());
            } else {
                downloadAndInstall(owner, service, r);
            }
        }
    }

    private static void downloadAndInstall(Window owner, UpdateService service, Release r) {
        Path[] result = new Path[1];
        ProgressDialog.run(owner, "Update " + r.version() + " herunterladen", p -> {
            p.log("Lade " + r.asset().name() + " …");
            result[0] = service.download(r.asset(), p::progress, p::isCancelled);
            p.log("Prüfsumme in Ordnung.");
            p.log("Gespeichert: " + result[0]);
        }, () -> {
            if (result[0] != null) {
                Platform.runLater(() -> install(owner, service, result[0]));
            }
        });
    }

    private static void install(Window owner, UpdateService service, Path file) {
        String howTo = switch (UpdateService.os()) {
            case WINDOWS -> "Der Installer wird gestartet und das Programm beendet. "
                    + "Nach der Installation TrainingListmanager wieder öffnen.";
            case MAC -> "Das Disk-Image wird geöffnet und das Programm beendet. Ziehen Sie TrainingListmanager "
                    + "in den Ordner „Programme“ und bestätigen Sie „Ersetzen“.";
            case LINUX -> "Das Paket wird mit der Softwareverwaltung geöffnet und das Programm beendet. "
                    + "Alternativ: sudo apt install " + file;
            case OTHER -> "Bitte installieren Sie die Datei manuell: " + file;
        };
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, howTo, ButtonType.OK, ButtonType.CANCEL);
        a.initOwner(owner);
        a.setTitle("Software-Update");
        a.setHeaderText("Update installieren");
        if (a.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        try {
            service.launchInstaller(file);
            Platform.exit();
        } catch (Exception e) {
            Fx.error(owner, "Installer konnte nicht gestartet werden", e.getMessage() + "\nDatei: " + file);
        }
    }

    private static String mb(long bytes) {
        return String.format(Locale.GERMANY, "%.1f MB", bytes / 1024.0 / 1024.0);
    }
}
