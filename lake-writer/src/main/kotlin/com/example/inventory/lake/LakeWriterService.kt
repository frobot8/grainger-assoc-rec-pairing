package com.example.inventory.lake

import com.example.inventory.contract.InventoryDomainEvent
import com.example.inventory.contract.LakeWriteRequest
import com.example.inventory.contract.LakeWriteResponse
import tools.jackson.databind.ObjectMapper

/**
 * Writes raw inventory domain events to the lake at deterministic object keys.
 * Writing the same [event id][InventoryDomainEvent.eventId] again resolves to
 * the same object key and simply overwrites it, so retries and redeliveries are
 * safe.
 */
class LakeWriterService(
    private val objectStore: ObjectStore,
    private val objectMapper: ObjectMapper,
) {
    fun write(request: LakeWriteRequest): LakeWriteResponse {
        val event = request.event
        val objectKey = PartitionKeyResolver.resolve(event)
        val payload = objectMapper.writeValueAsBytes(event)
        objectStore.putObject(objectKey, payload)
        return LakeWriteResponse(objectKey = objectKey)
    }
}
