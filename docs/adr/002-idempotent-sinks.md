# ADR-002: External writes are idempotent by construction

## Status

Accepted

## Context

Two services in the platform write to storage systems that sit outside
Kafka's delivery guarantees: `inventory-state` writes to MongoDB, and
`lake-writer` writes to SeaweedFS. Both are reached, directly or indirectly, from
Kafka consumers that use at-least-once delivery — a consumer restart, a
timeout on the HTTP call from `inventory-stream` to either service, or a
deliberate replay for backfill purposes can all cause the same domain event to
be delivered, and therefore written, more than once.

If either write path treated "apply this event" as an unconditional append or
increment, a redelivered event would double-count a quantity change or create
a duplicate lake object. Both would silently corrupt data in a way that is
expensive to detect and repair after the fact.

## Decision

Every external write in the platform is designed so that performing it twice
with the same input has the same effect as performing it once:

- **`lake-writer`** derives the object key deterministically from the event's
  own `eventId` and `eventTime`
  (`raw-inventory/year=.../month=.../day=.../{eventId}.json`). Writing the
  same event again resolves to the same key and overwrites it with byte-for-byte
  identical content — `S3ObjectStore.putObject` (using the AWS SDK for Java
  purely as an S3 protocol adapter to SeaweedFS) issues a plain
  S3-compatible `PutObject` call, which SeaweedFS treats as an in-place
  overwrite rather than a new version.
- **`inventory-state`** records every applied `eventId` and rejects a repeat
  of the same `eventId` with `UpdateStatus.DUPLICATE` instead of re-applying
  the quantity change. See [ADR-003](003-state-update-semantics.md) for the
  full update-evaluation order, including how this interacts with the
  sequence and version guards.
- **`inventory-stream`'s delta handler** keeps a MongoDB-backed idempotency
  ledger keyed by `eventId`. A redelivered `inventory.delta.v1` record
  short-circuits before making redundant state/lake calls, and the handler
  records completion only after both external effects succeed. The adjustment
  listener does not use this ledger; its state write remains safe because
  `inventory-state` independently deduplicates every applied `eventId`.

## Consequences

- At-least-once delivery from Kafka, and any retry logic layered on top of
  it, can never produce duplicate state changes or duplicate lake objects.
- Backfills and replays are safe to run against live data: re-publishing a
  window of already-processed events is a no-op everywhere it matters.
- Idempotency is enforced at more than one layer: the ingestion ledger is the
  ingestion-side boundary that records completion of both external effects
  (state and lake) for a canonical event, and each sink's own dedup logic is
  a second, independent check that holds on its own.
