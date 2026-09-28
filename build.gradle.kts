import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    base
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.jib) apply false
    alias(libs.plugins.ktlint) apply false
}

val kotlinVersion = libs.versions.kotlin.get()
val jacksonVersion = libs.versions.jackson3.get()
val jackson2BomVersion = libs.versions.jackson2.bom.get()

allprojects {
    group = "com.example.inventory"
    version = "1.0.0-SNAPSHOT"
    extra["kotlin.version"] = kotlinVersion
    extra["jackson-2-bom.version"] = jackson2BomVersion
    extra["jackson-bom.version"] = jacksonVersion

    repositories {
        mavenCentral()
    }
}

subprojects {
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(26)
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_26)
                freeCompilerArgs.add("-Xjsr305=strict")
            }
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            // MockK uses bootstrap instrumentation; class-data sharing is unsupported in that mode.
            jvmArgs("-Xshare:off")
        }
    }
}

tasks.named("check") {
    dependsOn(subprojects.map { "${it.path}:check" })
}

tasks.register("jibDockerBuild") {
    group = "containerization"
    description = "Builds local Docker images for every runnable service."
    dependsOn(
        ":inventory-stream:jibDockerBuild",
        ":inventory-state:jibDockerBuild",
        ":lake-writer:jibDockerBuild",
        ":legacy-consumer:jibDockerBuild",
    )
}
