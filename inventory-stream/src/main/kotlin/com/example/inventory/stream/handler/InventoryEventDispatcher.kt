package com.example.inventory.stream.handler

import com.example.inventory.contract.InventoryDomainEvent
import org.springframework.stereotype.Component

@Component
class InventoryEventDispatcher(
    private val registry: EventHandlerRegistry,
) {
    fun dispatch(event: InventoryDomainEvent) {
        registry.handlerFor(event.javaClass).handle(event)
    }
}
