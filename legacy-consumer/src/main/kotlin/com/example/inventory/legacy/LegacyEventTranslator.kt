package com.example.inventory.legacy

import com.example.inventory.contract.InventoryDeltaEvent
import org.springframework.stereotype.Component

/**
 * Translates the legacy update wire shape into the canonical [InventoryDeltaEvent]
 * published on `inventory-events`. Pure and side-effect free so it can be unit
 * tested without any Kafka or Spring wiring.
 */
@Component
class LegacyEventTranslator {
    fun translate(event: LegacyInventoryUpdate): InventoryDeltaEvent = InventoryDeltaEvent(
        eventId = event.eventId,
        sku = event.sku,
        facilityId = event.facilityId,
        eventTime = event.eventTime,
        sequence = event.sequence,
        quantityDelta = event.quantityDelta,
    )
}
