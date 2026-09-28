# AGENTS.md — inventory-stream

Last reviewed: 2026-01-09

Scoped guidance for the `inventory-stream` module. Repo-wide guidance in the root
[`AGENTS.md`](../AGENTS.md) still applies; this file narrows it for this module.

## Role

`inventory-stream` (port 8080) is the ingestion entrypoint for inventory domain events.
It consumes canonical delta events from `inventory-events` and simple absolute updates
from `inventory-adjustments`, both keyed `facilityId:sku`.

The canonical delta flow calls `inventory-state` and `lake-writer` over HTTP through the
module's handler dispatcher. A Mongo-backed processing ledger keyed by `eventId` protects
that flow during retries and redelivery. The simpler adjustment flow is a plain direct
listener that calls only `inventory-state`.

## Current dispatch shape and where it's headed

The canonical delta flow still uses the module's `EventHandler` registry. The adjustment
flow is already implemented as a plain `@KafkaListener`.

The direction for this module is toward direct Spring Kafka listeners instead of
continuing to grow the generic handler registry. Concretely, as of this review:

- Prefer a direct listener for new event types and topics.
- Leave existing registry-backed flows in place unless the change requires modifying
  them.
- Keep changes local to the selected path rather than restructuring the dispatcher.

## Environment configuration

No hardcoded broker/service addresses — everything comes from environment variables:
Kafka bootstrap servers, the `inventory-state` base URL, the `lake-writer` base URL, and
the Mongo URI backing the idempotency ledger.

## Testing

Run `mise exec -- gradle :inventory-stream:test` for module-local coverage before the
full `mise exec -- gradle check`. When you touch dispatch logic, the parts most worth a
focused test are: idempotent handling of a redelivered `eventId`, and correct routing for
each of the two live topics.
