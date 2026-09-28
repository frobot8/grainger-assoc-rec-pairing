package com.example.inventory.integration.support

import com.example.inventory.lake.ObjectStore
import java.util.concurrent.ConcurrentHashMap

/**
 * Deterministic in-memory [ObjectStore] used to exercise [com.example.inventory.lake.LakeWriterService]
 * without a real object storage backend.
 */
class InMemoryObjectStore : ObjectStore {
    val objects: MutableMap<String, ByteArray> = ConcurrentHashMap()
    val contentTypes: MutableMap<String, String> = ConcurrentHashMap()
    var bucketEnsured: Boolean = false
        private set

    override fun putObject(
        key: String,
        content: ByteArray,
        contentType: String,
    ) {
        objects[key] = content
        contentTypes[key] = contentType
    }

    override fun ensureBucketExists() {
        bucketEnsured = true
    }
}
