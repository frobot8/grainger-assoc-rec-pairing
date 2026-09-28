package com.example.inventory.stream.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("inventory.state")
data class InventoryStateClientProperties(
    val baseUrl: String = "http://localhost:8081",
)

@ConfigurationProperties("inventory.lake")
data class InventoryLakeClientProperties(
    val baseUrl: String = "http://localhost:8082",
)
