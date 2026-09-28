package com.example.inventory.lake.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * S3 connection settings for the lake writer, sourced from environment
 * variables so the same image runs unmodified across environments:
 * LAKE_S3_ENDPOINT, LAKE_S3_ACCESS_KEY, LAKE_S3_SECRET_KEY,
 * LAKE_S3_REGION, LAKE_S3_BUCKET.
 */
@ConfigurationProperties(prefix = "lake.s3")
data class S3Properties(
    val endpoint: String = "http://localhost:9000",
    val accessKey: String = "seaweedfsadmin",
    val secretKey: String = "seaweedfsadmin",
    val region: String = "us-east-1",
    val bucket: String = "inventory-lake",
)
