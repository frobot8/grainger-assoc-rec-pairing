package com.example.inventory.integration

import com.example.inventory.contract.InventoryDeltaEvent
import com.example.inventory.contract.InventoryKey
import com.example.inventory.integration.support.Fixtures
import com.example.inventory.integration.support.InMemoryObjectStore
import com.example.inventory.integration.support.InProcessLakeClient
import com.example.inventory.integration.support.InProcessStateClient
import com.example.inventory.integration.support.integrationObjectMapper
import com.example.inventory.lake.LakeWriterService
import com.example.inventory.state.InMemoryInventoryProjectionRepository
import com.example.inventory.state.InventoryProjectionService
import com.example.inventory.stream.handler.EventHandlerRegistry
import com.example.inventory.stream.handler.InventoryDeltaEventHandler
import com.example.inventory.stream.handler.InventoryEventDispatcher
import com.example.inventory.stream.ledger.InMemoryProcessingLedger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Composes the state-projection and lake-write ports the way the canonical event path does:
 * one inbound [InventoryDeltaEvent] must both update the sku/facility projection and persist
 * exactly one raw record in the lake.
 */
@Tag("integration")
class DeltaEventProjectionAndLakeRecordTest {
    private val repository = InMemoryInventoryProjectionRepository()
    private val projectionService = InventoryProjectionService(repository)
    private val objectStore = InMemoryObjectStore()
    private val lakeWriterService = LakeWriterService(objectStore, integrationObjectMapper())
    private val dispatcher =
        InventoryEventDispatcher(
            EventHandlerRegistry(
                listOf(
                    InventoryDeltaEventHandler(
                        InProcessStateClient(projectionService),
                        InProcessLakeClient(lakeWriterService),
                        InMemoryProcessingLedger(),
                    ),
                ),
            ),
        )

    @Test
    fun `delta event updates projection and writes one lake record`() {
        val event =
            InventoryDeltaEvent(
                eventId = Fixtures.EVENT_ID,
                sku = Fixtures.SKU,
                facilityId = Fixtures.FACILITY_ID,
                eventTime = Fixtures.BASE_TIME,
                sequence = 1,
                quantityDelta = 12,
            )

        dispatcher.dispatch(event)

        val projected = projectionService.getState(InventoryKey(event.sku, event.facilityId))
        assertThat(projected?.quantity).isEqualTo(12)
        assertThat(projected?.sequence).isEqualTo(1)
        assertThat(projected?.version).isEqualTo(1)

        val objectKey = "raw-inventory/year=2024/month=03/day=15/${event.eventId}.json"

        assertThat(objectStore.objects).containsKey(objectKey)
        assertThat(objectStore.objects).hasSize(1)
    }
}
