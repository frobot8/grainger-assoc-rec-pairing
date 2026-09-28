package com.example.inventory.stream.handler

import com.example.inventory.contract.InventoryDomainEvent

interface EventHandler<T : InventoryDomainEvent> {
    val eventType: Class<T>

    fun handle(event: T)
}
