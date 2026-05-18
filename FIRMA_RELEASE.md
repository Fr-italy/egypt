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
