package com.example.inventory.stream.handler

import com.example.inventory.contract.AbsoluteUpdateRequest
import com.example.inventory.contract.DeltaUpdateRequest
import com.example.inventory.contract.InventoryDeltaEvent
import com.example.inventory.contract.InventoryKey
import com.example.inventory.contract.InventoryState
import com.example.inventory.contract.InventoryUpdateResponse
import com.example.inventory.contract.LakeWriteResponse
import com.example.inventory.contract.UpdateStatus
import com.example.inventory.stream.client.LakeClient
import com.example.inventory.stream.client.StateClient
import com.example.inventory.stream.ledger.InMemoryProcessingLedger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class InventoryDeltaEventHandlerTest {
    private val key = InventoryKey(sku = "SKU-1", facilityId = "FAC-1")

    @Test
    fun `applies a delta writes it to the lake and records completion`() {
        val event = event(sequence = 1)
        val stateClient = FakeStateClient(appliedResponse(version = 1, sequence = 1, quantity = 4))
        val lakeClient = RecordingLakeClient()
        val ledger = InMemoryProcessingLedger()

        InventoryDeltaEventHandler(stateClient, lakeClient, ledger).handle(event)

        assertThat(stateClient.deltaRequests.single().expectedVersion).isZero()
        assertThat(lakeClient.events).containsExactly(event)
        assertThat(ledger.isProcessed(event.eventId)).isTrue()
        assertThat(ledger.latestVersion(key)).isEqualTo(1)
    }

    @Test
    fun `processed event is ignored before calling downstream services`() {
        val event = event(sequence = 1)
        val stateClient = FakeStateClient(appliedResponse(version = 1, sequence = 1, quantity = 4))
        val lakeClient = RecordingLakeClient()
        val ledger = InMemoryProcessingLedger().also { it.markProcessed(event.eventId, key, 1) }

        InventoryDeltaEventHandler(stateClient, lakeClient, ledger).handle(event)

        assertThat(stateClient.deltaRequests).isEmpty()
        assertThat(lakeClient.events).isEmpty()
    }

    @Test
    fun `conflict refreshes current state and retries once`() {
        val event = event(sequence = 8)
        val conflict = InventoryUpdateResponse(UpdateStatus.CONFLICT, state(version = 6, sequence = 6, quantity = 10))
        val applied = appliedResponse(version = 8, sequence = 8, quantity = 14)
        val stateClient = FakeStateClient(conflict, applied).also { it.currentState = state(version = 7, sequence = 7, quantity = 10) }

        InventoryDeltaEventHandler(stateClient, RecordingLakeClient(), InMemoryProcessingLedger()).handle(event)

        assertThat(stateClient.deltaRequests.map(DeltaUpdateRequest::expectedVersion)).containsExactly(0, 7)
        assertThat(stateClient.getRequests).containsExactly(key)
    }

    @Test
    fun `next event uses version returned by previous update`() {
        val first = event(sequence = 1)
        val second = event(sequence = 2)
        val stateClient =
            FakeStateClient(
                appliedResponse(version = 1, sequence = 1, quantity = 4),
                appliedResponse(version = 2, sequence = 2, quantity = 8),
            )
        val handler = InventoryDeltaEventHandler(stateClient, RecordingLakeClient(), InMemoryProcessingLedger())

        handler.handle(first)
        handler.handle(second)

        assertThat(stateClient.deltaRequests.map(DeltaUpdateRequest::expectedVersion)).containsExactly(0, 1)
    }

    private fun event(sequence: Long) = InventoryDeltaEvent(
        eventId = UUID.randomUUID(),
        sku = key.sku,
        facilityId = key.facilityId,
        eventTime = Instant.parse("2024-03-15T10:30:00Z"),
        sequence = sequence,
        quantityDelta = 4,
    )

    private fun appliedResponse(
        version: Long,
        sequence: Long,
        quantity: Int,
    ) = InventoryUpdateResponse(UpdateStatus.APPLIED, state(version, sequence, quantity))

    private fun state(
        version: Long,
        sequence: Long,
        quantity: Int,
    ) = InventoryState(key.sku, key.facilityId, quantity, sequence, version)

    private class FakeStateClient(
        vararg responses: InventoryUpdateResponse,
    ) : StateClient {
        private val responses = ArrayDeque(responses.toList())
        val deltaRequests = mutableListOf<DeltaUpdateRequest>()
        val getRequests = mutableListOf<InventoryKey>()
        var currentState: InventoryState? = null

        override fun getState(key: InventoryKey): InventoryState? {
            getRequests += key
            return currentState
        }

        override fun applyDelta(request: DeltaUpdateRequest): InventoryUpdateResponse {
            deltaRequests += request
            return responses.removeFirst()
        }

        override fun applyAbsolute(request: AbsoluteUpdateRequest): InventoryUpdateResponse = error("not used")
    }

    private class RecordingLakeClient : LakeClient {
        val events = mutableListOf<InventoryDeltaEvent>()

        override fun write(event: com.example.inventory.contract.InventoryDomainEvent): LakeWriteResponse {
            events += event as InventoryDeltaEvent
            return LakeWriteResponse("raw-inventory/${event.eventId}.json")
        }
    }
}
