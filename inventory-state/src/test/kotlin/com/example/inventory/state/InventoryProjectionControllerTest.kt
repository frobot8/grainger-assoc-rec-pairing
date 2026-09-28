package com.example.inventory.state

import com.example.inventory.contract.AbsoluteUpdateRequest
import com.example.inventory.contract.DeltaUpdateRequest
import com.example.inventory.contract.InventoryKey
import com.example.inventory.contract.InventoryState
import com.example.inventory.contract.InventoryUpdateResponse
import com.example.inventory.contract.UpdateStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID

class InventoryProjectionControllerTest {
    private val service = mockk<InventoryProjectionService>()
    private val controller = InventoryProjectionController(service)
    private val key = InventoryKey(sku = "SKU-1", facilityId = "FAC-1")

    @Test
    fun `getState returns 404 when no state exists`() {
        every { service.getState(key) } returns null

        val response = controller.getState(facilityId = "FAC-1", sku = "SKU-1")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertNull(response.body)
    }

    @Test
    fun `getState returns 200 with the state when present`() {
        val state = InventoryState(sku = "SKU-1", facilityId = "FAC-1", quantity = 5, sequence = 1, version = 1)
        every { service.getState(key) } returns state

        val response = controller.getState(facilityId = "FAC-1", sku = "SKU-1")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(state, response.body)
    }

    @Test
    fun `applyDelta delegates to the service and returns its response`() {
        val request = DeltaUpdateRequest(UUID.randomUUID(), key, quantityDelta = 3, sequence = 1, expectedVersion = 0)
        val expected =
            InventoryUpdateResponse(
                UpdateStatus.APPLIED,
                InventoryState(sku = "SKU-1", facilityId = "FAC-1", quantity = 3, sequence = 1, version = 1),
            )
        every { service.applyDelta(request) } returns expected

        val actual = controller.applyDelta(request)

        assertEquals(expected, actual)
        verify(exactly = 1) { service.applyDelta(request) }
    }

    @Test
    fun `applyAbsolute delegates to the service and returns its response`() {
        val request = AbsoluteUpdateRequest(UUID.randomUUID(), key, quantity = 10, sequence = 1, expectedVersion = 0)
        val expected =
            InventoryUpdateResponse(
                UpdateStatus.APPLIED,
                InventoryState(sku = "SKU-1", facilityId = "FAC-1", quantity = 10, sequence = 1, version = 1),
            )
        every { service.applyAbsolute(request) } returns expected

        val actual = controller.applyAbsolute(request)

        assertEquals(expected, actual)
        verify(exactly = 1) { service.applyAbsolute(request) }
    }
}
