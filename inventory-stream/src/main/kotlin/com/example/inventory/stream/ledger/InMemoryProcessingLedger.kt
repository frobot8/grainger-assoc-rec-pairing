package com.example.inventory.stream.ledger

import com.example.inventory.contract.InventoryKey
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Thread-safe ledger for deterministic in-process composition and tests. */
class InMemoryProcessingLedger : ProcessingLedger {
    private val processedEventIds = ConcurrentHashMap.newKeySet<UUID>()
    private val versions = ConcurrentHashMap<InventoryKey, Long>()

    override fun isProcessed(eventId: UUID): Boolean = processedEventIds.contains(eventId)

    override fun latestVersion(key: InventoryKey): Long? = versions[key]

    override fun markProcessed(
        eventId: UUID,
        key: InventoryKey,
        stateVersion: Long,
    ) {
        processedEventIds.add(eventId)
        versions.merge(key, stateVersion, ::maxOf)
    }
}
