# Pulizia server Egypt (dopo rimozione CAM / galleria)

## 1. Carica i PHP aggiornati

Sostituisci su **www.fr-italy.com** (stessa cartella di prima):

- `egypt_endpoint.php`
- `egypt_storage.php`

**Non toccare:** `db_config.php`, `egypt_data/config.json`, `egypt_data/documents/`

## 2. Elimina cartelle dati obsolete (FTP / file manager)

Se esistono, cancella:

```
egypt_data/camera_frames/
egypt_data/camera_audio/
egypt_data/camera_history/
egypt_data/gallery/
```

## 3. Database (phpMyAdmin) — opzionale

Puoi eliminare le tabelle non più usate (prefisso es. `ram_`):

```sql
DROP TABLE IF EXISTS ram_egypt_camera_settings;
DROP TABLE IF EXISTS ram_egypt_gallery;
```

Le tabelle **da tenere**: `egypt_users`, `egypt_messages`, `egypt_checklist`, `egypt_config`, `egypt_locations`.

## 4. Verifica

Apri nel browser:

`https://www.fr-italy.com/egypt_endpoint.php`

Deve rispondere JSON con `"ok": true` e la sezione `config`.

## API ancora attive

| Azione | Uso |
|--------|-----|
| `config`, `fetch`, `register`, `heartbeat` | App base |
| `send`, `delete_message`, `clear_messages`, `cleanup_users` | Chat |
| `update_location` | GPS in background |
| `toggle_checklist`, `update_rate`, `update_bounds` | Viaggio / admin |

Rimosse: tutte le `*camera*` e `*gallery*`, `get_locations`, `set_camera_sharing`.
