package com.example.inventory.legacy

import com.example.inventory.contract.InventoryDeltaEvent
import com.example.inventory.contract.InventoryKafkaKeys
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Bridges the legacy `legacy-inventory-updates` topic onto the canonical
 * `inventory-events` topic. Each incoming record is translated and republished
 * inside a single Kafka transaction, so consumer offset commit and producer
 * send either both succeed or both roll back.
 */
@Component
class LegacyUpdateListener(
    private val translator: LegacyEventTranslator,
    private val kafkaTemplate: KafkaTemplate<String, InventoryDeltaEvent>,
) {
    @KafkaListener(
        topics = ["legacy-inventory-updates"],
        groupId = "legacy-consumer",
        containerFactory = "kafkaListenerContainerFactory",
    )
    @Transactional("kafkaTransactionManager")
    fun onLegacyUpdate(update: LegacyInventoryUpdate) {
        val event = translator.translate(update)
        val key = InventoryKafkaKeys.forCoordinates(event.facilityId, event.sku)
        kafkaTemplate.send("inventory-events", key, event)
    }
}
