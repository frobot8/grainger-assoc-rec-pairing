package com.example.inventory.contract

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies that the checked-in `inventory-delta-event-v1.json` fixture stays deserializable
 * and reserializable across changes to [InventoryDeltaEvent]. A break here means a wire
 * format regression for every already-published `inventory.delta.v1` event.
 */
class InventoryDeltaEventCompatibilityTest {
    private val objectMapper: ObjectMapper =
        jacksonMapperBuilder().build()

    private val fixedEventId = UUID.fromString("5b1f4a2e-6c8a-4f8b-9a3e-1d2c3b4a5f60")
    private val fixedEventTime = Instant.parse("2024-01-15T08:30:00Z")

    @Test
    fun `fixture deserializes into the current InventoryDeltaEvent shape`() {
        val fixture = readFixture()

        val event = objectMapper.readValue(fixture, InventoryDomainEvent::class.java)

        require(event is InventoryDeltaEvent)
        assertEquals(fixedEventId, event.eventId)
        assertEquals("SKU-1000", event.sku)
        assertEquals("FAC-01", event.facilityId)
        assertEquals(fixedEventTime, event.eventTime)
        assertEquals(42L, event.sequence)
        assertEquals(-5, event.quantityDelta)
    }

    @Test
    fun `current InventoryDeltaEvent reserializes to the stable type and fields`() {
        val event =
            InventoryDeltaEvent(
                eventId = fixedEventId,
                sku = "SKU-1000",
                facilityId = "FAC-01",
                eventTime = fixedEventTime,
                sequence = 42,
                quantityDelta = -5,
            )

        val node = objectMapper.valueToTree<ObjectNode>(event)

        assertEquals("inventory.delta.v1", node.get("type").asString())
        assertEquals(fixedEventId.toString(), node.get("eventId").asString())
        assertEquals("SKU-1000", node.get("sku").asString())
        assertEquals("FAC-01", node.get("facilityId").asString())
        assertEquals(42L, node.get("sequence").asLong())
        assertEquals(-5, node.get("quantityDelta").asInt())

        // round-trip through the polymorphic base type
        val roundTripped =
            objectMapper.treeToValue(node, InventoryDomainEvent::class.java)
        assertEquals(event, roundTripped)
    }

    @Test
    fun `fixture round-trips with stable JSON fields`() {
        val fixture = readFixture()
        val event = objectMapper.readValue(fixture, InventoryDomainEvent::class.java)

        val reserialized = objectMapper.valueToTree<ObjectNode>(event)
        val original = objectMapper.readTree(fixture)

        assertEquals(original.toString(), reserialized.toString())
    }

    private fun readFixture(): String = checkNotNull(
        javaClass.getResourceAsStream("/fixtures/inventory-delta-event-v1.json"),
    ) { "missing fixture: fixtures/inventory-delta-event-v1.json" }
        .bufferedReader()
        .readText()
}
