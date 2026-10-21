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

## Modules

| Module | Type | Purpose |
|---|---|---|
| `leakweb-api` | JVM | Public types shared by `android` and `noop` |
| `leakweb-model` | JVM | Serializable models |
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
