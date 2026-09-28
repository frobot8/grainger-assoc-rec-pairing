package com.example.inventory.stream.client

import com.example.inventory.contract.AbsoluteUpdateRequest
import com.example.inventory.contract.DeltaUpdateRequest
import com.example.inventory.contract.InventoryKey
import com.example.inventory.contract.InventoryState
import com.example.inventory.contract.InventoryUpdateResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

interface StateClient {
    fun getState(key: InventoryKey): InventoryState?

    fun applyDelta(request: DeltaUpdateRequest): InventoryUpdateResponse

    fun applyAbsolute(request: AbsoluteUpdateRequest): InventoryUpdateResponse
}

@Component
class HttpStateClient(
    @Qualifier("inventoryStateRestClient") private val restClient: RestClient,
) : StateClient {
    override fun getState(key: InventoryKey): InventoryState? = try {
        restClient
            .get()
            .uri("/api/inventory/{facilityId}/{sku}", key.facilityId, key.sku)
            .retrieve()
            .body<InventoryState>()
    } catch (exception: HttpClientErrorException) {
        if (exception.statusCode == HttpStatus.NOT_FOUND) null else throw exception
    }

    override fun applyDelta(request: DeltaUpdateRequest): InventoryUpdateResponse = restClient
        .post()
        .uri("/api/inventory/delta")
        .body(request)
        .retrieve()
        .body<InventoryUpdateResponse>()
        ?: error("inventory-state returned an empty delta response")

    override fun applyAbsolute(request: AbsoluteUpdateRequest): InventoryUpdateResponse = restClient
        .post()
        .uri("/api/inventory/absolute")
        .body(request)
        .retrieve()
        .body<InventoryUpdateResponse>()
        ?: error("inventory-state returned an empty absolute response")
}
