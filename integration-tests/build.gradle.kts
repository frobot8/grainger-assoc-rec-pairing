plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.ktlint)
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
    }
}

dependencies {
    testImplementation(project(":event-contracts"))
    testImplementation(project(":inventory-stream"))
    testImplementation(project(":inventory-state"))
    testImplementation(project(":lake-writer"))
    testImplementation(project(":legacy-consumer"))

    testImplementation(libs.jackson.databind)
    testImplementation(libs.jackson.kotlin)
    testImplementation(libs.kotlin.reflect)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val integrationTest =
    tasks.register<Test>("integrationTest") {
        group = "verification"
        description = "Runs deterministic cross-module integration tests composed in-memory (no Docker)."
        useJUnitPlatform {
            includeTags("integration")
        }
        dependsOn(tasks.named("testClasses"))
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        shouldRunAfter(tasks.named("test"))
    }

val replayTest =
    tasks.register<Test>("replayTest") {
        group = "verification"
        description = "Runs deterministic replay/ordering scenarios (duplicate, stale, late-arriving events)."
        useJUnitPlatform {
            includeTags("replay")
        }
        dependsOn(tasks.named("testClasses"))
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        shouldRunAfter(integrationTest)
    }

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("integration", "replay")
    }
}

tasks.named("check") {
    dependsOn(integrationTest, replayTest)
}
