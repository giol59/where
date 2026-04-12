# WHERE — Knowledge Document
**Aggiornato**: 2026-04-12
**Versione corrente**: 1.0.0 — STEP 7 completato — tracker silente funzionante

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
| versionName | `1.0.0` |
| Linguaggio | Kotlin |
| Build system | Kotlin DSL (`.gradle.kts`) |
| IDE | Android Studio |

---

## Architettura del sistema — visione completa

```
[DEVICE A — Tracker]              [GOOGLE]                  [DEVICE B — Viewer]
App "where" background   →   Apps Script doPost   →   Google Sheets
                         ←   Apps Script doGet    ←   App "where-viewer" (futuro)
```

**Google Apps Script** gestisce entrambi i versi. Google Sheets è il database. Zero server proprio.

---

## Architettura componenti — produzione finale

```
Installazione APK
      ↓
WhereApplication.onCreate()
      ↓
registerLocationUpdates() → FusedLocationProviderClient
      ↓
PendingIntent → LocationReceiver
      ↓ [scatta su spostamento o timer]
LocationReceiver.onReceive()
      ↓                    ↓
Room DB (cache)      SheetsSender
(sent=false)               ↓
                   OkHttp POST → Apps Script → Google Sheets

BOOT_COMPLETED / LOCKED_BOOT_COMPLETED
      ↓
BootReceiver.onReceive()
      ↓
registerLocationUpdates() → ciclo riparte
```

---

## Ciclo di vita app — comportamento confermato su device reale (Oppo ColorOS)

| Evento | Comportamento |
|---|---|
| Installazione APK | `WhereApplication.onCreate()` → tracker attivo subito |
| Schermo spento | ✅ Continua a funzionare |
| Kill da lista recenti (swipe) | ✅ Continua a funzionare |
| Riavvio telefono | ✅ `BootReceiver` riregistra PendingIntent (~4 min su Oppo) |
| Forza arresto (Impostazioni → App → where → Arresta) | ❌ Si ferma — riparte solo al prossimo riavvio |

**Forza arresto** è l'unico modo per fermare il tracker. L'utente può farlo da Impostazioni → App → where → Arresta.

---

## Stack tecnologico — dipendenze attuali

```kotlin
implementation("androidx.core:core-ktx:1.15.0")
implementation("com.google.android.gms:play-services-location:21.3.0")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
kapt("androidx.room:room-compiler:2.6.1")
```

**Rimosso**: Compose, Activity, ViewModel, lifecycle-compose

---

## Struttura package attuale

```
app/src/main/java/com/Dev/where/
├── WhereApplication.kt              ← Application class, avvio tracker
├── StartupActivity.kt               ← Activity minimale Theme.NoDisplay (da rimuovere STEP 8)
├── db/
│   ├── GpsPoint.kt                  ← Entity Room
│   ├── GpsPointDao.kt               ← DAO Room
│   └── WhereDatabase.kt             ← Database singleton
├── receiver/
│   └── Receivers.kt                 ← BootReceiver + LocationReceiver + registerLocationUpdates()
├── tracker/
│   └── SheetsSender.kt              ← OkHttp POST + Room cache + retry
└── Util/
    └── PermissionHelper.kt          ← check permessi (usato solo in fase setup)
```

---

## Componenti implementati

### WhereApplication
- Estende `Application`
- `onCreate()` chiama `registerLocationUpdates(this)`
- Punto di avvio dell'intera catena GPS

### StartupActivity (temporanea — da rimuovere STEP 8)
- `Theme.NoDisplay` — nessuna UI visibile
- `onCreate()` chiama `registerLocationUpdates` + `finish()`
- Ha intent-filter MAIN/LAUNCHER → icona visibile nel launcher
- Da eliminare quando si rimuove l'icona

### BootReceiver
- Riceve `BOOT_COMPLETED` e `LOCKED_BOOT_COMPLETED`
- Chiama `registerLocationUpdates(context)`
- `android:exported="true"` obbligatorio

### LocationReceiver
- Riceve fix GPS via PendingIntent da FusedLocationProvider
- Estrae `LocationResult.extractResult(intent)`
- Chiama `SheetsSender.saveAndSend()`
- `android:exported="false"`

### registerLocationUpdates()
- `LocationRequest`: `PRIORITY_HIGH_ACCURACY`, interval 5s (da portare a 15min — STEP 9)
- `PendingIntent.FLAG_MUTABLE` — obbligatorio per FusedLocationProvider
- `.addOnSuccessListener` / `.addOnFailureListener` per log

### SheetsSender
- `saveAndSend()`: salva su Room (sent=false) + tenta invio immediato
- `retrySend()`: invia tutti i punti pending su Room
- `sendPoint()`: OkHttp POST asincrono, marca `sent=true` su successo
- Endpoint hardcoded (da spostare in `secrets.properties` — STEP 11)
- DeviceId hardcoded `"where_device_01"` (da rendere configurabile — STEP 12)

### GpsPoint (Room Entity)
- campi: `id`, `lat`, `lng`, `accuracy`, `timestamp`, `sent`
- `sent=false` → pending, `sent=true` → inviato

### GpsPointDao
- `insert()`, `getPending()`, `markSent()`, `deleteSent()`

---

## Backend — Google Apps Script

- **doPost**: riceve `{lat, lng, accuracy, timestamp, deviceId}` → appende riga su Sheet
- **Sheet**: `where_tracker` — colonne: timestamp, deviceId, lat, lng, accuracy
- **Deploy**: Web App pubblica, esegui come utente proprietario
- URL endpoint: attualmente hardcoded in `SheetsSender.kt` (da spostare — STEP 11)

---

## Permessi manifest

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION"/>
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>
<uses-permission android:name="android.permission.INTERNET"/>
```

---

## Visibilità app — stato attuale

| Punto di visibilità | Stato |
|---|---|
| Icona launcher | ⚠️ Visibile (da rimuovere STEP 8) |
| App recenti | Non appare (nessuna Activity aperta) |
| Notifiche | Nessuna ✅ |
| Elenco app (Impostazioni) | Visibile — non eliminabile |
| Permessi posizione | Visibile — non eliminabile |

---

## Compatibilità Android

| Versione | Note critiche |
|---|---|
| 8 (Oreo) — minSdk | PendingIntent system-managed corretto |
| 10+ | `ACCESS_BACKGROUND_LOCATION` separata, richiede "Consenti sempre" |
| 12+ | Indicatore GPS status bar — NON scatta con PendingIntent ✅ |
| 12+ | `PendingIntent.FLAG_MUTABLE` obbligatorio per FusedLocation |
| 13+ | `LOCKED_BOOT_COMPLETED` nel manifest |

---

## OEM — note critiche background survival

| OEM | Rischio | Azione richiesta |
|---|---|---|
| Xiaomi / MIUI | Alto | Autostart manuale + battery unrestricted |
| Huawei / EMUI | Molto alto | App launch on startup manuale |
| Samsung / OneUI | Medio | Battery "Unrestricted" |
| Oppo / ColorOS | Medio | Attività in background ON (confermato funzionante) |
| Stock Android | Basso | Solo battery optimization standard |

---

## Configurazione manuale dispositivo (una tantum — non bypassabile)

1. **Battery optimization OFF**: Impostazioni → App → where → Batteria → "Non ottimizzare"
2. **Background location**: Impostazioni → App → where → Permessi → Posizione → "Consenti sempre"
3. **Attività in background ON**: Impostazioni → App → where → "Consenti attività in background"

---

## Prossimi step

| Step | Descrizione | Priorità |
|---|---|---|
| STEP 8 | Rimozione icona — elimina StartupActivity + intent-filter dal manifest | Alta |
| STEP 9 | Intervallo produzione: 15min / 100m displacement | Alta |
| STEP 10 | Test survival OEM: Xiaomi, Samsung, Huawei | Alta |
| STEP 11 | `secrets.properties` — URL endpoint fuori dal codice | Media |
| STEP 12 | `deviceId` configurabile — non hardcoded | Media |
| STEP 13 | Progetto `where-viewer` — mappa + settings | Futuro |

---

## Sistema completo — due progetti separati

| Progetto | Nome | Package | Stato |
|---|---|---|---|
| Tracker | `where` | `com.dev.where` | ✅ v1.0.0 completato |
| Viewer | `where-viewer` | `com.dev.viewer` | ⬜ da iniziare |

---

## Regole operative Claude ↔ Giol

1. Leggere questo documento **intero** prima di qualsiasi analisi
2. Per ogni bug: tracciare il percorso completo input → stato → funzione → output
3. Ogni modifica specifica: file esatto + riga + before + after
4. Patch applicate **solo al file sorgente compilato**, mai a copie di output
5. Trattare le cause, non i sintomi
6. Sviluppo incrementale: implementa → testa su device reale → conferma → procedi

---

*Sostituisce integralmente tutte le versioni precedenti (proto-app, GPS_Tracker_Contesto_Progetto.md)*
