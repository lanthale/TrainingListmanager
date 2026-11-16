<?php
declare(strict_types=1);

require_once dirname(__DIR__) . '/src/invitation.php';

session_set_cookie_params([
    'httponly' => true,
    'secure'   => !empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off',
    'samesite' => 'Lax',
]);
session_start();

header('X-Frame-Options: DENY');
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: no-referrer'); // Token aus der URL nicht weitergeben

function h(?string $s): string
{
    return htmlspecialchars((string)$s, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

function format_date(?string $dt): string
{
    if (!$dt) {
        return '';
    }
    $ts = strtotime($dt);
    return $ts ? date('d.m.Y, H:i', $ts) . ' Uhr' : '';
}

const STATUS_MESSAGES = [
    'missing'  => 'Bitte verwenden Sie den QR-Code aus Ihrer Einladungs-E-Mail, um sich zu registrieren.',
    'invalid'  => 'Dieser Registrierungslink ist ungültig. Bitte verwenden Sie den QR-Code aus Ihrer Einladungs-E-Mail.',
    'used'     => 'Mit dieser Einladung wurde bereits eine Registrierung durchgeführt.',
    'expired'  => 'Diese Einladung ist abgelaufen.',
    'inactive' => 'Für dieses Training ist derzeit keine Registrierung möglich.',
    'error'    => 'Es ist ein technischer Fehler aufgetreten. Bitte versuchen Sie es später erneut.',
    'csrf'     => 'Die Sitzung ist abgelaufen. Bitte laden Sie die Seite neu und versuchen Sie es erneut.',
];

if (empty($_SESSION['csrf'])) {
    $_SESSION['csrf'] = bin2hex(random_bytes(32));
}

$view    = 'form';   // form | message | success
$message = '';
$errors  = [];
$values  = array_fill_keys(array_keys(REGISTRATION_FIELDS), '');
$invitation = null;

try {
    if (isset($_GET['done']) && !empty($_SESSION['registered'])) {
        // Erfolgsseite nach Post/Redirect/Get
        $view = 'success';
        $success = $_SESSION['registered'];
        unset($_SESSION['registered']);
    } elseif ($_SERVER['REQUEST_METHOD'] === 'POST') {
        $token = (string)($_POST['t'] ?? '');
        if (!hash_equals($_SESSION['csrf'], (string)($_POST['csrf'] ?? ''))) {
            $view = 'message';
            $message = STATUS_MESSAGES['csrf'];
        } else {
            $check = check_invitation($token);
            if ($check['status'] !== 'ok') {
                $view = 'message';
                $message = STATUS_MESSAGES[$check['status']];
            } else {
                $invitation = $check['invitation'];
                [$values, $errors] = validate_registration($_POST);
                if (!$errors) {
                    $status = register_with_invitation($token, $values);
                    if ($status === 'ok') {
                        $_SESSION['registered'] = [
                            'name'     => $values['first_name'] . ' ' . $values['last_name'],
                            'title'    => $invitation['title'],
                            'starts_at'=> $invitation['starts_at'],
                            'location' => $invitation['location'],
                        ];
                        header('Location: ?done=1', true, 303);
                        exit;
                    }
                    $view = 'message';
                    $message = STATUS_MESSAGES[$status];
                }
            }
        }
    } else {
        $token = (string)($_GET['t'] ?? '');
        if ($token === '') {
            $view = 'message';
            $message = STATUS_MESSAGES['missing'];
        } else {
            $check = check_invitation($token);
            if ($check['status'] !== 'ok') {
                $view = 'message';
                $message = STATUS_MESSAGES[$check['status']];
            } else {
                $invitation = $check['invitation'];
            }
        }
    }
} catch (Throwable $e) {
    error_log('TrainingListmanager: ' . $e->getMessage());
    $view = 'message';
    $message = STATUS_MESSAGES['error'];
}
?>
<!doctype html>
<html lang="de">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="robots" content="noindex, nofollow">
    <title>Training-Registrierung</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
<main class="card">
    <h1>Training-Registrierung</h1>

<?php if ($view === 'message'): ?>
    <p class="notice"><?= h($message) ?></p>

<?php elseif ($view === 'success'): ?>
    <p class="success">Vielen Dank, <?= h($success['name']) ?>! Ihre Registrierung war erfolgreich.</p>
    <div class="training">
        <h2><?= h($success['title']) ?></h2>
        <?php if ($success['starts_at']): ?><p><?= h(format_date($success['starts_at'])) ?></p><?php endif; ?>
        <?php if ($success['location']): ?><p><?= h($success['location']) ?></p><?php endif; ?>
    </div>

<?php else: ?>
    <div class="training">
        <h2><?= h($invitation['title']) ?></h2>
        <?php if ($invitation['starts_at']): ?><p><?= h(format_date($invitation['starts_at'])) ?></p><?php endif; ?>
        <?php if ($invitation['location']): ?><p><?= h($invitation['location']) ?></p><?php endif; ?>
        <?php if ($invitation['description']): ?><p class="desc"><?= nl2br(h($invitation['description'])) ?></p><?php endif; ?>
    </div>

    <form method="post" action="./" novalidate>
        <input type="hidden" name="t" value="<?= h($token) ?>">
        <input type="hidden" name="csrf" value="<?= h($_SESSION['csrf']) ?>">

        <?php foreach (REGISTRATION_FIELDS as $key => $def): ?>
            <label for="<?= h($key) ?>"><?= h($def['label']) ?></label>
            <input type="text" id="<?= h($key) ?>" name="<?= h($key) ?>"
                   value="<?= h($values[$key]) ?>" maxlength="<?= (int)$def['max'] ?>" required
                   <?= isset($errors[$key]) ? 'aria-invalid="true"' : '' ?>>
            <?php if (isset($errors[$key])): ?>
                <p class="error"><?= h($errors[$key]) ?></p>
            <?php endif; ?>
        <?php endforeach; ?>

        <button type="submit">Registrieren</button>
    </form>
<?php endif; ?>
</main>
</body>
</html>
