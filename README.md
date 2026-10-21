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
| `sample` | App | App with intentional leaks for testing |
| `build-logic` | Gradle | Convention plugins |

## Building

Requirements: Android SDK and JDK 17+ (the Gradle daemon and toolchains are provisioned automatically).

```sh
./gradlew build          # compile, unit tests, Detekt and Spotless checks
./gradlew spotlessApply  # format code and add the copyright header
```

## License

Not defined yet. All rights reserved until a license is chosen.
