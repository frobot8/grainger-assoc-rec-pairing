package com.example.inventory.state

import com.example.inventory.contract.AbsoluteUpdateRequest
import com.example.inventory.contract.DeltaUpdateRequest
import com.example.inventory.contract.InventoryKey
import com.example.inventory.contract.InventoryState
import com.example.inventory.contract.InventoryUpdateResponse

/**
 * Persistence port for inventory projections.
 *
 * [find] and [save] are general-purpose read/write operations. [updateDeltaIfVersion]
 * and [updateAbsoluteIfVersion] apply an atomic conditional update keyed by
 * (sku, facilityId), guarded by expected version, newer sequence, and event-id
 * deduplication.
 */
interface InventoryProjectionRepository {
    /** Returns the current projection for [key], or null if no state exists yet. */
    fun find(key: InventoryKey): InventoryState?

    /** Unconditionally overwrites the projection for the state's key. */
    fun save(state: InventoryState): InventoryState

    /**
     * Atomically applies a relative quantity change if the request's eventId has not
     * already been applied, the sequence is newer than the stored sequence, and the
     * expected version matches the stored version.
     */
    fun updateDeltaIfVersion(request: DeltaUpdateRequest): InventoryUpdateResponse

    /**
     * Atomically applies an absolute quantity if the request's eventId has not already
     * been applied, the sequence is newer than the stored sequence, and the expected
     * version matches the stored version.
     */
    fun updateAbsoluteIfVersion(request: AbsoluteUpdateRequest): InventoryUpdateResponse
}
