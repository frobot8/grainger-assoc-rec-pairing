package com.example.inventory.stream.handler

import com.example.inventory.contract.InventoryDomainEvent
import org.springframework.stereotype.Component

@Component
class EventHandlerRegistry(
    handlers: List<EventHandler<*>>,
) {
    private val handlersByType: Map<Class<out InventoryDomainEvent>, EventHandler<*>> =
        handlers
            .groupBy(EventHandler<*>::eventType)
            .mapValues { (eventType, registered) ->
                require(registered.size == 1) { "Multiple handlers registered for ${eventType.name}" }
                registered.single()
            }

    @Suppress("UNCHECKED_CAST")
    fun <T : InventoryDomainEvent> handlerFor(eventType: Class<T>): EventHandler<T> = handlersByType[eventType] as? EventHandler<T>
        ?: error("No handler registered for ${eventType.name}")
}
