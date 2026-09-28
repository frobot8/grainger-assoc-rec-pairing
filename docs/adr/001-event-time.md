# ADR-001: Partition the raw event lake by event time, not write time

## Status

Accepted

## Context

`lake-writer` persists an immutable JSON copy of each canonical
`InventoryDeltaEvent` delivered to it to SeaweedFS's S3-compatible
storage. Downstream consumers of the lake (analytics, historical backfills,
audits) expect objects for a given day to live under that day's partition so
that a scan of "everything that happened on 2026-05-01" is complete and
correct without scanning the entire bucket.

Events do not always arrive at `lake-writer` on the day they actually
happened. Producers retry, consumers restart from an earlier Kafka offset, and
backfills intentionally replay old events. If the object's date partition were
derived from wall-clock write time, a replayed event from three weeks ago
would land in *today's* partition, corrupting both partitions: today's
partition would contain an unrelated historical record, and the historical
partition would be missing it.

## Decision

`PartitionKeyResolver` (in `lake-writer`) derives the object key's date
partition from the event's own `eventTime` field — the instant the change
actually happened in the source system — converted to UTC, not from the
instant `lake-writer` happens to process the record:

```
raw-inventory/year=YYYY/month=MM/day=DD/{eventId}.json
```

`eventTime` is part of the canonical event envelope
(`InventoryDomainEvent.eventTime`) and is set once by the producer at the
moment the event is created, so it is stable across retries, redeliveries, and
replays of the same `eventId`.

## Consequences

- Late-arriving and replayed events land in the historical partition they
  belong to, regardless of when `lake-writer` actually processes them.
- All partitioning is done in UTC, so there is a single, unambiguous
  partition boundary independent of the timezone of any producer, consumer,
  or operator.
- Consumers that need "what did we learn about on day X" (processing-time
  semantics) rather than "what happened on day X" (event-time semantics) are
  not served by this partitioning and must derive that view separately; the
  platform does not currently need that view.
