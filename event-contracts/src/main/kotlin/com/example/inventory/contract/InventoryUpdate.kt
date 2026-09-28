package com.example.inventory.contract

import java.util.UUID

/** Applies a relative quantity change to the state identified by [key]. */
data class DeltaUpdateRequest(
    val eventId: UUID,
    val key: InventoryKey,
    val quantityDelta: Int,
    val sequence: Long,
    val expectedVersion: Long,
)

/** Applies an absolute quantity to the state identified by [key]. */
data class AbsoluteUpdateRequest(
    val eventId: UUID,
    val key: InventoryKey,
    val quantity: Int,
    val sequence: Long,
    val expectedVersion: Long,
)

/** Outcome of attempting to apply a [DeltaUpdateRequest] or [AbsoluteUpdateRequest]. */
enum class UpdateStatus {
    /** The update was applied and reflected in the returned state. */
    APPLIED,

    /** An update with this [DeltaUpdateRequest.eventId]/[AbsoluteUpdateRequest.eventId] was already applied. */
    DUPLICATE,

    /** The request's sequence number is not newer than the currently stored sequence (less than or equal). */
    STALE,

    /** The request's expectedVersion no longer matches the currently stored version. */
    CONFLICT,
}

/** Result of an inventory update attempt; [state] is present unless the key is unknown. */
data class InventoryUpdateResponse(
    val status: UpdateStatus,
    val state: InventoryState?,
)
