[English](README.md) · [Português (BR)](README.pt-BR.md) · [Español](README.es.md)

[![](https://jitpack.io/v/RogerPhilippe/leakweb.svg)](https://jitpack.io/#RogerPhilippe/leakweb)

# LeakWeb

Biblioteca Android (Kotlin) que detecta vazamentos de memória e permite inspecionar a memória do app por uma
**interface web**, servida por um servidor HTTP embutido no build debug do seu app.

- Detecção automática de objetos retidos depois do fim do ciclo de vida (Activity, Fragment, ViewModel, Service).
- Análise do heap dump com o [Shark](https://github.com/square/leakcanary/tree/main/shark), mostrando o
  caminho de referências até a GC root.
- Interface web no navegador do computador (via `adb forward`), protegida por token.
- Uso de memória ao vivo, histórico de vazamentos e download do `.hprof`.
- Módulo no-op com a mesma API pública para builds de release.

## Instalação

Publicado no [JitPack](https://jitpack.io/#RogerPhilippe/leakweb). Adicione o repositório no `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Depois as dependências (use a versão mais recente indicada no badge acima):

```kotlin
dependencies {
    // A biblioteca: watchers, análise de heap e o servidor web embutido. Apenas builds debug.
    debugImplementation("com.github.RogerPhilippe.leakweb:leakweb-android:<version>")

    // Opcional: só é necessário se o seu código chama a API do LeakWeb (veja abaixo).
    releaseImplementation("com.github.RogerPhilippe.leakweb:leakweb-noop:<version>")
}
```

`leakweb-android` é a única dependência que você declara para builds debug; os outros módulos
(`leakweb-api`, `-model`, `-watcher`, `-analyzer`, `-server`) vêm junto, como dependências transitivas.

### Preciso do `leakweb-noop`?

- **Não**, se você só usa a inicialização automática (`androidx.startup`, porta 8080) e nunca referencia o
  LeakWeb no seu código. Só o `debugImplementation` basta.
- **Sim**, se o seu código chama a API — por exemplo `LeakWeb.start(application, config)` na sua
  `Application` (inicialização manual com porta própria, obter o token de volta) ou `LeakWeb.watch(obj, "...")`.
  A biblioteca real está apenas no classpath de debug, então sem o `leakweb-noop` essas chamadas não compilam
  nos builds de release. O `leakweb-noop` tem a mesma API pública com implementação vazia: `start()`
  devolve uma sessão vazia e `watch()` não faz nada. O mesmo código compila nos dois builds, e o APK de
  release não contém servidor HTTP, analisador de heap nem watchers.

Nunca inclua o `leakweb-android` em um build de release: heap dumps expõem tudo o que está na memória.

## Uso

O LeakWeb pode iniciar sozinho (modo automático, o padrão) ou ser iniciado pelo seu código (modo manual).
Nos dois casos, o servidor web escuta no dispositivo; você o acessa a partir do computador com `adb forward`,
e toda requisição precisa do token da sessão.

### Modo automático (padrão)

Adicione a dependência e rode o build debug: não há mais nada a fazer. O LeakWeb inicia junto com o app
(por meio do `androidx.startup`), observa activities, fragments, views de fragments, ViewModels e
services destruídos, e inicia o servidor web na
porta **8080** (ou na próxima livre). A URL com o token é impressa no logcat, tag `LeakWeb`:

```
LeakWeb server running on port 8080. On the computer, run:
  adb forward tcp:8080 tcp:8080
then open http://localhost:8080/?token=Zm9vYmFyYmF6cXV4ZXhhbXBsZXRva2VuLWZvci1kb2Nz
```

Execute o comando `adb forward` e abra a URL no navegador do computador. A primeira visita troca o
token por um cookie de sessão, então as visitas seguintes a `http://localhost:8080/` não precisam do token.
Um novo token é gerado toda vez que o app inicia.

### Modo manual

Use para escolher a porta, manter o mesmo token entre reinicializações, escutar na rede local ou iniciar o
LeakWeb só quando quiser. Exige o `leakweb-noop` nos builds de release (veja acima).

1. Desative a inicialização automática no manifest do app. Coloque em `src/debug/AndroidManifest.xml`,
   já que o `leakweb-android` só existe em builds debug:

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

   O manifest merger remove apenas a entrada do LeakWeb; os outros inicializadores do `androidx.startup`
   continuam funcionando. O `tools:ignore="MissingClass"` existe porque o `androidx.startup` vem com o
   `leakweb-android` e não está no classpath de compilação do seu app, o que o Android Lint reporta.

2. Inicie o LeakWeb na sua `Application`:

   ```kotlin
   class MyApplication : Application() {
       override fun onCreate() {
           super.onCreate()
           val session = LeakWeb.start(this, LeakWebConfig(port = 9090, persistToken = true))
           // session.port, session.token e session.url, por exemplo para mostrá-los em uma tela de debug.
       }
   }
   ```

   Nos builds de release, o `leakweb-noop` faz o `start()` devolver `LeakWebSession.NOT_RUNNING` e não fazer nada.

Opções do `LeakWebConfig`:

| Opção | Padrão | Descrição |
|---|---|---|
| `port` | `8080` | Porta do servidor web |
| `portFallback` | `true` | Tenta as portas seguintes se `port` estiver em uso; a porta real é `session.port` |
| `bindAddress` | `Loopback` | `Loopback` (`127.0.0.1`, por `adb forward`) ou `AllInterfaces` (inclui a rede local) |
| `token` | `null` | Token fixo, útil para favoritos; `null` gera um aleatório |
| `persistToken` | `false` | Mantém o token gerado entre reinicializações do app |
| `logStartup` | `true` | Registra no log a URL com o token e o comando `adb forward` |
| `retainedThreshold` | `5` | Objetos retidos necessários para gerar o dump do heap com o app visível (1 em segundo plano) |
| `persistentNotification` | `false` | Mostra uma notificação de status contínua com um atalho de volta ao app |
| `watchers` | todos | O que é observado automaticamente (veja [Watchers](#watchers)); vazio mantém só o servidor e a memória ao vivo |

Outras chamadas: `LeakWeb.session` devolve a sessão em execução, `LeakWeb.stop()` para o servidor web
(os vazamentos continuam sendo detectados), e chamar `start()` de novo com outra config a aplica: `watchers` e
`retainedThreshold` mudam na hora sem reiniciar o servidor, as outras opções o reiniciam.
Chamar `start()` com a mesma config só devolve a sessão atual. `LeakWeb.watch(obj, "motivo")`
observa qualquer objeto que deveria sofrer garbage collection em breve.

Com `BindAddress.AllInterfaces`, o logcat também mostra a URL com o IP do dispositivo. Qualquer pessoa na rede
que tenha o token pode baixar heap dumps, então use apenas em redes confiáveis.

Para ativar a notificação de status contínua, peça a permissão a partir de uma Activity visível no Android 13+
e habilite-a na config:

```kotlin
LeakWeb.requestNotificationPermission(this)
LeakWeb.start(application, LeakWebConfig(persistentNotification = true))
```

Chame `start()` com a mesma config depois que o usuário conceder a permissão, se ela foi pedida mais tarde.
Se a permissão de notificação for negada, o LeakWeb continua funcionando e registra no log que a notificação não
foi exibida. A notificação pode ser desativada iniciando com `persistentNotification = false`.

### Watchers

| `WatcherType` | Observa | Precisa de |
|---|---|---|
| `ACTIVITY` | Activities após `onDestroy()` | — |
| `FRAGMENT` | Fragments AndroidX após `onDestroy()` | AndroidX Fragment no app |
| `FRAGMENT_VIEW` | Views de fragments AndroidX após `onDestroyView()` (uma view ou binding guardada em um campo) | AndroidX Fragment no app |
| `VIEW_MODEL` | ViewModels AndroidX após `onCleared()`, em activities e fragments | AndroidX Lifecycle no app |
| `SERVICE` | Services após `onDestroy()` | — |

Objetos observados que ainda estão na memória ~5 segundos depois (após um GC) são reportados como retidos. Além
deles, toda análise de heap também reporta objetos que estão claramente mortos mesmo que ninguém os tenha
observado (activity destruída, fragment destruído, view destacada, service parado...), então um heap dump manual
também os encontra.

`VIEW_MODEL` e `SERVICE` dependem de detalhes internos que o Android e o AndroidX não expõem:

- **ViewModels**: o LeakWeb substitui, por reflexão, o mapa privado de cada `ViewModelStore` por uma cópia
  que reporta seus ViewModels quando o store é limpo.
- **Services**: não existe callback para um service destruído. O LeakWeb intercepta o handler da thread principal
  da `ActivityThread` e envolve o `IActivityManager` do app em um proxy (interfaces não-SDK, a mesma
  técnica do LeakCanary) para ver o `serviceDoneExecuting()` após o `onDestroy()`.

Uma versão futura do Android ou do AndroidX pode mudar ou bloquear esses detalhes internos. O LeakWeb então registra
um aviso e deixa esse watcher desligado; o restante continua funcionando. Para evitá-los, deixe o tipo fora de `watchers`.

### Memória ao vivo

A seção Memory da interface web mostra o heap Java, o heap nativo e o PSS do processo do app.
O monitoramento ao vivo vem **desligado por padrão**: a seção mostra então os valores lidos ao abrir, e
nada é amostrado. "Start live monitoring" amostra a cada segundo (o PSS a cada 10 segundos, pois é mais lento
de ler) com um gráfico dos últimos 5 minutos. Ele continua rodando no app, mesmo com a página fechada ou
depois de reiniciar o app, até "Stop live monitoring"; iniciá-lo de novo limpa o gráfico.

### API HTTP

A interface web usa uma API JSON que você também pode chamar de scripts. Envie o token em
`Authorization: Bearer <token>` ou `X-LeakWeb-Token: <token>`:

```sh
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/leaks
```

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/status` | Versão da biblioteca, app, processo, porta, contagem de objetos observados/retidos |
| GET | `/api/leaks` | Vazamentos agrupados por assinatura |
| GET | `/api/leaks/{signature}` | Trace completo do vazamento e as análises em que apareceu |
| GET | `/api/analyses` | Histórico de análises |
| GET | `/api/analyses/{id}/hprof` | Baixa o heap dump |
| DELETE | `/api/analyses/{id}` | Apaga uma análise e seu heap dump |
| GET | `/api/retained` | Objetos retidos agora, ainda não analisados |
| POST | `/api/heapdump` | Gera e analisa o heap agora |
| POST | `/api/gc` | Executa o garbage collector |
| GET | `/api/memory` | Uso de memória atual |
| GET | `/api/memory/history` | Amostras de memória desde que o monitoramento ao vivo foi iniciado (no máximo 5 minutos), da mais antiga para a mais nova |
| POST | `/api/memory/live/start` | Inicia o monitoramento de memória ao vivo (limpa o histórico) |
| POST | `/api/memory/live/stop` | Para o monitoramento de memória ao vivo |
| GET | `/api/events` | Server-Sent Events: amostras de memória, monitoramento ao vivo ligado/desligado, objetos retidos, heap dumps, progresso da análise, novos vazamentos |

As respostas vêm no formato `{"schemaVersion": 1, "data": ...}`; os erros, como
`{"schemaVersion": 1, "error": {"status": 404, "message": "..."}}`.

## Módulos

| Módulo | Tipo | Finalidade |
|---|---|---|
| `leakweb-api` | JVM | Tipos públicos compartilhados por `android` e `noop` |
| `leakweb-model` | JVM | Modelos serializáveis |
| `leakweb-watcher` | JVM | `KeyedWeakReference` e o observador de objetos |
| `leakweb-analyzer` | JVM | Wrapper do Shark: hprof → análise |
| `leakweb-server` | JVM | Servidor HTTP embutido, API REST, SSE e frontend web |
| `leakweb-android` | Android | Watchers, gerador de heap dump, store, inicialização |
| `leakweb-noop` | Android | Mesma API pública, implementação vazia (release) |
| `sample` | App | App com vazamentos intencionais para testes; usa o modo manual na porta 9090 |
| `build-logic` | Gradle | Plugins de convenção |

## Compilando

Requisitos: Android SDK e JDK 17+ (o daemon do Gradle e os toolchains são provisionados automaticamente).

```sh
./gradlew build          # compila, testes unitários, verificações do Detekt e do Spotless
./gradlew spotlessApply  # formata o código e adiciona o cabeçalho de copyright
```

## Segurança

- Nunca inclua o `leakweb-android` em um build de release. Use `debugImplementation` e, se o seu código chama
  a API, `releaseImplementation` do `leakweb-noop`.
- Heap dumps contêm **tudo o que está na memória** (tokens, dados pessoais). Trate os arquivos `.hprof`
  como sensíveis e não os compartilhe.
- O servidor escuta apenas em loopback por padrão, e toda requisição precisa do token da sessão, mesmo em
  loopback, já que qualquer app do dispositivo alcança `127.0.0.1`. O token não é persistido, a menos que você
  peça.
- Os cabeçalhos `Host` e `Origin` são validados (proteção contra DNS rebinding) e a interface web usa um
  `Content-Security-Policy` restritivo.
- `BindAddress.AllInterfaces` expõe o servidor à rede local: use apenas em redes confiáveis.

## FAQ

**Funciona em builds de release?** Não, por design. O `leakweb-noop` tem a mesma API com implementação
vazia, então o mesmo código compila nos dois builds.

**Quais versões do Android são suportadas?** `minSdk` 23. Os watchers de ViewModel e Service usam detalhes
internos que podem mudar; se um deixar de funcionar, o LeakWeb registra um aviso e mantém os outros rodando.

**Qual a diferença para o LeakCanary?** O LeakCanary mostra os vazamentos no dispositivo. O LeakWeb serve o
mesmo tipo de análise (também baseada no Shark) como uma interface web no navegador do seu computador, com memória
ao vivo e download do `.hprof`.

**A página diz unauthorized.** Abra a URL com o token impresso no logcat (tag `LeakWeb`); um novo token é gerado
toda vez que o app inicia, a menos que você defina `token` ou `persistToken`.

**Um heap dump falhou.** Abra a análise que falhou na interface web para ver o stack trace e
[abra uma issue](https://github.com/RogerPhilippe/leakweb/issues) com ele.

## Licença

O LeakWeb é distribuído sob **dupla licença**:

- [AGPLv3](LICENSE) — gratuita, desde que qualquer versão modificada (incluindo
  uso como serviço em rede) também seja disponibilizada sob a mesma licença.
- [Licença comercial](COMMERCIAL-LICENSE.pt-BR.md) — uma garantia, não um
  produto: vender o LeakWeb ou usá-lo em software de código fechado exige
  autorização expressa do mantenedor.

O LeakWeb é uma ferramenta de depuração para builds debug. Usar a biblioteca sem modificações em builds debug que
ficam dentro da sua equipe não a distribui; veja [COMMERCIAL-LICENSE.pt-BR.md](COMMERCIAL-LICENSE.pt-BR.md) para
saber o que conta como distribuição.

Contribuições estão sujeitas aos termos em [CONTRIBUTING.pt-BR.md](CONTRIBUTING.pt-BR.md).

As dependências de terceiros mantêm suas próprias licenças (Apache-2.0, BSD, MIT), todas compatíveis com as duas
opções de licença.

Copyright © 2026 Roger Philippe Pereira
