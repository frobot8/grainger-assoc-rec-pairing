package com.example.inventory.stream.handler

import com.example.inventory.contract.InventoryDeltaEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class InventoryEventDispatcherTest {
    @Test
    fun `routes an event to its registered handler`() {
        val event =
            InventoryDeltaEvent(
                eventId = UUID.randomUUID(),
                sku = "SKU-1",
                facilityId = "FAC-1",
                eventTime = Instant.parse("2024-03-15T10:30:00Z"),
                sequence = 1,
                quantityDelta = 3,
            )
        val handler = RecordingHandler()
        val dispatcher = InventoryEventDispatcher(EventHandlerRegistry(listOf(handler)))

        dispatcher.dispatch(event)

        assertThat(handler.events).containsExactly(event)
    }

    private class RecordingHandler : EventHandler<InventoryDeltaEvent> {
        override val eventType = InventoryDeltaEvent::class.java
        val events = mutableListOf<InventoryDeltaEvent>()

        override fun handle(event: InventoryDeltaEvent) {
            events += event
        }
    }
}
