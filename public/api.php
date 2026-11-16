<?php
declare(strict_types=1);

/**
 * JSON-API für den Desktop-Client. Authentifizierung per Header X-Api-Key.
 * Aktionen über ?action=… (siehe README).
 */

require_once dirname(__DIR__) . '/src/api.php';

try {
    require_api_key();
    $action = (string)($_GET['action'] ?? '');
    $pdo = db();

    switch ($action) {
        case 'ping':
            json_out(['ok' => true]);

        case 'trainings':
            require_method('GET');
            $rows = $pdo->query(
                'SELECT t.id, t.title, t.description, t.starts_at, t.location, t.active, t.created_at,
                        (SELECT COUNT(*) FROM invitations i WHERE i.training_id = t.id) AS invited,
                        (SELECT COUNT(*) FROM invitations i WHERE i.training_id = t.id AND i.sent_at IS NOT NULL) AS sent,
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
                'SELECT id, email, sent_at, used_at, expires_at, created_at
                   FROM invitations WHERE training_id = ? ORDER BY email'
            );
            $stmt->execute([$id]);
            $invitations = $stmt->fetchAll();

            $stmt = $pdo->prepare(
                'SELECT r.id, r.invitation_id, r.first_name, r.last_name, r.job_position, r.dealer,
                        r.created_at, i.email
                   FROM registrations r JOIN invitations i ON i.id = r.invitation_id
                  WHERE r.training_id = ? ORDER BY r.last_name, r.first_name'
            );
            $stmt->execute([$id]);
            $registrations = $stmt->fetchAll();

            json_out([
                'training'      => normalize_training($training),
                'invitations'   => array_map(fn($r) => ['id' => (int)$r['id']] + $r, $invitations),
                'registrations' => array_map(fn($r) => ['id' => (int)$r['id'], 'invitation_id' => (int)$r['invitation_id']] + $r, $registrations),
            ]);

        case 'create_training':
            require_method('POST');
            $in = json_body();
            $stmt = $pdo->prepare(
                'INSERT INTO trainings (title, description, starts_at, location, active) VALUES (?, ?, ?, ?, 1)'
            );
            $stmt->execute([
                input_string($in, 'title', 200, true),
                input_string($in, 'description', 5000),
                input_datetime($in, 'starts_at'),
                input_string($in, 'location', 200),
            ]);
            json_out(['id' => (int)$pdo->lastInsertId()], 201);

        case 'set_active':
            require_method('POST');
            $in = json_body();
            $id = input_int($in, 'id');
            find_training($id);
            $pdo->prepare('UPDATE trainings SET active = ? WHERE id = ?')
                ->execute([!empty($in['active']) ? 1 : 0, $id]);
            json_out(['ok' => true]);

        case 'create_invitations':
            require_method('POST');
            $in = json_body();
            $trainingId = input_int($in, 'training_id');
            find_training($trainingId);
            $emails = $in['emails'] ?? null;
            if (!is_array($emails) || !$emails) {
                throw new ApiError("Feld 'emails' fehlt");
            }
            if (count($emails) > 1000) {
                throw new ApiError('Zu viele Adressen (max. 1000)');
            }

            $existing = $pdo->prepare('SELECT LOWER(email) FROM invitations WHERE training_id = ?');
            $existing->execute([$trainingId]);
            $known = array_fill_keys($existing->fetchAll(PDO::FETCH_COLUMN), true);

            $insert = $pdo->prepare(
                'INSERT INTO invitations (training_id, email, token_hash) VALUES (?, ?, ?)'
            );
            $created = [];
            $skipped = [];
            $pdo->beginTransaction();
            foreach ($emails as $email) {
                $email = trim((string)$email);
                $lower = mb_strtolower($email);
                if (!filter_var($email, FILTER_VALIDATE_EMAIL) || mb_strlen($email) > 254) {
                    $skipped[] = ['email' => $email, 'reason' => 'ungültig'];
                    continue;
                }
                if (isset($known[$lower])) {
                    $skipped[] = ['email' => $email, 'reason' => 'bereits eingeladen'];
                    continue;
                }
                $token = create_invitation_token();
                $insert->execute([$trainingId, $email, token_hash($token)]);
                $known[$lower] = true;
                $created[] = [
                    'id'    => (int)$pdo->lastInsertId(),
                    'email' => $email,
                    'url'   => invitation_url($token),
                ];
            }
            $pdo->commit();
            json_out(['created' => $created, 'skipped' => $skipped], 201);

        case 'renew_invitation':
            require_method('POST');
            $in = json_body();
            $id = input_int($in, 'id');
            $stmt = $pdo->prepare('SELECT id, email, used_at FROM invitations WHERE id = ?');
            $stmt->execute([$id]);
            $inv = $stmt->fetch();
            if (!$inv) {
                throw new ApiError('Einladung nicht gefunden', 404);
            }
            if ($inv['used_at'] !== null) {
                throw new ApiError('Einladung wurde bereits verwendet', 409);
            }
            $token = create_invitation_token();
            $pdo->prepare('UPDATE invitations SET token_hash = ?, sent_at = NULL WHERE id = ? AND used_at IS NULL')
                ->execute([token_hash($token), $id]);
            json_out(['id' => (int)$inv['id'], 'email' => $inv['email'], 'url' => invitation_url($token)]);

        case 'mark_sent':
            require_method('POST');
            $in = json_body();
            $ids = array_values(array_filter(
                array_map('intval', (array)($in['ids'] ?? [])),
                fn($i) => $i > 0
            ));
            if ($ids) {
                $placeholders = implode(',', array_fill(0, count($ids), '?'));
                $pdo->prepare("UPDATE invitations SET sent_at = NOW() WHERE id IN ($placeholders)")
                    ->execute($ids);
            }
            json_out(['updated' => count($ids)]);

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
    json_out(['error' => 'Serverfehler'], 500);
}

function normalize_training(array $t): array
{
    foreach (['id', 'invited', 'sent', 'registered'] as $k) {
        if (isset($t[$k])) {
            $t[$k] = (int)$t[$k];
        }
    }
    $t['active'] = (int)$t['active'] === 1;
    return $t;
}
