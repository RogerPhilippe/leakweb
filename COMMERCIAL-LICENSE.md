[English](COMMERCIAL-LICENSE.md) · [Português (BR)](COMMERCIAL-LICENSE.pt-BR.md) · [Español](COMMERCIAL-LICENSE.es.md)

# Commercial Licensing

LeakWeb is open-source software, distributed under a **dual license**:

1. **GNU Affero General Public License v3.0 (AGPLv3)** — free, for anyone or
   any company that uses, modifies, and redistributes the project (or offers
   it as a network service) **as long as they comply with the AGPLv3
   terms**, which includes making the complete source code of any modified
   version available, under the same license, to anyone who receives the
   software or uses the service.

2. **Commercial license**, granted **only at the sole discretion of the
   maintainer**, for those who want to use LeakWeb in ways the AGPLv3 does not
   allow, for example:
   - Selling LeakWeb, or a modified or rebranded version of it, as a
     closed-source product;
   - Embedding LeakWeb (or parts of it) in proprietary software or in a
     closed-source SDK that is distributed to third parties;
   - Distributing modified versions without sharing their source code.

## Purpose

The commercial license exists as a **safeguard**, not as a product for sale.
LeakWeb is meant to stay free and open. This option ensures that no one can
take the project and sell it as closed-source software without the
maintainer's explicit permission.

## Using LeakWeb in your own app

LeakWeb is a debugging tool meant for `debug` builds (`debugImplementation`).
The AGPLv3 obligations apply when you **convey** the software to others (for
example, handing an APK that contains LeakWeb to people outside your
organization) or when you **modify** it and let others interact with the
modified version over a network.

- Using the unmodified library in debug builds that stay inside your team
  does not distribute LeakWeb to anyone.
- Release builds should not contain LeakWeb at all: use `leakweb-noop`,
  which has the same public API and an empty implementation.
- If you modify LeakWeb, or distribute a build that contains it to third
  parties, either comply with the AGPLv3 or ask for a commercial license.

This section is a plain-language summary and is not legal advice. If in doubt,
ask a lawyer or contact the maintainer.

## When you need permission

If you want to use LeakWeb in a way the AGPLv3 **does not allow without
opening the corresponding code**, you must obtain written permission from the
maintainer. Requests may be refused.

## Contact

Roger Philippe Pereira — rogerphilippepereira@gmail.com

## About contributions

By contributing code to this project, you agree to the terms described in
[CONTRIBUTING.md](CONTRIBUTING.md), which include granting the maintainer
the right to relicense your contribution — that's what makes this
dual-license model possible.
