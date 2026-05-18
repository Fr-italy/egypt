-- Egypt app — MariaDB (prefisso ram_ come in db_config.php)
-- Esegui su database web_www.fr-italy.com (o il tuo db_name).

CREATE TABLE IF NOT EXISTS ram_egypt_users (
    user_id VARCHAR(64) NOT NULL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    last_seen DATETIME NOT NULL,
    INDEX idx_last_seen (last_seen)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ram_egypt_messages (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ram_egypt_checklist (
    item_id VARCHAR(64) NOT NULL PRIMARY KEY,
    done TINYINT(1) NOT NULL DEFAULT 0,
    done_by VARCHAR(128) NOT NULL DEFAULT '',
    done_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ram_egypt_config (
    id TINYINT UNSIGNED NOT NULL PRIMARY KEY DEFAULT 1,
    payload JSON NOT NULL,
    updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
