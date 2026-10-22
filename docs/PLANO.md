# LeakWeb — Plano de desenvolvimento

> Nome provisório. Troque por qualquer nome antes de publicar (pacote base sugerido: `io.github.rogerphilippe.leakweb`).

Biblioteca Android (Kotlin) open source para **detectar vazamentos de memória** e **inspecionar a memória do app** por uma **interface web** (HTML5 + CSS moderno), servida por um servidor HTTP embutido no próprio app de debug.

---

## 1. Objetivos

- Detectar automaticamente objetos retidos após o fim do ciclo de vida (Activity, Fragment, View de Fragment, ViewModel, Service).
- Gerar heap dump, analisar com **Shark** e apresentar o caminho de referências até a GC root.
- Servir uma interface web acessível no navegador do computador (via `adb forward`) ou pela rede local, sempre protegida por token.
- Mostrar uso de memória ao vivo, histórico de vazamentos e permitir download do `.hprof`.
- Zero configuração: adicionar a dependência `debugImplementation` deve bastar (início automático na porta 8080).
- Controle explícito quando necessário: iniciar no `Application` com porta própria e receber o token de volta.
- Zero impacto em release: módulo no-op com a mesma API pública.

### Não-objetivos (por enquanto)

- Substituir o Android Studio Profiler.
- Suporte a Kotlin Multiplatform ou iOS.
- Detecção em produção / envio de relatórios para servidor remoto.

### 1.1 Posicionamento vs LeakCanary (decidido: opção A)

O LeakCanary (Apache 2.0) já detecta vazamentos e tem UI no aparelho. O diferencial do LeakWeb é a **interface web**, a **memória ao vivo** e o **download do `.hprof`** — não a detecção em si. Decidir antes da Fase 2:

| Opção | Escopo | Prós | Contras |
|---|---|---|---|
| A. Watcher próprio (plano atual) | Fases 2–3 completas | Controle total, sem dependência do LeakCanary | Reimplementa código maduro; exige `LeakingObjectFinder` e `ObjectInspector` próprios |
| B. Reusar `leakcanary-object-watcher-android` | Fases 2–3 reduzidas | `KeyedWeakReferenceFinder` e inspectors do Shark funcionam direto; installers prontos | Desligar o `AppWatcher` automático dele; acoplamento a versões do LeakCanary |
| C. UI web para o LeakCanary (`LeakCanary.config.eventListeners`) | Fases 2–3 quase eliminadas | Menor esforço; foco total no diferencial | Produto vira complemento do LeakCanary |

Se código do LeakCanary for copiado/adaptado, manter o aviso de copyright e o `NOTICE` da Apache 2.0.

**Decisão: opção A** (issue #10). `ViewModelWatcher` e `ServiceWatcher` (Fase 6, reflection) podem ser adaptados do LeakCanary, preservando o aviso de copyright e o `NOTICE`. Um adaptador opcional para `LeakCanary.config.eventListeners` (opção C como funcionalidade extra) fica para o futuro.

---

## 2. Decisões técnicas

| Tema | Decisão | Motivo |
|---|---|---|
| Linguagem | Kotlin 100% | — |
| Análise de heap | `com.squareup.leakcanary:shark-android` | Parser de hprof + algoritmo de caminho mais curto já maduros (Apache 2.0) |
| Servidor HTTP | **NanoHTTPD** (`org.nanohttpd:nanohttpd`) | Minúsculo (um arquivo Java), sem dependências, Java puro: roda no Android e na JVM |
| Roteamento | Router próprio e pequeno (método + padrão de path) | NanoHTTPD não tem router; manter a lib sem dependências extras |
| Serialização | kotlinx.serialization | — |
| Frontend | HTML + CSS + JS **sem build step** (ES modules, Web Components) | Sem Node no build da lib, assets pequenos, fácil de contribuir |
| CSS | Nesting nativo, container queries, `:has()`, `color-mix()`, `light-dark()`, custom properties | "CSS moderno" sem framework |
| Tempo real | Server-Sent Events (SSE) via resposta *chunked* do NanoHTTPD | Unidirecional e simples; comandos vão por POST, então WebSocket não é necessário |
| Inicialização | Automática via `androidx.startup` **ou** manual com `LeakWeb.start(app, config)` no `Application` | Zero config por padrão; controle de porta e acesso ao token quando preciso (ver seção 4.1) |
| Autenticação | Token aleatório obrigatório em todas as rotas, devolvido por `start()` | Heap dump expõe qualquer dado em memória; outro app no aparelho também alcança `127.0.0.1` |
| Concorrência | Coroutines, dispatcher próprio para análise | — |
| Licença | **Em aberto.** Provável licença dupla: AGPLv3 + comercial | Dependências (Shark: Apache 2.0, NanoHTTPD: BSD-3, kotlinx: Apache 2.0) são compatíveis com AGPLv3. Ver seção 7.1 |
| Publicação | JitPack no início, Maven Central depois | — |
| minSdk | 23 | Libs AndroidX recentes já exigem 23 |
| Processos | Só o processo principal inicia watchers e servidor | `Application.onCreate` e `androidx.startup` rodam em todos os processos do app |

---

## 3. Estrutura de módulos

```
leakweb/
├─ leakweb-api/         (JVM)     Tipos públicos compartilhados por android e noop: LeakWebConfig, LeakWebSession, BindAddress, WatcherType
├─ leakweb-model/       (JVM)     Modelos serializáveis: Leak, LeakTrace, HeapAnalysis, MemorySample, Event
├─ leakweb-watcher/     (JVM)     KeyedWeakReference e (Fase 2) ObjectWatcher com Clock/GcTrigger injetáveis
├─ leakweb-analyzer/    (JVM)     Wrapper do Shark: hprof → HeapAnalysis (modelo próprio)
├─ leakweb-server/      (JVM)     NanoHTTPD: router, rotas REST, SSE, arquivos estáticos (frontend em resources/leakweb/web)
├─ leakweb-android/     (Android) Watcher, installers de ciclo de vida, heap dumper, store, startup, wiring
├─ leakweb-noop/        (Android) Mesma API pública, implementação vazia (release)
├─ sample/              (App)     App com vazamentos propositais para testar
└─ build-logic/                   Convention plugins (Gradle) compartilhados
```

Regra: tudo que **não depende de Android** fica em módulo JVM, para ser testado com JUnit puro e rápido.

`leakweb-android` e `leakweb-noop` expõem `api(project(":leakweb-api"))`; só o `object LeakWeb` é duplicado entre os dois, evitando divergência dos tipos de configuração.

O usuário da lib declara só `leakweb-android` (debug) e, opcionalmente, `leakweb-noop` (release); os demais módulos vêm como dependências transitivas.

---

## 4. Arquitetura

```
 ┌───────────── leakweb-android ─────────────┐
 │                                           │
 │  Installers (Activity/Fragment/VM/Service)│
 │          │ onDestroy → watch(obj)         │
 │          ▼                                │
 │     ObjectWatcher ── retido? ──► HeapDumpTrigger
 │      (KeyedWeakReference +       (threshold, app visível?)
 │       ReferenceQueue)                     │
 │                                           ▼
 │                                   HeapDumper (Debug.dumpHprofData)
 │                                           │ arquivo .hprof
 │                                           ▼
 │                         leakweb-analyzer (Shark) ─► HeapAnalysis
 │                                           │
 │                                           ▼
 │                              LeakStore (JSON em filesDir)
 │                                 │                │
 │      MemorySampler ──► EventBus ◄┘                │
 │                          │                       │
 └──────────────────────────┼───────────────────────┘
                            ▼                       ▼
                 leakweb-server (NanoHTTPD, 127.0.0.1:PORT)
                    /api/*  •  /api/events (SSE)  •  /  (SPA)
                            ▲
                 navegador ─┘  (adb forward tcp:PORT tcp:PORT)
```

### Detalhes importantes

- **ObjectWatcher**: `watch(obj, description)` cria `KeyedWeakReference(obj, key = UUID, description, watchUptimeMillis, queue)`. Após ~5 s, remove as referências já enfileiradas; se sobrar, força GC (`Runtime.gc()` + pequena espera + `System.runFinalization()`) e verifica de novo. Os que restam são **retidos**.
- **HeapDumpTrigger**: dispara dump quando retidos ≥ threshold (ex.: 5 com app visível, 1 em background). Debounce para não dumpar em sequência.
- **LeakingObjectFinder próprio** *(A)*: o `KeyedWeakReferenceFinder` do Shark procura a classe `leakcanary.KeyedWeakReference`. Como a classe será nossa, é preciso implementar um `LeakingObjectFinder` que encontre instâncias da **nossa** `KeyedWeakReference` no heap e leia o campo `referent`.
- **ObjectInspector próprio** *(A)*: pelo mesmo motivo, o inspector `KEYED_WEAK_REFERENCE` do Shark não reconhece a nossa classe. Implementar um `ObjectInspector` que marque o objeto observado como **LEAKING** ("observado desde X ms, descrição Y"), senão o status do nó vazado no trace sai errado/unknown. Em vez de um campo estático mutável `heapDumpUptimeMillis` (como o LeakCanary), o uptime do dump é passado como parâmetro ao `HprofAnalyzer`, para calcular há quanto tempo o objeto está retido.
- **Análise**: `HeapAnalyzer` do Shark com `AndroidReferenceMatchers.appDefaults` e `AndroidObjectInspectors.appDefaults` + o inspector próprio (em heaps de JVM, usados nos testes, `AndroidReferenceMatchers.ignoredReferencesOnly` e `ObjectInspectors.jdkDefaults`, pois os defaults Android leem `android.os.Build`), `computeRetainedHeapSize = true`. Converter o resultado para os modelos de `leakweb-model` (não expor tipos do Shark na API pública). Usar o `signature` que o Shark já calcula por `LeakTrace` como assinatura do vazamento.
- **Somente processo principal**: `LeakWebInitializer` e `LeakWeb.start` verificam o nome do processo (`Application.getProcessName()` no API 28+, `/proc/self/cmdline` antes) e não fazem nada fora do processo principal — inclusive no futuro `:leakweb`. Sem isso, cada processo sobe watchers e um servidor próprio, e o fallback troca a porta silenciosamente.
- **Processo separado** (fase posterior): mover a análise para um `Service` em `android:process=":leakweb"` para não derrubar o app em aparelhos fracos. Comunicação por arquivo + broadcast; o processo principal recarrega o `LeakStore` ao receber o broadcast.
- **Heap dump congela o app**: chamar `Debug.dumpHprofData` fora da main thread (o dump pausa todas as threads de qualquer forma, mas evita ANR por bloqueio da main antes/depois) e avisar no logcat e com Toast/notificação antes de dumpar.
- **Permissão**: o manifest do `leakweb-android` declara `android.permission.INTERNET` (necessária para abrir o `ServerSocket`); entra no app só via `debugImplementation`.
- **Coexistência com LeakCanary**: se `leakcanary.LeakCanary` estiver no classpath, avisar no logcat (os dois disparariam dumps). Opção de config para desligar os watchers e manter só servidor + memória ao vivo.
- **Servidor NanoHTTPD**: subclasse de `NanoHTTPD(hostname, port)` cujo `serve(session)` apenas delega para o `Router`. Cada rota é uma função `(Request) -> Response`, testável sem socket.
- **SSE no NanoHTTPD**: `newChunkedResponse(Status.OK, "text/event-stream", stream)`, onde `stream` é um `InputStream` próprio (`EventStreamInputStream`) alimentado por uma `LinkedBlockingQueue<ByteArray>`. Uma coroutine coleta o `Flow<Event>`, formata `event: ...\ndata: {json}\n\n` e enfileira. Enviar um comentário `: ping` a cada ~15 s; quando o NanoHTTPD fechar o stream (cliente desconectou), cancelar a coroutine. Evitar `PipedInputStream` (quebra se a thread escritora morrer). Headers: `Cache-Control: no-cache`, `Connection: keep-alive`.
- **SSE sem gzip**: por padrão o NanoHTTPD (`useGzipWhenAccepted`) comprime qualquer MIME `text/*`, inclusive `text/event-stream`; o `GZIPOutputStream` segura os bytes em buffer e os eventos nunca chegam. Sobrescrever `useGzipWhenAccepted` para devolver `false` em `text/event-stream` (e no download do `.hprof`). Cobrir com teste de integração enviando `Accept-Encoding: gzip`.
- **Arquivos estáticos**: frontend em `leakweb-server/src/main/resources/leakweb/web` (prefixo `leakweb/` para não colidir com resources `web/` de outras libs no APK), lido via `javaClass.classLoader.getResourceAsStream` (funciona no Android, pois recursos Java de JARs vão para o APK). Mapear MIME por extensão (`.js` → `text/javascript`, obrigatório para ES modules).
- **Threads**: o NanoHTTPD usa uma thread por conexão; cada aba com SSE aberta segura uma thread. Limitar clientes SSE simultâneos (ex.: 4).

### 4.1 Inicialização e token

**Modo automático (padrão):** o `LeakWebInitializer` (`androidx.startup`) instala os watchers e sobe o servidor na porta 8080. O token e a URL pronta são impressos no logcat:

```
I/LeakWeb: Servidor em http://localhost:8080/?token=Xk3...  (adb forward tcp:8080 tcp:8080)
```

**Modo manual:** desativa-se o início automático no manifest do app e chama-se `start` no `Application`:

```xml
<!-- AndroidManifest.xml (src/debug) -->
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    tools:node="merge">
    <meta-data
        android:name="io.github.rogerphilippe.leakweb.LeakWebInitializer"
        tools:node="remove" />
</provider>
```

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val session = LeakWeb.start(
            application = this,
            config = LeakWebConfig(
                port = 9090,
                logStartup = false,   // não imprime nada; você decide o que fazer com o token
            ),
        )
        Log.d("App", "LeakWeb token=${session.token} url=${session.url}")
        // ou: breakpoint aqui e inspecionar `session`
    }
}
```

API pública (esboço):

```kotlin
object LeakWeb {
    fun start(application: Application, config: LeakWebConfig = LeakWebConfig()): LeakWebSession
    fun stop()
    val session: LeakWebSession?          // sessão atual, se iniciada
    fun watch(obj: Any, description: String)
}

data class LeakWebConfig(
    val port: Int = 8080,
    val portFallback: Boolean = true,      // tenta a próxima porta livre se a escolhida estiver ocupada
    val bindAddress: BindAddress = BindAddress.Loopback,   // Loopback | AllInterfaces
    val token: String? = null,             // null = gera aleatório; fixo útil para favoritar URL
    val persistToken: Boolean = false,     // true = reaproveita o token gerado entre reinícios (filesDir)
    val logStartup: Boolean = true,        // imprime URL + token no logcat
    val retainedThreshold: Int = 5,
    val watchers: Set<WatcherType> = WatcherType.defaults,
)

data class LeakWebSession(
    val token: String,
    val port: Int,                         // porta real (pode diferir da pedida se houve fallback)
    val url: String,                       // http://localhost:<port>/?token=<token>
)
```

Regras:
- `start` é **idempotente**: chamado de novo com a mesma config devolve a sessão atual; com config diferente, reinicia o servidor e devolve a nova sessão.
- Retorna a porta **real**, por isso devolve um objeto e não só a string do token.
- No módulo `leakweb-noop`, `start` não faz nada e devolve uma sessão vazia (`token = ""`, `port = -1`), para o código do `Application` compilar igual em release.

**Token:**
- 32 bytes de `SecureRandom` em Base64 URL-safe, gerado a cada `start` (a menos que `config.token` seja fixo ou `persistToken = true`).
- **Trade-off de DX**: com token novo a cada `start`, todo reinício do app invalida o cookie e exige copiar a URL do logcat de novo. `persistToken = true` guarda o token em `filesDir/leakweb/token` (aceitável em build debug; desligado por padrão para manter a postura de segurança).
- Comparação em tempo constante (`MessageDigest.isEqual`).
- Aceito em `Authorization: Bearer <token>`, `X-LeakWeb-Token` ou `?token=` na primeira visita.
- Ao abrir `/?token=...`, o servidor responde com cookie `HttpOnly; SameSite=Strict; Path=/` e redireciona para `/` sem o token na URL. Isso é necessário porque o `EventSource` (SSE) do navegador não envia headers customizados; o cookie resolve.
- **Nome do cookie inclui a porta** (`leakweb_<port>`): cookies não são isolados por porta, então dois apps com LeakWeb em `localhost:8080` e `localhost:8081` sobrescreveriam o cookie um do outro.
- Sem token válido: `401` em JSON para `/api/*` e uma página simples pedindo o token para `/`. A UI trata `401` após reinício do app mostrando "sessão expirada, abra a nova URL do logcat"; o `EventSource` reconecta sozinho quando o app volta.

---

## 5. API HTTP (contrato inicial)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/` | SPA |
| GET | `/api/status` | Versão, app, processo, porta, nº de objetos observados/retidos |
| GET | `/api/leaks` | Lista resumida (assinatura, classe, nº ocorrências, retained size, última vez) |
| GET | `/api/leaks/{signature}` | Trace completo + ocorrências |
| GET | `/api/analyses` | Histórico de análises (sucesso/falha, duração, tamanho do dump) |
| GET | `/api/analyses/{id}/hprof` | Download do `.hprof` |
| DELETE | `/api/analyses/{id}` | Apaga análise e dump |
| GET | `/api/retained` | Objetos atualmente retidos (ainda não analisados) |
| POST | `/api/heapdump` | Dispara dump manual |
| POST | `/api/gc` | Força GC |
| GET | `/api/memory` | Snapshot de memória atual |
| GET | `/api/memory/history` | Amostras de memória desde que o ao vivo foi ligado (máx. 5 min) |
| POST | `/api/memory/live/start` | Liga o monitoramento de memória ao vivo (limpa o histórico) |
| POST | `/api/memory/live/stop` | Desliga o monitoramento de memória ao vivo |
| GET | `/api/events` | SSE: `memory.sample`, `memory.live`, `object.retained`, `heapdump.started`, `heapdump.failed`, `analysis.progress`, `analysis.done`, `leak.new` |

Autenticação: **todas** as rotas exigem token (cookie de sessão, `Authorization: Bearer`, `X-LeakWeb-Token` ou `?token=`), inclusive em loopback. Ver seção 4.1.

Validação: `{id}` e `{signature}` aceitos só no formato esperado (ex.: `^[A-Za-z0-9_-]{1,64}$`), senão `400`. O `id` nunca é concatenado direto num caminho de arquivo: é resolvido pelo `LeakStore` (evita path traversal no download/DELETE).

Respostas JSON incluem `schemaVersion`; o `LeakStore` grava o mesmo campo no disco para permitir migração entre versões da lib.

---

## 6. Fases

Cada fase termina com código compilando, testes passando e um commit/PR.

### Fase 0 — Fundação
- [x] Projeto Gradle multi-módulo com version catalog (`libs.versions.toml`) e convention plugins em `build-logic`.
- [x] Módulos vazios criados conforme a seção 3 (incluindo `leakweb-api` e `leakweb-noop`).
- [x] `sample` dependendo de `leakweb-android` em debug e `leakweb-noop` em release.
- [x] Detekt + ktlint (ou Spotless) configurados.
- [x] GitHub Actions: build + testes em PR.
- [x] `README.md` inicial. **Sem arquivo `LICENSE` por enquanto** (licença em aberto; repositório privado até a decisão).
- [x] Cabeçalho de copyright nos arquivos-fonte sem identificador de licença (ex.: `Copyright (c) 2026 Roger Philippe`), para facilitar a aplicação posterior.

**Aceite:** `./gradlew build` verde localmente e no CI.

### Fase 1 — Modelos e analisador (JVM)
- [x] Modelos em `leakweb-model`: `HeapAnalysis` (Success/Failure), `Leak`, `LeakTrace`, `LeakTraceReference`, `LeakTraceObject` (status leaking/not leaking/unknown + motivo), `MemorySample`, `Event`.
- [x] `KeyedWeakReference` própria em módulo JVM (usada pelo analyzer nos testes e pelo watcher na Fase 2) *(A)*.
- [x] `HprofAnalyzer` em `leakweb-analyzer` com `LeakingObjectFinder` e `ObjectInspector` próprios *(A)*.
- [x] Mapeamento Shark → modelos próprios.
- [x] Callback de progresso (etapas do Shark) para alimentar SSE depois.

**Aceite:** teste unitário que cria um vazamento proposital na própria JVM de teste (objeto observado por `KeyedWeakReference` e retido por um campo estático), gera o hprof com `HotSpotDiagnosticMXBean.dumpHeap` em diretório temporário e encontra o vazamento esperado com status LEAKING. Nada de `.hprof` versionado no git (dezenas de MB). Um teste extra com hprof Android real fica opcional (Git LFS ou baixado no CI) e entra só depois da Fase 3.

### Fase 2 — Watcher e installers (Android)
- [x] `ObjectWatcher` com `Clock` e `GcTrigger` injetáveis (testáveis).
- [x] `ActivityWatcher` (`ActivityLifecycleCallbacks.onActivityDestroyed`).
- [x] `FragmentWatcher` (AndroidX `FragmentLifecycleCallbacks`: `onFragmentDestroyed` e `onFragmentViewDestroyed` para a View). `androidx.fragment` como `compileOnly`, instalado só se a classe existir em runtime — a lib não impõe a dependência ao app.
- [x] Guarda de processo principal (seção 4) no ponto de instalação dos watchers.
- [x] API pública `LeakWeb.watch(obj, description)` para objetos arbitrários.
- [x] Log de objetos retidos no logcat.

**Aceite:** no `sample`, uma Activity vazada aparece como retida no logcat após rotação/back.

### Fase 3 — Heap dump + store
- [x] `HeapDumpTrigger` com threshold, debounce e verificação de app visível.
- [x] `HeapDumper` (`Debug.dumpHprofData`) em `filesDir/leakweb/dumps`.
- [x] Análise em background (coroutine, dispatcher dedicado) — ainda no mesmo processo.
- [x] `LeakStore`: persistência em JSON com `schemaVersion`, agrupamento por **assinatura** do vazamento (`signature` do Shark), limite de N dumps com remoção dos mais antigos.
- [x] `EventBus` (`SharedFlow`) com os eventos da seção 5.

**Aceite:** vazamento do `sample` gera dump, análise e registro no store, sem intervenção manual.

### Fase 4 — Servidor
- [x] `LeakWebServer` (subclasse de `NanoHTTPD`) em `leakweb-server` recebendo interfaces (`LeakStoreReader`, `Commands`, `Flow<Event>`) — sem dependência de Android.
- [x] `Router` próprio: registro por método + padrão (`/api/leaks/{signature}`), extração de parâmetros, 404/405, tratamento de exceção → 500 em JSON.
- [x] Rotas da seção 5, JSON com kotlinx.serialization.
- [x] SSE com `EventStreamInputStream` + resposta chunked, sem gzip (ver seção 4).
- [x] Servir arquivos estáticos do classpath com MIME correto.
- [x] Bind em `127.0.0.1` por padrão, porta configurável com fallback se ocupada.
- [x] Autenticação por token em todas as rotas, com troca por cookie (nome com a porta) na primeira visita (seção 4.1).
- [x] Validação do header `Host` (`localhost`/`127.0.0.1`/IP do bind) contra DNS rebinding, e do `Origin` em POST/DELETE; validação de formato de `{id}`/`{signature}` (seção 5).
- [x] Header `Content-Security-Policy` restritivo (`default-src 'self'`) nas respostas HTML.
- [x] API pública `LeakWeb.start(application, config): LeakWebSession`, `stop()` e `session`, com `start` idempotente.
- [x] `LeakWebConfig` e `LeakWebSession` em `leakweb-api`, conforme a seção 4.1.
- [x] `leakweb-noop` com o mesmo `object LeakWeb` (sessão vazia), já nesta fase — o `sample` em release passa a usar a API real.
- [x] Wiring em `leakweb-android` + `LeakWebInitializer` (`androidx.startup`) chamando `start` com config padrão.
- [x] Modo manual documentado (remoção do initializer no manifest) e demonstrado no `sample` com porta diferente de 8080.
- [x] README com instruções dos dois modos: automático (padrão, porta 8080, URL com token no logcat, `adb forward`) e manual (remoção do initializer no manifest + `LeakWeb.start(application, config)` no `Application`, acesso ao token/porta pela `LeakWebSession`).
- [x] Logcat ao iniciar (se `logStartup`): URL completa com token + comando `adb forward`; com `BindAddress.AllInterfaces`, também a URL com o IP da rede local.

**Aceite:** testes unitários do `Router` e das rotas (sem socket) + testes de integração subindo o servidor em porta aleatória na JVM (incluindo leitura de SSE com `Accept-Encoding: gzip`, desconexão e liberação da thread); `curl` funcionando no `sample`.

### Fase 5 — Interface web (MVP)
- [x] `resources/leakweb/web/index.html`, `app.js` (ES module), `styles.css`. Sem frameworks.
- [x] Todo texto vindo do heap (nomes de classe, campos, valores) renderizado com `textContent`, nunca `innerHTML` — evita XSS por strings do app.
- [x] Layout: barra lateral (Vazamentos, Retidos, Memória, Análises) + área principal.
- [x] Lista de vazamentos com busca e ordenação por retained size.
- [x] Tela de detalhe do trace: cadeia vertical GC root → objeto vazado, destacando referências suspeitas, status e motivo de cada nó.
- [x] Indicador ao vivo via `EventSource` (objetos retidos, progresso de análise, toast de novo vazamento).
- [x] Botões: forçar GC, dump manual, baixar `.hprof`, apagar análise.
- [x] Tema claro/escuro com `light-dark()` e responsivo.

**Aceite:** fluxo completo no navegador: provocar vazamento no `sample` → ver progresso → abrir trace → baixar hprof.

### Fase 6 — Memória ao vivo e mais installers
- [x] `MemorySampler`: heap Java (`Runtime`), nativo (`Debug.getNativeHeapAllocatedSize`), PSS periódico (mais caro, intervalo maior), contagem de objetos observados.
  - Desligado por padrão: só amostra depois de ligado na tela Memória, e fica ligado (inclusive após reiniciar o app) até ser desligado. Ligar limpa o histórico. Desligado, a tela mostra uma leitura pontual.
- [x] Gráfico de memória em tempo real (SVG ou `<canvas>` desenhado à mão).
- [x] `ViewModelWatcher`: não há como observar o `onCleared` de ViewModels de terceiros. Abordagem do LeakCanary: injetar um ViewModel próprio no `ViewModelStore` de cada Activity/Fragment e, no `onCleared` dele, ler o mapa interno do store por reflection e observar os demais. Mesmo risco de reflection do `ServiceWatcher` — isolar e tornar opcional.
  - Implementado de outra forma: no Lifecycle 2.11 o `ViewModelStore.clear()` esvazia o mapa **antes** de limpar os ViewModels, então o ViewModel injetado não vê mais os outros. O mapa privado do store é trocado (reflection) por uma cópia que reporta os ViewModels no `clear()`; funciona nas versões antigas (`mMap`) e novas (`map`).
- [x] `ServiceWatcher` (avaliar abordagem; pode exigir reflection — documentar riscos).
  - Hooks em APIs não-SDK (callback do `ActivityThread.mH` para `STOP_SERVICE` + proxy do `IActivityManager` para `serviceDoneExecuting`); falha → log e watcher desligado. Riscos no README.
- [x] `FragmentViewWatcher`: observar a view do Fragment no `onDestroyView()` (vazamento comum: binding/view guardado num campo do Fragment).
  - `WatcherType.FRAGMENT_VIEW`, separado de `FRAGMENT`.
- [x] Detecção sem registro na análise: procurar no heap objetos "obviamente mortos" mesmo que nunca observados (Activity com `mDestroyed`, Fragment desanexado, View desanexada), usando os inspectors do Shark, para que um dump manual também os encontre.
- [x] Aplicar em tempo de execução os campos de `LeakWebConfig` ligados a watchers (`retainedThreshold`, `watchers`) via novo `start`.
  - Sem reiniciar o servidor; watchers ligados depois valem para activities criadas a partir daí.

### Fase 7 — Robustez
- [ ] Análise em processo separado (`:leakweb`).
- [ ] Tratamento de pouca memória/espaço em disco (não dumpar se não houver espaço).
- [ ] Notificação persistente opcional com status e atalho (API 33+: pedir `POST_NOTIFICATIONS`; sem permissão, cair para logcat).
- [ ] Testes instrumentados end-to-end no `sample` (Activity vazada → vazamento registrado).

### Fase 8 — Open source
- [ ] Teste de compatibilidade de assinatura entre `leakweb-android` e `leakweb-noop` (o noop já existe desde a Fase 4).
- [ ] README com GIF, instalação, como acessar, segurança, FAQ.
- [ ] Definir a licença (ver seção 7.1), adicionar `LICENSE` e identificador SPDX nos cabeçalhos.
- [ ] `CONTRIBUTING.md`, templates de issue/PR, `CHANGELOG.md`.
- [ ] Se for licença dupla: CLA para contribuidores (ex.: CLA Assistant no GitHub) **antes** de aceitar o primeiro PR externo.
- [ ] Publicação (JitPack → Maven Central), versionamento semântico.
- [ ] Binary compatibility validator (`kotlinx-binary-compatibility-validator`) nos módulos públicos.

---

## 7. Segurança

- A lib **nunca** deve ir para release: documentar `debugImplementation` + `releaseImplementation(noop)`.
- Bind padrão em loopback. Acesso pela rede só com opt-in explícito (`BindAddress.AllInterfaces`).
- Token obrigatório sempre, inclusive em loopback: qualquer outro app instalado no aparelho consegue acessar `127.0.0.1`.
- Heap dumps contêm **qualquer dado em memória** (tokens, dados pessoais). Avisar no README e na interface.
- Token nunca persistido em disco (exceto com `config.token` fixo ou `persistToken = true`) e removido da URL após a troca por cookie.
- Sem CORS aberto.
- Header `Host` validado (proteção contra DNS rebinding: um site malicioso no navegador do dev apontando para `127.0.0.1`) e `Origin` validado em POST/DELETE.
- Parâmetros de rota validados por formato; ids nunca viram caminho de arquivo diretamente.
- UI com `Content-Security-Policy` restritivo e strings do heap renderizadas só como texto.

### 7.1 Licenciamento (em aberto)

Direção atual: **licença dupla**, AGPLv3 para uso aberto + licença comercial para quem quiser comercializar a lib ou não puder cumprir a AGPL.

Pontos a resolver antes de abrir o repositório:

- **Titularidade do código**: licença dupla só funciona se você detiver os direitos de todo o código. Por isso, CLA (ou cessão de direitos) para qualquer contribuição externa, desde o primeiro PR.
- **Dependências**: manter apenas licenças compatíveis com AGPLv3 e com a licença comercial (Apache 2.0, MIT, BSD). Evitar dependências GPL/AGPL de terceiros, que impediriam a versão comercial.
- **Escopo da AGPL**: como a lib roda só em builds de debug, definir no README o que conta como "distribuição"/"uso em rede" para quem adota.
- **Adoção**: muitas empresas proíbem AGPL por política, mesmo em `debugImplementation`, e existe alternativa gratuita e permissiva (LeakCanary, Apache 2.0). Pesar **Apache 2.0** (máxima adoção, sem CLA) contra **AGPL + comercial** (possível receita, menor adoção) antes de investir em CLA.
- **Código de terceiros**: trechos copiados/adaptados de projetos Apache 2.0 (ex.: LeakCanary) mantêm o aviso de copyright e o `NOTICE`, em qualquer das licenças.
- **Publicação**: JitPack gratuito exige repositório público — só depois da decisão.
- **Validação jurídica**: revisar o modelo final com um advogado antes da publicação.

---

## 8. Estratégia de testes

| Módulo | Tipo |
|---|---|
| model | Unitário (serialização round-trip) |
| analyzer | Unitário com hprof gerado no próprio teste (`HotSpotDiagnosticMXBean.dumpHeap`), nada versionado |
| server | Unitário do Router/rotas + integração com NanoHTTPD em porta aleatória na JVM, fakes das interfaces |
| android | Unitário (watcher com Clock/GcTrigger fake) + instrumentado (`sample`) |
| web | Verificação manual + (futuro) Playwright contra servidor fake na JVM |

---

## 9. Riscos

- **Tamanho do Shark em memória** em aparelhos fracos → fase 7 (processo separado).
- **Mudanças no formato hprof / Android novo** → acompanhar releases do Shark.
- **Reflection para Service/Views raiz** pode quebrar entre versões do Android → isolar e tornar opcional.
- **NanoHTTPD sem manutenção** (última release em 2016): manter o uso isolado atrás de uma interface interna; se surgir bug, corrigir num fork ou vendorizar o arquivo-fonte (licença BSD-3, preservar o aviso de copyright).
- **Licença dupla**: sem CLA, contribuições externas inviabilizam a licença comercial. Ver seção 7.1.
- **SSE manual**: ponto mais delicado do servidor; cobrir com teste de integração (conexão, recebimento, desconexão e liberação da thread, e gzip desligado).
- **Apps multi-processo**: sem a guarda de processo principal, servidores e watchers duplicados. Ver seção 4.
- **Coexistência com LeakCanary**: dumps duplicados se os dois estiverem instalados. Detectar e avisar; ver seção 1.1.

