package de.mindarchitects.tlm.ui;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Fenster mit Fortschrittsbalken und Protokoll für längere Abläufe (z. B. Mailversand). */
public final class ProgressDialog {
    private ProgressDialog() {}

    public interface Progress {
        void log(String line);

        void progress(long done, long total);

        boolean isCancelled();
    }

    @FunctionalInterface
    public interface Job {
        void run(Progress p) throws Exception;
    }

    public static void run(Window owner, String title, Job job, Runnable onFinished) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(title);

        ProgressBar bar = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        bar.setMaxWidth(Double.MAX_VALUE);
        TextArea logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        VBox.setVgrow(logArea, Priority.ALWAYS);

        Button cancel = new Button("Abbrechen");
        Button close = new Button("Schließen");
        close.setDisable(true);
        close.setDefaultButton(true);
        HBox buttons = new HBox(8, cancel, close);
        buttons.setStyle("-fx-alignment: center-right;");

        VBox root = new VBox(10, bar, logArea, buttons);
        root.setPadding(new Insets(14));
        stage.setScene(new Scene(root, 560, 380));

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                job.run(new Progress() {
                    @Override
                    public void log(String line) {
                        Platform.runLater(() -> logArea.appendText(line + "\n"));
                    }

                    @Override
                    public void progress(long done, long total) {
                        updateProgress(done, total);
                    }

                    @Override
                    public boolean isCancelled() {
                        return isCancelledTask() || Thread.currentThread().isInterrupted();
                    }
                });
                return null;
            }

            private boolean isCancelledTask() {
                return super.isCancelled();
            }
        };
        bar.progressProperty().bind(task.progressProperty());

        Runnable finish = () -> {
            bar.progressProperty().unbind();
            bar.setProgress(1);
            cancel.setDisable(true);
            close.setDisable(false);
            if (onFinished != null) {
                onFinished.run();
            }
        };
        task.setOnSucceeded(e -> finish.run());
        task.setOnCancelled(e -> {
            logArea.appendText("Abgebrochen.\n");
            finish.run();
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            logArea.appendText("\nFEHLER: " + (ex.getMessage() != null ? ex.getMessage() : ex.toString()) + "\n");
            finish.run();
        });

        cancel.setOnAction(e -> task.cancel(false));
        close.setOnAction(e -> stage.close());
        stage.setOnCloseRequest(e -> {
            if (task.isRunning()) {
                e.consume();
            }
        });

        Thread t = new Thread(task, "tlm-job");
        t.setDaemon(true);
        t.start();
        stage.show();
    }
}
