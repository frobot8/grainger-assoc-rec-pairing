package com.example.inventory.integration

import com.example.inventory.contract.InventoryDeltaEvent
import com.example.inventory.contract.LakeWriteRequest
import com.example.inventory.integration.support.Fixtures
import com.example.inventory.integration.support.InMemoryObjectStore
import com.example.inventory.integration.support.integrationObjectMapper
import com.example.inventory.lake.LakeWriterService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * A late-arriving event (processed well after its own [InventoryDeltaEvent.eventTime]) must
 * still land in the lake partition for its original event time, not the processing time.
 */
@Tag("replay")
class LateEventUsesEventTimePartitionTest {
    private val objectStore = InMemoryObjectStore()
    private val lakeWriterService = LakeWriterService(objectStore, integrationObjectMapper())

    @Test
    fun `an event processed long after it occurred still partitions by its own event time`() {
        val originalEventTime = Instant.parse("2023-11-02T04:15:00Z")
        val lateEvent =
            InventoryDeltaEvent(
                eventId = Fixtures.EVENT_ID,
                sku = "SKU-LATE",
                facilityId = "FAC-02",
                eventTime = originalEventTime,
                sequence = 42,
                quantityDelta = -1,
            )

        val response = lakeWriterService.write(LakeWriteRequest(lateEvent))

        assertThat(response.objectKey).isEqualTo(
            "raw-inventory/year=2023/month=11/day=02/${lateEvent.eventId}.json",
        )
        assertThat(objectStore.objects).containsKey(response.objectKey)
    }
}
