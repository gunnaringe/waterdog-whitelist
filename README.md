# waterdog-whitelist

A [WaterdogPE](https://waterdog.dev/) plugin that denies every player by default and only lets
through XUIDs explicitly added to a SQLite whitelist. Every login attempt (allowed or denied) is
recorded, so you can see who tried to join and whitelist them without editing config or restarting
the proxy.

## Why

WaterdogPE has no built-in whitelist — the only "whitelist" references in its source are for
server-list query responses, not join control. This plugin hooks `PlayerLoginEvent`, which fires
after Xbox Live authentication already succeeded, so the XUID/gamertag it sees is verified.

## Commands

All console-only by design (no permission is granted to any player by default, and
`ConsoleCommandSender` always passes permission checks):

- `/whitelist add <xuid> [gamertag]`
- `/whitelist remove <xuid>`
- `/whitelist list`
- `/whitelist attempts [count]` — most recent join attempts, allowed and denied, newest first

Denied and allowed attempts are also logged to the proxy console/log file directly.

## Data

SQLite database at `plugins/waterdogwhitelist/whitelist.db` (relative to the proxy's working
directory) — two tables, `whitelist` and `join_attempts`. Created automatically on first run.

## Building

```
mvn package
```

Produces a shaded `target/waterdog-whitelist.jar` (WaterdogPE API is `provided`, so it isn't
bundled — everything else, including the SQLite JDBC driver, is).

Releases are built and attached to GitHub Releases automatically by
`.github/workflows/release.yml` on any `v*` tag push.
