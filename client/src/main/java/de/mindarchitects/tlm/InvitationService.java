package de.mindarchitects.tlm;

import de.mindarchitects.tlm.api.ApiClient;
import de.mindarchitects.tlm.api.Model.CreateInvitationsResult;
import de.mindarchitects.tlm.api.Model.CreatedInvitation;
import de.mindarchitects.tlm.mail.MailTemplate;
import de.mindarchitects.tlm.mail.Mailer;
import de.mindarchitects.tlm.mail.QrCode;
import de.mindarchitects.tlm.settings.Secrets;
import de.mindarchitects.tlm.settings.Settings;
import de.mindarchitects.tlm.ui.ProgressDialog.Progress;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Ablauf: Einladungen auf dem Server anlegen, Mails mit QR-Code versenden, Versand melden. */
public final class InvitationService {

    /** Angaben zum Training für die Mail-Platzhalter. */
    public record TrainingInfo(String title, String date, String location) {}

    private final Settings settings;
    private final Secrets secrets;

    public InvitationService(Settings settings, Secrets secrets) {
        this.settings = settings;
        this.secrets = secrets;
    }

    public ApiClient api() {
        return new ApiClient(settings.apiUrl(), secrets.get(Secrets.API_KEY));
    }

    /** Neues Training anlegen und alle Adressen einladen. */
    public void createTrainingAndInvite(String title, String description, LocalDateTime startsAt, String location,
                                        List<String> emails, String subject, String body, Progress p) throws Exception {
        ApiClient api = api();
        p.log("Lege Training „" + title + "“ an …");
        long id = api.createTraining(title, description, startsAt, location);
        p.log("Training angelegt (ID " + id + ").");
        invite(id, new TrainingInfo(title, Fmt.dateTime(startsAt), location), emails, subject, body, p);
    }

    /** Weitere Adressen zu einem bestehenden Training einladen. */
    public void invite(long trainingId, TrainingInfo info, List<String> emails, String subject, String body,
                       Progress p) throws Exception {
        ApiClient api = api();
        p.log("Erzeuge " + emails.size() + " Einladung(en) …");
        CreateInvitationsResult result = api.createInvitations(trainingId, emails);
        result.skipped().forEach(s -> p.log("Übersprungen: " + s.email() + " (" + s.reason() + ")"));
        send(api, info, result.created(), subject, body, p);
    }

    /** Für unbenutzte Einladungen einen neuen Link erzeugen und erneut senden. */
    public void resend(TrainingInfo info, List<Long> invitationIds, String subject, String body, Progress p)
            throws Exception {
        ApiClient api = api();
        List<CreatedInvitation> renewed = new ArrayList<>();
        for (long id : invitationIds) {
            try {
                renewed.add(api.renewInvitation(id));
            } catch (Exception e) {
                p.log("Einladung " + id + " nicht erneuerbar: " + e.getMessage());
            }
        }
        send(api, info, renewed, subject, body, p);
    }

    private void send(ApiClient api, TrainingInfo info, List<CreatedInvitation> invitations,
                      String subject, String body, Progress p) throws Exception {
        if (invitations.isEmpty()) {
            p.log("Keine Mails zu versenden.");
            return;
        }
        p.log("Verbinde mit " + settings.smtpHost() + " …");
        List<Long> sent = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        try (Mailer mailer = new Mailer(settings, secrets.get(Secrets.SMTP_PASSWORD))) {
            int i = 0;
            for (CreatedInvitation inv : invitations) {
                if (p.isCancelled()) {
                    p.log("Abgebrochen.");
                    break;
                }
                p.progress(i++, invitations.size());
                try {
                    var mail = MailTemplate.render(subject, body, info.title(), info.date(), info.location(), inv.url());
                    mailer.send(inv.email(), mail, QrCode.png(inv.url(), 440));
                    sent.add(inv.id());
                    p.log("✓ " + inv.email());
                } catch (Exception e) {
                    failed.add(inv.email());
                    p.log("✗ " + inv.email() + ": " + e.getMessage());
                }
            }
            p.progress(invitations.size(), invitations.size());
        } finally {
            if (!sent.isEmpty()) {
                api.markSent(sent);
            }
        }
        p.log("");
        p.log(sent.size() + " gesendet, " + failed.size() + " fehlgeschlagen.");
        if (!failed.isEmpty()) {
            p.log("Fehlgeschlagene Einladungen können im Tab „Einladungen“ erneut gesendet werden.");
        }
    }
}
