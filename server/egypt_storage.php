<?php
/**
 * Storage Egypt: MariaDB se db_config.php esiste, altrimenti JSON.
 */

function egypt_storage_bootstrap(): void
{
    static $done = false;
    if ($done) {
        return;
    }
    $done = true;

    $cfgFile = __DIR__ . '/db_config.php';
    if (!file_exists($cfgFile)) {
        return;
    }
    $cfg = require $cfgFile;
    if (!is_array($cfg)) {
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
        egypt_maybe_migrate_json_to_db();
    } catch (Throwable $e) {
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

function egypt_db_ensure_tables(PDO $pdo, string $prefix): void
{
    $users = $prefix . 'egypt_users';
    $messages = $prefix . 'egypt_messages';
    $checklist = $prefix . 'egypt_checklist';

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
}

function egypt_db_migration_flag_path(): string
{
    global $messagesFile;

    return dirname($messagesFile) . '/.egypt_db_migrated';
}

function egypt_db_migration_done(): bool
{
    return file_exists(egypt_db_migration_flag_path());
}

function egypt_mark_db_migration_done(): void
{
    $flag = egypt_db_migration_flag_path();
    file_put_contents($flag, gmdate('c'));
}

/** Importa JSON → DB una sola volta (mai se la tabella messaggi è stata svuotata). */
function egypt_maybe_migrate_json_to_db(): void
{
    if (!egypt_using_db() || egypt_db_migration_done()) {
        return;
    }

    global $messagesFile, $usersFile, $checklistStateFile;

    $pdo = egypt_pdo();
    $msgTable = egypt_table('messages');
    $existing = (int) $pdo->query("SELECT COUNT(*) FROM {$msgTable}")->fetchColumn();
    if ($existing > 0) {
        egypt_mark_db_migration_done();
        write_json_file($messagesFile, []);

        return;
    }
    $jsonMessages = read_json_file($messagesFile, []);
    if (is_array($jsonMessages)) {
        foreach ($jsonMessages as $m) {
            if (!is_array($m) || empty($m['id'])) {
                continue;
            }
            egypt_db_insert_message($m);
        }
    }
    $jsonUsers = read_json_file($usersFile, []);
    if (is_array($jsonUsers)) {
        foreach ($jsonUsers as $u) {
            if (is_array($u) && !empty($u['user_id'])) {
                egypt_db_touch_user($u['user_id'], $u['name'] ?? '', $u['last_seen'] ?? gmdate('c'));
            }
        }
    }
    $jsonChecklist = read_json_file($checklistStateFile, []);
    if (is_array($jsonChecklist)) {
        foreach ($jsonChecklist as $itemId => $state) {
            if (!is_array($state)) {
                continue;
            }
            egypt_db_set_checklist($itemId, !empty($state['done']), $state['by'] ?? '', $state['at'] ?? gmdate('c'));
        }
    }

    egypt_mark_db_migration_done();
    write_json_file($messagesFile, []);
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

function egypt_db_delete_message(string $messageId, string $userId): bool
{
    $pdo = egypt_pdo();
    if (!$pdo) {
        return false;
    }
    $table = egypt_table('messages');
    $stmt = $pdo->prepare("DELETE FROM {$table} WHERE id = ? AND user_id = ?");
    $stmt->execute([$messageId, $userId]);
    return $stmt->rowCount() > 0;
}

function egypt_db_clear_messages(string $userId): int
{
    $pdo = egypt_pdo();
    if (!$pdo) {
        return 0;
    }
    $table = egypt_table('messages');
    if ($userId === '') {
        $count = (int) $pdo->query("SELECT COUNT(*) FROM {$table}")->fetchColumn();
        $pdo->exec("TRUNCATE TABLE {$table}");
        return $count;
    }
    $stmt = $pdo->prepare("DELETE FROM {$table} WHERE user_id = ? OR to_user_id = ? OR to_user_id = ''");
    $stmt->execute([$userId, $userId]);
    return $stmt->rowCount();
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

// --- Unified API ---

function egypt_touch_user(string $userId, string $name, ?string $now = null): void
{
    global $usersFile;
    $now = $now ?? gmdate('c');
    if (egypt_using_db()) {
        egypt_db_touch_user($userId, $name, $now);
        return;
    }
    $users = read_json_file($usersFile, []);
    if (!is_array($users)) {
        $users = [];
    }
    $users[$userId] = ['user_id' => $userId, 'name' => $name, 'last_seen' => $now];
    write_json_file($usersFile, $users);
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
    global $usersFile;
    if (egypt_using_db()) {
        $pdo = egypt_pdo();
        if ($pdo) {
            $table = egypt_table('users');
            $pdo->exec("TRUNCATE TABLE {$table}");
        }
    }
    write_json_file($usersFile, []);
}

function egypt_get_users_list(): array
{
    if (egypt_using_db()) {
        return egypt_dedupe_users_by_name(egypt_db_get_users());
    }
    global $usersFile;
    $users = read_json_file($usersFile, []);
    if (!is_array($users)) {
        return [];
    }
    $list = array_values($users);
    usort($list, static fn($a, $b) => strcasecmp($a['name'] ?? '', $b['name'] ?? ''));

    return egypt_dedupe_users_by_name($list);
}

function egypt_user_exists(string $userId): bool
{
    if (egypt_using_db()) {
        return egypt_db_user_exists($userId);
    }
    global $usersFile;
    $users = read_json_file($usersFile, []);
    return is_array($users) && isset($users[$userId]);
}

function egypt_filter_messages_for_viewer(array $allMessages, string $viewerId): array
{
    return array_values(array_filter($allMessages, static function ($m) use ($viewerId) {
        if (!is_array($m)) {
            return false;
        }
        $to = trim($m['to_user_id'] ?? '');
        if ($to === '') {
            return true;
        }
        if ($viewerId === '') {
            return true;
        }
        $from = trim($m['user_id'] ?? '');
        return $from === $viewerId || $to === $viewerId;
    }));
}

function egypt_get_messages(string $viewerId): array
{
    if (egypt_using_db()) {
        return egypt_db_get_messages($viewerId);
    }
    global $messagesFile;
    $all = read_json_file($messagesFile, []);
    if (!is_array($all)) {
        $all = [];
    }
    return egypt_filter_messages_for_viewer($all, $viewerId);
}

function egypt_add_message(array $message): void
{
    global $messagesFile;
    if (egypt_using_db()) {
        egypt_db_insert_message($message);
        return;
    }
    $all = read_json_file($messagesFile, []);
    if (!is_array($all)) {
        $all = [];
    }
    $all[] = $message;
    if (count($all) > 300) {
        $all = array_slice($all, -300);
    }
    write_json_file($messagesFile, $all);
}

function egypt_delete_message(string $messageId, string $userId): bool
{
    global $messagesFile;
    if (egypt_using_db()) {
        return egypt_db_delete_message($messageId, $userId);
    }
    $all = read_json_file($messagesFile, []);
    if (!is_array($all)) {
        return false;
    }
    $found = false;
    $all = array_values(array_filter($all, static function ($m) use ($messageId, $userId, &$found) {
        if (!is_array($m) || ($m['id'] ?? '') !== $messageId) {
            return true;
        }
        if (($m['user_id'] ?? '') !== $userId) {
            return true;
        }
        $found = true;
        return false;
    }));
    if ($found) {
        write_json_file($messagesFile, $all);
    }
    return $found;
}

function egypt_clear_all_messages(): int
{
    global $messagesFile;
    if (egypt_using_db()) {
        $count = egypt_db_clear_messages('');
        write_json_file($messagesFile, []);
        egypt_clear_all_users();
        return $count;
    }
    $all = read_json_file($messagesFile, []);
    $count = is_array($all) ? count($all) : 0;
    write_json_file($messagesFile, []);
    egypt_clear_all_users();
    return $count;
}

function egypt_get_checklist_state(): array
{
    if (egypt_using_db()) {
        return egypt_db_get_checklist_state();
    }
    global $checklistStateFile;
    $state = read_json_file($checklistStateFile, []);
    return is_array($state) ? $state : [];
}

function egypt_set_checklist_item(string $itemId, bool $done, string $name, ?string $now = null): void
{
    global $checklistStateFile;
    $now = $now ?? gmdate('c');
    if (egypt_using_db()) {
        egypt_db_set_checklist($itemId, $done, $name, $now);
        return;
    }
    $state = read_json_file($checklistStateFile, []);
    if (!is_array($state)) {
        $state = [];
    }
    if ($done) {
        $state[$itemId] = ['done' => true, 'by' => $name, 'at' => $now];
    } else {
        unset($state[$itemId]);
    }
    write_json_file($checklistStateFile, $state);
}
