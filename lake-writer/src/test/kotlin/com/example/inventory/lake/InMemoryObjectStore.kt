package com.example.inventory.lake

/**
 * In-memory [ObjectStore] for tests: keeps only the latest bytes for a given
 * key so idempotent-overwrite behavior is directly observable via [objects].
 */
class InMemoryObjectStore : ObjectStore {
    val objects: MutableMap<String, ByteArray> = mutableMapOf()
    var bucketEnsured: Boolean = false
        private set

    override fun putObject(
        key: String,
        content: ByteArray,
        contentType: String,
    ) {
        objects[key] = content
    }

    override fun ensureBucketExists() {
        bucketEnsured = true
    }
}
