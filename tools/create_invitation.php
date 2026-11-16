<?php
declare(strict_types=1);

/**
 * Referenz/Test: legt eine persönliche Einladung an und gibt die QR-URL aus.
 *
 *   php tools/create_invitation.php <training_id> [email] [gueltig_tage]
 *
 * So muss es auch der spätere Client machen: Token zufällig erzeugen,
 * nur den SHA-256-Hash speichern, den Klartext-Token in die URL/den QR-Code.
 */

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

require_once dirname(__DIR__) . '/src/invitation.php';

$trainingId = (int)($argv[1] ?? 0);
$email      = $argv[2] ?? null;
$days       = isset($argv[3]) ? (int)$argv[3] : null;

if ($trainingId <= 0) {
    fwrite(STDERR, "Aufruf: php tools/create_invitation.php <training_id> [email] [gueltig_tage]\n");
    exit(1);
}

$token = create_invitation_token();

$stmt = db()->prepare(
    'INSERT INTO invitations (training_id, email, token_hash, expires_at)
     VALUES (?, ?, ?, IF(? IS NULL, NULL, NOW() + INTERVAL ? DAY))'
);
$stmt->execute([$trainingId, $email, token_hash($token), $days, $days]);

echo invitation_url($token) . PHP_EOL;
