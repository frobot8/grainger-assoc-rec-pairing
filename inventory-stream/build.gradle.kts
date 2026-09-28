import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.jib)
    alias(libs.plugins.ktlint)
}

dependencies {
    implementation(project(":event-contracts"))

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.restclient)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.mongodb)
    implementation(libs.spring.boot.starter.kafka)
    implementation(libs.jackson.kotlin)
    implementation(libs.kotlin.reflect)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.kafka.test)
    testImplementation(libs.mockk)
}

jib {
    from {
        image = "eclipse-temurin:26.0.2_10-jre@sha256:2b3c7b20375e9ac3ab6a7bc39357d3dbe2caf48378fe9e5c306a22da3499f170"
    }
    to {
        image = "inventory-stream:local"
    }
    container {
        mainClass = "com.example.inventory.stream.InventoryStreamApplicationKt"
        ports = listOf("8080")
        creationTime.set("USE_CURRENT_TIMESTAMP")
    }
}

tasks.named<BootJar>("bootJar") {
    archiveFileName.set("inventory-stream.jar")
}
