package de.mindarchitects.tlm;

import de.mindarchitects.tlm.update.UpdateService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Öffnet einen Mail-Entwurf mit Anlage im Mailprogramm des Systems, ohne ihn zu senden.
 * Der Nutzer kann Empfänger und Text dort noch ändern und sendet selbst.
 * <ul>
 *   <li>macOS: Apple Mail per AppleScript (neue Nachricht mit Betreff, Text, Empfänger und Anlage).
 *       Klappt das nicht (z. B. Automation nicht erlaubt), öffnet Mail nur die Anlage in einer neuen Nachricht.</li>
 *   <li>Windows: .eml-Datei mit „X-Unsent: 1“; Outlook öffnet sie als bearbeitbaren Entwurf.</li>
 *   <li>Linux: xdg-email (Thunderbird, Evolution …), sonst wie Windows.</li>
 * </ul>
 */
public final class MailDraft {
    private MailDraft() {}

    /** COMPLETE: Entwurf mit Betreff/Text. ATTACHMENT_ONLY: nur Anlage, Betreff und Text muss der Nutzer eintragen. */
    public enum Result { COMPLETE, ATTACHMENT_ONLY }

    private static final String APPLE_SCRIPT = """
            on run argv
                set theSubject to item 1 of argv
                set theBody to item 2 of argv
                set theTo to item 3 of argv
                set theFile to (POSIX file (item 4 of argv)) as alias
                tell application "Mail"
                    set m to make new outgoing message with properties {subject:theSubject, content:theBody & return & return, visible:true}
                    tell m
                        if theTo is not "" then make new to recipient at end of to recipients with properties {address:theTo}
                        make new attachment with properties {file name:theFile} at after the last paragraph
                    end tell
                    activate
                end tell
            end run
            """;

    public static Result open(String to, String subject, String body, Path attachment) throws IOException {
        String recipient = to == null ? "" : to.strip();
        switch (UpdateService.os()) {
            case MAC -> {
                if (run(List.of("osascript", "-e", APPLE_SCRIPT, subject, body, recipient,
                        attachment.toAbsolutePath().toString()), 120)) {
                    return Result.COMPLETE;
                }
                if (run(List.of("open", "-a", "Mail", attachment.toAbsolutePath().toString()), 30)) {
                    return Result.ATTACHMENT_ONLY;
                }
                throw new IOException("Apple Mail konnte nicht geöffnet werden.");
            }
            case LINUX -> {
                List<String> cmd = new java.util.ArrayList<>(List.of("xdg-email", "--subject", subject, "--body", body,
                        "--attach", attachment.toAbsolutePath().toString()));
                if (!recipient.isEmpty()) {
                    cmd.add(recipient);
                }
                if (run(cmd, 30)) {
                    return Result.COMPLETE;
                }
                openFile(writeEml(recipient, subject, body, attachment));
                return Result.COMPLETE;
            }
            default -> {
                openFile(writeEml(recipient, subject, body, attachment));
                return Result.COMPLETE;
            }
        }
    }

    /** Entwurf als .eml (MIME) mit X-Unsent, damit Outlook ihn zum Bearbeiten öffnet. */
    static Path writeEml(String to, String subject, String body, Path attachment) throws IOException {
        String boundary = "tlm-" + Long.toHexString(System.nanoTime());
        String fileName = attachment.getFileName().toString();
        String asciiName = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        StringBuilder eml = new StringBuilder();
        eml.append("X-Unsent: 1\r\n");
        if (!to.isEmpty()) {
            eml.append("To: ").append(to).append("\r\n");
        }
        eml.append("Subject: ").append(encodeHeader(subject)).append("\r\n");
        eml.append("MIME-Version: 1.0\r\n");
        eml.append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append("\"\r\n\r\n");
        eml.append("--").append(boundary).append("\r\n");
        eml.append("Content-Type: text/plain; charset=UTF-8\r\n");
        eml.append("Content-Transfer-Encoding: base64\r\n\r\n");
        eml.append(base64Lines(body.getBytes(StandardCharsets.UTF_8))).append("\r\n");
        eml.append("--").append(boundary).append("\r\n");
        eml.append("Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet; name=\"")
                .append(asciiName).append("\"\r\n");
        eml.append("Content-Transfer-Encoding: base64\r\n");
        eml.append("Content-Disposition: attachment; filename=\"").append(asciiName).append("\"; filename*=UTF-8''")
                .append(java.net.URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"))
                .append("\r\n\r\n");
        eml.append(base64Lines(Files.readAllBytes(attachment))).append("\r\n");
        eml.append("--").append(boundary).append("--\r\n");

        Path file = attachment.resolveSibling(fileName.replaceFirst("\\.xlsx$", "") + ".eml");
        Files.writeString(file, eml, StandardCharsets.US_ASCII);
        file.toFile().deleteOnExit();
        return file;
    }

    private static String encodeHeader(String s) {
        return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)) + "?=";
    }

    private static String base64Lines(byte[] data) {
        return Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(data);
    }

    private static void openFile(Path file) throws IOException {
        List<String> cmd = switch (UpdateService.os()) {
            case WINDOWS -> List.of("rundll32", "url.dll,FileProtocolHandler", file.toAbsolutePath().toString());
            case MAC -> List.of("open", file.toAbsolutePath().toString());
            default -> List.of("xdg-open", file.toAbsolutePath().toString());
        };
        if (!run(cmd, 30)) {
            throw new IOException("Kein Mailprogramm gefunden. Der Entwurf liegt hier: " + file);
        }
    }

    /** Führt einen Befehl aus; true bei Exit-Code 0. */
    private static boolean run(List<String> cmd, int timeoutSeconds) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroy();
                return false;
            }
            return p.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
