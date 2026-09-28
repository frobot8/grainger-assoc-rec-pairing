package com.example.inventory.legacy

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.Instant
import java.util.UUID

/**
 * Wire shape produced by the legacy upstream system on the `legacy-inventory-updates`
 * topic. Unknown properties are tolerated so the legacy producer can evolve without
 * breaking this consumer.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class LegacyInventoryUpdate(
    val eventId: UUID,
    val sku: String,
    val facilityId: String,
    val eventTime: Instant,
    val quantityDelta: Int,
    val sequence: Long,
)
