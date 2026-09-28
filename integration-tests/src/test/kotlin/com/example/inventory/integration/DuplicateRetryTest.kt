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

@Tag("replay")
class DuplicateRetryTest {
    private val projectionService =
        InventoryProjectionService(InMemoryInventoryProjectionRepository())
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
    fun `redelivering the same event applies state once and writes one lake object`() {
        val event =
            InventoryDeltaEvent(
                eventId = Fixtures.EVENT_ID,
                sku = Fixtures.SKU,
                facilityId = Fixtures.FACILITY_ID,
                eventTime = Fixtures.BASE_TIME,
                sequence = 1,
                quantityDelta = 5,
            )
        val key = InventoryKey(event.sku, event.facilityId)
        dispatcher.dispatch(event)
        dispatcher.dispatch(event)

        val state = projectionService.getState(key)
        assertThat(state?.quantity).isEqualTo(5)
        assertThat(state?.version).isEqualTo(1)

        assertThat(objectStore.objects).hasSize(1)
    }
}
