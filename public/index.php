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
// Nur eigene Ressourcen, kein Inline-Skript, kein Einbetten; Formular darf nur an sich selbst senden
header("Content-Security-Policy: default-src 'none'; style-src 'self'; img-src 'self'; font-src 'self'; "
    . "form-action 'self'; frame-ancestors 'none'; base-uri 'none'");
header('Strict-Transport-Security: max-age=31536000');
header('Permissions-Policy: camera=(), microphone=(), geolocation=()');

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
    'missing'  => 'Bitte scannen Sie den QR-Code des Trainings, um sich zu registrieren.',
    'invalid'  => 'Dieser Registrierungslink ist ungültig. Bitte scannen Sie den aktuellen QR-Code des Trainings.',
    'used'     => 'Mit dieser Einladung wurde bereits eine Registrierung durchgeführt.',
    'full'     => 'Für dieses Training sind keine weiteren Registrierungen möglich.',
    'expired'  => 'Dieser QR-Code ist abgelaufen.',
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
$consent = false;
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
                $consent = !empty($_POST['consent']);
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
    <meta name="theme-color" content="#383a4f">
    <title>Training-Registrierung · MINDArchitects</title>
    <link rel="stylesheet" href="style.css">
    <link rel="icon" type="image/png" href="img/icon.png">
</head>
<body>
<header class="site-header">
    <a href="https://www.mind-architects.de/" class="brand" rel="noopener">
        <img src="img/logo.png" alt="MINDArchitects" width="260" height="116">
    </a>
</header>

<main class="card">
    <p class="eyebrow">Training</p>
    <h1>Registrierung</h1>

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

        <section class="privacy" aria-labelledby="privacy-title">
            <h3 id="privacy-title">Datenschutzhinweis</h3>
            <p>Verantwortlich ist MINDArchitects (Kontaktdaten siehe
                <a href="https://www.mind-architects.de/index.php/de/impressum.html" rel="noopener">Impressum</a>).
                Wir verarbeiten Ihren Vor- und Nachnamen, Ihre Job-Position und Ihren Händler
                auf Grundlage Ihrer Einwilligung (Art. 6 Abs. 1 lit. a DSGVO).</p>
            <p><strong>Die Daten werden nur zum Zweck der Anwesenheitsdokumentation gespeichert und nach
                der Weiterleitung gelöscht.</strong></p>
            <p>Sie können Ihre Einwilligung jederzeit mit Wirkung für die Zukunft widerrufen. Sie haben das Recht
                auf Auskunft, Berichtigung, Löschung und Einschränkung der Verarbeitung sowie auf Beschwerde bei
                einer Datenschutz-Aufsichtsbehörde. Mehr dazu in unserer
                <a href="https://www.mind-architects.de/index.php/de/datenschutz.html" rel="noopener">Datenschutzerklärung</a>.</p>
        </section>

        <label class="consent">
            <input type="checkbox" name="consent" value="1" required
                   <?= $consent ? 'checked' : '' ?> <?= isset($errors['consent']) ? 'aria-invalid="true"' : '' ?>>
            <span>Ich habe den Datenschutzhinweis gelesen und willige ein, dass meine Angaben zum Zweck der
                Anwesenheitsdokumentation gespeichert und verarbeitet werden.</span>
        </label>
        <?php if (isset($errors['consent'])): ?>
            <p class="error"><?= h($errors['consent']) ?></p>
        <?php endif; ?>

        <button type="submit">Registrieren</button>
    </form>
<?php endif; ?>
</main>

<footer class="site-footer">
    <nav>
        <a href="https://www.mind-architects.de/index.php/de/impressum.html" rel="noopener">Impressum</a>
        <span aria-hidden="true">|</span>
        <a href="https://www.mind-architects.de/index.php/de/datenschutz.html" rel="noopener">Datenschutz</a>
    </nav>
    <p>© MINDArchitects <?= date('Y') ?></p>
</footer>
</body>
</html>
