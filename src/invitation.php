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

/** Obergrenze an Registrierungen pro QR-Code (Schutz vor Missbrauch eines geteilten Codes). */
const MAX_REGISTRATIONS_PER_CODE = 500;

/**
 * Legt einen neuen QR-Code für ein Training an. Bisherige Codes des Trainings werden
 * sofort ungültig (Registrierungen bleiben erhalten). Gibt ['url', 'expires_at', 'created_at'] zurück.
 * Muss innerhalb einer Transaktion aufgerufen werden.
 */
function create_training_code(int $trainingId, ?string $startsAt): array
{
    $pdo = db();
    $pdo->prepare(
        'UPDATE invitations SET token = NULL, expires_at = NOW()
          WHERE training_id = ? AND email IS NULL AND (expires_at IS NULL OR expires_at > NOW())'
    )->execute([$trainingId]);
    $token = create_invitation_token();
    $expiresAt = invitation_expiry($startsAt);
    $pdo->prepare(
        'INSERT INTO invitations (training_id, email, token_hash, token, expires_at) VALUES (?, NULL, ?, ?, ?)'
    )->execute([$trainingId, token_hash($token), $token, $expiresAt]);
    return current_training_code($trainingId);
}

/** Aktueller QR-Code eines Trainings oder null. */
function current_training_code(int $trainingId): ?array
{
    $stmt = db()->prepare(
        'SELECT token, expires_at, created_at FROM invitations
          WHERE training_id = ? AND email IS NULL AND token IS NOT NULL
          ORDER BY id DESC LIMIT 1'
    );
    $stmt->execute([$trainingId]);
    $row = $stmt->fetch();
    if (!$row) {
        return null;
    }
    return ['url' => invitation_url($row['token']), 'expires_at' => $row['expires_at'], 'created_at' => $row['created_at']];
}

/** Registrierungs-URL für einen Klartext-Token. */
function invitation_url(string $token): string
{
    $baseUrl = rtrim(app_config()['base_url'] ?? '', '/') . '/';
    return $baseUrl . '?t=' . $token;
}

/**
 * Prüft eine Einladung. Gibt ['status' => ..., 'invitation' => row|null] zurück.
 * Status: ok | invalid | used | full | expired | inactive
 */
function check_invitation(string $token, bool $forUpdate = false): array
{
    if (!is_valid_token_format($token)) {
        return ['status' => 'invalid', 'invitation' => null];
    }
    $sql = 'SELECT i.id, i.training_id, i.used_at, i.email,
                   (SELECT COUNT(*) FROM registrations r WHERE r.invitation_id = i.id) AS registrations,
                   (i.expires_at IS NOT NULL AND i.expires_at <= NOW()) AS expired,
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
    // Persönliche Einladung (ältere Version): nur eine Registrierung
    if ($row['email'] !== null && $row['used_at'] !== null) {
        return ['status' => 'used', 'invitation' => $row];
    }
    if ((int)$row['registrations'] >= MAX_REGISTRATIONS_PER_CODE) {
        return ['status' => 'full', 'invitation' => $row];
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
    if (empty($input['consent'])) {
        $errors['consent'] = 'Bitte stimmen Sie der Verarbeitung Ihrer Daten zu, um sich zu registrieren.';
    }
    return [$data, $errors];
}

/**
 * Speichert die Registrierung samt Zeitpunkt der Einwilligung atomar.
 * Gibt den Status zurück (ok | invalid | used | full | expired | inactive).
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
                (training_id, invitation_id, first_name, last_name, job_position, dealer, consent_at)
             VALUES (?, ?, ?, ?, ?, ?, NOW())'
        )->execute([
            $inv['training_id'],
            $inv['id'],
            $data['first_name'],
            $data['last_name'],
            $data['job_position'],
            $data['dealer'],
        ]);

        $pdo->prepare('UPDATE invitations SET used_at = COALESCE(used_at, NOW()) WHERE id = ?')
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
