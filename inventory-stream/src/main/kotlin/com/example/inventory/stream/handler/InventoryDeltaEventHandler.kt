package com.example.inventory.stream.handler

import com.example.inventory.contract.DeltaUpdateRequest
import com.example.inventory.contract.InventoryDeltaEvent
import com.example.inventory.contract.InventoryKey
import com.example.inventory.contract.InventoryUpdateResponse
import com.example.inventory.contract.UpdateStatus
import com.example.inventory.stream.client.LakeClient
import com.example.inventory.stream.client.StateClient
import com.example.inventory.stream.ledger.ProcessingLedger
import org.springframework.stereotype.Component

@Component
class InventoryDeltaEventHandler(
    private val stateClient: StateClient,
    private val lakeClient: LakeClient,
    private val ledger: ProcessingLedger,
) : EventHandler<InventoryDeltaEvent> {
    override val eventType: Class<InventoryDeltaEvent> = InventoryDeltaEvent::class.java

    override fun handle(event: InventoryDeltaEvent) {
        if (ledger.isProcessed(event.eventId)) return

        val key = InventoryKey(sku = event.sku, facilityId = event.facilityId)
        val response = applyWithOneConflictRefresh(event, key)
        val state = requireNotNull(response.state) {
            "inventory-state returned ${response.status} without state for $key"
        }

        lakeClient.write(event)
        ledger.markProcessed(event.eventId, key, state.version)
    }

    private fun applyWithOneConflictRefresh(
        event: InventoryDeltaEvent,
        key: InventoryKey,
    ): InventoryUpdateResponse {
        val first = stateClient.applyDelta(event.toRequest(key, ledger.latestVersion(key) ?: 0L))
        if (first.status != UpdateStatus.CONFLICT) return first

        val refreshedVersion = stateClient.getState(key)?.version ?: 0L
        val retry = stateClient.applyDelta(event.toRequest(key, refreshedVersion))
        check(retry.status != UpdateStatus.CONFLICT) {
            "inventory-state version changed again while applying ${event.eventId}"
        }
        return retry
    }

    private fun InventoryDeltaEvent.toRequest(
        key: InventoryKey,
        expectedVersion: Long,
    ): DeltaUpdateRequest = DeltaUpdateRequest(
        eventId = eventId,
        key = key,
        quantityDelta = quantityDelta,
        sequence = sequence,
        expectedVersion = expectedVersion,
    )
}
