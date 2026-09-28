# Operations Runbook

This runbook covers local lifecycle, configuration, health checks, an end-to-end
smoke test, and common recovery cases for the inventory event platform.

## Topology

All Compose services share the `inventory-net` network. Inside that network,
use service DNS names; from the host, use `localhost` and the exposed port.

| Component | Compose DNS | Host endpoint | Persistent data |
| --- | --- | --- | --- |
| Redpanda (Kafka-compatible) | `redpanda:9092` | `localhost:9092` | Kafka log in the container |
| MongoDB | `mongo:27017` | `localhost:27017` | `mongo-data` volume |
| SeaweedFS S3 API | `seaweedfs:8333` | `http://localhost:9000` | `seaweedfs-data` volume |
| `inventory-stream` | `inventory-stream:8080` | `http://localhost:8080` | ingestion ledger in MongoDB database `inventory` |
| `inventory-state` | `inventory-state:8081` | `http://localhost:8081` | inventory documents in MongoDB database `inventory-state` |
| `lake-writer` | `lake-writer:8082` | `http://localhost:8082` | bucket `inventory-lake` in SeaweedFS |
| `legacy-consumer` | `legacy-consumer:8083` | `http://localhost:8083` | Kafka offsets |

The Compose project also contains one one-shot initializer. `redpanda-init`
waits for the broker and idempotently creates `inventory-events`,
`inventory-adjustments`, and `legacy-inventory-updates`. Application
containers wait for the initializer or health check they depend on. SeaweedFS
runs as `weed mini -dir=/data` and needs no separate initializer: its
`S3_BUCKET` environment variable pre-creates `inventory-lake` at startup. The
default local S3 credentials are `seaweedfsadmin` / `seaweedfsadmin`.

## Configuration

Spring's relaxed binding maps uppercase underscore-separated environment
variables onto the service properties shown below.

| Service | Environment variable | Compose value / local default | Purpose |
| --- | --- | --- | --- |
| `inventory-stream` | `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `redpanda:9092` / `localhost:9092` | Canonical Kafka broker bootstrap address. |
| `inventory-stream` | `SPRING_DATA_MONGODB_URI` | `mongodb://mongo:27017/inventory` / local Mongo equivalent | Idempotency-ledger database. |
| `inventory-stream` | `INVENTORY_STATE_BASE_URL` | `http://inventory-state:8081` / `http://localhost:8081` | HTTP base URL for state writes. |
| `inventory-stream` | `INVENTORY_LAKE_BASE_URL` | `http://lake-writer:8082` / `http://localhost:8082` | HTTP base URL for raw lake writes. |
| `inventory-state` | `SPRING_DATA_MONGODB_URI` | `mongodb://mongo:27017/inventory-state` / `mongodb://localhost:27017/inventory-state` | Authoritative-state database. |
| `lake-writer` | `LAKE_S3_ENDPOINT` | `http://seaweedfs:8333` / `http://localhost:9000` | S3-compatible SeaweedFS endpoint (consumed by the AWS SDK for Java as an S3 client). |
| `lake-writer` | `LAKE_S3_ACCESS_KEY` | `seaweedfsadmin` | SeaweedFS S3 access key. |
| `lake-writer` | `LAKE_S3_SECRET_KEY` | `seaweedfsadmin` | SeaweedFS S3 secret key. |
| `lake-writer` | `LAKE_S3_REGION` | `us-east-1` | S3 region signed into requests; SeaweedFS ignores the value but the SDK requires one. |
| `lake-writer` | `LAKE_S3_BUCKET` | `inventory-lake` | Raw event bucket; pre-created by SeaweedFS's `S3_BUCKET` at startup. |
| `legacy-consumer` | `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `redpanda:9092` / `localhost:9092` | Legacy input and canonical output broker. |

Topic names are platform contracts rather than per-deployment toggles:

| Topic | Key | Value |
| --- | --- | --- |
| `inventory-events` | `facilityId:sku` | `InventoryDeltaEvent` (`type: inventory.delta.v1`) |
| `inventory-adjustments` | `facilityId:sku` | `InventoryAdjustmentEvent` (`type: inventory.adjustment.v1`) |
| `legacy-inventory-updates` | `facilityId:sku` | `LegacyInventoryUpdate`; translated by `legacy-consumer` into `inventory.delta.v1` on `inventory-events` |

## Quality gates and image builds

Run the root checks from the repository root with the mise-managed toolchain:

```sh
mise exec -- gradle check
mise exec -- hk check --all
```

Build all four Docker images with Jib:

```sh
mise exec -- gradle jibDockerBuild
```

The root Jib task produces exactly these local tags:

```text
inventory-stream:local
inventory-state:local
lake-writer:local
legacy-consumer:local
```

```sh
mise run pull
mise run build
mise run test
mise run images
```

For one module, qualify the task with the Gradle project path, for example:

```sh
mise exec -- gradle :inventory-state:check
mise exec -- gradle :inventory-state:jibDockerBuild
mise exec -- gradle :inventory-state:bootRun
```

`event-contracts` and `integration-tests` are not runnable services and do not
have Jib tasks.

## Full stack

### Automated smoke test

Docker must be running. From the repository root:

```sh
mise run smoke
```

The smoke task is intentionally self-contained:

1. Builds all four `:local` images with Jib.
2. Pulls the pinned Redpanda, MongoDB, and SeaweedFS images.
3. Starts Redpanda, MongoDB, SeaweedFS, the Kafka topic initializer, and all
   four services through `compose.yaml`.
4. Waits for the infrastructure health checks and each service's
   `/actuator/health` endpoint.
5. Produces a fixed `inventory.delta.v1` fixture to `inventory-events` with the
   canonical `facilityId:sku` Kafka key.
6. Queries `inventory-state` and asserts the expected quantity.
7. Checks SeaweedFS for the deterministic object key derived from the
   fixture's UTC event time and event ID.
8. Tears the Compose stack down, including its named volumes, on success,
   assertion failure, or interruption.

The task uses the isolated Compose project `inventory-event-platform-smoke` and
host ports `18080`–`18083`, `19092`, `27018`, and `19000`, so it can
run beside the default interactive stack. It starts from empty project-specific data.
A zero exit code means both state and lake consequences were observed. Any
non-zero exit means the log immediately above the cleanup line identifies the
failed phase.

### Interactive Compose session

Prepare every image and start the full application stack through the native
mise dependency chain:

```sh
mise run up
```

The chain is `build → images → pull → up`. Mise skips `build` when its declared
sources and assembled outputs are current. Image preparation remains uncached
because Docker daemon state is not represented by filesystem timestamps.
After the dependencies complete, Compose starts with `--pull never`.

Check container and health state:

```sh
docker compose ps
curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:8081/actuator/health
curl --fail http://localhost:8082/actuator/health
curl --fail http://localhost:8083/actuator/health
```

Stop the stack when finished:

```sh
mise run down
```

Compose preserves the MongoDB and SeaweedFS named volumes between ordinary
`down`/`up` cycles.

### Infrastructure in Compose, services in Gradle

For a short edit/run loop, leave only the dependencies in Docker:

```sh
mise run infra-up
```

Then run the services in separate terminals. Their local defaults resolve the
infrastructure and each other on `localhost`:

```sh
mise run run-state
mise run run-lake
mise run run-stream
mise run run-legacy
```

These are aliases for the corresponding module `bootRun` tasks. Stop the
infrastructure afterward:

```sh
mise run infra-down
```

## Exercising the event path manually

With the interactive full stack healthy, publish a canonical delta event to
Redpanda. Keep the Kafka key and payload coordinates identical:

```sh
printf '%s\n' '{"type":"inventory.delta.v1","eventId":"11111111-1111-4111-8111-111111111111","sku":"SKU-RUNBOOK-001","facilityId":"FAC-RUNBOOK-001","eventTime":"2026-06-14T12:00:00Z","sequence":1,"quantityDelta":7}' \
  | docker compose exec -T redpanda rpk topic produce inventory-events \
      -k 'FAC-RUNBOOK-001:SKU-RUNBOOK-001'
```

Query the state consequence (an unknown key returns HTTP 404):

```sh
curl --fail http://localhost:8081/api/inventory/FAC-RUNBOOK-001/SKU-RUNBOOK-001
```

The first publication returns an `InventoryState` whose `quantity` is `7` and
whose `sequence` is `1`. Publishing the exact same fixture again is a safe
no-op: `inventory-stream` recognizes the `eventId` in its ingestion ledger;
`inventory-state` also independently protects against the duplicate.

The lake consequence is at:

```text
inventory-lake/raw-inventory/year=2026/month=06/day=14/11111111-1111-4111-8111-111111111111.json
```

SeaweedFS has no separate console. Inspect the object with the AWS CLI (or any
S3-compatible client) against `http://localhost:9000` using the
`seaweedfsadmin` / `seaweedfsadmin` credentials, for example:

```sh
aws --endpoint-url http://localhost:9000 s3 ls s3://inventory-lake/raw-inventory/ --recursive
```

The `2026/06/14` partition comes from `eventTime`, not the wall clock at which
the command was run.

## State HTTP API

`inventory-state` exposes:

| Method | Path | Request/result |
| --- | --- | --- |
| `GET` | `/api/inventory/{facilityId}/{sku}` | Current `InventoryState`; HTTP 404 if unseen. |
| `POST` | `/api/inventory/delta` | `DeltaUpdateRequest`; returns `InventoryUpdateResponse`. |
| `POST` | `/api/inventory/absolute` | `AbsoluteUpdateRequest`; returns `InventoryUpdateResponse`. |

Update responses use four explicit statuses:

- `APPLIED` — the conditional update succeeded.
- `DUPLICATE` — this `eventId` was already applied; do not retry as a new event.
- `CONFLICT` — `expectedVersion` lost an optimistic-write race; re-read the
  state before deciding whether to retry.
- `STALE` — with a matching current version, `sequence` is not newer than
  stored state; replay/backfill can move past it.

See [ADR-003](adr/003-state-update-semantics.md) for the evaluation order and
why `sequence` and `version` remain separate.

## Smoke failures

Start with the failing phase reported by the `smoke` mise task, then narrow it
with the checks below.

### Image build or container startup

```sh
docker compose ps
docker compose logs inventory-stream inventory-state lake-writer legacy-consumer
```

If an application container is missing, confirm the four `:local` images were
built with `mise run images`. If a dependency is unhealthy, inspect it
directly:

```sh
docker compose logs redpanda redpanda-init mongo seaweedfs
```

### Health never becomes ready

Check the service's Actuator endpoint on its host port, then its logs. A common
cause is using `localhost` in Compose environment variables: containers must
use `redpanda`, `mongo`, `seaweedfs`, `inventory-state`, and `lake-writer` as
DNS names exactly as `compose.yaml` does.

### Kafka record published but state is absent

1. Check `inventory-stream` logs for deserialization, state-call, or ledger
   errors.
2. Confirm the record key is exactly `facilityId:sku` and those strings match
   the payload.
3. Confirm delta events were sent to `inventory-events` and absolute events to
   `inventory-adjustments`.
4. Use a new UUID for a genuinely new event. Reusing an `eventId` intentionally
   deduplicates it.
5. Use a `sequence` newer than the stored sequence for that `(facilityId,
   sku)`; older/replayed sequences return `STALE` by design.

### State exists but no SeaweedFS object is visible

1. Check `inventory-stream` for the call to `lake-writer`, then check
   `lake-writer` logs.
2. Verify the `seaweedfs` container is healthy and bucket `inventory-lake`
   exists (it is pre-created by `S3_BUCKET` at startup).
3. Search the partition derived from the payload's UTC `eventTime`, not today's
   date.
4. Search for `{eventId}.json`. Retries overwrite that exact key; they do not
   create suffixed duplicates.

### Reset local persistent data

Ordinary `mise run down` keeps MongoDB and SeaweedFS data. To intentionally
remove all local inventory, ledger, and lake data, stop Compose and delete its
named volumes:

```sh
mise run reset
```

This is irreversible for local Compose data; use it only when a clean local
state is the goal.
