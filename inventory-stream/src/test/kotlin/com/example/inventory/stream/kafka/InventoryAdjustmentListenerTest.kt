package com.example.inventory.stream.kafka

import com.example.inventory.contract.AbsoluteUpdateRequest
import com.example.inventory.contract.DeltaUpdateRequest
import com.example.inventory.contract.InventoryAdjustmentEvent
import com.example.inventory.contract.InventoryKey
import com.example.inventory.contract.InventoryState
import com.example.inventory.contract.InventoryUpdateResponse
import com.example.inventory.contract.UpdateStatus
import com.example.inventory.stream.client.StateClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class InventoryAdjustmentListenerTest {
    @Test
    fun `applies absolute quantity using the current state version`() {
        val event =
            InventoryAdjustmentEvent(
                eventId = UUID.randomUUID(),
                sku = "SKU-1",
                facilityId = "FAC-1",
                eventTime = Instant.parse("2024-03-15T10:30:00Z"),
                sequence = 3,
                quantity = 25,
            )
        val client = RecordingStateClient()

        InventoryAdjustmentListener(client).onAdjustment(event)

        assertThat(client.requests.single())
            .isEqualTo(
                AbsoluteUpdateRequest(
                    eventId = event.eventId,
                    key = InventoryKey(event.sku, event.facilityId),
                    quantity = 25,
                    sequence = 3,
                    expectedVersion = 2,
                ),
            )
    }

    private class RecordingStateClient : StateClient {
        val requests = mutableListOf<AbsoluteUpdateRequest>()
        private val state = InventoryState("SKU-1", "FAC-1", quantity = 20, sequence = 2, version = 2)

        override fun getState(key: InventoryKey): InventoryState = state

        override fun applyDelta(request: DeltaUpdateRequest): InventoryUpdateResponse = error("not used")

        override fun applyAbsolute(request: AbsoluteUpdateRequest): InventoryUpdateResponse {
            requests += request
            return InventoryUpdateResponse(UpdateStatus.APPLIED, state.copy(quantity = request.quantity, version = 3))
        }
    }
}
