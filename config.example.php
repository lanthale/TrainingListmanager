<?php
// Vorlage: als config.php (gleicher Ordner, NICHT in public/) kopieren und Passwort eintragen.
// config.php ist per .gitignore vom Repository ausgeschlossen.
return [
    'db' => [
        'host'     => 'database-5021925029.ud-webspace.de',
        'port'     => 3306,
        'name'     => 'dbs16347872',
        'user'     => 'dbu3248472',
        'password' => '',
    ],
    // SHA-256-Hash (hex) des API-Schlüssels für den Desktop-Client.
    // Wird im Client unter Einstellungen → „Schlüssel erzeugen“ angezeigt. Leer = API gesperrt.
    'api_key_hash' => '',
    // Basis-URL der Registrierungsseite (für die QR-Links).
    'base_url' => 'https://trainings.mind-architects.de/',
];
