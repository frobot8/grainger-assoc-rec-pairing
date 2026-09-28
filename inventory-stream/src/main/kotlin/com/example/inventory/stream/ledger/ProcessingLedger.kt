package com.example.inventory.stream.ledger

import com.example.inventory.contract.InventoryKey
import java.util.UUID

/** Records canonical events only after their state and lake effects have completed. */
@Deprecated(
    message = "Being replaced by transactional event processing",
)
interface ProcessingLedger {
    fun isProcessed(eventId: UUID): Boolean

    fun latestVersion(key: InventoryKey): Long?

    fun markProcessed(
        eventId: UUID,
        key: InventoryKey,
        stateVersion: Long,
    )
}
