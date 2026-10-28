# Permisos y Comandos (Forge 1.20.1 / 47.4.20)

Mod **solo de servidor**: los clientes (incluso vanilla) no necesitan instalarlo.

Va solo en la carpeta `mods/` del servidor. No usa clases ni paquetes de cliente, no registra bloques/items
y declara `displayTest="IGNORE_SERVER_VERSION"`, asi que los jugadores entran con Minecraft vanilla 1.20.1
sin instalar nada. Todo lo que ven (prefijos, ping en el tab, mensajes) viaja con paquetes normales.
Ojo: si el servidor tiene OTROS mods que si necesiten estar en el cliente, los jugadores tendran que instalar esos.

## Obtener el .jar
Requiere JDK 17 y Gradle 8.1.1 (o el wrapper del MDK de Forge 1.20.1-47.4.20).

**Opcion A, sin instalar nada (GitHub Actions):**
1. Crea un repositorio en GitHub y sube todo el contenido de esta carpeta (incluida `.github`).
2. Pestana *Actions* -> *Compilar mod* (se lanza solo al subir, o con *Run workflow*).
3. Al terminar, descarga el artefacto `permscmds-jar` (zip con el .jar dentro).

**Opcion B, en tu PC:** `gradle build` -> el jar queda en `build/libs/permscmds-1.20.1-1.0.0.jar`.

Despues, copia el .jar a `mods/` del servidor.

## Archivos (se crean solos en `config/permscmds/`)
- `permissions.json` grupos y usuarios (se edita con /perm o a mano + `/perm reload`)
- `settings.json` ajustes: opsBypass, tpa, max homes, ping en el tab, `/rtp` (`rtpMaxRadius`, `rtpMinRadius`), apodos, limites de `/spawnmob`, `/explode`...
- `data.json` homes y spawn
- `custom_commands.json` comandos personalizados (requieren reiniciar o `/reload`)

## Rangos
- Todos los jugadores son del grupo **user** por defecto (prefijo `&a[User] ` en verde) sin tener que asignarlo.
  Cambiar el grupo por defecto: campo `defaultGroup` de `permissions.json`.
- Se crea tambien el grupo **admin** (`&c[Admin] `, hereda de user, con `permscmds.*` y `command.*`).
- Si ya tenias un `permissions.json` creado, borralo para regenerarlo o edita `defaultGroup`.
- Un jugador con otros grupos sigue heredando siempre el grupo por defecto.

## Lista de jugadores (tab)
`[Prefijo] Nombre [23ms]`, con el ping en verde (<80), amarillo (<150) o rojo. Se refresca cada 3 s
(el valor de latencia lo actualiza Minecraft aprox. cada 15 s). Se desactiva con `tabShowPing: false`.
El prefijo tambien sale en el chat.

## Permisos
- Nodos con comodin: `*`, `command.*`, `permscmds.*`.
- Negacion con `-`: `-command.kill`.
- `a.b` tambien concede/niega `a.b.c`.
- Gana la regla mas especifica; a igualdad, la negacion. Primero cuentan los permisos propios del jugador,
  luego sus grupos (por peso, con herencia) y por ultimo el grupo por defecto.
- Si un nodo no esta definido, se aplica el comportamiento normal de Minecraft.

### Comandos de Minecraft: `command.<ruta>`
- `command.gamemode` permite `/gamemode ...`; `command.gamemode.creative` solo `/gamemode creative`.
- `-command.kill` bloquea `/kill`.
- Para permitir un comando a quien no es op, el mod sube su nivel solo lo justo para ese comando.
- Cuidado: `command.execute`, `command.function`, `command.op` o `command.*` equivalen a dar casi todo.
- Los operadores nivel 4 se saltan las comprobaciones (`opsBypass`).

### Nodos del mod
Cada comando usa el nodo `permscmds.<nombre>`. Los que actuan sobre otros jugadores usan ademas `permscmds.<nombre>.others`.

**Permitidos por defecto a todos** (como en MoreCommands): `home`, `sethome`, `spawn`, `tpa`, `back`, `ping`, `warp`, `rtp`, `hat`,
`enderchest`, `nick`, `top`, `anvil`, `craft`, `ascend`, `descend`, `cyclepainting`, `dropstore`, `realname`, `skull`, `suicide`,
`through`, `tps`, `whereami`, `vault`, `kit`.

**Solo con permiso (u op):** `setspawn`, `setwarp`, `delwarp`, `limitwarp`, `warp.limited`, `fly`, `god`, `heal`, `feed`, `gamemode`,
`repair`, `repair.all`, `invsee`, `vanish`, `speed`, `superpickaxe`, `jump`, `spawnmob`, `explode`, `fireball`, `cmdblock`,
`time`, `weather`, `difficulty`, `barrier`, `break`, `broadcast`, `cannon`, `defuse`, `dimension`, `eclone`, `enchantmax`,
`extinguish`, `more`, `rename`, `smite`, `spawner`, `unlimited`, `wipeout`, `sysinfo`, `whois`, `disable`,
`kit.create`, `kit.delete`, `kit.nocooldown`, `kit.<nombre>` (para restringir un kit concreto), `admin` (/perm).

Maximo de homes: `permscmds.homes.<n>` o `permscmds.homes.unlimited`.
`/console` (`/consolecommand`) no usa permisos: solo ops nivel 4 y la consola.

## Comandos
```
/perm groups | creategroup <n> | deletegroup <n>
/perm group <g> info | add|remove <permiso> | parent add|remove <g2> | prefix <texto &c|none> | weight <n>
/perm user <jugador> info | add|remove <permiso> | setgroup|addgroup|removegroup <grupo>
/perm check <jugador> <permiso>      /perm reload

Teletransporte (sin cooldown):
/sethome [n] /home [n] /delhome <n> /homes   /spawn /setspawn   /back
/tpa <j> /tpahere <j> /tpaccept /tpdeny
/warp <n> /warps /setwarp <n> /delwarp <n> /limitwarp <n> /warpinfo <n>
/rtp (/wild, /rndtp)   aleatorio, sin limite de radio: solo el borde del mundo (Overworld)
/top  /jump  /through  /ascend  /descend  /dimension <dim>

Jugador:
/vuelo (/fly) /god /heal /feed /vanish (/v) /speed <0-10|reset> /gm <modo> /gmc /gms /gma /gmsp
/ping /nick <apodo|off> /realname <texto> /hat /repair [all] (/fix) /enderchest (/ec) /invsee <j>
/superpickaxe /suicide /extinguish /skull <jugador> [n] /whereami /tps

Objetos:
/anvil /craft /dropstore /barrier /more /rename <nombre> /enchantmax /unlimited /cmdblock
/kit <n> /kits /createkit <n> <segundos> /delkit <n>     (los segundos de espera del kit pueden ser 0)
/vault <n>                                              (vaults por jugador: settings `vaults` y `vaultRows`)

Mundo y diversion:
/day /night /sun /rain /thunder   /peaceful /easy /normal /hard
/spawnmob <mob> [n]  /explode [pos] [potencia] [fuego] [destruir] [lanzar]  /fireball [potencia]
/cannon [cantidad] [velocidad] [mecha]  /defuse [rango|all]  /smite [entidades]  /eclone [entidades]
/break  /spawner <mob>  /cyclepainting  /wipeout  /broadcast <msg>

Administracion:
/disable <comando> | /enable <comando>   comandos solo para ops
/whois <jugador>  /sysinfo
/console <comando>   (solo ops nivel 4)
```

## Ajustes extra (settings.json)
`defaultMaxHomes` (-1 = infinito, 0 = sin homes), `vaults`, `vaultRows`, `doFarmlandTrample`, `doExplosions`, `doChatColours`
(colores con & en el chat), `nickMaxLength`, `tpaTimeoutSeconds`, `disabledCommands`.

## Lo que NO se ha podido hacer solo en servidor
- Comandos de cliente de MoreCommands (/alias, /bind, /calc, /macro, /ptime, /screenshot, /urban, /fullbright...).
- `/fakeplayer`, `/potion`, `/reach` y `/fill` asincrono. Para ampliar `/fill`, sube el gamerule `commandModificationBlockLimit`.
- Gamerules que necesitan modificar el juego por dentro (hoppers, liquidos infinitos, doMelt, silk spawners...).

## /rtp sin limite
Con `rtpMaxRadius: 0` (por defecto) el destino es cualquier punto dentro del borde del mundo (por defecto ±29.999.984).
Cada intento genera el chunk de destino, asi que el mundo crece rapido y puede notarse un ttiron de lag.
Para limitarlo, pon `rtpMaxRadius` (y `rtpMinRadius`) en `settings.json` o reduce el borde con `/worldborder set`.

## Comandos personalizados (ejemplo)
```json
{ "commands": [
  { "name": "reglas", "permission": "", "runAs": "player",
    "messages": ["&6Reglas", "&f1. Respeta a todos"], "commands": [] },
  { "name": "curar", "permission": "custom.curar", "runAs": "console",
    "messages": ["&aCurado"], "commands": ["effect give {player} minecraft:instant_health 1 1"] }
]}
```

## Notas
- Los comandos que solo se pueden usar gracias a un permiso (no por ser op) funcionan al escribirlos, pero pueden no salir en el autocompletado.
- Si en el servidor tienes plugins de Bukkit (Arclight) con comandos iguales (/home, /spawn, /back...), pueden chocar con estos.
- `/vanish` es una invisibilidad: no oculta al jugador de la lista de jugadores. `/invsee` muestra los 36 huecos (sin armadura).
- Comandos de MoreCommands que son de cliente (colores arcoiris, Discord, /urban, buscar en el chat...) no se pueden hacer solo en servidor.
- El vuelo y el modo dios no se aplican en creativo/espectador (ya los tienen).
