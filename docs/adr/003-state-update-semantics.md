# ADR-003: Sequence guard plus optimistic version for state updates

## Status

Accepted

## Context

`inventory-state` holds the authoritative current quantity for every
`(facilityId, sku)`. It receives both delta and absolute updates, delivered
at-least-once and, occasionally, out of the order they were produced (a
redelivery, a slow consumer catching up, or a replay that overlaps live
traffic). It needs to reject three distinct failure modes without conflating
them:

1. The exact same event arriving twice (a redelivery of `eventId` X).
2. An event that is real but *older* than what has already been applied (an
   out-of-order or replayed delivery of an event whose `sequence` predates the
   current state).
3. A concurrent update from another writer racing with this one for the same
   key.

Treating all three the same way — e.g. rejecting anything that isn't exactly
the next expected sequence — would make ordinary at-least-once redelivery
indistinguishable from a genuine conflict, and would make the API unable to
tell a caller *why* an update didn't apply.

## Decision

`InventoryState` carries two independent counters. A delta or absolute update
is attempted as one MongoDB `findAndModify` whose match predicate requires all
of the following for the requested `(facilityId, sku)`:

- `expectedVersion` equals the stored `version`;
- the stored `sequence` is lower than the request's `sequence`; and
- `eventId` is not already present in the document's applied-event set.

The quantity change, new sequence, applied-event insertion, and version
increment are committed by that single atomic operation. A first write with
`expectedVersion = 0` is an atomic insert protected by the unique
`(sku, facilityId)` index, so concurrent creators also have exactly one winner.

If the conditional write does not match, the repository reads the current
document and classifies the result in this order:

1. **Duplicate check** — an already-applied `eventId` returns
   `UpdateStatus.DUPLICATE` with state unchanged.
2. **Optimistic version check** — a mismatched `expectedVersion` returns
   `UpdateStatus.CONFLICT`; another writer won the conditional race.
3. **Sequence guard** — a request whose `sequence` is less than or equal to
   the stored `sequence` returns `UpdateStatus.STALE`; it is causally older
   than, or tied with, the state already stored.
4. Any other failed predicate is conservatively classified as `CONFLICT`.

A successful write returns `UpdateStatus.APPLIED` with the resulting state.

`sequence` and `version` are deliberately independent: `sequence` is a
property of the event stream (monotonic per `(facilityId, sku)`, meaningful
for replay/backfill ordering), while `version` is a property of the storage
row (an optimistic-locking counter, meaningful for concurrent-writer
detection). Because version conflicts are classified before stale sequences,
a caller holding an old `expectedVersion` first receives `CONFLICT`; after it
re-reads and retries against the current version, the sequence guard can
classify the old event as `STALE`.

## Consequences

- Callers can distinguish "already done" (`DUPLICATE`), "too old to apply"
  (`STALE`), and "someone else changed this first" (`CONFLICT`) from a
  genuinely applied update, and can choose a different retry strategy for
  each.
- Replaying an old range of events against live state is safe: duplicates are
  no-ops, and after any required optimistic-version refresh, sequences at or
  below current state resolve to `STALE` rather than changing the quantity.
- Every update is a single, self-contained conditional write; `inventory-state`
  does not need distributed locks or cross-request coordination to remain
  correct under concurrent and out-of-order writers.
