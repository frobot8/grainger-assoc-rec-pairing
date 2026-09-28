package com.example.inventory.stream.kafka

import com.example.inventory.contract.AbsoluteUpdateRequest
import com.example.inventory.contract.InventoryAdjustmentEvent
import com.example.inventory.contract.InventoryKey
import com.example.inventory.contract.InventoryUpdateResponse
import com.example.inventory.contract.UpdateStatus
import com.example.inventory.stream.client.StateClient
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class InventoryAdjustmentListener(
    private val stateClient: StateClient,
) {
    @KafkaListener(
        topics = ["\${inventory.stream.topics.adjustments:inventory-adjustments}"],
        groupId = "inventory-stream",
        containerFactory = "inventoryAdjustmentsKafkaListenerContainerFactory",
    )
    fun onAdjustment(event: InventoryAdjustmentEvent) {
        val key = InventoryKey(sku = event.sku, facilityId = event.facilityId)
        val currentVersion = stateClient.getState(key)?.version ?: 0L
        val first = stateClient.applyAbsolute(event.toRequest(key, currentVersion))
        if (first.status == UpdateStatus.CONFLICT) {
            retryAfterConflict(event, key)
        }
    }

    private fun retryAfterConflict(
        event: InventoryAdjustmentEvent,
        key: InventoryKey,
    ): InventoryUpdateResponse {
        val refreshedVersion = stateClient.getState(key)?.version ?: 0L
        return stateClient.applyAbsolute(event.toRequest(key, refreshedVersion)).also { response ->
            check(response.status != UpdateStatus.CONFLICT) {
                "inventory-state version changed again while applying ${event.eventId}"
            }
        }
    }

    private fun InventoryAdjustmentEvent.toRequest(
        key: InventoryKey,
        expectedVersion: Long,
    ): AbsoluteUpdateRequest = AbsoluteUpdateRequest(
        eventId = eventId,
        key = key,
        quantity = quantity,
        sequence = sequence,
        expectedVersion = expectedVersion,
    )
}
