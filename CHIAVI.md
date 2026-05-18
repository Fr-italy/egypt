# Chiave Google Maps

La chiave **non è nel messaggio** ricevuto: aggiungila così:

1. Crea il file `local.properties` nella cartella `Egypt/` (stesso livello di `app/`)
2. Inserisci una riga sola:

```
MAPS_API_KEY=AIza...la_tua_chiave...
```

3. Ricompila: `./gradlew assembleRelease`

Il file `local.properties` non va committato su Git (è già in `.gitignore`).
