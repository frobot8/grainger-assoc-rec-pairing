package com.example.inventory.contract

/** Request to persist a domain event into the raw event lake. */
data class LakeWriteRequest(
    val event: InventoryDomainEvent,
)

/** Result of a lake write; [objectKey] is the deterministic, idempotent object key used. */
data class LakeWriteResponse(
    val objectKey: String,
)
