# WHERE — Knowledge Document
**Aggiornato**: 2026-04-15
**Versione documento**: 1.2
**Versione app**: 2.0.0 — ForegroundService — STEP 9 base completato

---

## Identità del progetto

| Campo | Valore |
|---|---|
| Nome app | `where` |
| Package | `com.dev.where` *(d minuscola — discrepanza con com.Dev.where nel filesystem, da normalizzare)* |
| Repository | https://github.com/giol59/where.git |
| Branch principale | `foreground` |
| Branch archivio headless | `where-headless` |
| Branch archivio v1 | `main` |
| Percorso locale | `D:\ProveAtomi\where` |
| minSdk | 26 (Android 8 Oreo) |
| targetSdk | 35 |
| compileSdk | 35 |
| versionName | `2.0.0` |
| versionCode | 2 |
| Linguaggio | Kotlin |
| Build system | Kotlin DSL (`.gradle.kts`) |
| IDE | Android Studio |

---

## Architettura del sistema — visione completa

```
[DEVICE A — Tracker]              [GOOGLE]                  [DEVICE B — Viewer]
App "where" ForegroundService →  Apps Script doPost   →   Google Sheets
                                 Apps Script doGet    ←   App "where-viewer" (futuro)
```

---

## Architettura componenti — produzione v2.0.0

```
Installazione APK
      ↓
Tap icona launcher (una volta sola)
      ↓
StartupActivity
  → chiede permesso notifiche (Android 13+)
  → chiede permesso posizione fine
  → chiede posizione background "Consenti sempre"
  → avvia LocationForegroundService
  → finish()
      ↓
LocationForegroundService.onCreate()
  → startForeground() — notifica permanente silenziosa
  → registerLocationUpdates() — registra PendingIntent FusedLocation
      ↓
FusedLocationProviderClient
  → PendingIntent → LocationReceiver
  → [scatta ogni 5s in sviluppo, 15min in produzione — STEP 10]
      ↓
LocationReceiver.onReceive()
  → SheetsSender.saveAndSend()
      ↓
OkHttp POST → Apps Script → Google Sheets

BOOT_COMPLETED / LOCKED_BOOT_COMPLETED
      ↓
BootReceiver.onReceive()
      ↓
LocationForegroundService.start() → ciclo riparte
```

---

## Ciclo di vita app — comportamento confermato su Oppo ColorOS

| Evento | Comportamento |
|---|---|
| Installazione APK | Tap icona → permessi → servizio attivo |
| Schermo spento | ✅ Continua |
| Swipe lista recenti | ✅ Continua |
| Riavvio telefono | ✅ BootReceiver riavvia ForegroundService automaticamente |
| Forza Arresto | ❌ Si ferma — ripartenza: tap icona launcher |

**Forza Arresto** è l'unico caso che richiede intervento manuale — limite Android by design, non bypassabile via codice su nessuna app.

---

## Stack tecnologico — dipendenze v2.0.0

```kotlin
implementation("androidx.core:core-ktx:1.15.0")
implementation("androidx.appcompat:appcompat:1.7.0")
implementation("androidx.activity:activity-ktx:1.9.3")
implementation("com.google.android.gms:play-services-location:21.3.0")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
kapt("androidx.room:room-compiler:2.6.1")
```

**Aggiunto rispetto a v1.0.0**: `appcompat:1.7.0`, `activity-ktx:1.9.3`

---

## Struttura package attuale

```
app/src/main/java/com/Dev/where/
├── WhereApplication.kt              ← Application class (onCreate senza chiamate GPS)
├── StartupActivity.kt               ← Activity launcher, richiesta permessi, avvio servizio
├── LocationForegroundService.kt     ← ForegroundService GPS — NUOVO in v2.0.0
├── db/
│   ├── GpsPoint.kt
│   ├── GpsPointDao.kt
│   └── WhereDatabase.kt
├── receiver/
│   └── Receivers.kt                 ← BootReceiver avvia ForegroundService / LocationReceiver
├── tracker/
│   └── SheetsSender.kt
└── Util/
    └── PermissionHelper.kt
```

---

## Componenti implementati

### WhereApplication
- Estende `Application`
- `onCreate()` — nessuna chiamata GPS diretta
- Il servizio parte da `StartupActivity` o `BootReceiver`

### StartupActivity
- `AppCompatActivity` con icona launcher visibile
- Flusso permessi in sequenza: Notifiche → Posizione fine → Posizione background
- Per "Consenti sempre": apre Settings dell'app (Android non consente richiesta diretta)
- Al termine avvia `LocationForegroundService` e chiama `finish()`
- **Icona launcher resta visibile** — utile come pulsante manuale dopo Forza Arresto

### LocationForegroundService
- `START_STICKY` — sistema lo riavvia se killato
- Notifica permanente silenziosa camuffata: "Servizio di rete / Sincronizzazione in corso"
- `onCreate()` chiama `registerLocationUpdates()`
- Icona notifica: `stat_sys_upload` (icona sistema generica)

### BootReceiver
- Riceve `BOOT_COMPLETED` e `LOCKED_BOOT_COMPLETED`
- Chiama `LocationForegroundService.start(context)`
- `android:exported="true"` obbligatorio

### LocationReceiver
- Riceve fix GPS via PendingIntent da FusedLocationProvider
- Action: `com.dev.where.LOCATION_UPDATE` con `setPackage(context.packageName)`
- Chiama `SheetsSender.saveAndSend()`
- `android:exported="false"`

### registerLocationUpdates()
- `LocationRequest`: `PRIORITY_HIGH_ACCURACY`, interval 5s (sviluppo)
- **STEP 10**: portare a 15min / 100m displacement
- PendingIntent con action string — NON componente esplicito (fix critico v2.0.0)

---

## Permessi manifest v2.0.0

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION"/>
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
```

---

## Procedura installazione su nuovo dispositivo — v2.0.0

1. Installa APK
2. Tap icona launcher "where"
3. Concedi permesso notifiche → **Consenti**
4. Concedi permesso posizione → **Consenti sempre**
5. Il servizio parte automaticamente — notifica silenziosa attiva

**Nessun ADB, nessun settaggio OEM manuale richiesto.**

In caso di Forza Arresto: tap icona launcher per riavviare.

---

## Visibilità app — stato v2.0.0

| Punto di visibilità | Stato |
|---|---|
| Icona launcher | ✅ Visibile (necessaria per riavvio post FA) |
| Notifica status bar | ✅ Icona piccola silenziosa (camuffata) |
| App recenti | Appare durante configurazione permessi |
| Elenco app (Impostazioni) | Visibile |
| Permessi posizione | Visibile |

---

## OEM — compatibilità ForegroundService

| OEM | Rischio con ForegroundService |
|---|---|
| Oppo / ColorOS | ✅ Confermato funzionante |
| Samsung / OneUI | ✅ ForegroundService rispettato |
| Xiaomi / MIUI | ✅ ForegroundService rispettato |
| Huawei / EMUI | ⚠️ Verificare — EMUI aggressivo |
| Stock Android | ✅ Nessun problema |

---

## Fix critici applicati in v2.0.0

### Fix 1 — PendingIntent con action string
**Problema**: PendingIntent costruito con componente esplicito (`Intent(context, LocationReceiver::class.java)`) — GMS accettava la registrazione ma non consegnava mai i fix.

**Fix**: Intent con action string + `setPackage()`:
```kotlin
val intent = Intent("com.dev.where.LOCATION_UPDATE").apply {
    setPackage(context.packageName)
}
```

### Fix 2 — LocationReceiver senza intent-filter
**Problema**: `LocationReceiver` dichiarato nel manifest senza `<intent-filter>` — il broadcast non veniva mai consegnato.

**Fix**: Aggiunto intent-filter con action `com.dev.where.LOCATION_UPDATE`.

### Fix 3 — Architettura headless → ForegroundService
**Motivazione**: survival OEM garantito, installazione senza ADB, compatibilità Samsung/Xiaomi/Oppo senza configurazioni manuali.

---

## Backend — Google Apps Script

- **doPost**: riceve `{lat, lng, accuracy, timestamp, deviceId}` → appende riga su Sheet
- **Sheet**: `where_tracker` — colonne: timestamp, deviceId, lat, lng, accuracy
- URL endpoint: hardcoded in `SheetsSender.kt` (da spostare — STEP 11)

---

## ADB Windows — riferimento rapido

```powershell
# Path adb
C:\Users\giol\AppData\Local\Android\Sdk\platform-tools\adb.exe

# Verifica package installato
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell pm list packages | findstr where

# Avvio manuale (solo sviluppo/debug)
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -n com.dev.where/.StartupActivity

# Logcat filtrato
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" logcat -s "ForegroundService" "BootReceiver" "LocationReceiver" "WhereApp"
```

**Nota**: package reale sul device è `com.dev.where` (d minuscola).

---

## Prossimi step

| Step | Descrizione | Priorità |
|---|---|---|
| STEP 10 | Intervallo produzione: 15min / 100m displacement | Alta |
| STEP 11 | `secrets.properties` — URL endpoint fuori dal codice | Media |
| STEP 12 | `deviceId` configurabile — non hardcoded | Media |
| STEP 13 | Test OEM: Samsung Galaxy A52 | Alta |
| STEP 14 | Progetto `where-viewer` — mappa + settings | Futuro |

---

## Sistema completo — due progetti separati

| Progetto | Nome | Package | Stato |
|---|---|---|---|
| Tracker | `where` | `com.dev.where` | ✅ v2.0.0 — ForegroundService funzionante |
| Viewer | `where-viewer` | `com.dev.viewer` | ⬜ da iniziare |

---

## Regole operative Claude ↔ Giol

1. Leggere questo documento **intero** prima di qualsiasi analisi
2. Per ogni bug: tracciare percorso completo input → stato → funzione → output
3. Ogni modifica specifica: file esatto + riga + before + after
4. Patch applicate **solo al file sorgente compilato**, mai a copie
5. Trattare le cause, non i sintomi
6. Sviluppo incrementale: implementa → testa su device reale → conferma → procedi

---

*Versione documento: 1.2 — Sostituisce WHERE_KNOWLEDGE 1.1*
*Branch attivo: foreground*
