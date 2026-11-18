#!/bin/bash
# Erzeugt lokal ein macOS-Disk-Image: client/target/dist/TrainingListmanager-<version>.dmg
#
# Aufruf über Maven (NetBeans-Aktion „macOS-Installer (dmg) erstellen“):
#   mvn -f client/pom.xml -Pdmg clean package
#
# Anders als im GitHub-Workflow braucht das lokale JDK kein eingebautes JavaFX:
# Die JavaFX-Jars (macOS) kopiert das Maven-Profil „dmg“ nach target/libs, sie laufen im Klassenpfad.
# Für Releases bleibt der GitHub-Build maßgeblich.
set -euo pipefail

cd "$(dirname "$0")/.."   # client/
VERSION="${1:?Aufruf: build-dmg.sh <version>}"
VERSION="${VERSION%-SNAPSHOT}"

if [ "$(uname)" != "Darwin" ]; then
    echo "Ein dmg lässt sich nur unter macOS erzeugen." >&2
    exit 1
fi
if ! ls target/libs/javafx-graphics-*-mac-*.jar > /dev/null 2>&1; then
    echo "JavaFX-Jars fehlen in target/libs – bitte mit Profil dmg bauen: mvn -Pdmg clean package" >&2
    exit 1
fi

# Auf einer Netzwerkfreigabe (SMB) kann codesign nicht signieren – deshalb im lokalen Temp-Ordner bauen
WORK="$(mktemp -d -t tlm-dmg)"
trap 'rm -rf "$WORK"' EXIT
rm -rf target/dist
mkdir -p target/dist
echo "Erzeuge App (Version $VERSION) …"
jpackage --type app-image \
    --name TrainingListmanager \
    --app-version "$VERSION" \
    --vendor "mind-architects" \
    --description "Trainings verwalten und QR-Codes zur Registrierung erzeugen" \
    --input "$(pwd)/target/libs" \
    --main-jar training-listmanager.jar \
    --main-class de.mindarchitects.tlm.Launcher \
    --add-modules java.base,java.desktop,java.logging,java.naming,java.net.http,java.prefs,java.security.sasl,java.sql,java.xml,jdk.net,jdk.security.auth,jdk.unsupported,jdk.localedata,jdk.charsets \
    --jlink-options "--strip-debug --no-man-pages --no-header-files" \
    --java-options "-Dfile.encoding=UTF-8 --enable-native-access=ALL-UNNAMED" \
    --java-options "-Dapp.version=$VERSION" \
    --mac-package-name "TL Manager" \
    --icon "$(pwd)/packaging/TrainingListmanager.icns" \
    --dest "$WORK/app"

# Hinweis für die Steuerung von Apple Mail (Mail-Entwurf), danach neu ad-hoc signieren
APP="$WORK/app/TrainingListmanager.app"
/usr/libexec/PlistBuddy -c "Add :NSAppleEventsUsageDescription string 'TrainingListmanager öffnet die Anwesenheitsliste als Entwurf in Apple Mail.'" "$APP/Contents/Info.plist"
codesign --force --deep --sign - "$APP"

echo "Erzeuge Disk-Image …"
jpackage --type dmg --app-image "$APP" --name TrainingListmanager --app-version "$VERSION" \
    --vendor "mind-architects" --dest "$WORK/dist"
cp "$WORK"/dist/*.dmg target/dist/

echo
echo "Fertig: $(pwd)/target/dist/TrainingListmanager-$VERSION.dmg"
