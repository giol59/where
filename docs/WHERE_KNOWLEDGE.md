# WHERE — Knowledge Document
**Aggiornato**: 2026-04-09
**Versione corrente**: proto-app v0.1.0 — STEP 2 completato

---

## Identità del progetto

| Campo | Valore |
|---|---|
| Nome app | `where` |
| Package | `com.Dev.where` *(D maiuscola — da normalizzare in futuro)* |
| Repository | https://github.com/giol59/where.git |
| Branch principale | `main` |
| Percorso locale | `D:\ProveAtomi\where` |
| minSdk | 26 (Android 8 Oreo) |
| targetSdk | 35 |
| compileSdk | 35 |
| versionName | `0.1.0-proto` |
| Linguaggio | Kotlin |
| Build system | Kotlin DSL (`.gradle.kts`) |
| IDE | Android Studio |

---

## Architettura del sistema — visione completa

Il sistema è composto da **due app separate** con Google nel mezzo come storage/relay:

```
[DEVICE A — Tracker]              [GOOGLE]                  [DEVICE B — Viewer]
App "where" background   →   Apps Script doPost   →   Google Sheets
                         ←   Apps Script doGet    ←   App viewer (o browser)
```

**Google Apps Script** gestisce entrambi i versi: riceve POST dal tracker, serve GET al viewer. Google Sheets è il database. Zero server proprio.

---

## App Tracker — architettura componenti (produzione finale)

```
BOOT_COMPLETED
      ↓
BootReceiver (BroadcastReceiver)
      ↓
Registra LocationRequest su FusedLocationProviderClient
      ↓
PendingIntent → LocationReceiver
      ↓ [scatta su spostamento >100m OPPURE timer >15min]
LocationReceiver
      ↓                    ↓
Room DB (cache)      Sender/Dispatcher
                           ↓
                   OkHttp POST → Apps Script
```

**Trigger invio** — vince chi scatta prima:
1. Spostamento > 100 metri (`smallestDisplacement = 100f`)
2. Intervallo temporale configurabile (default 15 minuti)

---

## Proto-app vs Produzione — differenze

La proto-app (stato attuale) è un trampolino di sviluppo e test. Ha componenti temporanei che verranno rimossi.

| Componente | Proto-app (ora) | Produzione (finale) |
|---|---|---|
| UI | Compose debug UI | Nessuna |
| Icona launcher | Visibile | Nascosta (rimuovere MAIN/LAUNCHER) |
| App recenti | Visibile | Nascosta (`excludeFromRecents="true"`) |
| Location delivery | ForegroundService + callback | PendingIntent system-managed |
| Notifica | Obbligatoria (ForegroundService) | Nessuna |
| BootReceiver | Stub vuoto | Implementato |
| LocationReceiver | Stub vuoto | Implementato |
| Room DB | Non presente | Presente |

---

## Stack tecnologico — dipendenze attuali

```kotlin
// Compose
implementation(platform("androidx.compose:compose-bom:2024.12.01"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")

// Lifecycle
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
implementation("androidx.activity:activity-compose:1.9.3")

// GPS
implementation("com.google.android.gms:play-services-location:21.3.0")

// HTTP
implementation("com.squareup.okhttp3:okhttp:4.12.0")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
```

**Da aggiungere in step futuri**: Room DB, WorkManager

---

## Struttura package attuale

```
app/src/main/java/com/Dev/where/
├── MainActivity.kt                  ← UI debug Compose + BroadcastReceiver GPS
├── MainViewModel.kt                 ← stato UI, log GPS, contatori
├── Util/
│   └── PermissionHelper.kt         ← check permessi centralizzato
├── receiver/
│   └── Receivers.kt                ← BootReceiver + LocationReceiver (stub)
├── tracker/
│   └── LocationForegroundService.kt ← ForegroundService GPS (temporaneo)
└── ui/theme/
    └── ...                          ← tema default Android Studio
```

---

## Componenti implementati

### LocationForegroundService (temporaneo — STEP 2)
- ForegroundService con notifica obbligatoria
- `FusedLocationProviderClient` con `LocationCallback` diretto
- Intervallo: 5 secondi (test), minInterval: 2 secondi
- Priority: `PRIORITY_HIGH_ACCURACY`
- Broadcast verso MainActivity via `ACTION_LOCATION_UPDATE`
- Extras broadcast: `lat`, `lng`, `accuracy`, `time`, `available`

### MainViewModel
- `WhereUiState`: `isTracking`, `gpsAvailable`, `lastPoint`, `log`
- `GpsPoint`: `lat`, `lng`, `accuracy`, `timestamp`, `count`
- Log circolare ultimi 20 eventi
- Contatore punti ricevuti per sessione

### PermissionHelper
- `hasFineLocation()`, `hasBackgroundLocation()`, `isBatteryOptimizationIgnored()`
- `buildBatteryOptimizationIntent()` → apre impostazioni dirette
- `buildAppSettingsIntent()` → fallback impostazioni app

### Receivers (stub — STEP 6)
- `BootReceiver` — dichiarato nel manifest, corpo vuoto
- `LocationReceiver` — dichiarato nel manifest, corpo vuoto

---

## Permessi manifest

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION"/>
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION"/>
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>
<uses-permission android:name="android.permission.INTERNET"/>
```

---

## Visibilità app — produzione finale

| Punto di visibilità | Stato | Metodo |
|---|---|---|
| Icona launcher | Nascosta | Rimuovere `MAIN/LAUNCHER` dal manifest |
| App recenti | Nascosta | `android:excludeFromRecents="true"` |
| Notifiche | Nessuna | — |
| Elenco app (Impostazioni) | Visibile — non eliminabile | — |
| Permessi posizione | Visibile — non eliminabile | — |
| Gestore autostart OEM | Visibile — non eliminabile | — |

---

## Compatibilità Android

| Versione | Note critiche |
|---|---|
| 8 (Oreo) — minSdk | Background service puro non utilizzabile → PendingIntent corretto |
| 10+ | `ACCESS_BACKGROUND_LOCATION` separata, richiede "Consenti sempre" |
| 12+ | Indicatore GPS status bar — NON scatta con PendingIntent system-managed ✓ |
| 13 (TIRAMISU) | `registerReceiver` richiede flag `RECEIVER_EXPORTED` / `RECEIVER_NOT_EXPORTED` |
| 14+ | `foregroundServiceType="location"` obbligatorio per ForegroundService — non applicabile in produzione |

---

## Configurazione manuale dispositivo (una tantum — non bypassabile)

1. **Battery optimization OFF**: Impostazioni → App → where → Batteria → "Non ottimizzare"
   - Critico su Xiaomi, Huawei, Samsung
2. **Background location**: Impostazioni → App → where → Permessi → Posizione → "Consenti sempre"
   - Obbligatorio Android 10+

---

## OEM — note critiche background survival

| OEM | Rischio | Azione richiesta |
|---|---|---|
| Xiaomi / MIUI | Alto | Autostart manuale + battery unrestricted + MIUI optimization OFF |
| Huawei / EMUI | Molto alto | App launch on startup manuale, kill-list aggressiva |
| Samsung / OneUI | Medio | Battery "Unrestricted" in background usage |
| Oppo / ColorOS | Medio | Autostart + battery saver off |
| Stock Android (Pixel) | Basso | Solo battery optimization standard |

Non bypassabile via codice — configurazione manuale post-installazione.

---

## Backend — Google Apps Script

- **doPost**: riceve `{lat, lng, accuracy, timestamp, deviceId}` dal tracker
- **doGet**: serve i dati al viewer
- **Storage**: Google Sheets — una sheet per deviceId
- **Deploy**: Web App pubblica (chiunque può chiamarla)
- URL endpoint: da configurare in `secrets.properties` (mai in repo)

---

## App Viewer — decisione aperta

Opzioni ancora valutate:
- App Android dedicata (secondo progetto separato)
- Browser su Google Sheets direttamente

---

## Roadmap step proto-app

| Step | Descrizione | Stato |
|---|---|---|
| STEP 1 | Progetto base + permessi + UI shell | ✅ Completato |
| STEP 2 | ForegroundService + FusedLocation callback → coordinate a schermo | ✅ Completato |
| STEP 3 | Google Apps Script (doPost) → test con curl | ⬜ Prossimo |
| STEP 4 | OkHttp POST da app → coordinate su Google Sheet | ⬜ |
| STEP 5 | Room DB → cache offline + retry su rete | ⬜ |
| STEP 6 | Switch a PendingIntent → rimuovi ForegroundService | ⬜ |
| STEP 7 | Strip UI → manifest silenzioso → test BOOT_COMPLETED | ⬜ |

---

## Regole operative Claude ↔ Giol

1. Leggere questo documento **intero** prima di qualsiasi analisi
2. Per ogni bug: tracciare il percorso completo input → stato → funzione → output
3. Ogni modifica specifica: file esatto + riga + before + after
4. Patch applicate **solo al file sorgente compilato**, mai a copie di output
5. Trattare le cause, non i sintomi — no workaround con annotazioni
6. Sviluppo incrementale: implementa → testa su device reale → conferma → procedi

---

*Sostituisce integralmente: `GPS_Tracker_Contesto_Progetto.md`*
