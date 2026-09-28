package com.example.inventory.legacy

import com.example.inventory.contract.InventoryDeltaEvent
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.kafka.autoconfigure.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer
import org.springframework.kafka.support.serializer.JacksonJsonSerializer
import org.springframework.kafka.transaction.KafkaTransactionManager

/**
 * Wires the legacy bridge's Kafka consumer/producer beyond what Spring Boot's
 * `spring.kafka.*` autoconfiguration covers: JSON (de)serialization without
 * relying on `__TypeId__` headers, `read_committed` consumer isolation, and a
 * transactional producer so the consume-translate-publish flow commits atomically.
 */
@Configuration
class KafkaConfig(
    private val kafkaProperties: KafkaProperties,
) {
    @Bean
    fun consumerFactory(): DefaultKafkaConsumerFactory<String, LegacyInventoryUpdate> {
        val properties = kafkaProperties.buildConsumerProperties()
        properties[ConsumerConfig.ISOLATION_LEVEL_CONFIG] = "read_committed"

        val valueDeserializer = JacksonJsonDeserializer(LegacyInventoryUpdate::class.java)
        valueDeserializer.setRemoveTypeHeaders(true)
        valueDeserializer.addTrustedPackages("com.example.inventory.legacy")
        valueDeserializer.setUseTypeHeaders(false)

        return DefaultKafkaConsumerFactory(
            properties,
            StringDeserializer(),
            valueDeserializer,
        )
    }

    @Bean
    fun producerFactory(): ProducerFactory<String, InventoryDeltaEvent> {
        val properties = kafkaProperties.buildProducerProperties()
        properties[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        properties[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JacksonJsonSerializer::class.java

        val factory = DefaultKafkaProducerFactory<String, InventoryDeltaEvent>(properties)
        factory.setTransactionIdPrefix(kafkaProperties.producer.transactionIdPrefix ?: "legacy-consumer-tx-")
        return factory
    }

    @Bean
    fun kafkaTemplate(producerFactory: ProducerFactory<String, InventoryDeltaEvent>): KafkaTemplate<String, InventoryDeltaEvent> = KafkaTemplate(producerFactory)

    @Bean
    fun kafkaTransactionManager(
        producerFactory: ProducerFactory<String, InventoryDeltaEvent>,
    ): KafkaTransactionManager<String, InventoryDeltaEvent> = KafkaTransactionManager(producerFactory)

    @Bean
    fun kafkaListenerContainerFactory(
        consumerFactory: DefaultKafkaConsumerFactory<String, LegacyInventoryUpdate>,
        kafkaTransactionManager: KafkaTransactionManager<String, InventoryDeltaEvent>,
    ): ConcurrentKafkaListenerContainerFactory<String, LegacyInventoryUpdate> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, LegacyInventoryUpdate>()
        factory.setConsumerFactory(consumerFactory)
        factory.containerProperties.setKafkaAwareTransactionManager(kafkaTransactionManager)
        return factory
    }
}
