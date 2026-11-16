import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Ed25519-Signaturen für Release-Dateien (Software-Update).
 *
 * Schlüsselpaar erzeugen (einmalig, lokal):
 *   java client/packaging/SignRelease.java keygen <datei-für-privaten-schlüssel>
 *   → schreibt den privaten Schlüssel (Base64, PKCS#8) in die Datei und gibt den öffentlichen aus.
 *
 * Signieren (im GitHub-Workflow, privater Schlüssel aus der Umgebungsvariable UPDATE_SIGNING_KEY):
 *   java client/packaging/SignRelease.java sign dist/*.dmg dist/*.msi …
 *   → legt neben jede Datei eine <datei>.sig (Base64-Signatur).
 */
public class SignRelease {

    public static void main(String[] args) throws Exception {
        if (args.length >= 2 && args[0].equals("keygen")) {
            keygen(Path.of(args[1]));
        } else if (args.length >= 2 && args[0].equals("sign")) {
            String key = System.getenv("UPDATE_SIGNING_KEY");
            if (key == null || key.isBlank()) {
                System.err.println("UPDATE_SIGNING_KEY ist nicht gesetzt.");
                System.exit(2);
            }
            PrivateKey pk = KeyFactory.getInstance("Ed25519")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(key.strip())));
            for (int i = 1; i < args.length; i++) {
                Path file = Path.of(args[i]);
                if (file.toString().endsWith(".sig")) continue;
                Signature s = Signature.getInstance("Ed25519");
                s.initSign(pk);
                s.update(Files.readAllBytes(file));
                Path sig = Path.of(file + ".sig");
                Files.writeString(sig, Base64.getEncoder().encodeToString(s.sign()) + "\n");
                System.out.println("signiert: " + file.getFileName());
            }
        } else {
            System.err.println("Aufruf: keygen <datei> | sign <dateien…>");
            System.exit(1);
        }
    }

    private static void keygen(Path privateKeyFile) throws Exception {
        if (Files.exists(privateKeyFile)) {
            System.err.println("Datei existiert bereits, wird nicht überschrieben: " + privateKeyFile);
            System.exit(3);
        }
        KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Files.createDirectories(privateKeyFile.toAbsolutePath().getParent());
        Files.writeString(privateKeyFile, Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()) + "\n");
        try {
            Files.setPosixFilePermissions(privateKeyFile, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // Windows
        }
        System.out.println("Privater Schlüssel gespeichert: " + privateKeyFile.toAbsolutePath());
        System.out.println("Öffentlicher Schlüssel (in UpdateService.PUBLIC_KEY eintragen):");
        System.out.println(Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()));
    }
}
