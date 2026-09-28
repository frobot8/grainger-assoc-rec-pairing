package com.example.inventory.lake

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class LakeWriterApplication

fun main(args: Array<String>) {
    runApplication<LakeWriterApplication>(*args)
}
