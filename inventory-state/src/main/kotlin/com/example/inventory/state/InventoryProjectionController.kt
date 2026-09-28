package com.example.inventory.state

import com.example.inventory.contract.AbsoluteUpdateRequest
import com.example.inventory.contract.DeltaUpdateRequest
import com.example.inventory.contract.InventoryKey
import com.example.inventory.contract.InventoryState
import com.example.inventory.contract.InventoryUpdateResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/inventory")
class InventoryProjectionController(
    private val service: InventoryProjectionService,
) {
    @GetMapping("/{facilityId}/{sku}")
    fun getState(
        @PathVariable facilityId: String,
        @PathVariable sku: String,
    ): ResponseEntity<InventoryState> {
        val state = service.getState(InventoryKey(sku = sku, facilityId = facilityId))
        return if (state == null) ResponseEntity.notFound().build() else ResponseEntity.ok(state)
    }

    @PostMapping("/delta")
    fun applyDelta(
        @Valid @RequestBody request: DeltaUpdateRequest,
    ): InventoryUpdateResponse = service.applyDelta(request)

    @PostMapping("/absolute")
    fun applyAbsolute(
        @Valid @RequestBody request: AbsoluteUpdateRequest,
    ): InventoryUpdateResponse = service.applyAbsolute(request)
}
