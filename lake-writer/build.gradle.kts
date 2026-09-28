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
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.kotlin)
    implementation(platform(libs.aws.sdk.bom))
    implementation(libs.aws.sdk.s3)
    implementation(libs.aws.sdk.url.connection.client)
    implementation(libs.kotlin.reflect)

    testImplementation(libs.spring.boot.starter.test)
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("lake-writer.jar")
}

jib {
    from {
        image = "eclipse-temurin:26.0.2_10-jre@sha256:2b3c7b20375e9ac3ab6a7bc39357d3dbe2caf48378fe9e5c306a22da3499f170"
    }
    to {
        image = "lake-writer:local"
    }
    container {
        mainClass = "com.example.inventory.lake.LakeWriterApplicationKt"
        ports = listOf("8082")
        creationTime.set("USE_CURRENT_TIMESTAMP")
    }
}
