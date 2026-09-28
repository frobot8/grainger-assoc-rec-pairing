# Inventory Event Platform

A Kotlin/Spring platform for tracking on-hand inventory at fulfillment facilities from an ordered stream of domain
events. Each quantity is identified by a `(facilityId, sku)` pair. Shipments, picks, and sales produce signed delta
events; cycle counts and corrections produce absolute adjustment events. The platform applies events to current state
and archives the raw events sent to the lake for replay and analysis.

## Event flow

```text
legacy-inventory-updates ──> legacy-consumer ──> inventory-events ──> inventory-stream
                                                                        /         \
                                                                       v           v
                                                               inventory-state   lake-writer
                                                                       |           |
inventory-adjustments ──────────────────────────────> inventory-stream |           |
                                                                       v           v
                                                                   MongoDB     SeaweedFS
```

- For `inventory-events` deltas, `inventory-stream` deduplicates by `eventId`
  in its MongoDB ledger, applies the delta through `inventory-state`, archives it through `lake-writer`, then marks the
  event processed.
- For `inventory-adjustments`, `inventory-stream` applies the absolute update through `inventory-state`; that path
  relies on the state's own conditional, idempotent update rather than the delta handler's ledger/lake workflow.
- `inventory-state` maintains the authoritative quantity in MongoDB using a per-key sequence guard plus an optimistic
  version check.
- `lake-writer` stores one deterministic JSON object per event, partitioned by the event's own UTC `eventTime`.
- `legacy-consumer` translates older inventory updates into the canonical
  `inventory.delta.v1` contract and republishes them to `inventory-events`.

Every producer uses the Kafka key `facilityId:sku`. This keeps events for one facility/SKU pair in one partition; the
event's monotonically increasing
`sequence` makes duplicates, late events, replays, and backfills safe to identify at the state boundary.

For the full domain and API contracts, read [docs/context.md](docs/context.md). For runbooks and troubleshooting, read
[docs/operations.md](docs/operations.md).

## Modules and ports

| Module              | Purpose                                                                       | Host port / health          |
|---------------------|-------------------------------------------------------------------------------|-----------------------------|
| `event-contracts`   | Shared event, state, request/response, and Kafka-key types                    | library only                |
| `inventory-stream`  | Kafka consumer, idempotency ledger, state/lake dispatch                       | `8080` — `/actuator/health` |
| `inventory-state`   | MongoDB-backed current inventory and update API                               | `8081` — `/actuator/health` |
| `lake-writer`       | SeaweedFS-backed raw event archive (via the AWS SDK for Java as an S3 client) | `8082` — `/actuator/health` |
| `legacy-consumer`   | Legacy Kafka-to-Kafka translation bridge                                      | `8083` — `/actuator/health` |
| `integration-tests` | Cross-module integration scenarios and test support                           | test module only            |

Local infrastructure is exposed on Kafka-compatible Redpanda `9092`, MongoDB
`27017`, and SeaweedFS's S3 API on host port `9000` (container port `8333`). Compose connects every container to the
single `inventory-net` network and services address each other by Compose DNS name (`redpanda`, `mongo`,
`seaweedfs`, `inventory-state`, `lake-writer`, `inventory-stream`,
`legacy-consumer`). SeaweedFS has no separate console or init container: it runs as `weed mini -dir=/data` and
pre-creates its `inventory-lake` bucket via the `S3_BUCKET` environment variable, serving object data directly.

## Prerequisites

- [mise](https://mise.jdx.dev/) — installs the exact Java, Gradle, Kotlin, hk, and Pkl versions declared in [
  `mise.toml`](mise.toml).
- Docker Engine or Docker Desktop with the Compose plugin.
- A POSIX shell for the `smoke` mise task (macOS or Linux; WSL works on Windows).
- Java 26. `mise install` provides it; no separate JDK is needed.

A Gradle wrapper is committed (`./gradlew`, `gradlew.bat`) for tooling that requires it, but the documented root
commands run Gradle through mise so the selected Java 26 and Gradle version always match the repository.

Install and verify the toolchain from the repository root:

```sh
mise trust
mise install
mise doctor
mise current
```

Review `mise.toml` before trusting it; a trusted mise configuration can define executable project tasks.

## Build and verify

Run every command below from the repository root.

### Entire repository

Build every module:

```sh
mise exec -- gradle build
```

The root quality gate compiles all modules, runs unit/integration/contract checks, and runs the event-contract
compatibility fixtures:

```sh
mise exec -- gradle check
```

Build all runnable local container images with Jib (Docker must be running):

```sh
mise exec -- gradle jibDockerBuild
```

The four image tags are:

```text
inventory-stream:local
inventory-state:local
lake-writer:local
legacy-consumer:local
```

Run all repository hooks/checks explicitly through hk:

```sh
mise exec -- hk check --all
```

To install the same hk checks as local Git hooks:

```sh
mise exec -- hk install
```

### One module

Build one module with a fully qualified Gradle task:

```sh
mise exec -- gradle :event-contracts:build
mise exec -- gradle :inventory-stream:build
mise exec -- gradle :inventory-state:build
mise exec -- gradle :lake-writer:build
mise exec -- gradle :legacy-consumer:build
mise exec -- gradle :integration-tests:build
```

Run only that module's quality gate with the corresponding `check` task:

```sh
mise exec -- gradle :event-contracts:check
mise exec -- gradle :inventory-stream:check
mise exec -- gradle :inventory-state:check
mise exec -- gradle :lake-writer:check
mise exec -- gradle :legacy-consumer:check
mise exec -- gradle :integration-tests:check
```

Build only one runnable service image with Jib:

```sh
mise exec -- gradle :inventory-stream:jibDockerBuild
mise exec -- gradle :inventory-state:jibDockerBuild
mise exec -- gradle :lake-writer:jibDockerBuild
mise exec -- gradle :legacy-consumer:jibDockerBuild
```

Run one service outside Compose with its module `bootRun` task (configure the environment described
in [docs/operations.md](docs/operations.md)):

```sh
mise exec -- gradle :inventory-stream:bootRun
mise exec -- gradle :inventory-state:bootRun
mise exec -- gradle :lake-writer:bootRun
mise exec -- gradle :legacy-consumer:bootRun
```

## Full-stack smoke test

Docker must be running. The single smoke task builds the four `:local` images, pulls the pinned infrastructure images,
starts Redpanda, MongoDB, SeaweedFS, and all services, waits for every Actuator health endpoint, publishes a fixed
`inventory.delta.v1` event to `inventory-events` using the correct
`facilityId:sku` key, verifies the expected quantity through
`inventory-state`, and verifies the event object in SeaweedFS:

```sh
mise run smoke
```

The smoke task starts from empty local state and always tears down the Compose stack **including its named volumes**,
whether it succeeds or fails. The smoke stack is isolated under Compose project `inventory-event-platform-smoke`
and uses host ports `18080`–`18083`, `19092`, `27018`, and `19000`; interactive `mise run up` keeps the default ports
listed above. A non-zero exit means one of the health, Kafka publication, state, or object-store assertions failed;
see [Smoke failures](docs/operations.md#smoke-failures) for diagnosis.

For an interactive full stack, run one command:

```sh
mise run up
# inspect or exercise the services
mise run down
```

`up` follows the native mise dependency chain
`build → images → pull → up`: unchanged module outputs make `build` report
`sources up-to-date, skipping`, while image preparation still checks Docker daemon state. Compose starts with
`--pull never` after those dependencies finish.

## mise task reference

| Task                       | Command               | What it does                                                                                                                                                      |
|----------------------------|-----------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Build all modules          | `mise run build`      | Compiles and assembles all modules (`gradle assemble`), cached by mise on source/output.                                                                          |
| Test/verify                | `mise run test`       | Runs the repository Gradle check task.                                                                                                                            |
| Pull infrastructure images | `mise run pull`       | After `images`, serially checks the pinned Redpanda and MongoDB tags and digest-pinned SeaweedFS image; uncached because Docker daemon state is external to mise. |
| Build images               | `mise run images`     | After the cached `build` task, builds all four `:local` images with Jib. Uncached because Docker daemon state is external to mise.                                |
| Start infrastructure       | `mise run infra-up`   | Depends on `pull`, transitively preparing local images first; starts Redpanda, MongoDB, SeaweedFS, and the Kafka topic initializer with `--pull never`.           |
| Stop infrastructure        | `mise run infra-down` | Stops the infrastructure stack.                                                                                                                                   |
| Start full stack           | `mise run up`         | Follows `build → images → pull → up`, then starts every container with `--pull never`.                                                                            |
| Stop full stack            | `mise run down`       | Stops and removes the Compose stack.                                                                                                                              |
| Reset local data           | `mise run reset`      | Stops Compose and removes the MongoDB and SeaweedFS named volumes.                                                                                                |
| Run stream locally         | `mise run run-stream` | Runs `inventory-stream` with Gradle `bootRun`.                                                                                                                    |
| Run state locally          | `mise run run-state`  | Runs `inventory-state` with Gradle `bootRun`.                                                                                                                     |
| Run lake locally           | `mise run run-lake`   | Runs `lake-writer` with Gradle `bootRun`.                                                                                                                         |
| Run legacy bridge locally  | `mise run run-legacy` | Runs `legacy-consumer` with Gradle `bootRun`.                                                                                                                     |
| End-to-end smoke           | `mise run smoke`      | Follows `build → images → pull → smoke`, starts the stack, checks Kafka → state + lake, and tears down.                                                           |

## Architecture notes

- [Domain and architecture context](docs/context.md)
- [Operations runbook](docs/operations.md)
- [Kafka simplification migration](docs/migrations/kafka-simplification.md)
- [ADR-001: event-time lake partitions](docs/adr/001-event-time.md)
- [ADR-002: idempotent external writes](docs/adr/002-idempotent-sinks.md)
- [ADR-003: state update semantics](docs/adr/003-state-update-semantics.md)
- [ADR-017: external sink delivery scope](docs/adr/004-external-sink-delivery.md)
