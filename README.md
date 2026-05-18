# Egypt — App Android vacanza Sharm / Nabq

## Funzioni

| Tab | Descrizione |
|-----|-------------|
| Euro | EGP → EUR con **tasso da server** (cache offline) |
| Nabq | Google Maps + GPS (chiave in `local.properties`) |
| Villaggio | Mappa resort offline + GPS |
| Frasi | IT / EN / AR (+ frasi extra da server) |
| Lista | Checklist viaggio **condivisa** tra tutti |
| Chat | Messaggi a **tutti** o **privati** (selettore destinatario) |

## Server (già online)

- Endpoint: `https://www.fr-italy.com/egypt_endpoint.php`
- Dati: `https://www.fr-italy.com/egypt_data/` (scrivibile)

### File da avere in `egypt_data/`

| File | Ruolo |
|------|--------|
| `config.json` | Tasso, mappe, checklist, annuncio, frasi extra |
| `messages.json` | Chat (creato automaticamente) |
| `users.json` | Utenti registrati |
| `checklist_state.json` | Spunte checklist (creato automaticamente) |

Carica `server/egypt_data/config.json` se non esiste ancora.

### Aggiornare il tasso EUR

Modifica `egypt_data/config.json`:

```json
"egp_to_eur": 0.01625,
"rate_updated_at": "2026-05-18",
"rate_source": "OANDA"
```

L'app scarica il nuovo valore al prossimo avvio o entro ~1 minuto con internet.

### Calibrare le mappe

In `config.json`, sezione `area_bounds` / `resort_bounds`:

```json
"resort_bounds": {
  "north_lat": 28.0486,
  "south_lat": 28.0438,
  "west_lon": 34.4248,
  "east_lon": 34.4312
}
```

### API (POST JSON)

| action | Note |
|--------|------|
| `config` | Solo configurazione |
| `fetch` | Messaggi + utenti + config |
| `register` / `heartbeat` | Utente + config |
| `send` | Messaggio chat |
| `toggle_checklist` | `item_id`, `done`, `user_id`, `name` |

GET senza body → restituisce solo `config` (utile per test browser).

## Google Maps (sviluppo)

Crea `local.properties` (non committato):

```properties
MAPS_API_KEY=la_tua_chiave
```

Restrizione Google Cloud: pacchetto `com.frenky.egypt` + SHA-1 del keystore release.

## Build

Apri in Android Studio → Run, oppure:

```bash
./gradlew assembleDebug
```
