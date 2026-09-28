# Migration: simplifying Kafka consumption toward direct listeners

## Why this exists

`inventory-stream` is currently the only consumer of the two canonical topics,
`inventory-events` and `inventory-adjustments`. A delta from
`inventory-events` follows a MongoDB-ledger-backed handler: the handler applies
state over HTTP, writes the raw event to `lake-writer`, and records the
`eventId` only after both effects complete. An adjustment from
`inventory-adjustments` calls `inventory-state` directly and relies on that
service's atomic conditional update instead of the delta ledger/lake workflow.

This works, but it means `inventory-state` (whose actual job is a single
conditional write to MongoDB) has no direct relationship to the Kafka topics
that drive it: it only sees HTTP requests from `inventory-stream`. The extra
hop also means state consumption cannot be operated through a state-owned
consumer group, lag metric, or restart policy. The ledger still has a distinct
purpose for the delta path because it coordinates a non-Kafka object-store
effect; that purpose must be preserved when the state hop is simplified.

## Target shape

The migration uses two different mechanisms at two different boundaries:

1. **Direct listeners for sink ownership.** `inventory-state` runs its own
   `@KafkaListener` on `inventory-events` and `inventory-adjustments`, keyed
   and partitioned exactly as they are today (`facilityId:sku`). It commits an
   offset only after its conditional MongoDB write succeeds. If it crashes
   after MongoDB commits but before Kafka records the offset, the event is
   delivered again and the existing `eventId`/`sequence` guards return a safe
   no-op. Kafka does not participate in the MongoDB transaction.
2. **Kafka transactions for Kafka-to-Kafka flows.** A consumer whose only
   output is another Kafka record uses a Kafka transaction to commit the
   consumed offset and produced record atomically. `legacy-consumer` already
   demonstrates this shape for `legacy-inventory-updates` → `inventory-events`
   and remains the reference implementation. Kafka transactions are not used
   as a substitute for idempotency in an external database or object store.
3. Once the direct state listener is proven, `inventory-stream` stops calling
   `inventory-state` over HTTP for those topics. This removes a hop and makes
   `inventory-state`'s consumption visible through its own consumer group and
   lag metrics.

## What this migration does not cover

This migration does not move a flow that terminates in, or replays from, a
non-Kafka external object store onto Kafka transactions. Today that means
`lake-writer` and the SeaweedFS object store it writes to.
[ADR-017](../adr/004-external-sink-delivery.md) records why that boundary is
drawn where it is: `lake-writer` and any future object-storage replay path keep
the current handler-plus-ledger shape, coordinated by `inventory-stream`,
until a replacement provides equivalent crash recovery and idempotency.

## Sequencing

1. Land a direct `@KafkaListener` in `inventory-state` for
   `inventory-events`/`inventory-adjustments`, run alongside the existing
   HTTP endpoint (`POST /api/inventory/delta` / `POST /api/inventory/absolute`)
   so both paths can apply updates during the transition.
2. Let the new state-owned consumer group catch up while the HTTP path remains
   active. Concurrent delivery is safe because the conditional state write
   deduplicates by `eventId`. Once lag and update outcomes are stable for a
   full replay-eligible retention window, remove the state HTTP call from
   `inventory-stream` at a deployment boundary.
3. Reshape `inventory-stream`'s delta handler and MongoDB ledger around the
   remaining lake delivery only. Keep its completed `eventId` records: they
   are still the recovery boundary for the SeaweedFS write required by ADR-017.
4. The HTTP endpoints on `inventory-state` stay — they remain useful for
   direct queries and for the smoke/operational tooling described in
   [docs/operations.md](../operations.md) — only the Kafka-driven write path
   changes.
