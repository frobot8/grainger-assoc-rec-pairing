# CLAUDE.md

This file exists so Claude (and any other harness that only looks for `CLAUDE.md`) picks
up the same guidance every other agent uses. It intentionally does not add anything new.

See [`AGENTS.md`](./AGENTS.md) in this directory for the full, current engineering
guidance: module map, how to make a change (nearest owning-module example, small local
changes, idiomatic Spring/Kotlin, consult `docs/context.md` when architecture is
unresolved, test narrow-then-wide, understand compatibility assertions before changing
them, update stale assertions when behavior legitimately changes, regenerate checked-in
fixtures when the event model changes), and the verification commands (`mise exec --
gradle check`, `mise exec -- gradle jibDockerBuild`, `mise exec -- hk check --all`,
`mise run smoke`).

If you're working inside a specific module, also check for a module-level
`AGENTS.md`/`CLAUDE.md` (for example `inventory-stream/AGENTS.md`) — it takes precedence
for that module.
