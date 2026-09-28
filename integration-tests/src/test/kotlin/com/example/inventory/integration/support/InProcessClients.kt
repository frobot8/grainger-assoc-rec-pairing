package com.example.inventory.integration.support

import com.example.inventory.contract.AbsoluteUpdateRequest
import com.example.inventory.contract.DeltaUpdateRequest
import com.example.inventory.contract.InventoryDomainEvent
import com.example.inventory.contract.InventoryKey
import com.example.inventory.contract.InventoryState
import com.example.inventory.contract.InventoryUpdateResponse
import com.example.inventory.contract.LakeWriteRequest
import com.example.inventory.contract.LakeWriteResponse
import com.example.inventory.lake.LakeWriterService
import com.example.inventory.state.InventoryProjectionService
import com.example.inventory.stream.client.LakeClient
import com.example.inventory.stream.client.StateClient

class InProcessStateClient(
    private val service: InventoryProjectionService,
) : StateClient {
    override fun getState(key: InventoryKey): InventoryState? = service.getState(key)

    override fun applyDelta(request: DeltaUpdateRequest): InventoryUpdateResponse = service.applyDelta(request)

    override fun applyAbsolute(request: AbsoluteUpdateRequest): InventoryUpdateResponse = service.applyAbsolute(request)
}

class InProcessLakeClient(
    private val service: LakeWriterService,
) : LakeClient {
    override fun write(event: InventoryDomainEvent): LakeWriteResponse = service.write(LakeWriteRequest(event))
}
