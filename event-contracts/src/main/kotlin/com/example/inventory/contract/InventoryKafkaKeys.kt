package com.example.inventory.contract

/** Stable Kafka partitioning key helpers shared by every producer/consumer in the platform. */
object InventoryKafkaKeys {
    /** Builds the canonical Kafka record key `facilityId:sku` for a given [key]. */
    fun forKey(key: InventoryKey): String = forCoordinates(key.facilityId, key.sku)

    /** Builds the canonical Kafka record key `facilityId:sku` for raw coordinates. */
    fun forCoordinates(
        facilityId: String,
        sku: String,
    ): String = "$facilityId:$sku"
}
