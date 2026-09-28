package com.example.inventory.state

import com.example.inventory.contract.AbsoluteUpdateRequest
import com.example.inventory.contract.DeltaUpdateRequest
import com.example.inventory.contract.InventoryKey
import com.example.inventory.contract.InventoryState
import com.example.inventory.contract.InventoryUpdateResponse
import org.springframework.stereotype.Service

/**
 * Orchestrates inventory projection reads and concurrency-safe writes. Classification
 * of the update outcome (applied/duplicate/stale/conflict) is owned by the repository's
 * conditional update methods so the same rules apply regardless of storage backend.
 */
@Service
class InventoryProjectionService(
    private val repository: InventoryProjectionRepository,
) {
    fun getState(key: InventoryKey): InventoryState? = repository.find(key)

    fun applyDelta(request: DeltaUpdateRequest): InventoryUpdateResponse = repository.updateDeltaIfVersion(request)

    fun applyAbsolute(request: AbsoluteUpdateRequest): InventoryUpdateResponse = repository.updateAbsoluteIfVersion(request)
}
