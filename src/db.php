<?php
declare(strict_types=1);

/**
 * Lädt die Konfiguration aus config.php (eine Ebene über public/, nicht im Git).
 */
function app_config(): array
{
    static $config = null;
    if ($config === null) {
        $file = dirname(__DIR__) . '/config.php';
        if (!is_file($file)) {
            throw new RuntimeException('config.php fehlt (Vorlage: config.example.php).');
        }
        $config = require $file;
    }
    return $config;
}

/**
 * Liefert eine gemeinsame PDO-Verbindung zur MySQL-Datenbank.
 */
function db(): PDO
{
    static $pdo = null;
    if ($pdo === null) {
        $c = app_config()['db'];
        $dsn = sprintf(
            'mysql:host=%s;port=%d;dbname=%s;charset=utf8mb4',
            $c['host'],
            (int)($c['port'] ?? 3306),
            $c['name']
        );
        $pdo = new PDO($dsn, $c['user'], $c['password'], [
            PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES   => false,
        ]);
    }
    return $pdo;
}
