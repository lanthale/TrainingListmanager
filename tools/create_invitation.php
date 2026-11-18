<?php
declare(strict_types=1);

/**
 * Referenz/Test: legt einen neuen QR-Code für ein Training an und gibt die URL aus.
 * Ein bisheriger Code des Trainings wird dabei ungültig.
 *
 *   php tools/create_invitation.php <training_id>
 */

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

require_once dirname(__DIR__) . '/src/invitation.php';

$trainingId = (int)($argv[1] ?? 0);
if ($trainingId <= 0) {
    fwrite(STDERR, "Aufruf: php tools/create_invitation.php <training_id>\n");
    exit(1);
}

$stmt = db()->prepare('SELECT starts_at FROM trainings WHERE id = ?');
$stmt->execute([$trainingId]);
$training = $stmt->fetch();
if (!$training) {
    fwrite(STDERR, "Training $trainingId nicht gefunden.\n");
    exit(1);
}

$code = create_training_code($trainingId, $training['starts_at']);
echo $code['url'] . PHP_EOL . 'gültig bis ' . $code['expires_at'] . PHP_EOL;
