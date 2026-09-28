# ADR-004: Keep external sink delivery on the handler-and-ledger path

- Date: 2026-06-14

## Status

Accepted

## Context

`docs/migrations/kafka-simplification.md` lays out a broader intent to replace
unnecessary handler indirection with direct Spring Kafka listeners, and to use
Kafka transactions where a flow consumes from Kafka and produces only to
Kafka. Those are related migration tools, not a claim that one transaction can
cover every sink.

For `inventory-state`, a direct listener is viable because the MongoDB update
is already a single, conditional, idempotent write (see
[ADR-003](003-state-update-semantics.md)). The listener commits its Kafka
offset only after that write succeeds; if the process fails between the write
and the offset commit, redelivery is safe because the `eventId`/`sequence`
guards make the repeated state update a no-op. A Kafka transaction does not
make MongoDB and Kafka atomic and is not presented as doing so.

For Kafka-to-Kafka flows such as consuming a legacy record and publishing its
canonical event, a Kafka transaction can atomically bind the consumed offset
to the output record. That removes the corresponding consume/publish gap
without introducing an external transaction coordinator.

`lake-writer`'s write target is SeaweedFS, an S3-compatible object store with no
participation in Kafka's transaction protocol. Kafka transactions coordinate
offset commits with writes to *other Kafka topics* (and, via output topics,
with downstream Kafka consumers) — they do not extend to arbitrary external
systems. Moving `lake-writer` to a direct listener would trade a well-understood
idempotent-overwrite guarantee (see [ADR-002](002-idempotent-sinks.md)) for a
dual-write problem: a transaction can commit the Kafka offset and still lose
  the SeaweedFS write to a crash, or the reverse, with nothing to reconcile the two
after the fact. The same is true for any future consumer that needs to
*replay* from the object store back through the platform — object storage is
not a Kafka log, so a replay from it cannot ride a Kafka consumer transaction
either.

## Decision

The simplification target is deliberately split by boundary:

- services that own a consequence should move toward direct Spring Kafka
  listeners where their sink-specific idempotency makes redelivery safe;
- Kafka-to-Kafka flows should use Kafka transactions so the consumed offset
  and produced records commit together; and
- `lake-writer` and all external-object-storage replay paths remain on the
  current handler-plus-ledger shape (a Kafka-consuming handler, a MongoDB-backed
  idempotency ledger, and an idempotent overwrite at the sink).

The last category stays on that path until a mechanism exists that gives the
same recoverability across the Kafka/object-storage boundary. A Kafka
transaction alone is not such a mechanism.

## Consequences

- `inventory-state` is a near-term target for adopting a direct
  `@KafkaListener`; its existing conditional MongoDB update, rather than a
  Kafka transaction, remains the redelivery safety mechanism.
- `lake-writer` keeps depending on `inventory-stream`'s ledger-backed handler
  calling it over HTTP; this is a durable architectural choice, not a
  temporary gap.
- Any future object-storage replay tooling must be built against the
  handler-plus-ledger idempotency guarantees (deterministic, overwritable
  object keys) rather than assuming Kafka transactional semantics extend
  into SeaweedFS.
- If a transactional or otherwise atomic bridge between Kafka and SeaweedFS (or
  an equivalent object store) becomes available, this decision should be
  revisited; until then, the scope boundary drawn here stands.
