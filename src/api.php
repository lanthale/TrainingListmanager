<?php
declare(strict_types=1);

require_once __DIR__ . '/invitation.php';

final class ApiError extends RuntimeException
{
    public function __construct(string $message, public readonly int $status = 400)
    {
        parent::__construct($message);
    }
}

function json_out(mixed $data, int $status = 200): never
{
    http_response_code($status);
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

/** Prüft den Header X-Api-Key gegen den Hash in config.php. */
function require_api_key(): void
{
    $expected = (string)(app_config()['api_key_hash'] ?? '');
    $key = (string)($_SERVER['HTTP_X_API_KEY'] ?? '');
    if ($expected === '' || $key === '' || !hash_equals(strtolower($expected), hash('sha256', $key))) {
        throw new ApiError('Nicht autorisiert', 401);
    }
}

function require_method(string $method): void
{
    if ($_SERVER['REQUEST_METHOD'] !== $method) {
        throw new ApiError('Methode nicht erlaubt', 405);
    }
}

/** JSON-Body des Requests als Array. */
function json_body(): array
{
    $raw = file_get_contents('php://input') ?: '';
    $data = json_decode($raw, true);
    if (!is_array($data)) {
        throw new ApiError('Ungültiges JSON');
    }
    return $data;
}

function input_string(array $in, string $key, int $max, bool $required = false): ?string
{
    $value = isset($in[$key]) ? trim((string)$in[$key]) : '';
    if ($value === '') {
        if ($required) {
            throw new ApiError("Feld '$key' fehlt");
        }
        return null;
    }
    if (mb_strlen($value) > $max) {
        throw new ApiError("Feld '$key' ist zu lang (max. $max)");
    }
    return $value;
}

function input_int(array $in, string $key): int
{
    $value = filter_var($in[$key] ?? null, FILTER_VALIDATE_INT, ['options' => ['min_range' => 1]]);
    if ($value === false) {
        throw new ApiError("Feld '$key' ungültig");
    }
    return $value;
}

/** Erwartet 'YYYY-MM-DD HH:MM[:SS]' oder 'YYYY-MM-DDTHH:MM[:SS]'. */
function input_datetime(array $in, string $key): ?string
{
    $value = input_string($in, $key, 25);
    if ($value === null) {
        return null;
    }
    $value = str_replace('T', ' ', $value);
    foreach (['Y-m-d H:i:s', 'Y-m-d H:i'] as $format) {
        $dt = DateTime::createFromFormat('!' . $format, $value);
        if ($dt && $dt->format($format) === $value) {
            return $dt->format('Y-m-d H:i:s');
        }
    }
    throw new ApiError("Feld '$key' ist kein gültiges Datum");
}

function find_training(int $id): array
{
    $stmt = db()->prepare('SELECT * FROM trainings WHERE id = ?');
    $stmt->execute([$id]);
    $row = $stmt->fetch();
    if (!$row) {
        throw new ApiError('Training nicht gefunden', 404);
    }
    return $row;
}
