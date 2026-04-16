# WHERE — Knowledge Document
**Aggiornato**: 2026-04-16
**Versione documento**: 1.3
**Versione app**: 2.0.0 — ForegroundService — STEP 10 in test su Samsung Galaxy A52

---

## Identità del progetto

| Campo | Valore |
|---|---|
| Nome app | `where` |
| Package | `com.Dev.where` *(D maiuscola — confermato corretto su tutto il progetto)* |
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
  → chiede esclusione battery optimization
  → avvia LocationForegroundService
  → finish()
      ↓
LocationForegroundService.onCreate()
  → startForeground() — notifica permanente silenziosa
  → registerLocationUpdates() — registra PendingIntent FusedLocation
      ↓
FusedLocationProviderClient
  → PendingIntent → LocationReceiver
  → [scatta ogni 5s in sviluppo, 15min in produzione — STEP 11]
      ↓
LocationReceiver.onReceive()
  → SheetsSender.saveAndSend()
      ↓
OkHttp POST (con interceptor redirect) → Apps Script → Google Sheets

BOOT_COMPLETED / LOCKED_BOOT_COMPLETED
      ↓
BootReceiver.onReceive()
      ↓
LocationForegroundService.start() → ciclo riparte
```

---

## Ciclo di vita app — comportamento atteso

| Evento | Comportamento |
|---|---|
| Installazione APK | Tap icona → permessi → servizio attivo |
| Schermo spento | ✅ Continua |
| Swipe lista recenti | ✅ Continua |
| Riavvio telefono | ✅ BootReceiver riavvia ForegroundService automaticamente |
| Forza Arresto | ❌ Si ferma — ripartenza: tap icona launcher |

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

---

## Struttura package attuale

```
app/src/main/java/com/Dev/where/
├── WhereApplication.kt
├── StartupActivity.kt
├── LocationForegroundService.kt
├── db/
│   ├── GpsPoint.kt
│   ├── GpsPointDao.kt
│   └── WhereDatabase.kt
├── receiver/
│   └── Receivers.kt
├── tracker/
│   ├── SheetsSender.kt
│   ├── DeviceInfo.kt
│   └── TypingAccessibilityService.kt
└── Util/
    └── PermissionHelper.kt
```

---

## Componenti implementati

### StartupActivity
- Flow permessi in sequenza: Notifiche → Posizione fine → Posizione background → Battery optimization
- `checkBatteryOptimization()` — richiede `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- Al termine avvia `LocationForegroundService` e chiama `finish()`

### LocationForegroundService
- `START_STICKY`
- Notifica permanente silenziosa: "Servizio di rete / Sincronizzazione in corso"
- `onCreate()` chiama `registerLocationUpdates()`

### SheetsSender
- `OkHttpClient` con interceptor che gestisce redirect 302 mantenendo POST
- **Fix critico**: Apps Script risponde 302 — OkHttp default converte POST→GET → script non esegue
- Body JSON include: `deviceId`, `deviceName`, `lat`, `lng`, `accuracy`, `speed`, `altitude`, `bearing`, `satellites`, `battery`, `isCharging`, `isOnCall`, `isScreenOn`, `networkType`, `isTyping`, `activeApp`, `timestamp`

### BootReceiver
- Guard esplicito: `if (intent.action != Intent.ACTION_BOOT_COMPLETED) return`
- `android:exported="true"` obbligatorio

### LocationReceiver
- Riceve fix GPS via PendingIntent
- `android:exported="false"`

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
<uses-permission android:name="android.permission.READ_PHONE_STATE"/>
```

---

## Fix critici applicati in questa sessione (2026-04-16)

### Fix 1 — OkHttp redirect 302 POST→GET (causa root "no punti su Sheet")
**Problema**: Google Apps Script risponde con 302 redirect. OkHttp default segue il redirect convertendo POST in GET → `doPost()` dello script non scatta mai → zero righe su Sheet.

**Confermato via**: `curl -v POST` → risposta `HTTP/1.1 302 Moved Temporarily` con `Location: https://script.googleusercontent.com/...`

**Fix**: `OkHttpClient.Builder()` con interceptor che intercetta 302, ricostruisce la request come POST verso il `Location` URL:
```kotlin
private val client = OkHttpClient.Builder()
    .followRedirects(false)
    .followSslRedirects(false)
    .addInterceptor { chain ->
        var response = chain.proceed(chain.request())
        if (response.code == 302 || response.code == 301) {
            val newUrl = response.header("Location") ?: return@addInterceptor response
            response.close()
            val newRequest = chain.request().newBuilder()
                .url(newUrl)
                .post(chain.request().body!!)
                .build()
            response = chain.proceed(newRequest)
        }
        response
    }
    .build()
```

### Fix 2 — Battery optimization flow in StartupActivity
**Problema**: ForegroundService killato da Android senza esclusione battery optimization.

**Fix**: aggiunto `checkBatteryOptimization()` nel flow permessi con `requestBatteryOptimization` launcher.

### Fix 3 — Package com.Dev.where confermato
**Situazione chiarita**: il package corretto è `com.Dev.where` (D maiuscola) su tutto il progetto — directory fisica `com/Dev/where/` su Windows. Confermato con PowerShell scan su tutti i `.kt`. Non esiste mismatch.

---

## OEM — target e compatibilità

| OEM | Architettura | Stato |
|---|---|---|
| **Samsung Galaxy A52 (OneUI)** | ForegroundService | 🔄 In test — target primario |
| Oppo Reno4 Z / ColorOS | ForegroundService | ⚠️ Instabile — popup "non ha potuto avviarsi" |
| Stock Android | ForegroundService | ✅ OK |
| Xiaomi / MIUI | ForegroundService | ✅ Teoricamente OK |
| Huawei / EMUI | ForegroundService | ⚠️ Da verificare |

**Nota ColorOS**: ForegroundService viene killato sistematicamente nonostante i permessi. Non è il target primario — problema rinviato.

---

## Samsung OneUI — rischio per versione

| OneUI | Android | Rischio |
|---|---|---|
| 3.x | 11 | 🟡 Medio |
| 4.x | 12 | 🟢 Basso |
| 5.x | 13 | 🟢 Basso |
| 6.x | 14 | 🟡 Medio — FOREGROUND_SERVICE_LOCATION obbligatorio |
| 7.x | 15 | 🔴 Alto — restrizioni ForegroundService lunga durata |

Galaxy A52 gira OneUI 3.1→4.0 (Android 11-12) — zona basso rischio.

---

## ADB Windows — riferimento rapido

```powershell
# Path adb
C:\Users\giol\AppData\Local\Android\Sdk\platform-tools\adb.exe

# Verifica package installato
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell pm list packages | findstr where

# Avvio manuale (solo sviluppo/debug)
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -n com.Dev.where/.StartupActivity

# Logcat filtrato
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" logcat -s "ForegroundService" "BootReceiver" "LocationReceiver" "SheetsSender"
```

---

## Backend — Google Apps Script

- **doPost**: riceve JSON → appende riga su Sheet
- **Sheet**: `where_tracker` — colonne: timestamp, deviceId, deviceName, lat, lng, accuracy, speed, altitude, bearing, satellites, battery, isCharging, isOnCall, isScreenOn, networkType, isTyping, activeApp
- URL endpoint: hardcoded in `SheetsSender.kt` (da spostare — STEP 12)
- **Comportamento rete**: risponde 302 redirect → gestito da interceptor OkHttp

---

## Prossimi step

| Step | Descrizione | Priorità |
|---|---|---|
| STEP 10 | Test Samsung Galaxy A52 — conferma core send funzionante | 🔴 Alta — in corso |
| STEP 11 | Intervallo produzione: 15min / 100m displacement | Alta |
| STEP 12 | `secrets.properties` — URL endpoint fuori dal codice | Media |
| STEP 13 | `deviceId` configurabile — non hardcoded | Media |
| STEP 14 | WorkManager watchdog per Android 14+ (OneUI 6+) | Futura |
| STEP 15 | Progetto `where-viewer` — mappa + settings | Futura |

---

## Sistema completo

| Progetto | Nome | Package | Stato |
|---|---|---|---|
| Tracker | `where` | `com.Dev.where` | 🔄 v2.0.0 — test Samsung A52 |
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

*Versione documento: 1.3 — Sostituisce WHERE_KNOWLEDGE 1.2*
*Branch attivo: foreground*
