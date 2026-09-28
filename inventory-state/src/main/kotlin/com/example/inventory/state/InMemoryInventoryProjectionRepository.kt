package com.example.inventory.state

import com.example.inventory.contract.AbsoluteUpdateRequest
import com.example.inventory.contract.DeltaUpdateRequest
import com.example.inventory.contract.InventoryKey
import com.example.inventory.contract.InventoryState
import com.example.inventory.contract.InventoryUpdateResponse
import com.example.inventory.contract.UpdateStatus
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Internal projection record. [appliedEventIds] tracks every event that produced this
 * key's state so even non-consecutive replays are recognized as duplicates; this is a
 * persistence concern and is intentionally not part of the public [InventoryState].
 */
private data class ProjectionRecord(
    val state: InventoryState,
    val appliedEventIds: Set<UUID>,
)

/**
 * Thread-safe, non-persistent [InventoryProjectionRepository] suitable for unit tests
 * and in-process composition. Applies the same classification rules the production
 * Mongo adapter enforces: event-id dedupe, then version conflict, then sequence
 * staleness, else apply.
 */
class InMemoryInventoryProjectionRepository : InventoryProjectionRepository {
    private val records = ConcurrentHashMap<InventoryKey, AtomicReference<ProjectionRecord>>()

    override fun find(key: InventoryKey): InventoryState? = records[key]?.get()?.state

    override fun save(state: InventoryState): InventoryState {
        val key = InventoryKey(state.sku, state.facilityId)
        records.compute(key) { _, existing ->
            val appliedEventIds = existing?.get()?.appliedEventIds.orEmpty()
            AtomicReference(ProjectionRecord(state, appliedEventIds))
        }
        return state
    }

    override fun updateDeltaIfVersion(request: DeltaUpdateRequest): InventoryUpdateResponse = applyConditionally(
        key = request.key,
        eventId = request.eventId,
        sequence = request.sequence,
        expectedVersion = request.expectedVersion,
        nextQuantity = { currentQuantity -> currentQuantity + request.quantityDelta },
    )

    override fun updateAbsoluteIfVersion(request: AbsoluteUpdateRequest): InventoryUpdateResponse = applyConditionally(
        key = request.key,
        eventId = request.eventId,
        sequence = request.sequence,
        expectedVersion = request.expectedVersion,
        nextQuantity = { request.quantity },
    )

    private fun applyConditionally(
        key: InventoryKey,
        eventId: UUID,
        sequence: Long,
        expectedVersion: Long,
        nextQuantity: (currentQuantity: Int) -> Int,
    ): InventoryUpdateResponse {
        val slot = records.computeIfAbsent(key) { AtomicReference(null) }
        while (true) {
            val before = slot.get()
            val classification = classify(before, eventId, sequence, expectedVersion)
            if (classification != UpdateStatus.APPLIED) {
                return InventoryUpdateResponse(classification, before?.state)
            }
            val currentQuantity = before?.state?.quantity ?: 0
            val nextState =
                InventoryState(
                    sku = key.sku,
                    facilityId = key.facilityId,
                    quantity = nextQuantity(currentQuantity),
                    sequence = sequence,
                    version = (before?.state?.version ?: 0) + 1,
                )
            val after = ProjectionRecord(nextState, before?.appliedEventIds.orEmpty() + eventId)
            if (slot.compareAndSet(before, after)) {
                return InventoryUpdateResponse(UpdateStatus.APPLIED, nextState)
            }
            // Lost the race against a concurrent update; retry classification against
            // the winning value.
        }
    }

    private fun classify(
        before: ProjectionRecord?,
        eventId: UUID,
        sequence: Long,
        expectedVersion: Long,
    ): UpdateStatus {
        if (before == null) {
            return if (expectedVersion == 0L) UpdateStatus.APPLIED else UpdateStatus.CONFLICT
        }
        if (eventId in before.appliedEventIds) {
            return UpdateStatus.DUPLICATE
        }
        if (expectedVersion != before.state.version) {
            return UpdateStatus.CONFLICT
        }
        if (sequence <= before.state.sequence) {
            return UpdateStatus.STALE
        }
        return UpdateStatus.APPLIED
    }
}
