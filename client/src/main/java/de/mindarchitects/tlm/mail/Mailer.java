package de.mindarchitects.tlm.mail;

import de.mindarchitects.tlm.settings.Settings;
import de.mindarchitects.tlm.settings.Settings.SmtpSecurity;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import jakarta.activation.DataHandler;

import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Properties;

/** Versendet Mails per SMTP. Eine Verbindung wird für mehrere Mails wiederverwendet. */
public final class Mailer implements AutoCloseable {

    private final Session session;
    private final Transport transport;
    private final InternetAddress from;

    public Mailer(Settings s, String password) throws MessagingException {
        if (s.smtpHost().isBlank() || s.fromAddress().isBlank()) {
            throw new MessagingException("SMTP-Server und Absenderadresse in den Einstellungen angeben.");
        }
        Properties p = new Properties();
        p.put("mail.smtp.host", s.smtpHost());
        p.put("mail.smtp.port", String.valueOf(s.smtpPort()));
        p.put("mail.smtp.auth", String.valueOf(!s.smtpUser().isBlank()));
        p.put("mail.smtp.connectiontimeout", "20000");
        p.put("mail.smtp.timeout", "30000");
        p.put("mail.smtp.writetimeout", "30000");
        if (s.smtpSecurity() == SmtpSecurity.STARTTLS) {
            p.put("mail.smtp.starttls.enable", "true");
            p.put("mail.smtp.starttls.required", "true");
        } else if (s.smtpSecurity() == SmtpSecurity.SSL) {
            p.put("mail.smtp.ssl.enable", "true");
        }
        if (s.smtpSecurity() != SmtpSecurity.NONE) {
            p.put("mail.smtp.ssl.checkserveridentity", "true");
        }
        session = Session.getInstance(p);
        try {
            from = new InternetAddress(s.fromAddress(), s.fromName().isBlank() ? null : s.fromName(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new MessagingException("Absender ungültig", e);
        }
        transport = session.getTransport("smtp");
        String where = s.smtpHost() + ":" + s.smtpPort() + " (" + s.smtpSecurity() + ")";
        try {
            if (s.smtpUser().isBlank()) {
                transport.connect();
            } else {
                if (password == null || password.isEmpty()) {
                    throw new MessagingException("Kein SMTP-Passwort eingetragen (Einstellungen).");
                }
                transport.connect(s.smtpHost(), s.smtpPort(), s.smtpUser(), password);
            }
        } catch (AuthenticationFailedException e) {
            throw new MessagingException("Anmeldung am Mailserver abgelehnt – Benutzer oder Passwort ist falsch.\n"
                    + "Benutzer: " + s.smtpUser() + "\nServer: " + where
                    + "\nAntwort des Servers: " + nz(e.getMessage()), e);
        } catch (MessagingException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Kein SMTP-Passwort")) {
                throw e;
            }
            throw new MessagingException("Verbindung zum Mailserver " + where + " fehlgeschlagen: "
                    + rootMessage(e) + "\nPort und Sicherheit prüfen (465 = SSL, 587 = STARTTLS).", e);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s.strip();
    }

    private static String rootMessage(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) {
            r = r.getCause();
        }
        return r.getClass().getSimpleName() + (r.getMessage() != null ? ": " + r.getMessage() : "");
    }

    public InternetAddress from() {
        return from;
    }

    /** Sendet eine Mail mit Text- und HTML-Teil; qrPng wird als Inline-Bild (cid:qrcode) angehängt. */
    public void send(String to, MailTemplate.Rendered mail, byte[] qrPng) throws MessagingException {
        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(from);
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, true));
        msg.setSubject(mail.subject(), "UTF-8");
        msg.setSentDate(new Date());

        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(mail.text(), "UTF-8");
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setText(mail.html(), "UTF-8", "html");
        MimeMultipart alternative = new MimeMultipart("alternative", textPart, htmlPart);

        if (qrPng == null) {
            msg.setContent(alternative);
        } else {
            MimeBodyPart altWrapper = new MimeBodyPart();
            altWrapper.setContent(alternative);
            MimeBodyPart image = new MimeBodyPart();
            image.setDataHandler(new DataHandler(new ByteArrayDataSource(qrPng, "image/png")));
            image.setContentID("<" + MailTemplate.QR_CID + ">");
            image.setDisposition(MimeBodyPart.INLINE);
            image.setFileName("qrcode.png");
            msg.setContent(new MimeMultipart("related", altWrapper, image));
        }
        msg.saveChanges();
        transport.sendMessage(msg, msg.getAllRecipients());
    }

    @Override
    public void close() {
        try {
            transport.close();
        } catch (MessagingException ignored) {
            // nichts zu tun
        }
    }
}
