# AGENTS.md

Engineering guidance for anyone (human or agent, in any harness) making changes in this repository. It applies
repo-wide; module-scoped guidance (where present) narrows or supplements it and takes precedence for that module.

## Repository shape

Gradle multi-module Kotlin/Spring Boot project on Java 26, managed with the Gradle wrapper and pinned by `mise.toml`.

| Module              | Role                                                                                   | Port |
|---------------------|----------------------------------------------------------------------------------------|------|
| `event-contracts`   | Shared event/DTO definitions and wire-format compatibility tests                       | –    |
| `inventory-stream`  | Inventory event ingestion/streaming service                                            | 8080 |
| `inventory-state`   | Materialized inventory state service                                                   | 8081 |
| `lake-writer`       | Persists events to the data lake (SeaweedFS, via the AWS SDK for Java as an S3 client) | 8082 |
| `legacy-consumer`   | Bridges legacy inventory updates onto the current event model                          | 8083 |
| `integration-tests` | Cross-module/integration coverage                                                      | –    |

Backing infrastructure: a Kafka-compatible broker, MongoDB, and SeaweedFS (S3-compatible object storage). Services are
configured entirely through environment variables (Kafka bootstrap servers, Mongo URI, state/lake service URLs,
SeaweedFS S3 endpoint and credentials, named `LAKE_S3_*`) so the same images run unmodified across local Compose and
other environments.

## How to make a change

1. **Find the nearest owning-module example first.** Before introducing a new pattern, look for how the module you're
   editing already solves a similar problem (a similar listener, repository, client, DTO mapping, etc.) and follow that
   shape. Don't import a pattern from a different module unless the owning module has none to offer.
2. **Keep changes small and local.** Scope edits to the module that owns the behavior you're changing. Cross-module
   changes should be the exception, driven by an actual contract change in `event-contracts`, not convenience.
3. **Write idiomatic Spring/Kotlin.** Constructor injection over field injection, data classes for DTOs/events,
   null-safety instead of platform types, explicit over clever. Match the Kotlin/Spring conventions already used in the
   module you're touching.
4. **When the architecture is unresolved, consult the docs before improvising.**
   `docs/context.md` describes the current architecture migration target for this platform — check it (and any
   module-level `AGENTS.md`) when a change touches how services are wired together, not just what a single method does.
5. **Test narrow, then wide.** Run the owning module's tests first (e.g. `mise exec -- gradle :inventory-stream:test`),
   then run the full
   `mise exec -- gradle check` before considering the change done.
6. **Understand existing regression/compatibility assertions before touching them.**
   Modules such as `event-contracts` carry compatibility tests (see the
   `compatibilityTest` source set) that pin the wire format of already-published events against checked-in fixtures. If
   a change requires updating one of these assertions, first understand which guarantee it protects and who depends on
   it — a wire-format break there fails silently for already-published events, not just at compile time.
7. **Update stale assertions when behavior legitimately changes.** When an existing test encodes behavior that a change
   is meant to replace, update the assertion to the new behavior alongside the change rather than leaving both in a
   failing state; describe the changed guarantee in the commit.
8. **Regenerate checked-in fixtures when the event model changes.** Fixture files under
   `compatibilityTest` are generated artifacts; when the domain model is extended, regenerate them from the current
   model so the fixtures and the code agree.

## Verification

Run from the repository root:

```sh
mise exec -- gradle check
mise exec -- gradle jibDockerBuild
mise exec -- hk check --all
mise run smoke
```

`mise run smoke` builds the local images, brings up the full Compose stack on a shared network (services addressed by
their Compose service DNS names), waits for health, exercises a real event through Kafka end-to-end, and always tears
the stack down afterward, success or failure.

## Module-scoped guidance

Check for an `AGENTS.md`/`CLAUDE.md` inside the module you're editing (for example
`inventory-stream/AGENTS.md`) before starting — it may describe an in-flight transition or constraint that isn't obvious
from the code alone.
