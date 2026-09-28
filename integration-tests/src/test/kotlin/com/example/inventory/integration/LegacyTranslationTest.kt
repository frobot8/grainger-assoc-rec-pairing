package com.example.inventory.integration

import com.example.inventory.contract.InventoryDeltaEvent
import com.example.inventory.contract.InventoryKafkaKeys
import com.example.inventory.contract.LakeWriteRequest
import com.example.inventory.integration.support.Fixtures
import com.example.inventory.integration.support.InMemoryObjectStore
import com.example.inventory.integration.support.integrationObjectMapper
import com.example.inventory.lake.LakeWriterService
import com.example.inventory.legacy.LegacyEventTranslator
import com.example.inventory.legacy.LegacyInventoryUpdate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * A legacy-formatted update must translate into the canonical [InventoryDeltaEvent]
 * without losing or renaming any wire field, and the translated event must be usable by every
 * downstream port (here: the lake writer) exactly like a canonical event.
 */
@Tag("integration")
class LegacyTranslationTest {
    private val translator = LegacyEventTranslator()

    @Test
    fun `legacy translation preserves wire fields and keying`() {
        val legacyUpdate =
            LegacyInventoryUpdate(
                eventId = Fixtures.EVENT_ID,
                sku = Fixtures.SKU,
                facilityId = Fixtures.FACILITY_ID,
                eventTime = Fixtures.BASE_TIME,
                quantityDelta = -4,
                sequence = 7,
            )

        val translated = translator.translate(legacyUpdate)

        assertThat(translated.eventId).isEqualTo(legacyUpdate.eventId)
        assertThat(translated.sku).isEqualTo(legacyUpdate.sku)
        assertThat(translated.facilityId).isEqualTo(legacyUpdate.facilityId)
        assertThat(translated.eventTime).isEqualTo(legacyUpdate.eventTime)
        assertThat(translated.sequence).isEqualTo(legacyUpdate.sequence)
        assertThat(translated.quantityDelta).isEqualTo(legacyUpdate.quantityDelta)

        val objectMapper = integrationObjectMapper()
        val serialized = objectMapper.readTree(objectMapper.writeValueAsBytes(translated))
        assertThat(serialized.get("type").asString()).isEqualTo("inventory.delta.v1")

        val kafkaKey = InventoryKafkaKeys.forCoordinates(translated.facilityId, translated.sku)
        assertThat(kafkaKey).isEqualTo("${legacyUpdate.facilityId}:${legacyUpdate.sku}")

        val objectStore = InMemoryObjectStore()
        val lakeResponse =
            LakeWriterService(objectStore, objectMapper).write(LakeWriteRequest(translated))

        assertThat(objectStore.objects).hasSize(1)
        assertThat(lakeResponse.objectKey).contains(translated.eventId.toString())
    }
}
