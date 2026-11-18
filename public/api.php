<?php
declare(strict_types=1);

/**
 * JSON-API für den Desktop-Client. Authentifizierung per Header X-Api-Key.
 * Aktionen über ?action=… (siehe README).
 */

// Vorab-Prüfungen mit lesbarer JSON-Meldung statt leerem HTTP 500
function api_fail_early($message)
{
    http_response_code(500);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode(array('error' => $message));
    exit;
}
if (PHP_VERSION_ID < 70400) {
    api_fail_early('PHP ' . PHP_VERSION . ' ist zu alt, benötigt wird mindestens 7.4 (empfohlen 8.1+).');
}
foreach (array('api.php', 'invitation.php', 'db.php') as $f) {
    if (!is_file(dirname(__DIR__) . '/src/' . $f)) {
        api_fail_early("Datei src/$f fehlt auf dem Server.");
    }
}
if (!is_file(dirname(__DIR__) . '/config.php')) {
    api_fail_early('config.php fehlt auf dem Server (neben public/).');
}
foreach (array('pdo_mysql', 'mbstring') as $ext) {
    if (!extension_loaded($ext)) {
        api_fail_early("PHP-Erweiterung $ext ist nicht aktiv.");
    }
}

require_once dirname(__DIR__) . '/src/api.php';
if (!function_exists('create_training_code')) {
    api_fail_early('src/invitation.php ist veraltet. Bitte die aktuelle Version hochladen.');
}

try {
    $authenticated = false;
    require_api_key();
    $authenticated = true;
    $action = (string)($_GET['action'] ?? '');
    $pdo = db();

    switch ($action) {
        case 'ping':
            json_out(['ok' => true]);

        case 'trainings':
            require_method('GET');
            $rows = $pdo->query(
                'SELECT t.id, t.title, t.description, t.starts_at, t.location, t.active, t.created_at,
                        (SELECT COUNT(*) FROM registrations r WHERE r.training_id = t.id) AS registered
                   FROM trainings t
               ORDER BY t.starts_at IS NULL, t.starts_at DESC, t.id DESC'
            )->fetchAll();
            json_out(['trainings' => array_map('normalize_training', $rows)]);

        case 'training':
            require_method('GET');
            $id = input_int($_GET, 'id');
            $training = find_training($id);

            $stmt = $pdo->prepare(
                'SELECT id, first_name, last_name, job_position, dealer, consent_at, created_at
                   FROM registrations WHERE training_id = ? ORDER BY last_name, first_name'
            );
            $stmt->execute([$id]);
            $registrations = $stmt->fetchAll();

            json_out([
                'training'      => normalize_training($training),
                'code'          => current_training_code($id),
                'registrations' => array_map(fn($r) => ['id' => (int)$r['id']] + $r, $registrations),
            ]);

        case 'create_training':
            require_method('POST');
            $in = json_body();
            $startsAt = input_datetime($in, 'starts_at');
            $pdo->beginTransaction();
            $stmt = $pdo->prepare(
                'INSERT INTO trainings (title, description, starts_at, location, active) VALUES (?, ?, ?, ?, 1)'
            );
            $stmt->execute([
                input_string($in, 'title', 200, true),
                input_string($in, 'description', 5000),
                $startsAt,
                input_string($in, 'location', 200),
            ]);
            $id = (int)$pdo->lastInsertId();
            $code = create_training_code($id, $startsAt);
            $pdo->commit();
            json_out(['id' => $id, 'code' => $code], 201);

        case 'set_active':
            require_method('POST');
            $in = json_body();
            $id = input_int($in, 'id');
            find_training($id);
            $pdo->prepare('UPDATE trainings SET active = ? WHERE id = ?')
                ->execute([!empty($in['active']) ? 1 : 0, $id]);
            json_out(['ok' => true]);

        // Neuer QR-Code für ein Training; der bisherige wird ungültig.
        case 'create_code':
            require_method('POST');
            $in = json_body();
            $id = input_int($in, 'training_id');
            $training = find_training($id);
            $pdo->beginTransaction();
            $code = create_training_code($id, $training['starts_at']);
            $pdo->commit();
            json_out(['code' => $code], 201);

        // Training mit allen QR-Codes und Registrierungen endgültig löschen (DSGVO).
        case 'delete_training':
            require_method('POST');
            $in = json_body();
            $id = input_int($in, 'id');
            find_training($id);
            $pdo->prepare('DELETE FROM trainings WHERE id = ?')->execute([$id]);
            json_out(['ok' => true]);

        default:
            throw new ApiError('Unbekannte Aktion', 404);
    }
} catch (ApiError $e) {
    json_out(['error' => $e->getMessage()], $e->status);
} catch (Throwable $e) {
    if (isset($pdo) && $pdo->inTransaction()) {
        $pdo->rollBack();
    }
    error_log('TrainingListmanager API: ' . $e->getMessage());
    // Details nur für den angemeldeten Client (Admin), sonst generisch
    $detail = !empty($authenticated) ? ': ' . $e->getMessage() : '';
    if (!empty($authenticated) && $e instanceof PDOException
        && strpos($e->getMessage(), 'Unknown column') !== false) {
        $detail .= ' – bitte sql/migrations/004_training_code_consent.sql in phpMyAdmin ausführen.';
    }
    json_out(['error' => 'Serverfehler' . $detail], 500);
}

function normalize_training(array $t): array
{
    foreach (['id', 'registered'] as $k) {
        if (isset($t[$k])) {
            $t[$k] = (int)$t[$k];
        }
    }
    $t['active'] = (int)$t['active'] === 1;
    return $t;
}
