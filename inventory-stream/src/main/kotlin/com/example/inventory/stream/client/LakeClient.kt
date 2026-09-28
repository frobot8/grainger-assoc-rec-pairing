package com.example.inventory.stream.client

import com.example.inventory.contract.InventoryDomainEvent
import com.example.inventory.contract.LakeWriteRequest
import com.example.inventory.contract.LakeWriteResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

interface LakeClient {
    fun write(event: InventoryDomainEvent): LakeWriteResponse
}

@Component
class HttpLakeClient(
    @Qualifier("inventoryLakeRestClient") private val restClient: RestClient,
) : LakeClient {
    override fun write(event: InventoryDomainEvent): LakeWriteResponse = restClient
        .post()
        .uri("/api/lake/records")
        .body(LakeWriteRequest(event))
        .retrieve()
        .body(LakeWriteResponse::class.java)
        ?: error("lake-writer returned an empty response")
}
