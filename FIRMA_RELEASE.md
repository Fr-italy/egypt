# APK Release — Egypt

## File da installare

`Egypt-release.apk` (versione definitiva, firmata, ottimizzata)

## Conserva questi file (obbligatorio per aggiornamenti futuri)

| File | Descrizione |
|------|-------------|
| `egypt-release.jks` | Certificato di firma |
| `keystore.properties` | Password del keystore |

**Senza il file `.jks` non potrai pubblicare aggiornamenti** sopra la stessa app installata.

Password attuale: vedi `keystore.properties` (non committare su Git).

## Ricompilare release

```bash
export JAVA_HOME=/home/frenky/android-dev/jdk-17
export ANDROID_HOME=/home/frenky/android-dev/sdk
cd ~/workspace/Egypt
./gradlew assembleRelease
cp app/build/outputs/apk/release/app-release.apk Egypt-release.apk
```

Prima di ogni nuova release, incrementa in `app/build.gradle.kts`:
- `versionCode` (numero intero, es. 2, 3…)
- `versionName` (es. "1.0.1")

## Traduttore insegne (Cloud Vision)

In `local.properties`:
- `MAPS_API_KEY` — mappe Google
- `VISION_API_KEY` (opzionale) — chiave dedicata solo a Vision; se vuota usa `MAPS_API_KEY`

Sulla chiave API in [Google Cloud Console](https://console.cloud.google.com/) → **Credenziali**:
1. **API e servizi → Libreria** → abilita **Cloud Vision API** (stesso progetto della chiave)
2. Apri la chiave → **Restrizioni API** → aggiungi **Cloud Vision API** (non solo Maps), oppure «Non limitare la chiave»
3. **Fatturazione** attiva sul progetto
4. Se restrizione «App Android»: pacchetto `com.frenky.egypt` + impronta SHA-1 del keystore release

Dopo aver modificato la chiave, ricompila l'APK e reinstalla.
