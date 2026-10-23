# Changelog

All notable changes to this project are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project
follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Memory leak detection for activities, fragments, fragment views, ViewModels and services.
- Heap dump analysis with Shark, including objects that are dead even when nobody watched them.
- Embedded web server with token authentication, REST API, Server-Sent Events and web UI.
- Live memory monitoring and `.hprof` download.
- Heap analysis in a separate process, free space check before dumping, and an optional persistent
  notification.
- `leakweb-noop` module with the same public API for release builds.
- Dual license (AGPLv3 and commercial), contributing guide and README in English, Portuguese (Brazil)
  and Spanish.

### Fixed

- Heap analysis no longer fails with `KotlinNullPointerException` when Shark's Android filter for
  `Toast` finds a toast without the field it expects.
