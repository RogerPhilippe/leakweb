[English](README.md) · [Português (BR)](README.pt-BR.md) · [Español](README.es.md)

# LeakWeb

> En desarrollo. Nombre provisional.

Biblioteca Android (Kotlin) que detecta fugas de memoria y permite inspeccionar la memoria de la app desde una
**interfaz web**, servida por un servidor HTTP integrado en el build debug de su app.

- Detección automática de objetos retenidos después de que termina su ciclo de vida (Activity, Fragment, ViewModel, Service).
- Análisis del heap dump con [Shark](https://github.com/square/leakcanary/tree/main/shark), mostrando la
  ruta de referencias hasta la GC root.
- Interfaz web en el navegador del computador (mediante `adb forward`), protegida por token.
- Uso de memoria en vivo, historial de fugas y descarga del `.hprof`.
- Módulo no-op con la misma API pública para builds de release.

El plan de desarrollo está en [docs/PLANO.md](docs/PLANO.md).

## Instalación

> Aún no publicado. Las coordenadas de abajo son las planificadas.

```kotlin
dependencies {
    // La biblioteca: watchers, análisis de heap y el servidor web integrado. Solo builds debug.
    debugImplementation("io.github.rogerphilippe:leakweb-android:<version>")

    // Opcional: solo es necesario si su código llama a la API de LeakWeb (vea abajo).
    releaseImplementation("io.github.rogerphilippe:leakweb-noop:<version>")
}
```

`leakweb-android` es la única dependencia que declara para builds debug; los demás módulos
(`leakweb-api`, `-model`, `-watcher`, `-analyzer`, `-server`) vienen con él, como dependencias transitivas.

### ¿Necesito `leakweb-noop`?

- **No**, si solo usa el inicio automático (`androidx.startup`, puerto 8080) y nunca hace referencia a
  LeakWeb en su código. Con `debugImplementation` basta.
- **Sí**, si su código llama a la API — por ejemplo `LeakWeb.start(application, config)` en su
  `Application` (inicio manual con puerto propio, recuperar el token) o `LeakWeb.watch(obj, "...")`.
  La biblioteca real solo está en el classpath de debug, así que sin `leakweb-noop` esas llamadas no compilan
  en los builds de release. `leakweb-noop` tiene la misma API pública con una implementación vacía: `start()`
  devuelve una sesión vacía y `watch()` no hace nada. El mismo código compila en ambos builds, y el APK de
  release no contiene servidor HTTP, analizador de heap ni watchers.

Nunca incluya `leakweb-android` en un build de release: los heap dumps exponen todo lo que está en memoria.

## Uso

LeakWeb puede iniciar solo (modo automático, el predeterminado) o ser iniciado desde su código (modo manual).
En ambos casos, el servidor web escucha en el dispositivo; usted accede desde el computador con `adb forward`,
y toda solicitud necesita el token de la sesión.

### Modo automático (predeterminado)

Agregue la dependencia y ejecute el build debug: no hay nada más que hacer. LeakWeb inicia junto con la app
(mediante `androidx.startup`), observa activities, fragments, vistas de fragments, ViewModels y
services destruidos, e inicia el servidor web en el
puerto **8080** (o en el siguiente libre). La URL con el token se imprime en logcat, etiqueta `LeakWeb`:

```
LeakWeb server running on port 8080. On the computer, run:
  adb forward tcp:8080 tcp:8080
then open http://localhost:8080/?token=Zm9vYmFyYmF6cXV4ZXhhbXBsZXRva2VuLWZvci1kb2Nz
```

Ejecute el comando `adb forward` y abra la URL en el navegador del computador. La primera visita cambia el
token por una cookie de sesión, así que las visitas siguientes a `http://localhost:8080/` no necesitan el token.
Se genera un token nuevo cada vez que la app inicia.

### Modo manual

Úselo para elegir el puerto, mantener el mismo token entre reinicios, escuchar en la red local o iniciar
LeakWeb solo cuando quiera. Requiere `leakweb-noop` en los builds de release (vea arriba).

1. Desactive el inicio automático en el manifest de la app. Colóquelo en `src/debug/AndroidManifest.xml`,
   ya que `leakweb-android` solo existe en builds debug:

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

   El manifest merger elimina solo la entrada de LeakWeb; los otros inicializadores de `androidx.startup`
   siguen funcionando. `tools:ignore="MissingClass"` está ahí porque `androidx.startup` viene con
   `leakweb-android` y no está en el classpath de compilación de su app, lo que Android Lint reporta.

2. Inicie LeakWeb en su `Application`:

   ```kotlin
   class MyApplication : Application() {
       override fun onCreate() {
           super.onCreate()
           val session = LeakWeb.start(this, LeakWebConfig(port = 9090, persistToken = true))
           // session.port, session.token y session.url, por ejemplo para mostrarlos en una pantalla de debug.
       }
   }
   ```

   En los builds de release, `leakweb-noop` hace que `start()` devuelva `LeakWebSession.NOT_RUNNING` y no haga nada.

Opciones de `LeakWebConfig`:

| Opción | Predeterminado | Descripción |
|---|---|---|
| `port` | `8080` | Puerto del servidor web |
| `portFallback` | `true` | Prueba los puertos siguientes si `port` está en uso; el puerto real es `session.port` |
| `bindAddress` | `Loopback` | `Loopback` (`127.0.0.1`, mediante `adb forward`) o `AllInterfaces` (incluye la red local) |
| `token` | `null` | Token fijo, útil para marcadores; `null` genera uno aleatorio |
| `persistToken` | `false` | Mantiene el token generado entre reinicios de la app |
| `logStartup` | `true` | Registra en el log la URL con el token y el comando `adb forward` |
| `retainedThreshold` | `5` | Objetos retenidos necesarios para generar el dump del heap con la app visible (1 en segundo plano) |
| `persistentNotification` | `false` | Muestra una notificación de estado continua con un atajo de vuelta a la app |
| `watchers` | todos | Qué se observa automáticamente (vea [Watchers](#watchers)); vacío mantiene solo el servidor y la memoria en vivo |

Otras llamadas: `LeakWeb.session` devuelve la sesión en ejecución, `LeakWeb.stop()` detiene el servidor web
(las fugas se siguen detectando), y llamar a `start()` de nuevo con otra config la aplica: `watchers` y
`retainedThreshold` cambian al instante sin reiniciar el servidor, las otras opciones lo reinician.
Llamar a `start()` con la misma config solo devuelve la sesión actual. `LeakWeb.watch(obj, "motivo")`
observa cualquier objeto que debería ser recolectado por el garbage collector en breve.

Con `BindAddress.AllInterfaces`, logcat también muestra la URL con la IP del dispositivo. Cualquier persona en la
red que tenga el token puede descargar heap dumps, así que úselo solo en redes de confianza.

Para activar la notificación de estado continua, solicite el permiso desde una Activity visible en Android 13+
y habilítela en la config:

```kotlin
LeakWeb.requestNotificationPermission(this)
LeakWeb.start(application, LeakWebConfig(persistentNotification = true))
```

Llame a `start()` con la misma config después de que el usuario conceda el permiso, si se solicitó más tarde.
Si se deniega el permiso de notificaciones, LeakWeb sigue funcionando y registra en el log que la notificación no
se mostró. La notificación se puede desactivar iniciando con `persistentNotification = false`.

### Watchers

| `WatcherType` | Observa | Necesita |
|---|---|---|
| `ACTIVITY` | Activities tras `onDestroy()` | — |
| `FRAGMENT` | Fragments AndroidX tras `onDestroy()` | AndroidX Fragment en la app |
| `FRAGMENT_VIEW` | Vistas de fragments AndroidX tras `onDestroyView()` (una vista o binding guardado en un campo) | AndroidX Fragment en la app |
| `VIEW_MODEL` | ViewModels AndroidX tras `onCleared()`, en activities y fragments | AndroidX Lifecycle en la app |
| `SERVICE` | Services tras `onDestroy()` | — |

Los objetos observados que siguen en memoria ~5 segundos después (tras un GC) se reportan como retenidos. Además
de ellos, cada análisis de heap también reporta objetos que están claramente muertos aunque nadie los haya
observado (activity destruida, fragment destruido, vista desacoplada, service detenido...), así que un heap dump
manual también los encuentra.

`VIEW_MODEL` y `SERVICE` dependen de detalles internos que Android y AndroidX no exponen:

- **ViewModels**: LeakWeb reemplaza, por reflexión, el mapa privado de cada `ViewModelStore` por una copia
  que reporta sus ViewModels cuando el store se limpia.
- **Services**: no existe un callback para un service destruido. LeakWeb intercepta el handler del hilo principal
  de `ActivityThread` y envuelve el `IActivityManager` de la app en un proxy (interfaces no-SDK, la misma
  técnica de LeakCanary) para ver `serviceDoneExecuting()` tras `onDestroy()`.

Una versión futura de Android o AndroidX puede cambiar o bloquear estos detalles internos. LeakWeb entonces registra
una advertencia y deja ese watcher desactivado; el resto sigue funcionando. Para evitarlos, deje el tipo fuera de `watchers`.

### Memoria en vivo

La sección Memory de la interfaz web muestra el heap Java, el heap nativo y el PSS del proceso de la app.
El monitoreo en vivo viene **desactivado por defecto**: la sección muestra entonces los valores leídos al abrirse, y
no se toma ninguna muestra. "Start live monitoring" toma muestras cada segundo (el PSS cada 10 segundos, porque es
más lento de leer) con un gráfico de los últimos 5 minutos. Sigue ejecutándose en la app, incluso con la página
cerrada o tras reiniciar la app, hasta "Stop live monitoring"; iniciarlo de nuevo limpia el gráfico.

### API HTTP

La interfaz web usa una API JSON que también puede llamar desde scripts. Envíe el token en
`Authorization: Bearer <token>` o `X-LeakWeb-Token: <token>`:

```sh
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/leaks
```

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/status` | Versión de la biblioteca, app, proceso, puerto, conteo de objetos observados/retenidos |
| GET | `/api/leaks` | Fugas agrupadas por firma |
| GET | `/api/leaks/{signature}` | Trace completo de la fuga y los análisis en que apareció |
| GET | `/api/analyses` | Historial de análisis |
| GET | `/api/analyses/{id}/hprof` | Descarga el heap dump |
| DELETE | `/api/analyses/{id}` | Elimina un análisis y su heap dump |
| GET | `/api/retained` | Objetos retenidos ahora, aún no analizados |
| POST | `/api/heapdump` | Genera y analiza el heap ahora |
| POST | `/api/gc` | Ejecuta el garbage collector |
| GET | `/api/memory` | Uso de memoria actual |
| GET | `/api/memory/history` | Muestras de memoria desde que se inició el monitoreo en vivo (5 minutos como máximo), de la más antigua a la más nueva |
| POST | `/api/memory/live/start` | Inicia el monitoreo de memoria en vivo (limpia el historial) |
| POST | `/api/memory/live/stop` | Detiene el monitoreo de memoria en vivo |
| GET | `/api/events` | Server-Sent Events: muestras de memoria, monitoreo en vivo activado/desactivado, objetos retenidos, heap dumps, progreso del análisis, nuevas fugas |

Las respuestas vienen con el formato `{"schemaVersion": 1, "data": ...}`; los errores, como
`{"schemaVersion": 1, "error": {"status": 404, "message": "..."}}`.

## Módulos

| Módulo | Tipo | Finalidad |
|---|---|---|
| `leakweb-api` | JVM | Tipos públicos compartidos por `android` y `noop` |
| `leakweb-model` | JVM | Modelos serializables |
| `leakweb-watcher` | JVM | `KeyedWeakReference` y el observador de objetos |
| `leakweb-analyzer` | JVM | Wrapper de Shark: hprof → análisis |
| `leakweb-server` | JVM | Servidor HTTP integrado, API REST, SSE y frontend web |
| `leakweb-android` | Android | Watchers, generador de heap dump, store, inicialización |
| `leakweb-noop` | Android | Misma API pública, implementación vacía (release) |
| `sample` | App | App con fugas intencionales para pruebas; usa el modo manual en el puerto 9090 |
| `build-logic` | Gradle | Plugins de convención |

## Compilación

Requisitos: Android SDK y JDK 17+ (el daemon de Gradle y los toolchains se aprovisionan automáticamente).

```sh
./gradlew build          # compila, pruebas unitarias, verificaciones de Detekt y Spotless
./gradlew spotlessApply  # formatea el código y agrega el encabezado de copyright
```

## Seguridad

- Nunca incluya `leakweb-android` en un build de release. Use `debugImplementation` y, si su código llama a
  la API, `releaseImplementation` de `leakweb-noop`.
- Los heap dumps contienen **todo lo que está en memoria** (tokens, datos personales). Trate los archivos `.hprof`
  como sensibles y no los comparta.
- El servidor escucha solo en loopback por defecto, y toda solicitud necesita el token de la sesión, incluso en
  loopback, ya que cualquier app del dispositivo alcanza `127.0.0.1`. El token no se persiste a menos que usted
  lo pida.
- Los encabezados `Host` y `Origin` se validan (protección contra DNS rebinding) y la interfaz web usa un
  `Content-Security-Policy` restrictivo.
- `BindAddress.AllInterfaces` expone el servidor a la red local: úselo solo en redes de confianza.

## FAQ

**¿Funciona en builds de release?** No, por diseño. `leakweb-noop` tiene la misma API con una implementación
vacía, así que el mismo código compila en ambos builds.

**¿Qué versiones de Android son compatibles?** `minSdk` 23. Los watchers de ViewModel y Service usan detalles
internos que pueden cambiar; si uno deja de funcionar, LeakWeb registra una advertencia y mantiene los otros activos.

**¿En qué se diferencia de LeakCanary?** LeakCanary muestra las fugas en el dispositivo. LeakWeb sirve el
mismo tipo de análisis (también basado en Shark) como una interfaz web en el navegador de su computador, con memoria
en vivo y descarga del `.hprof`.

**La página dice unauthorized.** Abra la URL con el token impreso en logcat (etiqueta `LeakWeb`); se genera un token
nuevo cada vez que la app inicia, a menos que defina `token` o `persistToken`.

**Falló un heap dump.** Abra el análisis fallido en la interfaz web para ver el stack trace y
[abra una issue](https://github.com/RogerPhilippe/leakweb/issues) con él.

## Licencia

LeakWeb se distribuye bajo **doble licencia**:

- [AGPLv3](LICENSE) — gratuita, siempre que cualquier versión modificada
  (incluido su uso como servicio en red) también se ponga a disposición bajo
  la misma licencia.
- [Licencia comercial](COMMERCIAL-LICENSE.es.md) — una garantía, no un
  producto: vender LeakWeb o usarlo en software de código cerrado requiere la
  autorización expresa del mantenedor.

LeakWeb es una herramienta de depuración para builds debug. Usar la biblioteca sin modificar en builds debug que
quedan dentro de su equipo no la distribuye; vea [COMMERCIAL-LICENSE.es.md](COMMERCIAL-LICENSE.es.md) para saber
qué cuenta como distribución.

Las contribuciones están sujetas a los términos de [CONTRIBUTING.es.md](CONTRIBUTING.es.md).

Las dependencias de terceros conservan sus propias licencias (Apache-2.0, BSD, MIT), todas compatibles con ambas
opciones de licencia.

Copyright © 2026 Roger Philippe Pereira
