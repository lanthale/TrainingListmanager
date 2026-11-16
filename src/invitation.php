<?php
declare(strict_types=1);

require_once __DIR__ . '/db.php';

/** Maximale Feldlängen (entsprechen sql/schema.sql). */
const REGISTRATION_FIELDS = [
    'first_name'   => ['label' => 'Vorname',      'max' => 100],
    'last_name'    => ['label' => 'Nachname',     'max' => 100],
    'job_position' => ['label' => 'Job-Position', 'max' => 150],
    'dealer'       => ['label' => 'Händler',      'max' => 200],
];

/** Token aus dem QR-Code: 64 Hex-Zeichen (32 Zufallsbytes). */
function is_valid_token_format(string $token): bool
{
    return (bool)preg_match('/^[0-9a-f]{64}$/', $token);
}

function token_hash(string $token): string
{
    return hash('sha256', $token);
}

/** Neuer Klartext-Token für einen QR-Code (nur dessen Hash wird gespeichert). */
function create_invitation_token(): string
{
    return bin2hex(random_bytes(32));
}

/** Gültigkeit einer Einladung ohne Trainingsdatum (Tage ab Erstellung). */
const INVITATION_DAYS_WITHOUT_DATE = 90;

/**
 * Ablaufzeitpunkt einer Einladung: Ende des Tages nach Trainingsbeginn
 * (also Beginn-Datum + 2 Tage, 00:00). Ohne Trainingsdatum: 90 Tage ab jetzt.
 */
function invitation_expiry(?string $startsAt): string
{
    if ($startsAt !== null && $startsAt !== '') {
        $start = new DateTime($startsAt);
        $start->setTime(0, 0, 0)->modify('+2 days');
        return $start->format('Y-m-d H:i:s');
    }
    return (new DateTime())->modify('+' . INVITATION_DAYS_WITHOUT_DATE . ' days')->format('Y-m-d H:i:s');
}

/** Registrierungs-URL für einen Klartext-Token. */
function invitation_url(string $token): string
{
    $baseUrl = rtrim(app_config()['base_url'] ?? '', '/') . '/';
    return $baseUrl . '?t=' . $token;
}

/**
 * Prüft eine Einladung. Gibt ['status' => ..., 'invitation' => row|null] zurück.
 * Status: ok | invalid | used | expired | inactive
 */
function check_invitation(string $token, bool $forUpdate = false): array
{
    if (!is_valid_token_format($token)) {
        return ['status' => 'invalid', 'invitation' => null];
    }
    $sql = 'SELECT i.id, i.training_id, i.used_at,
                   (i.expires_at IS NOT NULL AND i.expires_at < NOW()) AS expired,
                   t.title, t.description, t.starts_at, t.location, t.active
              FROM invitations i
              JOIN trainings t ON t.id = i.training_id
             WHERE i.token_hash = ?';
    if ($forUpdate) {
        $sql .= ' FOR UPDATE';
    }
    $stmt = db()->prepare($sql);
    $stmt->execute([token_hash($token)]);
    $row = $stmt->fetch();

    if (!$row) {
        return ['status' => 'invalid', 'invitation' => null];
    }
    if ($row['used_at'] !== null) {
        return ['status' => 'used', 'invitation' => $row];
    }
    if ((int)$row['expired'] === 1) {
        return ['status' => 'expired', 'invitation' => $row];
    }
    if ((int)$row['active'] !== 1) {
        return ['status' => 'inactive', 'invitation' => $row];
    }
    return ['status' => 'ok', 'invitation' => $row];
}

/**
 * Trimmt und validiert die Formulareingaben.
 * Gibt [$data, $errors] zurück.
 */
function validate_registration(array $input): array
{
    $data = [];
    $errors = [];
    foreach (REGISTRATION_FIELDS as $key => $def) {
        $value = trim((string)($input[$key] ?? ''));
        $value = preg_replace('/\s+/u', ' ', $value) ?? '';
        if ($value === '') {
            $errors[$key] = $def['label'] . ' ist ein Pflichtfeld.';
        } elseif (mb_strlen($value) > $def['max']) {
            $errors[$key] = $def['label'] . ' darf höchstens ' . $def['max'] . ' Zeichen lang sein.';
        }
        $data[$key] = $value;
    }
    return [$data, $errors];
}

/**
 * Speichert die Registrierung atomar und markiert die Einladung als benutzt.
 * Gibt den Status zurück (ok | invalid | used | expired | inactive).
 */
function register_with_invitation(string $token, array $data): string
{
    $pdo = db();
    $pdo->beginTransaction();
    try {
        $check = check_invitation($token, true);
        if ($check['status'] !== 'ok') {
            $pdo->rollBack();
            return $check['status'];
        }
        $inv = $check['invitation'];

        $pdo->prepare(
            'INSERT INTO registrations
                (training_id, invitation_id, first_name, last_name, job_position, dealer)
             VALUES (?, ?, ?, ?, ?, ?)'
        )->execute([
            $inv['training_id'],
            $inv['id'],
            $data['first_name'],
            $data['last_name'],
            $data['job_position'],
            $data['dealer'],
        ]);

        $pdo->prepare('UPDATE invitations SET used_at = NOW() WHERE id = ?')
            ->execute([$inv['id']]);

        $pdo->commit();
        return 'ok';
    } catch (Throwable $e) {
        if ($pdo->inTransaction()) {
            $pdo->rollBack();
        }
        throw $e;
    }
}
