# LeakWeb

> Work in progress. Provisional name.

Android library (Kotlin) that detects memory leaks and lets you inspect the app's memory from a
**web interface**, served by an HTTP server embedded in the debug build of your app.

- Automatic detection of objects retained after their lifecycle ends (Activity, Fragment, ViewModel, Service).
- Heap dump analysis with [Shark](https://github.com/square/leakcanary/tree/main/shark), showing the
  reference path to the GC root.
- Web UI in the desktop browser (via `adb forward`), protected by a token.
- Live memory usage, leak history and `.hprof` download.
- No-op module with the same public API for release builds.

The development plan is in [docs/PLANO.md](docs/PLANO.md).

## Installation

> Not published yet. Coordinates below are the planned ones.

```kotlin
dependencies {
    // The library: watchers, heap analysis and the embedded web server. Debug builds only.
    debugImplementation("io.github.rogerphilippe:leakweb-android:<version>")

    // Optional: only needed if your code calls the LeakWeb API (see below).
    releaseImplementation("io.github.rogerphilippe:leakweb-noop:<version>")
}
```

`leakweb-android` is the only dependency you declare for debug builds; the other modules
(`leakweb-api`, `-model`, `-watcher`, `-analyzer`, `-server`) come with it transitively.

### Do I need `leakweb-noop`?

- **No**, if you only use the automatic start (`androidx.startup`, port 8080) and never reference
  LeakWeb in your code. `debugImplementation` alone is enough.
- **Yes**, if your code calls the API — for example `LeakWeb.start(application, config)` in your
  `Application` (manual start with a custom port, getting the token back) or `LeakWeb.watch(obj, "...")`.
  The real library is only on the debug classpath, so without `leakweb-noop` those calls do not compile
  in release builds. `leakweb-noop` has the same public API with an empty implementation: `start()`
  returns an empty session and `watch()` does nothing. The same code compiles in both builds, and the
  release APK contains no HTTP server, no heap analyzer and no watchers.

Never ship `leakweb-android` in a release build: heap dumps expose everything that is in memory.

## Usage

LeakWeb can start by itself (automatic mode, the default) or be started from your code (manual mode).
Either way, the web server listens on the device; you reach it from the computer through `adb forward`,
and every request needs the session token.

### Automatic mode (default)

Add the dependency and run the debug build: nothing else to do. LeakWeb starts with the app
(through `androidx.startup`), watches destroyed activities, fragments, fragment views, ViewModels and
services, and starts the web server on
port **8080** (or the next free port). The URL with the token is printed in logcat, tag `LeakWeb`:

```
LeakWeb server running on port 8080. On the computer, run:
  adb forward tcp:8080 tcp:8080
then open http://localhost:8080/?token=Zm9vYmFyYmF6cXV4ZXhhbXBsZXRva2VuLWZvci1kb2Nz
```

Run the `adb forward` command and open the URL in the desktop browser. The first visit exchanges the
token for a session cookie, so later visits to `http://localhost:8080/` need no token. A new token is
generated each time the app starts.

### Manual mode

Use it to choose the port, keep the same token across restarts, listen on the local network or start
LeakWeb only when you want to. It needs `leakweb-noop` in release builds (see above).

1. Turn off the automatic start in the app manifest. Put it in `src/debug/AndroidManifest.xml`,
   since `leakweb-android` only exists in debug builds:

   ```xml
   <manifest xmlns:android="http://schemas.android.com/apk/res/android"
       xmlns:tools="http://schemas.android.com/tools">
       <application>
           <provider
               android:name="androidx.startup.InitializationProvider"
               android:authorities="${applicationId}.androidx-startup"
               tools:ignore="MissingClass"
               tools:node="merge">
               <meta-data
                   android:name="io.github.rogerphilippe.leakweb.LeakWebInitializer"
                   tools:node="remove" />
           </provider>
       </application>
   </manifest>
   ```

   The manifest merger removes only the LeakWeb entry; other `androidx.startup` initializers keep
   working. `tools:ignore="MissingClass"` is there because `androidx.startup` comes with
   `leakweb-android` and is not on your app's compile classpath, which Android Lint reports.

2. Start LeakWeb in your `Application`:

   ```kotlin
   class MyApplication : Application() {
       override fun onCreate() {
           super.onCreate()
           val session = LeakWeb.start(this, LeakWebConfig(port = 9090, persistToken = true))
           // session.port, session.token and session.url, e.g. to show them in a debug screen.
       }
   }
   ```

   In release builds `leakweb-noop` makes `start()` return `LeakWebSession.NOT_RUNNING` and do nothing.

`LeakWebConfig` options:

| Option | Default | Description |
|---|---|---|
| `port` | `8080` | Port of the web server |
| `portFallback` | `true` | Tries the next ports if `port` is in use; the real one is `session.port` |
| `bindAddress` | `Loopback` | `Loopback` (`127.0.0.1`, through `adb forward`) or `AllInterfaces` (local network too) |
| `token` | `null` | Fixed token, handy for bookmarks; `null` generates a random one |
| `persistToken` | `false` | Keeps the generated token across app restarts |
| `logStartup` | `true` | Logs the URL with the token and the `adb forward` command |
| `retainedThreshold` | `5` | Retained objects needed to dump the heap while the app is visible (1 in background) |
| `persistentNotification` | `false` | Shows an ongoing status notification with a shortcut back to the app |
| `watchers` | all | What is watched automatically (see [Watchers](#watchers)); empty keeps only the server and live memory |

Other calls: `LeakWeb.session` returns the running session, `LeakWeb.stop()` stops the web server
(leaks are still detected), and `start()` again with another config applies it: `watchers` and
`retainedThreshold` change right away without restarting the server, the other options restart it.
Calling `start()` with the same config just returns the current session. `LeakWeb.watch(obj, "reason")`
watches any object that should be garbage collected soon.

With `BindAddress.AllInterfaces`, logcat also shows the URL with the device IP. Anyone on the network
who has the token can download heap dumps, so use it only on trusted networks.

To opt in to the ongoing status notification, request permission from a visible Activity on Android 13+
and enable it in the config:

```kotlin
LeakWeb.requestNotificationPermission(this)
LeakWeb.start(application, LeakWebConfig(persistentNotification = true))
```

Call `start()` with the same config after the user grants permission if permission was requested later.
If notification permission is denied, LeakWeb continues working and logs that the notification was
not shown. The notification can be disabled by starting with `persistentNotification = false`.

### Watchers

| `WatcherType` | Watches | Needs |
|---|---|---|
| `ACTIVITY` | Activities after `onDestroy()` | — |
| `FRAGMENT` | AndroidX fragments after `onDestroy()` | AndroidX Fragment in the app |
| `FRAGMENT_VIEW` | Views of AndroidX fragments after `onDestroyView()` (a view or binding kept in a field) | AndroidX Fragment in the app |
| `VIEW_MODEL` | AndroidX ViewModels after `onCleared()`, in activities and fragments | AndroidX Lifecycle in the app |
| `SERVICE` | Services after `onDestroy()` | — |

Watched objects still in memory ~5 seconds later (after a GC) are reported as retained. Besides them,
every heap analysis also reports objects that are obviously dead even if nothing watched them
(destroyed activity, destroyed fragment, detached view, stopped service...), so a manual heap dump
finds them too.

`VIEW_MODEL` and `SERVICE` rely on internals that Android and AndroidX do not expose:

- **ViewModels**: LeakWeb replaces the private map of each `ViewModelStore`, by reflection, with a copy
  that reports its ViewModels when the store is cleared.
- **Services**: there is no callback for a destroyed service. LeakWeb hooks the main thread handler of
  `ActivityThread` and wraps the app's `IActivityManager` in a proxy (non-SDK interfaces, the same
  technique as LeakCanary) to see `serviceDoneExecuting()` after `onDestroy()`.

A future Android or AndroidX version may change or block these internals. LeakWeb then logs a warning
and leaves that watcher off; the rest keeps working. To avoid them, leave the type out of `watchers`.

### Live memory

The Memory section of the web UI shows the Java heap, the native heap and the PSS of the app process.
Live monitoring is **off by default**: the section then shows the values read when it opens, and
nothing is sampled. "Start live monitoring" samples every second (PSS every 10 seconds, it is slower to
read) with a chart of the last 5 minutes. It keeps running in the app, even with the page closed or
after an app restart, until "Stop live monitoring"; starting it again clears the chart.

### HTTP API

The web UI uses a JSON API that you can also call from scripts. Send the token as
`Authorization: Bearer <token>` or `X-LeakWeb-Token: <token>`:

```sh
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/leaks
```

| Method | Route | Description |
|---|---|---|
| GET | `/api/status` | Library version, app, process, port, watched/retained object counts |
| GET | `/api/leaks` | Leaks grouped by signature |
| GET | `/api/leaks/{signature}` | Full leak trace and the analyses where it appeared |
| GET | `/api/analyses` | Analysis history |
| GET | `/api/analyses/{id}/hprof` | Downloads the heap dump |
| DELETE | `/api/analyses/{id}` | Deletes an analysis and its heap dump |
| GET | `/api/retained` | Objects retained right now, not analyzed yet |
| POST | `/api/heapdump` | Dumps and analyzes the heap now |
| POST | `/api/gc` | Runs the garbage collector |
| GET | `/api/memory` | Current memory usage |
| GET | `/api/memory/history` | Memory samples since live monitoring was started (5 minutes at most), oldest first |
| POST | `/api/memory/live/start` | Starts live memory monitoring (clears the history) |
| POST | `/api/memory/live/stop` | Stops live memory monitoring |
| GET | `/api/events` | Server-Sent Events: memory samples, live monitoring on/off, retained objects, heap dumps, analysis progress, new leaks |

Responses are wrapped as `{"schemaVersion": 1, "data": ...}`; errors as
`{"schemaVersion": 1, "error": {"status": 404, "message": "..."}}`.

## Modules

| Module | Type | Purpose |
|---|---|---|
| `leakweb-api` | JVM | Public types shared by `android` and `noop` |
| `leakweb-model` | JVM | Serializable models |
| `leakweb-watcher` | JVM | `KeyedWeakReference` and the object watcher |
| `leakweb-analyzer` | JVM | Shark wrapper: hprof → analysis |
| `leakweb-server` | JVM | Embedded HTTP server, REST API, SSE and web frontend |
| `leakweb-android` | Android | Watchers, heap dumper, store, startup wiring |
| `leakweb-noop` | Android | Same public API, empty implementation (release) |
| `sample` | App | App with intentional leaks for testing; uses the manual mode on port 9090 |
| `build-logic` | Gradle | Convention plugins |

## Building

Requirements: Android SDK and JDK 17+ (the Gradle daemon and toolchains are provisioned automatically).

```sh
./gradlew build          # compile, unit tests, Detekt and Spotless checks
./gradlew spotlessApply  # format code and add the copyright header
```

## License

Not defined yet. All rights reserved until a license is chosen.
