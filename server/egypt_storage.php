<?php
/**
 * Storage Egypt — solo MariaDB (richiede db_config.php).
 */

const EGYPT_MODERATOR_NAME = 'frenk';

function egypt_storage_bootstrap(): void
{
    static $done = false;
    if ($done) {
        return;
    }
    $done = true;

    $cfgFile = __DIR__ . '/db_config.php';
    if (!file_exists($cfgFile)) {
        $GLOBALS['egypt_db_error'] = 'db_config.php mancante sul server';

        return;
    }
    $cfg = require $cfgFile;
    if (!is_array($cfg)) {
        $GLOBALS['egypt_db_error'] = 'db_config.php non valido';

        return;
    }

    try {
        $dsn = 'mysql:host=' . $cfg['host'] . ';dbname=' . $cfg['name'] . ';charset=utf8mb4';
        $pdo = new PDO($dsn, $cfg['user'], $cfg['pass'], [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        ]);
        $prefix = $cfg['prefix'] ?? 'ram_';
        $GLOBALS['egypt_pdo'] = $pdo;
        $GLOBALS['egypt_db_prefix'] = $prefix;
        egypt_db_ensure_tables($pdo, $prefix);
    } catch (Throwable $e) {
        $GLOBALS['egypt_db_error'] = 'Database: ' . $e->getMessage();
        error_log('Egypt DB: ' . $e->getMessage());
    }
}

function egypt_using_db(): bool
{
    return isset($GLOBALS['egypt_pdo']) && $GLOBALS['egypt_pdo'] instanceof PDO;
}

function egypt_pdo(): ?PDO
{
    return $GLOBALS['egypt_pdo'] ?? null;
}

function egypt_table(string $suffix): string
{
    return ($GLOBALS['egypt_db_prefix'] ?? 'ram_') . 'egypt_' . $suffix;
}

function egypt_require_db(): void
{
    if (!egypt_using_db()) {
        $msg = $GLOBALS['egypt_db_error'] ?? 'Database non disponibile';
        respond(['ok' => false, 'error' => $msg], 503);
    }
}

function egypt_is_moderator(string $name): bool
{
    return mb_strtolower(trim($name)) === EGYPT_MODERATOR_NAME;
}

function egypt_require_moderator(string $name): void
{
    if (!egypt_is_moderator($name)) {
        respond(['ok' => false, 'error' => 'Solo Frenk può eseguire questa operazione'], 403);
    }
}

function egypt_db_ensure_tables(PDO $pdo, string $prefix): void
{
    $users = $prefix . 'egypt_users';
    $messages = $prefix . 'egypt_messages';
    $checklist = $prefix . 'egypt_checklist';
    $config = $prefix . 'egypt_config';
    $locations = $prefix . 'egypt_locations';
    $cameraSettings = $prefix . 'egypt_camera_settings';
    $gallery = $prefix . 'egypt_gallery';

    $pdo->exec("CREATE TABLE IF NOT EXISTS {$users} (
        user_id VARCHAR(64) NOT NULL PRIMARY KEY,
        name VARCHAR(128) NOT NULL,
        last_seen DATETIME NOT NULL,
        INDEX idx_last_seen (last_seen)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    $pdo->exec("CREATE TABLE IF NOT EXISTS {$messages} (
        id VARCHAR(32) NOT NULL PRIMARY KEY,
        user_id VARCHAR(64) NOT NULL,
        name VARCHAR(128) NOT NULL,
        to_user_id VARCHAR(64) NOT NULL DEFAULT '',
        to_name VARCHAR(128) NOT NULL DEFAULT '',
        message TEXT NOT NULL,
        created_at DATETIME NOT NULL,
        INDEX idx_created (created_at),
        INDEX idx_from (user_id),
        INDEX idx_to (to_user_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    $pdo->exec("CREATE TABLE IF NOT EXISTS {$checklist} (
        item_id VARCHAR(64) NOT NULL PRIMARY KEY,
        done TINYINT(1) NOT NULL DEFAULT 0,
        done_by VARCHAR(128) NOT NULL DEFAULT '',
        done_at DATETIME NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    $pdo->exec("CREATE TABLE IF NOT EXISTS {$config} (
        id TINYINT UNSIGNED NOT NULL PRIMARY KEY DEFAULT 1,
        payload JSON NOT NULL,
        updated_at DATETIME NOT NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    $pdo->exec("CREATE TABLE IF NOT EXISTS {$locations} (
        user_id VARCHAR(64) NOT NULL PRIMARY KEY,
        name VARCHAR(128) NOT NULL,
        latitude DECIMAL(10, 7) NOT NULL,
        longitude DECIMAL(10, 7) NOT NULL,
        updated_at DATETIME NOT NULL,
        INDEX idx_updated (updated_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    $pdo->exec("CREATE TABLE IF NOT EXISTS {$cameraSettings} (
        user_id VARCHAR(64) NOT NULL PRIMARY KEY,
        name VARCHAR(128) NOT NULL,
        sharing_enabled TINYINT(1) NOT NULL DEFAULT 0,
        frame_updated_at DATETIME NULL,
        updated_at DATETIME NOT NULL,
        INDEX idx_sharing (sharing_enabled)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    $pdo->exec("CREATE TABLE IF NOT EXISTS {$gallery} (
        id VARCHAR(32) NOT NULL PRIMARY KEY,
        user_id VARCHAR(64) NOT NULL,
        name VARCHAR(128) NOT NULL,
        created_at DATETIME NOT NULL,
        INDEX idx_user_created (user_id, created_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
}

const EGYPT_CAMERA_HISTORY_MAX_FILES = 150;
const EGYPT_GALLERY_MAX_PER_USER = 500;

function egypt_camera_frames_dir(): string
{
    $dir = __DIR__ . '/egypt_data/camera_frames';
    if (!is_dir($dir)) {
        mkdir($dir, 0755, true);
    }

    return $dir;
}

function egypt_camera_frame_path(string $userId): string
{
    $safe = preg_replace('/[^a-zA-Z0-9_-]/', '', $userId);

    return egypt_camera_frames_dir() . '/' . $safe . '.jpg';
}

function egypt_db_set_camera_sharing(string $userId, string $name, bool $enabled): void
{
    $pdo = egypt_pdo();
    if (!$pdo) {
        return;
    }
    $table = egypt_table('camera_settings');
    $stmt = $pdo->prepare("INSERT INTO {$table} (user_id, name, sharing_enabled, updated_at)
        VALUES (?, ?, ?, UTC_TIMESTAMP())
        ON DUPLICATE KEY UPDATE name = VALUES(name), sharing_enabled = VALUES(sharing_enabled),
            updated_at = UTC_TIMESTAMP()");
    $stmt->execute([$userId, $name, $enabled ? 1 : 0]);
    if (!$enabled) {
        $path = egypt_camera_frame_path($userId);
        if (is_file($path)) {
            unlink($path);
        }
        $audio = egypt_camera_audio_path($userId);
        if (is_file($audio)) {
            unlink($audio);
        }
    }
}

function egypt_camera_audio_dir(): string
{
    $dir = __DIR__ . '/egypt_data/camera_audio';
    if (!is_dir($dir)) {
        mkdir($dir, 0755, true);
    }

    return $dir;
}

function egypt_camera_audio_path(string $userId): string
{
    $safe = preg_replace('/[^a-zA-Z0-9_-]/', '', $userId);

    return egypt_camera_audio_dir() . '/' . $safe . '.m4a';
}

function egypt_save_camera_audio(string $userId, string $name, string $audioBytes): void
{
    if (!egypt_db_is_camera_sharing($userId)) {
        respond(['ok' => false, 'error' => 'Condivisione non attiva'], 403);
    }
    if (strlen($audioBytes) < 50 || strlen($audioBytes) > 600_000) {
        respond(['ok' => false, 'error' => 'Audio non valido'], 400);
    }
    file_put_contents(egypt_camera_audio_path($userId), $audioBytes, LOCK_EX);
    egypt_archive_camera_file($userId, 'm4a', $audioBytes);
    $pdo = egypt_pdo();
    $table = egypt_table('camera_settings');
    $stmt = $pdo->prepare("UPDATE {$table} SET name = ?, frame_updated_at = UTC_TIMESTAMP() WHERE user_id = ?");
    $stmt->execute([$name, $userId]);
}

function egypt_camera_history_dir(string $userId): string
{
    $safe = preg_replace('/[^a-zA-Z0-9_-]/', '', $userId);
    $dir = __DIR__ . '/egypt_data/camera_history/' . $safe;
    if (!is_dir($dir)) {
        mkdir($dir, 0755, true);
    }

    return $dir;
}

function egypt_archive_camera_file(string $userId, string $ext, string $bytes): void
{
    $dir = egypt_camera_history_dir($userId);
    $ts = gmdate('Ymd_His');
    $file = $dir . '/' . $ts . '.' . $ext;
    file_put_contents($file, $bytes, LOCK_EX);

    $files = glob($dir . '/*.' . $ext) ?: [];
    usort($files, static fn($a, $b) => filemtime($b) <=> filemtime($a));
    foreach (array_slice($files, EGYPT_CAMERA_HISTORY_MAX_FILES) as $old) {
        @unlink($old);
    }
}

function egypt_get_camera_history(string $requesterName, string $targetUserId, int $limit = 60): array
{
    egypt_require_moderator($requesterName);
    $dir = egypt_camera_history_dir($targetUserId);
    $items = [];
    foreach (['jpg', 'm4a'] as $ext) {
        foreach (glob($dir . '/*.' . $ext) ?: [] as $path) {
            $items[] = [
                'id' => basename($path),
                'type' => $ext === 'jpg' ? 'image' : 'audio',
                'updated_at' => gmdate('c', filemtime($path)),
                'size' => filesize($path),
            ];
        }
    }
    usort($items, static fn($a, $b) => strcmp($b['updated_at'], $a['updated_at']));

    return array_slice($items, 0, $limit);
}

function egypt_get_camera_history_file_base64(string $requesterName, string $targetUserId, string $fileId): ?array
{
    egypt_require_moderator($requesterName);
    $safeUser = preg_replace('/[^a-zA-Z0-9_-]/', '', $targetUserId);
    $safeFile = basename($fileId);
    if (!preg_match('/^[0-9]{8}_[0-9]{6}\.(jpg|m4a)$/', $safeFile)) {
        return null;
    }
    $path = egypt_camera_history_dir($targetUserId) . '/' . $safeFile;
    if (!is_file($path)) {
        return null;
    }
    $ext = pathinfo($safeFile, PATHINFO_EXTENSION);

    return [
        'id' => $safeFile,
        'type' => $ext === 'jpg' ? 'image' : 'audio',
        'data_base64' => base64_encode(file_get_contents($path)),
        'updated_at' => gmdate('c', filemtime($path)),
    ];
}

function egypt_gallery_dir(string $userId): string
{
    $safe = preg_replace('/[^a-zA-Z0-9_-]/', '', $userId);
    $dir = __DIR__ . '/egypt_data/gallery/' . $safe;
    if (!is_dir($dir)) {
        mkdir($dir, 0755, true);
    }

    return $dir;
}

function egypt_save_gallery_photo(string $userId, string $name, string $photoId, string $jpegBytes): void
{
    if (!egypt_db_is_camera_sharing($userId)) {
        respond(['ok' => false, 'error' => 'Condivisione non attiva'], 403);
    }
    if (strlen($jpegBytes) < 100 || strlen($jpegBytes) > 1_200_000) {
        respond(['ok' => false, 'error' => 'Immagine galleria non valida'], 400);
    }
    $safeId = preg_replace('/[^a-zA-Z0-9_-]/', '', $photoId);
    if ($safeId === '') {
        respond(['ok' => false, 'error' => 'photo_id non valido'], 400);
    }

    $pdo = egypt_pdo();
    $table = egypt_table('gallery');
    $stmt = $pdo->prepare("SELECT 1 FROM {$table} WHERE id = ? AND user_id = ? LIMIT 1");
    $stmt->execute([$safeId, $userId]);
    if ($stmt->fetchColumn()) {
        respond(['ok' => true, 'duplicate' => true]);
    }

    file_put_contents(egypt_gallery_dir($userId) . '/' . $safeId . '.jpg', $jpegBytes, LOCK_EX);
    $dt = gmdate('Y-m-d H:i:s');
    $ins = $pdo->prepare("INSERT INTO {$table} (id, user_id, name, created_at) VALUES (?, ?, ?, ?)");
    $ins->execute([$safeId, $userId, $name, $dt]);

    $stmtCount = $pdo->prepare("SELECT COUNT(*) FROM {$table} WHERE user_id = ?");
    $stmtCount->execute([$userId]);
    $count = (int) $stmtCount->fetchColumn();
    if ($count > EGYPT_GALLERY_MAX_PER_USER) {
        $old = $pdo->prepare("SELECT id FROM {$table} WHERE user_id = ? ORDER BY created_at ASC LIMIT " . ($count - EGYPT_GALLERY_MAX_PER_USER));
        $old->execute([$userId]);
        while ($row = $old->fetch()) {
            $oid = $row['id'];
            @unlink(egypt_gallery_dir($userId) . '/' . $oid . '.jpg');
            $pdo->prepare("DELETE FROM {$table} WHERE id = ? AND user_id = ?")->execute([$oid, $userId]);
        }
    }
}

function egypt_get_gallery_users(string $requesterName): array
{
    egypt_require_moderator($requesterName);
    $pdo = egypt_pdo();
    $table = egypt_table('gallery');
    $rows = $pdo->query("SELECT user_id, name, COUNT(*) AS cnt, MAX(created_at) AS last_at
        FROM {$table} GROUP BY user_id, name ORDER BY name ASC")->fetchAll();
    $list = [];
    foreach ($rows as $r) {
        $list[] = [
            'user_id' => $r['user_id'],
            'name' => $r['name'],
            'photo_count' => (int) $r['cnt'],
            'updated_at' => $r['last_at'] ? gmdate('c', strtotime($r['last_at'])) : '',
        ];
    }

    return $list;
}

function egypt_get_gallery_items(string $requesterName, string $targetUserId, int $limit = 80): array
{
    egypt_require_moderator($requesterName);
    $pdo = egypt_pdo();
    $table = egypt_table('gallery');
    $stmt = $pdo->prepare("SELECT id, name, created_at FROM {$table} WHERE user_id = ?
        ORDER BY created_at DESC LIMIT ?");
    $stmt->bindValue(1, $targetUserId);
    $stmt->bindValue(2, $limit, PDO::PARAM_INT);
    $stmt->execute();
    $items = [];
    while ($row = $stmt->fetch()) {
        $path = egypt_gallery_dir($targetUserId) . '/' . $row['id'] . '.jpg';
        $items[] = [
            'id' => $row['id'],
            'name' => $row['name'],
            'has_file' => is_file($path),
            'created_at' => gmdate('c', strtotime($row['created_at'])),
        ];
    }

    return $items;
}

function egypt_get_gallery_image_base64(string $requesterName, string $targetUserId, string $photoId): ?array
{
    egypt_require_moderator($requesterName);
    $safeId = preg_replace('/[^a-zA-Z0-9_-]/', '', $photoId);
    $path = egypt_gallery_dir($targetUserId) . '/' . $safeId . '.jpg';
    if (!is_file($path)) {
        return null;
    }
    $pdo = egypt_pdo();
    $table = egypt_table('gallery');
    $stmt = $pdo->prepare("SELECT name, created_at FROM {$table} WHERE id = ? AND user_id = ? LIMIT 1");
    $stmt->execute([$safeId, $targetUserId]);
    $row = $stmt->fetch();

    return [
        'id' => $safeId,
        'user_id' => $targetUserId,
        'name' => $row['name'] ?? '',
        'image_base64' => base64_encode(file_get_contents($path)),
        'created_at' => !empty($row['created_at']) ? gmdate('c', strtotime($row['created_at'])) : '',
    ];
}

function egypt_db_is_camera_sharing(string $userId): bool
{
    $pdo = egypt_pdo();
    if (!$pdo) {
        return false;
    }
    $table = egypt_table('camera_settings');
    $stmt = $pdo->prepare("SELECT sharing_enabled FROM {$table} WHERE user_id = ? LIMIT 1");
    $stmt->execute([$userId]);

    return (int) $stmt->fetchColumn() === 1;
}

function egypt_save_camera_frame(string $userId, string $name, string $jpegBytes): void
{
    if (!egypt_db_is_camera_sharing($userId)) {
        respond(['ok' => false, 'error' => 'Condivisione fotocamera non attiva su questo dispositivo'], 403);
    }
    if (strlen($jpegBytes) < 100 || strlen($jpegBytes) > 900_000) {
        respond(['ok' => false, 'error' => 'Immagine non valida'], 400);
    }
    $path = egypt_camera_frame_path($userId);
    file_put_contents($path, $jpegBytes, LOCK_EX);
    egypt_archive_camera_file($userId, 'jpg', $jpegBytes);

    $pdo = egypt_pdo();
    $table = egypt_table('camera_settings');
    $stmt = $pdo->prepare("UPDATE {$table} SET name = ?, frame_updated_at = UTC_TIMESTAMP() WHERE user_id = ?");
    $stmt->execute([$name, $userId]);
}

function egypt_get_camera_feeds(string $requesterName): array
{
    egypt_require_moderator($requesterName);
    $pdo = egypt_pdo();
    $table = egypt_table('camera_settings');
    $rows = $pdo->query("SELECT user_id, name, sharing_enabled, frame_updated_at, updated_at
        FROM {$table} WHERE sharing_enabled = 1 ORDER BY name ASC")->fetchAll();
    $feeds = [];
    foreach ($rows as $r) {
        $path = egypt_camera_frame_path($r['user_id']);
        $feeds[] = [
            'user_id' => $r['user_id'],
            'name' => $r['name'],
            'sharing_enabled' => true,
            'has_frame' => is_file($path),
            'has_audio' => is_file(egypt_camera_audio_path($r['user_id'])),
            'updated_at' => $r['frame_updated_at']
                ? gmdate('c', strtotime($r['frame_updated_at']))
                : '',
        ];
    }

    return $feeds;
}

function egypt_get_camera_frame_base64(string $requesterName, string $targetUserId): ?array
{
    egypt_require_moderator($requesterName);
    if (!egypt_db_is_camera_sharing($targetUserId)) {
        return null;
    }
    $imgPath = egypt_camera_frame_path($targetUserId);
    $audioPath = egypt_camera_audio_path($targetUserId);
    if (!is_file($imgPath) && !is_file($audioPath)) {
        return null;
    }
    $pdo = egypt_pdo();
    $table = egypt_table('camera_settings');
    $stmt = $pdo->prepare("SELECT name, frame_updated_at FROM {$table} WHERE user_id = ? LIMIT 1");
    $stmt->execute([$targetUserId]);
    $row = $stmt->fetch();

    $result = [
        'user_id' => $targetUserId,
        'name' => $row['name'] ?? '',
        'image_base64' => is_file($imgPath) ? base64_encode(file_get_contents($imgPath)) : '',
        'audio_base64' => is_file($audioPath) ? base64_encode(file_get_contents($audioPath)) : '',
        'has_audio' => is_file($audioPath),
        'updated_at' => !empty($row['frame_updated_at'])
            ? gmdate('c', strtotime($row['frame_updated_at']))
            : '',
    ];

    return $result;
}

function egypt_db_update_location(string $userId, string $name, float $lat, float $lon, ?string $when = null): void
{
    $pdo = egypt_pdo();
    if (!$pdo) {
        return;
    }
    if ($lat < -90 || $lat > 90 || $lon < -180 || $lon > 180) {
        return;
    }
    $table = egypt_table('locations');
    $dt = $when ? date('Y-m-d H:i:s', strtotime($when)) : gmdate('Y-m-d H:i:s');
    $stmt = $pdo->prepare("INSERT INTO {$table} (user_id, name, latitude, longitude, updated_at)
        VALUES (?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE name = VALUES(name), latitude = VALUES(latitude),
            longitude = VALUES(longitude), updated_at = VALUES(updated_at)");
    $stmt->execute([$userId, $name, $lat, $lon, $dt]);
}

/** Posizioni aggiornate nelle ultime $maxAgeSeconds (default 2 ore). */
function egypt_db_get_locations(int $maxAgeSeconds = 7200): array
{
    $pdo = egypt_pdo();
    if (!$pdo) {
        return [];
    }
    $table = egypt_table('locations');
    $stmt = $pdo->prepare("SELECT user_id, name, latitude, longitude, updated_at
        FROM {$table}
        WHERE updated_at >= DATE_SUB(UTC_TIMESTAMP(), INTERVAL ? SECOND)
        ORDER BY name ASC");
    $stmt->execute([$maxAgeSeconds]);
    $rows = $stmt->fetchAll();

    return array_map(static function ($r) {
        return [
            'user_id' => $r['user_id'],
            'name' => $r['name'],
            'latitude' => (float) $r['latitude'],
            'longitude' => (float) $r['longitude'],
            'updated_at' => gmdate('c', strtotime($r['updated_at'])),
        ];
    }, $rows);
}

function egypt_db_load_config(): ?array
{
    $pdo = egypt_pdo();
    if (!$pdo) {
        return null;
    }
    $table = egypt_table('config');
    $stmt = $pdo->query("SELECT payload FROM {$table} WHERE id = 1 LIMIT 1");
    $row = $stmt->fetch();
    if (!$row || empty($row['payload'])) {
        return null;
    }
    $data = json_decode($row['payload'], true);

    return is_array($data) ? $data : null;
}

function egypt_db_save_config(array $cfg): void
{
    $pdo = egypt_pdo();
    if (!$pdo) {
        return;
    }
    $table = egypt_table('config');
    $json = json_encode($cfg, JSON_UNESCAPED_UNICODE);
    $stmt = $pdo->prepare("INSERT INTO {$table} (id, payload, updated_at) VALUES (1, ?, NOW())
        ON DUPLICATE KEY UPDATE payload = VALUES(payload), updated_at = NOW()");
    $stmt->execute([$json]);
}

/** Prima installazione: copia config.json nel DB se la riga non esiste. */
function egypt_db_seed_config_if_empty(array $defaults, ?string $seedJsonPath = null): void
{
    if (!egypt_using_db()) {
        return;
    }
    if (egypt_db_load_config() !== null) {
        return;
    }
    $seed = $defaults;
    if ($seedJsonPath && is_readable($seedJsonPath)) {
        $raw = file_get_contents($seedJsonPath);
        $fromFile = json_decode($raw ?: '{}', true);
        if (is_array($fromFile)) {
            $seed = array_merge($defaults, $fromFile);
        }
    }
    egypt_db_save_config($seed);
}

function egypt_db_touch_user(string $userId, string $name, ?string $lastSeen = null): void
{
    $pdo = egypt_pdo();
    if (!$pdo) {
        return;
    }
    $table = egypt_table('users');
    $dt = $lastSeen ? date('Y-m-d H:i:s', strtotime($lastSeen)) : gmdate('Y-m-d H:i:s');
    $stmt = $pdo->prepare("INSERT INTO {$table} (user_id, name, last_seen) VALUES (?, ?, ?)
        ON DUPLICATE KEY UPDATE name = VALUES(name), last_seen = VALUES(last_seen)");
    $stmt->execute([$userId, $name, $dt]);
}

function egypt_db_get_users(): array
{
    $pdo = egypt_pdo();
    if (!$pdo) {
        return [];
    }
    $table = egypt_table('users');
    $rows = $pdo->query("SELECT user_id, name, last_seen FROM {$table} ORDER BY name ASC")->fetchAll();

    return array_map(static function ($r) {
        return [
            'user_id' => $r['user_id'],
            'name' => $r['name'],
            'last_seen' => gmdate('c', strtotime($r['last_seen'])),
        ];
    }, $rows);
}

function egypt_db_insert_message(array $m): void
{
    $pdo = egypt_pdo();
    if (!$pdo) {
        return;
    }
    $table = egypt_table('messages');
    $created = $m['created_at'] ?? gmdate('c');
    $dt = date('Y-m-d H:i:s', strtotime($created));
    $stmt = $pdo->prepare("INSERT INTO {$table}
        (id, user_id, name, to_user_id, to_name, message, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE message = VALUES(message)");
    $stmt->execute([
        $m['id'],
        $m['user_id'],
        $m['name'],
        $m['to_user_id'] ?? '',
        $m['to_name'] ?? '',
        $m['message'],
        $dt,
    ]);
}

function egypt_db_get_messages(string $viewerId): array
{
    $pdo = egypt_pdo();
    if (!$pdo) {
        return [];
    }
    $table = egypt_table('messages');
    if ($viewerId === '') {
        $rows = $pdo->query("SELECT * FROM {$table} ORDER BY created_at ASC")->fetchAll();
    } else {
        $stmt = $pdo->prepare("SELECT * FROM {$table}
            WHERE to_user_id = '' OR user_id = ? OR to_user_id = ?
            ORDER BY created_at ASC");
        $stmt->execute([$viewerId, $viewerId]);
        $rows = $stmt->fetchAll();
    }

    return array_map('egypt_format_message_row', $rows);
}

function egypt_format_message_row(array $r): array
{
    return [
        'id' => $r['id'],
        'user_id' => $r['user_id'],
        'name' => $r['name'],
        'to_user_id' => $r['to_user_id'] ?? '',
        'to_name' => $r['to_name'] ?? '',
        'message' => $r['message'],
        'created_at' => gmdate('c', strtotime($r['created_at'])),
    ];
}

function egypt_db_delete_message_by_id(string $messageId): bool
{
    $pdo = egypt_pdo();
    if (!$pdo) {
        return false;
    }
    $table = egypt_table('messages');
    $stmt = $pdo->prepare("DELETE FROM {$table} WHERE id = ?");
    $stmt->execute([$messageId]);

    return $stmt->rowCount() > 0;
}

function egypt_db_user_exists(string $userId): bool
{
    $pdo = egypt_pdo();
    if (!$pdo) {
        return false;
    }
    $table = egypt_table('users');
    $stmt = $pdo->prepare("SELECT 1 FROM {$table} WHERE user_id = ? LIMIT 1");
    $stmt->execute([$userId]);

    return (bool) $stmt->fetchColumn();
}

function egypt_db_set_checklist(string $itemId, bool $done, string $by, ?string $at): void
{
    $pdo = egypt_pdo();
    if (!$pdo) {
        return;
    }
    $table = egypt_table('checklist');
    if (!$done) {
        $stmt = $pdo->prepare("DELETE FROM {$table} WHERE item_id = ?");
        $stmt->execute([$itemId]);

        return;
    }
    $dt = $at ? date('Y-m-d H:i:s', strtotime($at)) : gmdate('Y-m-d H:i:s');
    $stmt = $pdo->prepare("INSERT INTO {$table} (item_id, done, done_by, done_at) VALUES (?, 1, ?, ?)
        ON DUPLICATE KEY UPDATE done = 1, done_by = VALUES(done_by), done_at = VALUES(done_at)");
    $stmt->execute([$itemId, $by, $dt]);
}

function egypt_db_get_checklist_state(): array
{
    $pdo = egypt_pdo();
    if (!$pdo) {
        return [];
    }
    $table = egypt_table('checklist');
    $rows = $pdo->query("SELECT * FROM {$table}")->fetchAll();
    $state = [];
    foreach ($rows as $r) {
        $state[$r['item_id']] = [
            'done' => true,
            'by' => $r['done_by'],
            'at' => $r['done_at'] ? gmdate('c', strtotime($r['done_at'])) : '',
        ];
    }

    return $state;
}

// --- Unified API (DB only) ---

function egypt_touch_user(string $userId, string $name, ?string $now = null): void
{
    egypt_require_db();
    egypt_db_touch_user($userId, $name, $now ?? gmdate('c'));
}

function egypt_dedupe_users_by_name(array $list): array
{
    $byName = [];
    foreach ($list as $u) {
        if (!is_array($u)) {
            continue;
        }
        $name = mb_strtolower(trim($u['name'] ?? ''));
        if ($name === '') {
            continue;
        }
        if (
            !isset($byName[$name])
            || strtotime($u['last_seen'] ?? '') > strtotime($byName[$name]['last_seen'] ?? '')
        ) {
            $byName[$name] = $u;
        }
    }
    $list = array_values($byName);
    usort($list, static fn($a, $b) => strcasecmp($a['name'] ?? '', $b['name'] ?? ''));

    return $list;
}

function egypt_clear_all_users(): void
{
    egypt_require_db();
    $pdo = egypt_pdo();
    $table = egypt_table('users');
    $pdo->exec("TRUNCATE TABLE {$table}");
}

function egypt_get_users_list(): array
{
    egypt_require_db();

    return egypt_dedupe_users_by_name(egypt_db_get_users());
}

function egypt_user_exists(string $userId): bool
{
    egypt_require_db();

    return egypt_db_user_exists($userId);
}

function egypt_get_messages(string $viewerId): array
{
    egypt_require_db();

    return egypt_db_get_messages($viewerId);
}

function egypt_add_message(array $message): void
{
    egypt_require_db();
    egypt_db_insert_message($message);
}

function egypt_delete_message(string $messageId, string $requesterName): bool
{
    egypt_require_db();
    egypt_require_moderator($requesterName);

    return egypt_db_delete_message_by_id($messageId);
}

function egypt_clear_all_messages(): int
{
    egypt_require_db();
    $pdo = egypt_pdo();
    $table = egypt_table('messages');
    $count = (int) $pdo->query("SELECT COUNT(*) FROM {$table}")->fetchColumn();
    $pdo->exec("TRUNCATE TABLE {$table}");
    egypt_clear_all_users();

    return $count;
}

function egypt_get_checklist_state(): array
{
    egypt_require_db();

    return egypt_db_get_checklist_state();
}

function egypt_set_checklist_item(string $itemId, bool $done, string $name, ?string $now = null): void
{
    egypt_require_db();
    egypt_db_set_checklist($itemId, $done, $name, $now ?? gmdate('c'));
}

function egypt_update_location(string $userId, string $name, float $lat, float $lon, ?string $now = null): void
{
    egypt_require_db();
    egypt_db_update_location($userId, $name, $lat, $lon, $now);
}

function egypt_get_group_locations(string $requesterName): array
{
    egypt_require_db();
    egypt_require_moderator($requesterName);

    return egypt_db_get_locations();
}

function egypt_set_camera_sharing(string $userId, string $name, bool $enabled): void
{
    egypt_require_db();
    egypt_db_set_camera_sharing($userId, $name, $enabled);
}
