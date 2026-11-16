package de.mindarchitects.tlm.ui;

import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.stage.Window;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Kleine Helfer für Hintergrund-Tasks und Meldungen. */
public final class Fx {
    private Fx() {}

    /** Führt work im Hintergrund aus und ruft onSuccess im FX-Thread auf; Fehler werden angezeigt. */
    public static <T> void async(Window owner, Callable<T> work, Consumer<T> onSuccess) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> error(owner, "Fehler", task.getException()));
        Thread t = new Thread(task, "tlm-worker");
        t.setDaemon(true);
        t.start();
    }

    public static void error(Window owner, String header, Throwable ex) {
        String msg = ex == null ? "Unbekannter Fehler" : ex.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = ex.getClass().getSimpleName();
        }
        error(owner, header, msg);
    }

    public static void error(Window owner, String header, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.initOwner(owner);
        a.setTitle("TrainingListmanager");
        a.setHeaderText(header);
        a.setContentText(message);
        a.showAndWait();
    }

    public static void info(Window owner, String header, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.initOwner(owner);
        a.setTitle("TrainingListmanager");
        a.setHeaderText(header);
        a.setContentText(message);
        a.showAndWait();
    }

    /** Symbol (Ikonli, Material Design Icons) für Buttons. */
    public static FontIcon icon(Ikon ikon) {
        FontIcon icon = new FontIcon(ikon);
        icon.setIconSize(16);
        return icon;
    }

    /** Button mit Symbol links vom Text. */
    public static Button button(String text, Ikon ikon) {
        Button b = new Button(text, icon(ikon));
        b.setGraphicTextGap(6);
        return b;
    }

    /** Symbol an einen bereits vorhandenen Button (z. B. Dialog-Button) hängen. */
    public static void decorate(Node button, Ikon ikon) {
        if (button instanceof Button b) {
            b.setGraphic(icon(ikon));
            b.setGraphicTextGap(6);
        }
    }

    public static TextArea textArea(String text, int rows) {
        TextArea ta = new TextArea(text);
        ta.setPrefRowCount(rows);
        ta.setWrapText(true);
        return ta;
    }
}
