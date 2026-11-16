# TrainingListmanager

Trainings verwalten und Teilnehmer per persönlichem QR-Code registrieren lassen.

- **Registrierungsseite (PHP):** Teilnehmer öffnen ihren persönlichen Link (QR-Code aus der Einladungs-E-Mail) und registrieren sich mit Vorname, Nachname, Job-Position und Händler. Ohne gültigen QR-Code ist keine Registrierung möglich, und jeder Code funktioniert nur einmal.
- **Desktop-Client (JavaFX):** Trainings und Registrierungen einsehen, neue Trainings anlegen und Einladungs-Mails mit QR-Code versenden. Der Client spricht über eine JSON-API (`api.php`) mit dem Server, weil die IONOS-Datenbank von außen nicht erreichbar ist.

## Aufbau

```
config.example.php           Vorlage für config.php (DB-Zugang, API-Schlüssel-Hash, Basis-URL)
config.php                   echte Konfiguration mit Passwort – NICHT im Git, nur auf dem Server
sql/schema.sql               Tabellen: trainings, invitations, registrations
sql/migrations/              Änderungen für bereits bestehende Datenbanken
public/                      Webroot: index.php (Registrierung), api.php (für den Client), style.css
src/                         DB-Verbindung, Einladungs- und API-Logik
tools/create_invitation.php  CLI: Test-Einladung anlegen, gibt QR-URL aus
client/                      JavaFX-Desktop-Client (Maven)
.github/workflows/client.yml baut Installer für macOS, Windows und Linux
```

## Server einrichten (IONOS-Webspace)

1. **Schema einspielen:** `sql/schema.sql` in phpMyAdmin in der Datenbank `dbs16347872` ausführen.
   Wurde das Schema schon früher eingespielt, stattdessen die noch fehlenden Migrationen aus `sql/migrations/` der Reihe nach ausführen:
   - `002_invitations_sent_at.sql`
   - `003_invitation_expiry.sql`
2. **Dateien hochladen:** den Projektordner ohne `client/` hochladen, auch die `.htaccess`-Dateien.
   Im FTP-Programm dafür versteckte Dateien einblenden.
3. **Webroot:** In der IONOS-Domainverwaltung zeigt `trainings.mind-architects.de` auf den Unterordner `public`.
   Damit sind `config.php`, `src/` und `sql/` nicht über das Web erreichbar.
4. **config.php:** `config.example.php` als `config.php` in den Projektordner kopieren, also neben `public/` und nicht hinein. Dort eintragen:
   - das DB-Passwort
   - den `api_key_hash` (siehe Abschnitt „Client einrichten“)
   - die `base_url`

   Die Datei per SFTP direkt auf dem Server anlegen. Sie ist per `.gitignore` vom Repository ausgeschlossen.
5. Die PHP-Version muss **8.1 oder neuer** sein, mit den Erweiterungen `pdo_mysql` und `mbstring`.

## Desktop-Client

### Installieren
Die fertigen Installer baut GitHub Actions, sobald auf GitHub ein Release veröffentlicht wird. Sie hängen dann am Release unter **Releases**:

| System | Datei |
|---|---|
| macOS (Apple Silicon) | `TrainingListmanager-x.y.z.dmg` |
| Windows | `TrainingListmanager-x.y.z.msi` |
| Linux (Debian/Ubuntu) | `traininglistmanager_x.y.z_amd64.deb` |

Java muss auf den Rechnern nicht installiert sein, die Installer bringen ihre eigene Laufzeit mit.

Die Pakete sind nicht signiert:
- **macOS:** Beim ersten Start mit Rechtsklick → *Öffnen* bestätigen, oder unter *Systemeinstellungen → Datenschutz & Sicherheit* „Trotzdem öffnen“ wählen.
- **Windows:** Bei der SmartScreen-Warnung *Weitere Informationen → Trotzdem ausführen* wählen.

**Release erstellen:**
1. In `client/pom.xml` die `<version>` erhöhen, committen und pushen.
2. Auf GitHub unter **Releases → Draft a new release** einen neuen Tag `vX.Y.Z` anlegen, z. B. `v1.0.1`, auf Branch `main`.
   Versionshinweise eintragen, der Client zeigt sie im Update-Dialog an. Dann **Publish release** klicken.
   Alternativ per Kommandozeile:
   ```
   gh release create v1.0.1 --target main --title "1.0.1" --notes "…"
   ```
3. Erst das Veröffentlichen startet den Build. Der Workflow baut die Installer mit der Version aus dem Tag und hängt sie nach etwa 10 Minuten an das Release. In dieser Zeit sehen Clients das Release schon, bieten aber noch keine Installation an.

Ein Push auf `main` startet keinen Build mehr. Für einen Test-Build ohne Release: **Actions → Client bauen → Run workflow**. Die Installer liegen dann unter *Artifacts*.

### Software-Update
Der Client fragt beim Start im Hintergrund das neueste Release auf GitHub ab (`lanthale/TrainingListmanager`). Ist es neuer als die installierte Version, erscheint ein Dialog mit den Versionshinweisen und drei Möglichkeiten:
- **Herunterladen & installieren:** lädt den passenden Installer (.dmg, .msi oder .deb) über HTTPS und prüft ihn mit der SHA-256-Prüfsumme von GitHub. Passt sie nicht, installiert der Client nichts. Sonst wird der Installer gestartet und der Client beendet:
  - Windows: Das MSI aktualisiert die vorhandene Installation.
  - macOS: Das Disk-Image wird geöffnet. Die App in „Programme“ ziehen und „Ersetzen“ bestätigen.
  - Linux: Das Paket öffnet sich in der Softwareverwaltung.
- **Diese Version überspringen:** Diese Version wird beim Start nicht mehr gemeldet.
- **Später:** Der Dialog schließt sich, beim nächsten Start kommt der Hinweis wieder.

Mit **Nach Updates suchen** in der Werkzeugleiste kann man jederzeit selbst prüfen. Die automatische Prüfung lässt sich in den Einstellungen abschalten. Wird der Client per `mvn javafx:run` gestartet, gilt er als Entwicklungsversion und prüft beim Start nicht automatisch.

### Client einrichten
1. *Einstellungen* öffnen und **Schlüssel erzeugen** klicken.
   Der Client zeigt eine Zeile `'api_key_hash' => '…',`. Diese Zeile in `config.php` auf dem Server eintragen.
   Der Schlüssel selbst bleibt im Schlüsselbund des Rechners, auf dem Server liegt nur sein Hash.
2. SMTP prüfen und das Passwort eintragen. Vorbelegt sind:
   - Server `smtps.udag.de`, Port 465 mit SSL/TLS (alternativ Port 587 mit STARTTLS)
   - Benutzer und Absenderadresse `office@mind-architects.de`
   - Nur das **Passwort** und optional den Absendernamen tragen Sie selbst ein.
3. Die Verbindung mit **API testen** und **Test-Mail senden** prüfen.

API-Schlüssel und SMTP-Passwort liegen im Schlüsselbund des Betriebssystems: macOS-Keychain, Windows-Anmeldeinformationsverwaltung oder Linux Secret Service. Alle anderen Einstellungen speichert Java in den Benutzer-Einstellungen.

### Bedienung
- **Neues Training:** Titel, Datum und Uhrzeit, Ort und Beschreibung eingeben und die E-Mail-Adressen einfügen. Die Adressen dürfen eine pro Zeile oder durch Komma oder Semikolon getrennt sein, auch im Format `Name <mail>`. Dann Betreff und Mailtext prüfen und *Anlegen & senden* klicken.
  Platzhalter im Mailtext: `{titel}`, `{datum}`, `{ort}`, `{link}` und `{qr}`. `{qr}` steht für das QR-Bild. Fehlt dieser Platzhalter, hängt der Client das QR-Bild unten an.
- **Registrierungen:** Wer sich registriert hat, steht mit Vorname, Nachname, Job-Position, Händler, E-Mail und Zeitpunkt in der Liste.
- **Einladungen:** Hier sehen Sie, wann jede Einladung gesendet wurde und ob sich der Teilnehmer registriert hat. Zwei Aktionen sind möglich:
  - *Weitere einladen …* fügt neue Adressen zum Training hinzu.
  - *Erneut senden …* erzeugt für die gewählten, noch unbenutzten Einladungen einen neuen Link. Der alte Link wird dabei ungültig.
- **Excel-Export …:** speichert eine `.xlsx` mit den Blättern „Teilnehmer“ (alle Registrierungen, sortiert nach Nachname, mit Filter) und „Einladungen“ (Status jeder Einladung). Die Datei wird danach geöffnet.
- **Registrierung offen:** Ist der Haken entfernt, ist keine Registrierung mehr möglich, auch nicht mit gültigem Code.

### Entwickeln
JDK 27 und Maven werden benötigt.

```
mvn -f client/pom.xml javafx:run      # starten
mvn -f client/pom.xml package         # Jars nach client/target/libs
```

**App-Icon ändern:** Die Zeichnung steht in `client/packaging/IconGen.java`. Nach einer Änderung neu erzeugen (macOS, wegen `iconutil`):

```
cd client
java -Djava.awt.headless=true packaging/IconGen.java .
iconutil -c icns packaging/icon.iconset -o packaging/TrainingListmanager.icns
```

## API (`public/api.php`)
Jede Anfrage braucht den Header `X-Api-Key: <schlüssel>`. Die Aktion wird über den Parameter `?action=` gewählt, POST-Anfragen schicken einen JSON-Body.

| Aktion | Methode | Parameter / Body | Antwort |
|---|---|---|---|
| `ping` | GET | – | `{ok}` |
| `trainings` | GET | – | Liste mit `invited`, `sent`, `registered` |
| `training` | GET | `id` | Training, Einladungen und Registrierungen |
| `create_training` | POST | `title`, `description`, `starts_at` (`YYYY-MM-DD HH:MM:SS`), `location` | `{id}` |
| `set_active` | POST | `id`, `active` | `{ok}` |
| `create_invitations` | POST | `training_id`, `emails[]` | `{created: [{id,email,url}], skipped}` |
| `renew_invitation` | POST | `id` (unbenutzt) | `{id,email,url}` mit neuem Token |
| `mark_sent` | POST | `ids[]` | `{updated}` |

Die Tokens erzeugt der Server. In der Datenbank liegt nur ihr SHA-256-Hash. Den Klartext-Token enthält einzig die `url` in der Antwort, und er kommt nur in den QR-Code der Mail.

## Sicherheit

- Eine Registrierung ist nur mit gültigem, unbenutztem und nicht abgelaufenem Token möglich, und das Training muss aktiv sein.
- Die Registrierung läuft in einer Transaktion mit `SELECT … FOR UPDATE`. Zusätzlich verhindert ein `UNIQUE`-Index auf `registrations.invitation_id` Doppel-Einträge.
- Die Seite nutzt CSRF-Schutz, Prepared Statements und HTML-Escaping. DB-Fehler gehen ins Server-Log und werden nicht angezeigt.
- **Ablauf von Einladungen:** Einladungen laufen automatisch am Ende des Tages nach Trainingsbeginn ab, ohne Trainingsdatum nach 90 Tagen. Beim erneuten Senden wird das Ablaufdatum neu gesetzt. Der Client zeigt es in der Spalte „Gültig bis“.
- **Server-Header:** Die Seite sendet HSTS, eine strikte Content-Security-Policy (nur eigene Ressourcen, kein Einbetten), `nosniff` und `Referrer-Policy: no-referrer`.
- **API-Schlüssel:** Die API ist gesperrt, solange in `config.php` kein `api_key_hash` steht. Der Schlüssel hat 256 Bit. Ein neu erzeugter Schlüssel macht den alten ungültig, sobald sein Hash in `config.php` steht.
  **Geht ein Rechner mit dem Client verloren:** sofort einen neuen Schlüssel erzeugen und den Hash in `config.php` ersetzen.
- **HTTPS-Pflicht im Client:** Der Client lehnt API-URLs ohne `https://` ab, damit der Schlüssel nie unverschlüsselt übertragen wird.
- **Build:** Die GitHub Actions sind auf feste Commits festgelegt. Nur der Release-Job hat Schreibrechte.
- **Konto:** Wer das GitHub-Konto kontrolliert, kann Updates an alle Clients verteilen. Für das Konto deshalb unbedingt die Zwei-Faktor-Anmeldung aktivieren.
