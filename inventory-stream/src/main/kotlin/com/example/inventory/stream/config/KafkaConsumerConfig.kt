package com.example.inventory.stream.config

import com.example.inventory.contract.InventoryAdjustmentEvent
import com.example.inventory.contract.InventoryDeltaEvent
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.boot.kafka.autoconfigure.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer

@Configuration
class KafkaConsumerConfig(
    private val kafkaProperties: KafkaProperties,
) {
    @Bean("inventoryEventsKafkaListenerContainerFactory")
    fun inventoryEventsKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, InventoryDeltaEvent> = listenerContainerFactory(InventoryDeltaEvent::class.java)

    @Bean("inventoryAdjustmentsKafkaListenerContainerFactory")
    fun inventoryAdjustmentsKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, InventoryAdjustmentEvent> = listenerContainerFactory(InventoryAdjustmentEvent::class.java)

    private fun <T : Any> listenerContainerFactory(eventType: Class<T>): ConcurrentKafkaListenerContainerFactory<String, T> {
        val valueDeserializer = JacksonJsonDeserializer(eventType)
        valueDeserializer.addTrustedPackages("com.example.inventory.contract")
        valueDeserializer.setUseTypeHeaders(false)
        valueDeserializer.setRemoveTypeHeaders(true)

        val consumerFactory =
            DefaultKafkaConsumerFactory(
                kafkaProperties.buildConsumerProperties(),
                StringDeserializer(),
                valueDeserializer,
            )
        return ConcurrentKafkaListenerContainerFactory<String, T>().also {
            it.setConsumerFactory(consumerFactory)
        }
    }
}
