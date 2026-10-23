[English](CONTRIBUTING.md) · [Português (BR)](CONTRIBUTING.pt-BR.md) · [Español](CONTRIBUTING.es.md)

# Contributing to LeakWeb

Thanks for your interest in contributing! Please read the following before opening a Pull Request.

## How to contribute

1. Open an Issue describing the bug or proposed feature, to align before coding (avoids rework).
2. Fork the repository and create a branch from `main`.
3. Follow the code style already used in the project (Kotlin, Detekt and ktlint through Spotless).
4. Run `./gradlew spotlessApply` to format the code and add the file header, then `./gradlew build`
   (compile, unit tests, Detekt, Spotless and lint) before opening the PR.
5. Add or update tests for what you changed.
6. Clearly describe, in the PR description, what was changed and why.

Requirements: Android SDK and JDK 17+ (see the [README](README.md#building)).

## Clean-room rule

LeakWeb is written from scratch. **Do not submit code copied or adapted
from any project whose license is incompatible with this
project's dual-license model** (for example, GPL-only code). Contributions
must be your own original work, or come from sources under permissive
licenses (MIT, BSD, Apache-2.0, ISC) with their notices preserved.

New dependencies must also have a license compatible with both options
(Apache-2.0, MIT, BSD or similar). GPL and AGPL dependencies are not accepted.

## Licensing of contributions and Contributor Agreement

This project uses a **dual-license** model: it's publicly available under
[AGPLv3](LICENSE), and also offered under a
[separate commercial license](COMMERCIAL-LICENSE.md) for those who can't
comply with the AGPLv3 terms.

For this model to work legally, the project maintainer (Roger Philippe
Pereira) needs to hold the rights necessary to relicense the complete code
commercially, including third-party contributions.

**By submitting a contribution (Pull Request, patch, or any other
material) to this project, you agree that:**

1. Your contribution is licensed under the AGPLv3, like the rest of the
   project; and
2. You grant the project maintainer (Roger Philippe Pereira) a perpetual,
   worldwide, non-exclusive, royalty-free license to also relicense your
   contribution under commercial terms, as part of the dual-license model
   described in [COMMERCIAL-LICENSE.md](COMMERCIAL-LICENSE.md); and
3. You represent that you have the right to make this contribution and this
   grant (for example, that the code is yours or that you have your
   employer's authorization, if applicable).

The pull request template asks you to confirm this agreement. If you don't
agree with these terms, unfortunately we can't accept your contribution to
this project.
