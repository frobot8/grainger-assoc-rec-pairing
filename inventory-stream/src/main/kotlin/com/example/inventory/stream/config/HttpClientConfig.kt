package com.example.inventory.stream.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class HttpClientConfig {
    @Bean("inventoryStateRestClient")
    fun inventoryStateRestClient(
        builder: RestClient.Builder,
        properties: InventoryStateClientProperties,
    ): RestClient = builder.baseUrl(properties.baseUrl).build()

    @Bean("inventoryLakeRestClient")
    fun inventoryLakeRestClient(
        builder: RestClient.Builder,
        properties: InventoryLakeClientProperties,
    ): RestClient = builder.baseUrl(properties.baseUrl).build()
}
