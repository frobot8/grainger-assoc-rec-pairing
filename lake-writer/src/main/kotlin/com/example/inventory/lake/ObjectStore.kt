package com.example.inventory.lake

/**
 * Storage port for the lake writer. Implementations own their target bucket
 * and MUST make [putObject] an idempotent overwrite of the same key.
 */
interface ObjectStore {
    fun putObject(
        key: String,
        content: ByteArray,
        contentType: String = "application/json",
    )

    fun ensureBucketExists()
}
