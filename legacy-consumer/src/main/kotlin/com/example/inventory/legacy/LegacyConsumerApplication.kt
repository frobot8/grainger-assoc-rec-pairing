package com.example.inventory.legacy

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class LegacyConsumerApplication

fun main(args: Array<String>) {
    runApplication<LegacyConsumerApplication>(*args)
}
