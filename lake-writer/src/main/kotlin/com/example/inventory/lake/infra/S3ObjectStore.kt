package com.example.inventory.lake.infra

import com.example.inventory.lake.ObjectStore
import com.example.inventory.lake.config.S3Properties
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception

/**
 * [ObjectStore] backed by an S3-compatible bucket. [putObject] always writes
 * with the same key on retry, so the object store performs an in-place
 * overwrite and preserves the lake writer's idempotent semantics.
 */
class S3ObjectStore(
    private val s3Client: S3Client,
    private val properties: S3Properties,
) : ObjectStore {
    override fun putObject(
        key: String,
        content: ByteArray,
        contentType: String,
    ) {
        val request = PutObjectRequest.builder()
            .bucket(properties.bucket)
            .key(key)
            .contentType(contentType)
            .build()

        s3Client.putObject(request, RequestBody.fromBytes(content))
    }

    override fun ensureBucketExists() {
        val headRequest = HeadBucketRequest.builder().bucket(properties.bucket).build()

        try {
            s3Client.headBucket(headRequest)
        } catch (exception: S3Exception) {
            if (exception.statusCode() != 404) {
                throw exception
            }

            s3Client.createBucket(CreateBucketRequest.builder().bucket(properties.bucket).build())
        }
    }
}
