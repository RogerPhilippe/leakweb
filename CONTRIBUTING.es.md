[English](CONTRIBUTING.md) · [Português (BR)](CONTRIBUTING.pt-BR.md) · [Español](CONTRIBUTING.es.md)

# Contribuir a LeakWeb

¡Gracias por su interés en contribuir! Antes de abrir un Pull Request, lea lo
siguiente.

## Cómo contribuir

1. Abra una Issue describiendo el error o la funcionalidad propuesta, para
   alinear antes de programar (evita retrabajo).
2. Haga un fork del repositorio y cree una rama a partir de `main`.
3. Siga el estilo de código ya utilizado en el proyecto (Kotlin, Detekt y ktlint mediante Spotless).
4. Ejecute `./gradlew spotlessApply` para formatear el código y agregar el
   encabezado de los archivos, y luego `./gradlew build` (compilación, pruebas
   unitarias, Detekt, Spotless y lint) antes de abrir el PR.
5. Agregue o actualice pruebas para lo que cambió.
6. Describa claramente, en la descripción del PR, qué se cambió y por qué.

Requisitos: Android SDK y JDK 17+ (vea el [README](README.es.md#compilación)).

## Regla de "sala limpia"

LeakWeb está escrito desde cero. **No envíe código copiado o adaptado de
proyectos cuya licencia sea incompatible con el
modelo de doble licencia de este proyecto** (por ejemplo, código solo GPL).
Las contribuciones deben ser trabajo original suyo, o provenir de fuentes
bajo licencias permisivas (MIT, BSD, Apache-2.0, ISC) con sus avisos
preservados.

Las nuevas dependencias también deben tener una licencia compatible con ambas
opciones (Apache-2.0, MIT, BSD o similar). No se aceptan dependencias GPL ni
AGPL.

## Licenciamiento de las contribuciones y Contributor Agreement

Este proyecto usa un modelo de **doble licencia**: está disponible
públicamente bajo la [AGPLv3](LICENSE), y también se ofrece bajo una
[licencia comercial separada](COMMERCIAL-LICENSE.es.md) para quien no puede
cumplir los términos de la AGPLv3.

Para que este modelo funcione legalmente, es necesario que el mantenedor del
proyecto (Roger Philippe Pereira) posea los derechos necesarios para
relicenciar comercialmente el código completo, incluidas las contribuciones
de terceros.

**Al enviar una contribución (Pull Request, parche o cualquier otro
material) a este proyecto, usted acepta que:**

1. Su contribución se licencia bajo la AGPLv3, como el resto del proyecto; y
2. Usted concede al mantenedor del proyecto (Roger Philippe Pereira) una
   licencia perpetua, mundial, no exclusiva y libre de regalías para también
   relicenciar su contribución bajo términos comerciales, como parte del
   modelo de doble licencia descrito en [COMMERCIAL-LICENSE.es.md](COMMERCIAL-LICENSE.es.md); y
3. Usted declara tener el derecho de hacer esta contribución y esta concesión
   (por ejemplo, que el código es suyo o que tiene la autorización de su
   empleador, si corresponde).

La plantilla de pull request le pide que confirme este acuerdo. Si no está de
acuerdo con estos términos, lamentablemente no podemos aceptar su
contribución a este proyecto.
