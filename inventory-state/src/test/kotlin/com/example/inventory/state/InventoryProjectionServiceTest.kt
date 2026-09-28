package com.example.inventory.state

import com.example.inventory.contract.AbsoluteUpdateRequest
import com.example.inventory.contract.DeltaUpdateRequest
import com.example.inventory.contract.InventoryKey
import com.example.inventory.contract.UpdateStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class InventoryProjectionServiceTest {
    private val repository = InMemoryInventoryProjectionRepository()
    private val service = InventoryProjectionService(repository)
    private val key = InventoryKey(sku = "SKU-1", facilityId = "FAC-1")

    @Test
    fun `getState reflects an unknown key as null`() {
        assertNull(service.getState(key))
    }

    @Test
    fun `applyDelta then getState reflects the applied change`() {
        service.applyDelta(
            DeltaUpdateRequest(UUID.randomUUID(), key, quantityDelta = 4, sequence = 1, expectedVersion = 0),
        )

        val state = service.getState(key)
        assertEquals(4, state?.quantity)
        assertEquals(1L, state?.version)
    }

    @Test
    fun `applyAbsolute sets the exact quantity`() {
        service.applyDelta(
            DeltaUpdateRequest(UUID.randomUUID(), key, quantityDelta = 4, sequence = 1, expectedVersion = 0),
        )

        val response =
            service.applyAbsolute(
                AbsoluteUpdateRequest(UUID.randomUUID(), key, quantity = 99, sequence = 2, expectedVersion = 1),
            )

        assertEquals(UpdateStatus.APPLIED, response.status)
        assertEquals(99, service.getState(key)?.quantity)
    }
}
