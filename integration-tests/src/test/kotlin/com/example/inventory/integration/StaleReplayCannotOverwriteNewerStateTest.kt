package com.example.inventory.integration

import com.example.inventory.contract.DeltaUpdateRequest
import com.example.inventory.contract.InventoryKey
import com.example.inventory.contract.UpdateStatus
import com.example.inventory.integration.support.Fixtures
import com.example.inventory.state.InMemoryInventoryProjectionRepository
import com.example.inventory.state.InventoryProjectionService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("replay")
class StaleReplayCannotOverwriteNewerStateTest {
    private val repository = InMemoryInventoryProjectionRepository()
    private val projectionService = InventoryProjectionService(repository)
    private val key = InventoryKey(Fixtures.SKU, Fixtures.FACILITY_ID)

    @Test
    fun `a stale sequence is rejected and does not disturb newer state`() {
        val first =
            projectionService.applyDelta(
                DeltaUpdateRequest(
                    eventId = Fixtures.EVENT_ID,
                    key = key,
                    quantityDelta = 10,
                    sequence = 5,
                    expectedVersion = 0,
                ),
            )
        assertThat(first.status).isEqualTo(UpdateStatus.APPLIED)

        val newer =
            projectionService.applyDelta(
                DeltaUpdateRequest(
                    eventId = Fixtures.SECOND_EVENT_ID,
                    key = key,
                    quantityDelta = 3,
                    sequence = 10,
                    expectedVersion = 1,
                ),
            )
        assertThat(newer.status).isEqualTo(UpdateStatus.APPLIED)
        assertThat(newer.state?.quantity).isEqualTo(13)

        val staleReplay =
            projectionService.applyDelta(
                DeltaUpdateRequest(
                    eventId = Fixtures.THIRD_EVENT_ID,
                    key = key,
                    quantityDelta = 99,
                    sequence = 3,
                    expectedVersion = 2,
                ),
            )

        assertThat(staleReplay.status).isEqualTo(UpdateStatus.STALE)
        assertThat(projectionService.getState(key)).isEqualTo(newer.state)
    }

    @Test
    fun `an expected version mismatch is rejected as a conflict, not applied`() {
        val first =
            projectionService.applyDelta(
                DeltaUpdateRequest(
                    eventId = Fixtures.EVENT_ID,
                    key = key,
                    quantityDelta = 10,
                    sequence = 5,
                    expectedVersion = 0,
                ),
            )
        assertThat(first.status).isEqualTo(UpdateStatus.APPLIED)

        val conflicting =
            projectionService.applyDelta(
                DeltaUpdateRequest(
                    eventId = Fixtures.SECOND_EVENT_ID,
                    key = key,
                    quantityDelta = 1,
                    sequence = 6,
                    expectedVersion = 0,
                ),
            )

        assertThat(conflicting.status).isEqualTo(UpdateStatus.CONFLICT)
        assertThat(projectionService.getState(key)).isEqualTo(first.state)
    }
}
