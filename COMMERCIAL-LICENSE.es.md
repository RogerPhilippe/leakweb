[English](COMMERCIAL-LICENSE.md) · [Português (BR)](COMMERCIAL-LICENSE.pt-BR.md) · [Español](COMMERCIAL-LICENSE.es.md)

# Licenciamiento Comercial

LeakWeb es software de código abierto, distribuido bajo **doble licencia**:

1. **GNU Affero General Public License v3.0 (AGPLv3)** — gratuita, para
   cualquier persona o empresa que use, modifique y redistribuya el proyecto
   (o lo ofrezca como servicio en red) **siempre que cumpla los términos de la
   AGPLv3**, lo que incluye poner a disposición el código fuente completo de
   cualquier versión modificada, bajo la misma licencia, a quien reciba el
   software o use el servicio.

2. **Licencia comercial**, concedida **únicamente a criterio exclusivo del
   mantenedor**, para quien quiera usar LeakWeb de formas que la AGPLv3 no
   permite, por ejemplo:
   - Vender LeakWeb, o una versión modificada o con otra marca, como producto
     de código cerrado;
   - Incorporar LeakWeb (o partes de él) en software propietario o en un SDK
     de código cerrado distribuido a terceros;
   - Distribuir versiones modificadas sin compartir su código fuente.

## Finalidad

La licencia comercial existe como **garantía**, no como producto a la venta.
LeakWeb está pensado para seguir siendo libre y abierto. Esta opción asegura
que nadie pueda tomar el proyecto y venderlo como software de código cerrado
sin la autorización expresa del mantenedor.

## Usar LeakWeb en su propia app

LeakWeb es una herramienta de depuración pensada para builds `debug`
(`debugImplementation`). Las obligaciones de la AGPLv3 se aplican cuando usted
**transmite** el software a otras personas (por ejemplo, entrega un APK que
contiene LeakWeb a quien está fuera de su organización) o cuando lo
**modifica** y permite que otros interactúen con la versión modificada a
través de una red.

- Usar la biblioteca sin modificar en builds debug que quedan dentro de su
  equipo no distribuye LeakWeb a nadie.
- Los builds de release no deben contener LeakWeb: use `leakweb-noop`, que
  tiene la misma API pública y una implementación vacía.
- Si modifica LeakWeb, o distribuye a terceros un build que lo contenga,
  cumpla la AGPLv3 o solicite una licencia comercial.

Esta sección es un resumen en lenguaje sencillo y no constituye asesoramiento
jurídico. Si tiene dudas, consulte a un abogado o contacte al mantenedor.

## Cuándo hay que pedir autorización

Si quiere usar LeakWeb de una forma que la AGPLv3 **no permite sin abrir el
código correspondiente**, debe obtener la autorización por escrito del
mantenedor. Las solicitudes pueden ser rechazadas.

## Contacto

Roger Philippe Pereira — rogerphilippepereira@gmail.com

## Sobre las contribuciones

Al contribuir con código a este proyecto, usted acepta los términos descritos
en [CONTRIBUTING.es.md](CONTRIBUTING.es.md), que incluyen la concesión al
mantenedor del derecho de relicenciar su contribución — esto es lo que hace
posible este modelo de doble licencia.
