plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.ktlint)
    `java-library`
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
    }
}

dependencies {
    api(libs.jackson.annotations)
    api(libs.jackson.databind)
    api(libs.jackson.kotlin)
    implementation(libs.kotlin.reflect)

    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.spring.boot.starter.test)
}

sourceSets {
    create("compatibilityTest") {
        kotlin.srcDir("src/compatibilityTest/kotlin")
        compileClasspath += sourceSets.main.get().output + sourceSets.test.get().compileClasspath
        runtimeClasspath += output + sourceSets.test.get().runtimeClasspath
    }
}

val compatibilityTestImplementation =
    configurations.named("compatibilityTestImplementation").get()

compatibilityTestImplementation.extendsFrom(configurations.testImplementation.get())

tasks.register<Test>("compatibilityTest") {
    description = "Verifies checked-in JSON fixtures remain compatible with the current contract types."
    group = "verification"
    testClassesDirs = sourceSets["compatibilityTest"].output.classesDirs
    classpath = sourceSets["compatibilityTest"].runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
}

tasks.named("check") {
    dependsOn(tasks.named("compatibilityTest"))
}
