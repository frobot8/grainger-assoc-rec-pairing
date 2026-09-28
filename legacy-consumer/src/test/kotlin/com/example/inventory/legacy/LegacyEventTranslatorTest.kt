package com.example.inventory.legacy

import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class LegacyEventTranslatorTest {
    private val translator = LegacyEventTranslator()

    @Test
    fun `translates legacy update into a canonical delta event`() {
        val eventId = UUID.randomUUID()
        val eventTime = Instant.parse("2026-01-15T10:30:00Z")
        val update =
            LegacyInventoryUpdate(
                eventId = eventId,
                sku = "SKU-1000",
                facilityId = "FAC-01",
                eventTime = eventTime,
                quantityDelta = -5,
                sequence = 42L,
            )

        val event = translator.translate(update)

        assertEquals(eventId, event.eventId)
        assertEquals("SKU-1000", event.sku)
        assertEquals("FAC-01", event.facilityId)
        assertEquals(eventTime, event.eventTime)
        assertEquals(42L, event.sequence)
        assertEquals(-5, event.quantityDelta)
    }

    @Test
    fun `is deterministic for the same input`() {
        val update =
            LegacyInventoryUpdate(
                eventId = UUID.randomUUID(),
                sku = "SKU-2",
                facilityId = "FAC-02",
                eventTime = Instant.parse("2026-02-01T00:00:00Z"),
                quantityDelta = 10,
                sequence = 7L,
            )

        assertEquals(translator.translate(update), translator.translate(update))
    }
}
