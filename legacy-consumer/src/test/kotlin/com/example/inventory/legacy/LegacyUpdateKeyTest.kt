package com.example.inventory.legacy

import com.example.inventory.contract.InventoryKafkaKeys
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Confirms the legacy bridge republishes translated events under the same
 * `facilityId:sku` Kafka key convention shared by the rest of the platform.
 */
class LegacyUpdateKeyTest {
    private val translator = LegacyEventTranslator()

    @Test
    fun `translated event key matches facility-then-sku convention`() {
        val update =
            LegacyInventoryUpdate(
                eventId = UUID.randomUUID(),
                sku = "SKU-1000",
                facilityId = "FAC-01",
                eventTime = Instant.parse("2026-01-15T10:30:00Z"),
                quantityDelta = -5,
                sequence = 42L,
            )

        val event = translator.translate(update)
        val key = InventoryKafkaKeys.forCoordinates(event.facilityId, event.sku)

        assertEquals("FAC-01:SKU-1000", key)
    }

    @Test
    fun `key is stable for the same coordinates`() {
        val first = InventoryKafkaKeys.forCoordinates("FAC-02", "SKU-2")
        val second = InventoryKafkaKeys.forCoordinates("FAC-02", "SKU-2")

        assertEquals(first, second)
    }
}
