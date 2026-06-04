<?php
/**
 * Egypt app API — https://www.fr-italy.com/egypt_endpoint.php
 * Dati in https://www.fr-italy.com/egypt_data/ (cartella scrivibile)
 */

header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, GET, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, X-Egypt-Key');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(204);
    exit;
}

/** Chiave admin: lascia vuota per disabilitare update da app. Imposta la stessa in Android se usi update remoto. */
const EGYPT_ADMIN_KEY = '';

/** Cartella dati (stesso livello dello script: /egypt_data/) */
const EGYPT_DATA_DIR = __DIR__ . '/egypt_data';

/** Aggiorna il tasso EGP→EUR da internet al massimo ogni N secondi (6 ore). */
const RATE_REFRESH_INTERVAL_SEC = 21600;

$configFile = EGYPT_DATA_DIR . '/config.json';

// --- Bootstrap file system ---

if (!is_dir(EGYPT_DATA_DIR)) {
    mkdir(EGYPT_DATA_DIR, 0755, true);
}

$defaultConfig = [
    'egp_to_eur' => 0.01625,
    'rate_source' => 'manuale',
    'rate_updated_at' => gmdate('Y-m-d'),
    'rate_fetched_at' => '',
    'rate_auto_update' => true,
    'announcement' => '',
    'area_bounds' => [
        'north_lat' => 28.0525,
        'south_lat' => 28.0345,
        'west_lon' => 34.4050,
        'east_lon' => 34.4420,
    ],
    'resort_bounds' => [
        'north_lat' => 28.0486,
        'south_lat' => 28.0438,
        'west_lon' => 34.4248,
        'east_lon' => 34.4312,
    ],
    'checklist' => [
        ['id' => 'passport', 'text' => 'Passaporto e documenti'],
        ['id' => 'insurance', 'text' => 'Assicurazione viaggio'],
        ['id' => 'adapter', 'text' => 'Adattatore prese tipo C/F'],
        ['id' => 'sunscreen', 'text' => 'Crema solare'],
        ['id' => 'egp_cash', 'text' => 'Contanti EGP / cambio'],
        ['id' => 'parmigiano', 'text' => 'Parmigiano Reggiano'],
        ['id' => 'passport_photos', 'text' => 'Due foto tessere'],
        ['id' => 'usd_tips', 'text' => 'Dollari per le mance'],
        ['id' => 'resort_map', 'text' => 'Scarica mappa offline (già in app)'],
        ['id' => 'whatsapp', 'text' => 'Gruppo WhatsApp famiglia'],
        ['id' => 'pharmacy', 'text' => 'Farmaco personale + kit base'],
    ],
    'phrases_extra' => [],
    'documents' => [],
];

if (!file_exists($configFile)) {
    write_json_file($configFile, $defaultConfig);
}
require_once __DIR__ . '/egypt_storage.php';

// --- Helpers ---

function read_json_file(string $path, $fallback = [])
{
    $raw = @file_get_contents($path);
    if ($raw === false || $raw === '') {
        return $fallback;
    }
    $data = json_decode($raw, true);
    return $data ?? $fallback;
}

function write_json_file(string $path, $data): bool
{
    $dir = dirname($path);
    if (!is_dir($dir)) {
        @mkdir($dir, 0755, true);
    }
    if (!is_writable($dir)) {
        return false;
    }

    return @file_put_contents(
        $path,
        json_encode($data, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT),
        LOCK_EX
    ) !== false;
}

function respond(array $payload, int $code = 200): void
{
    http_response_code($code);
    echo json_encode($payload, JSON_UNESCAPED_UNICODE);
    exit;
}

function merge_checklist_defaults(array $cfg, array $defaults): array
{
    $existing = [];
    foreach ($cfg['checklist'] ?? [] as $item) {
        if (is_array($item) && !empty($item['id'])) {
            $existing[$item['id']] = true;
        }
    }
    $merged = is_array($cfg['checklist'] ?? null) ? $cfg['checklist'] : [];
    foreach ($defaults['checklist'] ?? [] as $item) {
        if (is_array($item) && !empty($item['id']) && empty($existing[$item['id']])) {
            $merged[] = $item;
        }
    }
    $cfg['checklist'] = $merged;

    return $cfg;
}

function merge_documents_defaults(array $cfg, array $defaults, ?string $seedFile): array
{
    $seedDocs = $defaults['documents'] ?? [];
    if ($seedFile && is_readable($seedFile)) {
        $fromFile = read_json_file($seedFile, []);
        if (!empty($fromFile['documents']) && is_array($fromFile['documents'])) {
            $seedDocs = $fromFile['documents'];
        }
    }
    if (empty($seedDocs)) {
        return $cfg;
    }

    $bySection = [];
    foreach ($cfg['documents'] ?? [] as $section) {
        if (is_array($section) && !empty($section['id'])) {
            $bySection[$section['id']] = $section;
        }
    }

    foreach ($seedDocs as $seedSection) {
        if (!is_array($seedSection) || empty($seedSection['id'])) {
            continue;
        }
        $sid = $seedSection['id'];
        if (!isset($bySection[$sid])) {
            $bySection[$sid] = $seedSection;
            continue;
        }
        $existingItems = [];
        foreach ($bySection[$sid]['items'] ?? [] as $item) {
            if (is_array($item) && !empty($item['id'])) {
                $existingItems[$item['id']] = true;
            }
        }
        $items = is_array($bySection[$sid]['items'] ?? null) ? $bySection[$sid]['items'] : [];
        foreach ($seedSection['items'] ?? [] as $seedItem) {
            if (is_array($seedItem) && !empty($seedItem['id']) && empty($existingItems[$seedItem['id']])) {
                $items[] = $seedItem;
            }
        }
        $bySection[$sid] = array_merge($bySection[$sid], ['items' => $items]);
    }

    $cfg['documents'] = array_values($bySection);

    return $cfg;
}

function load_config(): array
{
    global $defaultConfig, $configFile;
    egypt_require_db();
    $cfg = egypt_db_load_config();
    if ($cfg === null) {
        egypt_db_seed_config_if_empty($defaultConfig, $configFile);
        $cfg = egypt_db_load_config();
    }

    $cfg = array_merge($defaultConfig, is_array($cfg) ? $cfg : []);
    $beforeChecklist = json_encode($cfg['checklist'] ?? []);
    $cfg = merge_checklist_defaults($cfg, $defaultConfig);
    if (json_encode($cfg['checklist'] ?? []) !== $beforeChecklist) {
        save_config($cfg);
    }
    $beforeDocs = json_encode($cfg['documents'] ?? []);
    $cfg = merge_documents_defaults($cfg, $defaultConfig, $configFile);
    if (json_encode($cfg['documents'] ?? []) !== $beforeDocs) {
        save_config($cfg);
    }

    return $cfg;
}

function save_config(array $cfg): void
{
    egypt_db_save_config($cfg);
}

function http_get_json(string $url, int $timeoutSec = 8): ?array
{
    if (function_exists('curl_init')) {
        $ch = curl_init($url);
        curl_setopt_array($ch, [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_FOLLOWLOCATION => true,
            CURLOPT_TIMEOUT => $timeoutSec,
            CURLOPT_HTTPHEADER => ['Accept: application/json', 'User-Agent: EgyptApp/1.0'],
        ]);
        $raw = curl_exec($ch);
        $code = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);
        if ($raw === false || $code < 200 || $code >= 300) {
            return null;
        }
    } else {
        $ctx = stream_context_create([
            'http' => [
                'timeout' => $timeoutSec,
                'header' => "Accept: application/json\r\nUser-Agent: EgyptApp/1.0\r\n",
            ],
        ]);
        $raw = @file_get_contents($url, false, $ctx);
        if ($raw === false) {
            return null;
        }
    }
    $data = json_decode($raw, true);
    return is_array($data) ? $data : null;
}

/** @return array{rate: float, source: string}|null */
function fetch_live_egp_to_eur(): ?array
{
    $providers = [
        [
            'source' => 'ExchangeRate-API',
            'url' => 'https://open.er-api.com/v6/latest/EGP',
            'parse' => static function (array $d): ?float {
                if (($d['result'] ?? '') !== 'success') {
                    return null;
                }
                $eur = $d['rates']['EUR'] ?? null;
                return is_numeric($eur) ? (float) $eur : null;
            },
        ],
        [
            'source' => 'currency-api (CDN)',
            'url' => 'https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/egp.json',
            'parse' => static function (array $d): ?float {
                $eur = $d['egp']['eur'] ?? ($d['egp']['EUR'] ?? null);
                return is_numeric($eur) ? (float) $eur : null;
            },
        ],
    ];

    foreach ($providers as $p) {
        $data = http_get_json($p['url']);
        if ($data === null) {
            continue;
        }
        $rate = $p['parse']($data);
        if ($rate !== null && $rate > 0.001 && $rate < 0.1) {
            return ['rate' => round($rate, 6), 'source' => $p['source']];
        }
    }
    return null;
}

/** Aggiorna config.json se scaduto o se $force. Restituisce config aggiornata. */
function maybe_refresh_exchange_rate(bool $force = false): array
{
    $cfg = load_config();
    $auto = $cfg['rate_auto_update'] ?? true;
    if (!$auto && !$force) {
        return $cfg;
    }

    $lastFetch = strtotime($cfg['rate_fetched_at'] ?? '');
    $stale = $lastFetch === false || (time() - $lastFetch) >= RATE_REFRESH_INTERVAL_SEC;
    if (!$force && !$stale) {
        return $cfg;
    }

    $live = fetch_live_egp_to_eur();
    if ($live === null) {
        return $cfg;
    }

    $cfg['egp_to_eur'] = $live['rate'];
    $cfg['rate_source'] = $live['source'] . ' (auto)';
    $cfg['rate_updated_at'] = gmdate('Y-m-d');
    $cfg['rate_fetched_at'] = gmdate('c');
    save_config($cfg);
    return $cfg;
}

function build_config_payload(): array
{
    $cfg = maybe_refresh_exchange_rate(false);
    $checklistState = egypt_get_checklist_state();
    $checklist = [];
    foreach ($cfg['checklist'] ?? [] as $item) {
        if (!is_array($item) || empty($item['id'])) {
            continue;
        }
        $id = $item['id'];
        $state = $checklistState[$id] ?? [];
        $checklist[] = [
            'id' => $id,
            'text' => $item['text'] ?? '',
            'done' => !empty($state['done']),
            'done_by' => $state['by'] ?? '',
            'done_at' => $state['at'] ?? '',
        ];
    }
    return [
        'egp_to_eur' => (float) ($cfg['egp_to_eur'] ?? 0.01625),
        'rate_source' => (string) ($cfg['rate_source'] ?? ''),
        'rate_updated_at' => (string) ($cfg['rate_updated_at'] ?? ''),
        'announcement' => (string) ($cfg['announcement'] ?? ''),
        'area_bounds' => $cfg['area_bounds'] ?? [],
        'resort_bounds' => $cfg['resort_bounds'] ?? [],
        'checklist' => $checklist,
        'phrases_extra' => $cfg['phrases_extra'] ?? [],
        'documents' => $cfg['documents'] ?? [],
    ];
}

function require_admin(array $body): void
{
    if (EGYPT_ADMIN_KEY === '') {
        respond(['ok' => false, 'error' => 'Aggiornamento remoto disabilitato sul server'], 403);
    }
    $key = $_SERVER['HTTP_X_EGYPT_KEY'] ?? ($body['admin_key'] ?? '');
    if ($key !== EGYPT_ADMIN_KEY) {
        respond(['ok' => false, 'error' => 'Chiave admin non valida'], 401);
    }
}

// --- Input ---

ini_set('memory_limit', '64M');
egypt_storage_bootstrap();
egypt_db_seed_config_if_empty($defaultConfig, $configFile);

$now = gmdate('c');

if ($_SERVER['REQUEST_METHOD'] === 'GET') {
    respond(['ok' => true, 'config' => build_config_payload()]);
}

$body = json_decode(file_get_contents('php://input') ?: '{}', true);
if (!is_array($body)) {
    respond(['ok' => false, 'error' => 'JSON non valido'], 400);
}

$action = $body['action'] ?? '';

// --- Actions ---

switch ($action) {

    case 'config':
        respond(['ok' => true, 'config' => build_config_payload()]);

    case 'refresh_rate':
        maybe_refresh_exchange_rate(true);
        respond(['ok' => true, 'config' => build_config_payload()]);

    case 'fetch':
        $viewerId = trim($body['user_id'] ?? '');
        $viewerName = trim($body['name'] ?? '');
        respond([
            'ok' => true,
            'messages' => egypt_get_messages($viewerId),
            'users' => egypt_get_users_list(),
            'can_moderate' => egypt_is_moderator($viewerName),
            'config' => build_config_payload(),
        ]);

    case 'update_location':
        $userId = trim($body['user_id'] ?? '');
        $name = trim($body['name'] ?? '');
        $lat = isset($body['latitude']) ? (float) $body['latitude'] : null;
        $lon = isset($body['longitude']) ? (float) $body['longitude'] : null;
        if ($userId === '' || $name === '' || $lat === null || $lon === null) {
            respond(['ok' => false, 'error' => 'user_id, name, latitude e longitude obbligatori'], 400);
        }
        if ($lat < 22 || $lat > 32 || $lon < 24 || $lon > 37) {
            respond(['ok' => false, 'error' => 'Coordinate fuori dall\'area vacanza'], 400);
        }
        egypt_update_location($userId, $name, $lat, $lon, $now);
        respond(['ok' => true]);

    case 'register':
    case 'heartbeat':
        $userId = trim($body['user_id'] ?? '');
        $name = trim($body['name'] ?? '');
        if ($userId === '' || $name === '') {
            respond(['ok' => false, 'error' => 'user_id e name obbligatori'], 400);
        }
        egypt_touch_user($userId, $name, $now);
        respond([
            'ok' => true,
            'users' => egypt_get_users_list(),
            'config' => build_config_payload(),
        ]);

    case 'send':
        $userId = trim($body['user_id'] ?? '');
        $name = trim($body['name'] ?? '');
        $message = trim($body['message'] ?? '');
        $toUserId = trim($body['to_user_id'] ?? '');
        $toName = trim($body['to_name'] ?? '');
        if ($userId === '' || $name === '' || $message === '') {
            respond(['ok' => false, 'error' => 'Campi mancanti'], 400);
        }
        if (mb_strlen($message) > 500) {
            respond(['ok' => false, 'error' => 'Messaggio troppo lungo (max 500)'], 400);
        }
        if ($toUserId !== '') {
            if (!egypt_user_exists($toUserId)) {
                respond(['ok' => false, 'error' => 'Destinatario non trovato'], 404);
            }
            if ($toUserId === $userId) {
                respond(['ok' => false, 'error' => 'Non puoi scriverti da solo'], 400);
            }
            if ($toName === '') {
                foreach (egypt_get_users_list() as $u) {
                    if (($u['user_id'] ?? '') === $toUserId) {
                        $toName = $u['name'] ?? '';
                        break;
                    }
                }
            }
        }
        egypt_add_message([
            'id' => bin2hex(random_bytes(8)),
            'user_id' => $userId,
            'name' => $name,
            'to_user_id' => $toUserId,
            'to_name' => $toName,
            'message' => $message,
            'created_at' => $now,
        ]);
        egypt_touch_user($userId, $name, $now);
        respond([
            'ok' => true,
            'messages' => egypt_get_messages($userId),
            'config' => build_config_payload(),
        ]);

    case 'delete_message':
        $userId = trim($body['user_id'] ?? '');
        $name = trim($body['name'] ?? '');
        $messageId = trim($body['message_id'] ?? '');
        if ($userId === '' || $name === '' || $messageId === '') {
            respond(['ok' => false, 'error' => 'user_id, name e message_id obbligatori'], 400);
        }
        if (!egypt_delete_message($messageId, $name)) {
            respond(['ok' => false, 'error' => 'Messaggio non trovato'], 404);
        }
        respond([
            'ok' => true,
            'messages' => egypt_get_messages($userId),
            'users' => egypt_get_users_list(),
            'config' => build_config_payload(),
        ]);

    case 'clear_messages':
        $userId = trim($body['user_id'] ?? '');
        $name = trim($body['name'] ?? '');
        if ($userId === '' || $name === '') {
            respond(['ok' => false, 'error' => 'user_id e name obbligatori'], 400);
        }
        egypt_require_moderator($name);
        $removed = egypt_clear_all_messages();
        egypt_touch_user($userId, $name, $now);
        respond([
            'ok' => true,
            'removed' => $removed,
            'messages' => [],
            'users' => egypt_get_users_list(),
            'config' => build_config_payload(),
        ]);

    case 'cleanup_users':
        $userId = trim($body['user_id'] ?? '');
        $name = trim($body['name'] ?? '');
        if ($userId === '' || $name === '') {
            respond(['ok' => false, 'error' => 'user_id e name obbligatori'], 400);
        }
        egypt_require_moderator($name);
        $stats = egypt_cleanup_duplicate_users();
        egypt_touch_user($userId, $name, $now);
        respond([
            'ok' => true,
            'removed_users' => $stats['removed_users'],
            'users' => $stats['users'],
            'config' => build_config_payload(),
        ]);

    case 'toggle_checklist':
        $userId = trim($body['user_id'] ?? '');
        $name = trim($body['name'] ?? '');
        $itemId = trim($body['item_id'] ?? '');
        if ($userId === '' || $name === '' || $itemId === '') {
            respond(['ok' => false, 'error' => 'user_id, name e item_id obbligatori'], 400);
        }
        $cfg = load_config();
        $validIds = [];
        foreach ($cfg['checklist'] ?? [] as $item) {
            if (is_array($item) && !empty($item['id'])) {
                $validIds[] = $item['id'];
            }
        }
        if (!in_array($itemId, $validIds, true)) {
            respond(['ok' => false, 'error' => 'Voce checklist non trovata'], 404);
        }
        $done = !empty($body['done']);
        egypt_set_checklist_item($itemId, $done, $name, $now);
        egypt_touch_user($userId, $name, $now);
        respond(['ok' => true, 'config' => build_config_payload()]);

    case 'update_rate':
        require_admin($body);
        $rate = (float) ($body['egp_to_eur'] ?? 0);
        if ($rate <= 0 || $rate > 1) {
            respond(['ok' => false, 'error' => 'egp_to_eur non valido'], 400);
        }
        $cfg = load_config();
        $cfg['egp_to_eur'] = $rate;
        $cfg['rate_updated_at'] = $body['rate_updated_at'] ?? gmdate('Y-m-d');
        $cfg['rate_source'] = $body['rate_source'] ?? ($cfg['rate_source'] ?? 'Admin');
        if (isset($body['announcement'])) {
            $cfg['announcement'] = (string) $body['announcement'];
        }
        save_config($cfg);
        respond(['ok' => true, 'config' => build_config_payload()]);

    case 'update_bounds':
        require_admin($body);
        $cfg = load_config();
        if (!empty($body['area_bounds']) && is_array($body['area_bounds'])) {
            $cfg['area_bounds'] = array_merge($cfg['area_bounds'] ?? [], $body['area_bounds']);
        }
        if (!empty($body['resort_bounds']) && is_array($body['resort_bounds'])) {
            $cfg['resort_bounds'] = array_merge($cfg['resort_bounds'] ?? [], $body['resort_bounds']);
        }
        save_config($cfg);
        respond(['ok' => true, 'config' => build_config_payload()]);

    default:
        respond(['ok' => false, 'error' => 'Azione non valida: ' . $action], 400);
}
