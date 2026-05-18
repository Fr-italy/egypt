# Caricamento su fr-italy.com

Il server attuale risponde ancora con la **versione vecchia** dell'endpoint (`Azione non valida` per `config`).
Sostituisci questi file:

## 1. `egypt_endpoint.php` (root sito)

Carica il file aggiornato da `server/egypt_endpoint.php` sovrascrivendo:
`https://www.fr-italy.com/egypt_endpoint.php`

## 2. Cartella `egypt_data/`

Assicurati che esista e sia scrivibile. Carica almeno:

- `server/egypt_data/config.json` → `/egypt_data/config.json`

Messaggi, utenti, checklist e configurazione app sono salvati **solo su MariaDB** (tabelle `ram_egypt_*`).  
`egypt_data/config.json` serve solo come seed iniziale alla prima richiesta, poi tutto è nel DB.  
Solo l'utente con nome **Frenk** può svuotare la chat o eliminare messaggi.

## 3. Verifica

```bash
curl -s "https://www.fr-italy.com/egypt_endpoint.php"
```

Deve rispondere con `"ok":true` e un oggetto `config` con `egp_to_eur`.

```bash
curl -s -X POST "https://www.fr-italy.com/egypt_endpoint.php" \
  -H "Content-Type: application/json" \
  -d '{"action":"config"}'
```

## Modificare il tasso senza ricompilare l'app

Edita solo `egypt_data/config.json`:

```json
"egp_to_eur": 0.01630,
"rate_updated_at": "2026-05-19",
"rate_source": "OANDA"
```

Salva: tutti i telefoni ricevono il nuovo tasso alla prossima sincronizzazione.
