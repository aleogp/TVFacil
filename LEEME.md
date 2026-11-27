# TV Fácil

Lanzador para Fire TV con botones grandes. Cada botón pone la VPN del país correcto
(a través de la app WireGuard) y abre la app que toca.

| Botón | VPN | Abre |
|---|---|---|
| ⚽ Disney y Fútbol | colombia | Disney+ |
| 🎬 Netflix | ninguna | Netflix |
| 📺 TV Colombia | colombia | DGO (elegir en configuración) |
| 📡 Canales | mexico | KAI (elegir en configuración) |
| 🔧 No funciona | repite el último botón, esperando más | — |
| 📞 Llamar a Andrés | ninguna | AnyDesk |
| 🔄 Reiniciar | — | Muestra el atajo: CENTRO + ▶⏸ por 5 s |

## 1. Armar el APK con GitHub (sin instalar nada)
1. Crea un repositorio **público** en GitHub llamado `TVFacil`.
2. Sube TODO el contenido de esta carpeta (incluida la carpeta `.github` y el archivo `app/tvfacil.keystore`).
3. Ve a la pestaña **Actions**: se arma solo (≈5 min). Si sale en verde, en **Releases** aparece `TVFacil.apk`.
4. Enlace fijo para descargar siempre la última versión:
   `https://github.com/TU_USUARIO/TVFacil/releases/latest/download/TVFacil.apk`

Cada vez que cambies algo y lo subas, se arma una versión nueva que se instala encima de la anterior.

## 2. Preparar las VPN (Surfshark → WireGuard)
1. En la web de Surfshark: *VPN → Manual setup → Router/Desktop → WireGuard*. Genera un par de llaves y descarga
   la configuración de **un servidor de Colombia** y otra de **México** (o EE. UU.).
2. En el Fire Stick instala **WireGuard** (tienda de Amazon o con Downloader) e importa los dos archivos.
3. Nombra los túneles exactamente: `colombia` y `mexico` (minúsculas, sin tilde).
4. Enciende cada túnel una vez a mano para aceptar el aviso de "solicitud de conexión" de Android.
5. En WireGuard → Ajustes, activa **"Allow remote control apps"**.
6. Desactiva el inicio automático de la app de Surfshark (o desinstálala): solo puede haber una VPN.

## 3. Instalar TV Fácil en el Fire Stick
1. Fire Stick: *Ajustes → Mi Fire TV → Opciones para desarrolladores → Instalar apps desconocidas* → activar para **Downloader**.
2. En Downloader escribe el enlace del paso 1.4 e instala.
3. Abre TV Fácil y acepta el permiso para controlar WireGuard.
4. Fíjala de primera en la fila de apps de la pantalla de inicio.

## 4. Configuración escondida (solo para ti)
- En el control: aprieta la tecla **Menú (≡) 5 veces seguidas**.
- En un celular: mantén presionado el título "¿Qué quieren ver?".

Ahí eliges qué app abre cada botón (DGO y KAI hay que elegirlas la primera vez), qué VPN usa, nombre,
emoji, color y orden. También puedes agregar o borrar botones.

## 5. Cambiar los botones desde el celular (config.json)
Sin entrar a la tele: edita `config.json` en GitHub (se puede desde la app de GitHub o el navegador).
1. Cambia lo que quieras en `botones` (nombre, emoji, color `#RRGGBB`, app, VPN, orden).
2. **Sube el número de `version`** (1, 2, 3…). Si no lo subes, la tele no lo aplica.
3. La tele lo aplica sola la próxima vez que abran TV Fácil (revisa como mucho cada 10 min).

Con `version: 0` no se aplica nada. Lo que cambies a mano en la tele sigue funcionando hasta
la siguiente versión de `config.json`. Para ver cómo está configurada la tele ahora:
configuración escondida → "Ver configuración actual". Para aplicarlo al instante:
"Traer botones de GitHub ahora". Cambiar `config.json` no arma un APK nuevo.

## 6. Actualizaciones automáticas
Cada vez que GitHub arma un APK nuevo, TV Fácil lo detecta (revisa cada 6 h) y muestra
"Hay una versión nueva". Con OK la descarga y Android pide confirmar **Instalar**.
Con ↩ Atrás no vuelve a preguntar hasta el día siguiente.

**Una sola vez:** dar permiso para instalar apps a TV Fácil:
*Ajustes → Mi Fire TV → Opciones para desarrolladores → Instalar apps desconocidas → TV Fácil → Activar*
(o configuración escondida → "Buscar versión nueva", que abre el ajuste). Sin ese permiso no pregunta nada.
La primera versión con esto (v7.N nueva) hay que instalarla a mano con Downloader como siempre.

## Notas
- Instala AnyDesk en el Fire Stick para el botón "Llamar a Andrés".
- El repositorio público expone la llave de firma; para una app familiar no es problema.


---

# Historial de versiones

## TV Fácil v7.14

### Novedades
- **Se actualiza sola:** cada 6 horas revisa si hay versión nueva. Si la hay, sale un aviso en la tele: OK para instalar, o Atrás para dejarlo para el día siguiente.
- **Botones desde GitHub:** los botones (nombre, color, VPN, app) se pueden cambiar editando `config.json` (subiendo `version`), sin armar una app nueva. La tele lo aplica sola en unos minutos.
- **30 fotos de la nieta** rotando arriba en la pantalla principal (cifradas; no se ven en GitHub).
- **Configuración escondida** (Menú ≡ 5 veces) con tres opciones nuevas: traer los botones de GitHub, ver la configuración actual y buscar una versión nueva.

### Lo que ya traía
- Botones grandes de colores: Partido del Barça, Disney y Fútbol, Netflix, TV Colombia, Canales, No funciona, Llamar a Andrés y Reiniciar.
- VPN integrada (Colombia, México, EE. UU.): cada botón pone la VPN correcta y abre la app que toca.
- Partido del Barça: muestra el próximo partido y si lo dan por Disney o DGO. Se actualiza solo dos veces al día.
- Bloqueo de IPv6 fuera de la VPN (desde la v7).
- Si falta una app, abre la tienda de Amazon para instalarla.
- Ícono con la foto de la nieta.

### Pendiente
- KAI sigue sin funcionar en casa de los papás. Queda por probar otros países de VPN.

### Instalar
- Primera vez: Downloader → código **8102941**, y permitir "Instalar apps desconocidas" para TV Fácil.
- Después: se actualiza sola.

## TV Fácil v8 (v7.15 en adelante)
- **Reloj y clima** de Calonge arriba a la derecha.
- **Mensajes y recordatorios**: barra amarilla abajo; los mensajes nuevos salen una vez en grande. Se editan en `mensajes.json` (no arma app nueva).
- **Salvapantallas** con las fotos tras 3 min sin tocar el control (máximo 1 hora).
- **"Llamar a Andrés"** avisa al celular por ntfy (secreto NTFY_TOPIC) y abre AnyDesk.
- **Señal de vida**: la app avisa a healthchecks.io (secreto HC_PING_URL) cada hora mientras se usa; si pasan ~3 días sin uso, llega un correo.
