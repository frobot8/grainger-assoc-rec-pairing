package com.example.inventory.stream

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class InventoryStreamApplication

fun main(args: Array<String>) {
    runApplication<InventoryStreamApplication>(*args)
}
