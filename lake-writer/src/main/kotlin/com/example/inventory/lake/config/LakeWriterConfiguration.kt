package com.example.inventory.lake.config

import com.example.inventory.lake.LakeWriterService
import com.example.inventory.lake.ObjectStore
import com.example.inventory.lake.infra.S3ObjectStore
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.net.URI

@Configuration
@EnableConfigurationProperties(S3Properties::class)
class LakeWriterConfiguration {
    @Bean
    fun lakeObjectMapper(): ObjectMapper = jacksonMapperBuilder().build()

    @Bean
    fun s3Client(properties: S3Properties): S3Client = S3Client.builder()
        .endpointOverride(URI.create(properties.endpoint))
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKey, properties.secretKey)),
        )
        .region(Region.of(properties.region))
        .httpClientBuilder(UrlConnectionHttpClient.builder())
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build()

    @Bean
    fun objectStore(
        s3Client: S3Client,
        properties: S3Properties,
    ): ObjectStore = S3ObjectStore(s3Client, properties)

    @Bean
    fun lakeWriterService(
        objectStore: ObjectStore,
        lakeObjectMapper: ObjectMapper,
    ): LakeWriterService = LakeWriterService(objectStore, lakeObjectMapper)

    @Bean
    fun ensureBucketOnStartup(objectStore: ObjectStore): ApplicationRunner = ApplicationRunner { objectStore.ensureBucketExists() }
}
