package com.example.inventory.lake

import com.example.inventory.contract.InventoryDomainEvent
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Resolves the deterministic raw-lake object key from an event's own UTC timestamp. */
internal object PartitionKeyResolver {
    private val datePartition: DateTimeFormatter =
        DateTimeFormatter.ofPattern("'year='yyyy'/month='MM'/day='dd").withZone(ZoneOffset.UTC)

    fun resolve(event: InventoryDomainEvent): String {
        val partition = datePartition.format(event.eventTime)
        return "raw-inventory/$partition/${event.eventId}.json"
    }
}
