package com.example.inventory.stream.ledger

import com.example.inventory.contract.InventoryKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class InMemoryProcessingLedgerTest {
    @Test
    fun `records event completion and highest observed version per key`() {
        val ledger = InMemoryProcessingLedger()
        val key = InventoryKey("SKU-1", "FAC-1")
        val firstEventId = UUID.randomUUID()

        ledger.markProcessed(firstEventId, key, 4)
        ledger.markProcessed(UUID.randomUUID(), key, 3)

        assertThat(ledger.isProcessed(firstEventId)).isTrue()
        assertThat(ledger.latestVersion(key)).isEqualTo(4)
    }
}
