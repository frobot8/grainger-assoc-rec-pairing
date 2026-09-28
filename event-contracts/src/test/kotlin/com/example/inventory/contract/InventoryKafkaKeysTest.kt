package com.example.inventory.contract

import kotlin.test.Test
import kotlin.test.assertEquals

class InventoryKafkaKeysTest {
    @Test
    fun `builds facility-then-sku key from coordinates`() {
        assertEquals("FAC-01:SKU-1000", InventoryKafkaKeys.forCoordinates("FAC-01", "SKU-1000"))
    }

    @Test
    fun `builds facility-then-sku key from an InventoryKey`() {
        val key = InventoryKey(sku = "SKU-1000", facilityId = "FAC-01")

        assertEquals("FAC-01:SKU-1000", InventoryKafkaKeys.forKey(key))
    }

    @Test
    fun `is stable for the same coordinates`() {
        val first = InventoryKafkaKeys.forCoordinates("FAC-02", "SKU-2")
        val second = InventoryKafkaKeys.forCoordinates("FAC-02", "SKU-2")

        assertEquals(first, second)
    }
}
