package com.example.inventory.contract

/** Identifies a single sku at a single facility. */
data class InventoryKey(
    val sku: String,
    val facilityId: String,
)

/**
 * The current, durably persisted quantity for a [InventoryKey].
 *
 * [sequence] is the highest event sequence number applied so far and is used for
 * duplicate/out-of-order detection; [version] is an independent optimistic-locking
 * counter incremented on every successful write.
 */
data class InventoryState(
    val sku: String,
    val facilityId: String,
    val quantity: Int,
    val sequence: Long,
    val version: Long,
)
