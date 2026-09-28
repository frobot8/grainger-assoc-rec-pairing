package com.example.inventory.stream.kafka

import com.example.inventory.contract.InventoryDeltaEvent
import com.example.inventory.stream.handler.InventoryEventDispatcher
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class InventoryEventsListener(
    private val dispatcher: InventoryEventDispatcher,
) {
    @KafkaListener(
        topics = ["\${inventory.stream.topics.events:inventory-events}"],
        groupId = "inventory-stream",
        containerFactory = "inventoryEventsKafkaListenerContainerFactory",
    )
    fun onInventoryEvent(event: InventoryDeltaEvent) {
        dispatcher.dispatch(event)
    }
}
