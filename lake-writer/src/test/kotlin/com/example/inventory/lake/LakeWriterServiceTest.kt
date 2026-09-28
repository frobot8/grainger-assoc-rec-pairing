package com.example.inventory.lake

import com.example.inventory.contract.InventoryDeltaEvent
import com.example.inventory.contract.InventoryDomainEvent
import com.example.inventory.contract.LakeWriteRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.time.Instant
import java.util.UUID

class LakeWriterServiceTest {
    private val objectMapper: ObjectMapper =
        jacksonMapperBuilder().build()

    private fun deltaEvent(
        eventId: UUID = UUID.randomUUID(),
        sku: String = "SKU-1",
        facilityId: String = "FAC-1",
        eventTime: Instant,
        sequence: Long = 1,
    ): InventoryDeltaEvent = InventoryDeltaEvent(
        eventId = eventId,
        sku = sku,
        facilityId = facilityId,
        eventTime = eventTime,
        sequence = sequence,
        quantityDelta = 5,
    )

    @Test
    fun `writes a new event to the UTC date partition derived from its event time`() {
        val store = InMemoryObjectStore()
        val service = LakeWriterService(store, objectMapper)
        val eventId = UUID.randomUUID()
        val eventTime = Instant.parse("2024-03-15T10:30:00Z")

        val response = service.write(LakeWriteRequest(deltaEvent(eventId = eventId, eventTime = eventTime)))

        assertThat(response.objectKey).isEqualTo("raw-inventory/year=2024/month=03/day=15/$eventId.json")
        assertThat(store.objects).containsKey(response.objectKey)
        val storedJson = objectMapper.readTree(store.objects.getValue(response.objectKey))
        assertThat(storedJson["type"].stringValue()).isEqualTo("inventory.delta.v1")
        assertThat(storedJson["quantityDelta"].intValue()).isEqualTo(5)
    }

    @Test
    fun `repeated writes of the same event id overwrite a single object instead of accumulating`() {
        val store = InMemoryObjectStore()
        val service = LakeWriterService(store, objectMapper)
        val eventId = UUID.randomUUID()
        val eventTime = Instant.parse("2024-03-15T10:30:00Z")
        val first = deltaEvent(eventId = eventId, eventTime = eventTime, sequence = 1)
        val second = first.copy(sequence = 2)

        val firstResponse = service.write(LakeWriteRequest(first))
        val secondResponse = service.write(LakeWriteRequest(second))

        assertThat(secondResponse.objectKey).isEqualTo(firstResponse.objectKey)
        assertThat(store.objects).hasSize(1)
        val stored =
            objectMapper.readValue(store.objects.getValue(firstResponse.objectKey), InventoryDomainEvent::class.java)
        assertThat(stored.sequence).isEqualTo(2)
    }

    @Test
    fun `a late event dated just before UTC midnight lands in the prior day partition, not the write-time day`() {
        val store = InMemoryObjectStore()
        val service = LakeWriterService(store, objectMapper)
        val justBeforeMidnightUtc = Instant.parse("2024-02-29T23:59:59Z")

        val response = service.write(LakeWriteRequest(deltaEvent(eventTime = justBeforeMidnightUtc)))

        assertThat(response.objectKey).startsWith("raw-inventory/year=2024/month=02/day=29/")
    }

    @Test
    fun `an event dated just after UTC midnight lands in the next day partition`() {
        val store = InMemoryObjectStore()
        val service = LakeWriterService(store, objectMapper)
        val justAfterMidnightUtc = Instant.parse("2024-03-01T00:00:01Z")

        val response = service.write(LakeWriteRequest(deltaEvent(eventTime = justAfterMidnightUtc)))

        assertThat(response.objectKey).startsWith("raw-inventory/year=2024/month=03/day=01/")
    }

    @Test
    fun `object key embeds the event id so distinct events never collide`() {
        val store = InMemoryObjectStore()
        val service = LakeWriterService(store, objectMapper)
        val eventTime = Instant.parse("2024-03-15T10:30:00Z")

        val first = service.write(LakeWriteRequest(deltaEvent(eventTime = eventTime)))
        val second = service.write(LakeWriteRequest(deltaEvent(eventTime = eventTime)))

        assertThat(first.objectKey).isNotEqualTo(second.objectKey)
        assertThat(store.objects).hasSize(2)
    }
}
