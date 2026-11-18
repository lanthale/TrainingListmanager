# TrainingListmanager

Trainings verwalten und Teilnehmer per QR-Code registrieren lassen.

- **Registrierungsseite (PHP):** Teilnehmer scannen den QR-Code des Trainings mit dem Handy und registrieren sich mit Vorname, Nachname, Job-Position und Händler. Dazu müssen sie dem Datenschutzhinweis zustimmen (DSGVO). Ohne gültigen QR-Code ist keine Registrierung möglich.
- **Desktop-Client (JavaFX):** Trainings anlegen, den QR-Code als PNG exportieren, Registrierungen einsehen, die Anwesenheitsliste als Excel per Mail-Entwurf weiterleiten und das Training danach mit allen Teilnehmerdaten löschen. Der Client spricht über eine JSON-API (`api.php`) mit dem Server, weil die IONOS-Datenbank von außen nicht erreichbar ist.

## Aufbau

```
config.example.php           Vorlage für config.php (DB-Zugang, API-Schlüssel-Hash, Basis-URL)
config.php                   echte Konfiguration mit Passwort – NICHT im Git, nur auf dem Server
sql/schema.sql               Tabellen: trainings, invitations, registrations
sql/migrations/              Änderungen für bereits bestehende Datenbanken
public/                      Webroot: index.php (Registrierung), api.php (für den Client), style.css
src/                         DB-Verbindung, Einladungs- und API-Logik
tools/create_invitation.php  CLI: neuen QR-Code für ein Training anlegen, gibt die URL aus
client/                      JavaFX-Desktop-Client (Maven)
.github/workflows/client.yml baut Installer für macOS, Windows und Linux
```

## Server einrichten (IONOS-Webspace)

1. **Schema einspielen:** `sql/schema.sql` in phpMyAdmin in der Datenbank `dbs16347872` ausführen.
   Wurde das Schema schon früher eingespielt, stattdessen die noch fehlenden Migrationen aus `sql/migrations/` der Reihe nach ausführen:
   - `002_invitations_sent_at.sql`
   - `003_invitation_expiry.sql`
   - `004_training_code_consent.sql` (QR-Code pro Training, Einwilligung speichern)
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
2. Die Verbindung mit **API testen** prüfen.
3. Optional unter *Anwesenheitsliste per Mail weiterleiten* den Empfänger und die Mail-Vorlage anpassen.

Der API-Schlüssel liegt im Schlüsselbund des Betriebssystems: macOS-Keychain, Windows-Anmeldeinformationsverwaltung oder Linux Secret Service. Alle anderen Einstellungen speichert Java in den Benutzer-Einstellungen.

### Bedienung
1. **Neues Training:** Titel, Datum und Uhrzeit, Ort und Beschreibung eingeben und *Anlegen* klicken. Der Server erzeugt dabei den QR-Code.
2. **QR-Code:** Im Tab *QR-Code* mit **Als PNG exportieren …** ein Bild mit Titel, Datum und QR-Code speichern, zum Ausdrucken oder für den Beamer. Alle Teilnehmer scannen denselben Code.
   Der Code gilt bis zum Ende des Tages nach Trainingsbeginn, ohne Trainingsdatum 90 Tage. **Neuen QR-Code erzeugen …** macht den bisherigen sofort ungültig, z. B. wenn ein Foto des Codes weitergegeben wurde.
3. **Registrierungen:** Wer sich registriert hat, steht mit Vorname, Nachname, Job-Position, Händler und Zeitpunkt in der Liste.
4. **Per Mail weiterleiten …:** erzeugt die Anwesenheitsliste als Excel und öffnet sie als **Entwurf** im Mailprogramm. Nichts wird automatisch gesendet: Empfänger und Text lassen sich dort noch ändern.
   - macOS: Apple Mail. Beim ersten Mal fragt macOS, ob TrainingListmanager „Mail“ steuern darf. Ohne diese Erlaubnis öffnet Mail nur eine neue Nachricht mit der Anlage, der Text liegt dann in der Zwischenablage. Die Erlaubnis lässt sich unter *Systemeinstellungen → Datenschutz & Sicherheit → Automation* nachholen.
   - Windows: Die Mail öffnet sich als Entwurf in Outlook (klassisch).
   - Linux: über `xdg-email`, z. B. in Thunderbird.

   Platzhalter in Betreff und Text (Einstellungen): `{titel}`, `{datum}`, `{ort}`, `{anzahl}`.
5. **Training löschen …:** löscht das Training mit allen Registrierungen und QR-Codes endgültig vom Server. Das ist der Schritt „nach der Weiterleitung gelöscht“ aus dem Datenschutzhinweis.

- **Excel-Export …:** speichert dieselbe Liste als `.xlsx` (sortiert nach Nachname, mit Filter und dem Zeitpunkt der Einwilligung) und öffnet sie.
- **Registrierung offen:** Ist der Haken entfernt, ist keine Registrierung mehr möglich, auch nicht mit gültigem Code.

### Entwickeln
JDK 27 und Maven werden benötigt.

```
mvn -f client/pom.xml javafx:run      # starten
mvn -f client/pom.xml package         # Jars nach client/target/libs
```

**dmg lokal bauen (macOS):** In NetBeans Rechtsklick auf das Projekt → *Run Maven* → **macOS-Installer (dmg) erstellen**, oder:

```
mvn -f client/pom.xml -Pdmg clean package
```

Das Ergebnis liegt in `client/target/dist/TrainingListmanager-<version>.dmg`. Das lokale JDK braucht kein eingebautes JavaFX: Das Profil `dmg` packt die JavaFX-Jars mit ein, das Skript `client/packaging/build-dmg.sh` baut wie der GitHub-Workflow. Gebaut wird in einem lokalen Temp-Ordner, weil `codesign` auf Netzwerkfreigaben nicht signieren kann. Für Releases bleibt der GitHub-Build maßgeblich.

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
| `trainings` | GET | – | Liste mit `registered` |
| `training` | GET | `id` | Training, aktueller QR-Code (`code`) und Registrierungen |
| `create_training` | POST | `title`, `description`, `starts_at` (`YYYY-MM-DD HH:MM:SS`), `location` | `{id, code}` |
| `set_active` | POST | `id`, `active` | `{ok}` |
| `create_code` | POST | `training_id` | `{code}`; der bisherige Code wird ungültig |
| `delete_training` | POST | `id` | `{ok}`; löscht Training, Codes und Registrierungen |

`code` ist `{url, expires_at, created_at}`. Die Tokens erzeugt der Server. Gesucht wird über den SHA-256-Hash; der Klartext steht zusätzlich in der Datenbank, damit jeder Client das PNG jederzeit neu exportieren kann. Der Code ist ohnehin zum Aushängen gedacht und erlaubt nur Registrierungen für dieses eine Training.

## Sicherheit

- Eine Registrierung ist nur mit gültigem, nicht abgelaufenem QR-Code möglich, und das Training muss aktiv sein. Pro Code sind höchstens 500 Registrierungen möglich.
- Die Registrierung läuft in einer Transaktion mit `SELECT … FOR UPDATE`.
- **Datenschutz (DSGVO):** Die Seite zeigt vor dem Absenden einen Datenschutzhinweis. Ohne Häkchen bei der Einwilligung wird nichts gespeichert; der Zeitpunkt der Einwilligung steht in `registrations.consent_at` und in der Excel-Liste. Nach der Weiterleitung löscht *Training löschen …* alle Teilnehmerdaten.
- Die Seite nutzt CSRF-Schutz, Prepared Statements und HTML-Escaping. DB-Fehler gehen ins Server-Log und werden nicht angezeigt.
- **Ablauf der QR-Codes:** Ein Code läuft automatisch am Ende des Tages nach Trainingsbeginn ab, ohne Trainingsdatum nach 90 Tagen. Der Client zeigt das Ablaufdatum im Tab *QR-Code*.
- **Server-Header:** Die Seite sendet HSTS, eine strikte Content-Security-Policy (nur eigene Ressourcen, kein Einbetten), `nosniff` und `Referrer-Policy: no-referrer`.
- **API-Schlüssel:** Die API ist gesperrt, solange in `config.php` kein `api_key_hash` steht. Der Schlüssel hat 256 Bit. Ein neu erzeugter Schlüssel macht den alten ungültig, sobald sein Hash in `config.php` steht.
  **Geht ein Rechner mit dem Client verloren:** sofort einen neuen Schlüssel erzeugen und den Hash in `config.php` ersetzen.
- **HTTPS-Pflicht im Client:** Der Client lehnt API-URLs ohne `https://` ab, damit der Schlüssel nie unverschlüsselt übertragen wird.
- **Build:** Die GitHub Actions sind auf feste Commits festgelegt. Nur der Release-Job hat Schreibrechte.
- **Konto:** Wer das GitHub-Konto kontrolliert, kann Updates an alle Clients verteilen. Für das Konto deshalb unbedingt die Zwei-Faktor-Anmeldung aktivieren.
