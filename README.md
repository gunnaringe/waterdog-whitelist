# waterdog-access

A [WaterdogPE](https://waterdog.dev/) plugin that denies every player by default and grants
access per-world (or per-player, or both, via wildcards), backed by SQLite. Every login attempt
(allowed or denied) is recorded, so you can see who tried to join and grant them access without
editing config or restarting the proxy.

## Why

WaterdogPE has no built-in access control — the only "whitelist" references in its source are for
server-list query responses, not join control. This plugin hooks `PlayerLoginEvent` (fires after
Xbox Live authentication already succeeded, so the XUID/gamertag it sees is verified),
`ServerPreConnectEvent` (fires before every downstream connection, initial or transfer), and a
custom `IJoinHandler` (picks a player's initial world instead of always using `priorities[0]`).

## Access model

Access is keyed by `(xuid, world)`. Either half can be the wildcard `*`:

- `(realXuid, bob)` — that player can join `bob` specifically.
- `(realXuid, *)` — that player can join any current or future world.
- `(*, bob)` — anyone can join `bob`, no per-player grant needed.
- `(*, *)` — the whole proxy is open to anyone.

A login is allowed if the player has *any* row (a specific grant, or some world is public via a
`(*, ...)` row); which world they actually land on, and whether they can `/server <name>` to
another one, is checked per-world separately.

## Commands

All console-only by design (no permission is granted to any player by default, and
`ConsoleCommandSender` always passes permission checks):

- `/access grant <xuid> [world] [gamertag]` — world defaults to `*` (all current and future)
- `/access revoke <xuid> [world]` — world defaults to `*`
- `/access list`
- `/access attempts [count]` — most recent join attempts, allowed and denied, newest first

Denied/allowed logins, and blocked transfers, are also logged to the proxy console/log directly.

## Admin API

A loopback-only HTTP API on port `8181` (bound to `127.0.0.1`, so only reachable from inside the
same pod/host — e.g. `kubectl exec ... -- curl localhost:8181/...`, never through the proxy's own
listener) mirrors the four commands for scripting:

```
GET /grant?xuid=...&world=...&gamertag=...   (world defaults to *)
GET /revoke?xuid=...&world=...               (world defaults to *)
GET /list
GET /attempts?count=...
```

## Switching worlds

Grant `waterdog.command.server.permission` in the proxy's `permissions_default` so players can use
the built-in `/server <name>` command to switch; `ServerPreConnectEvent` vetoes the transfer if
they don't have access, safely leaving them on their current world rather than disconnecting them.

## Data

SQLite database at `plugins/waterdogaccess/access.db` (relative to the proxy's working directory)
— two tables, `access` and `attempts`. Created automatically on first run.

## Building

```
mvn package
```

Produces a shaded `target/waterdog-access.jar` (WaterdogPE API is `provided`, so it isn't bundled
— everything else, including the SQLite JDBC driver, is).

WaterdogPE's own build depends on CloudburstMC snapshot artifacts not mirrored on
`repo.waterdog.dev`, so Maven can't resolve the `waterdog` dependency's full transitive graph even
at `provided` scope. Before building, install the actual runtime jar (a shaded fat jar with no POM
dependencies of its own) as a synthetic local artifact instead:

```
curl -fSL -o /tmp/waterdog-runtime.jar https://repo.waterdog.dev/main/dev/waterdog/waterdogpe/waterdog/2.0.4-SNAPSHOT/waterdog-2.0.4-20260903.213911-111.jar
mvn install:install-file -Dfile=/tmp/waterdog-runtime.jar \
  -DgroupId=dev.waterdog.waterdogpe -DartifactId=waterdog -Dversion=2.0.4-local1 -Dpackaging=jar
```

`pom.xml`'s dependency version must match whatever you install this as. `.github/workflows/release.yml`
does this automatically before building; keep both in sync with whichever runtime build the
cluster actually deploys.

Releases are built and attached to GitHub Releases automatically by that workflow on any `v*` tag
push.
