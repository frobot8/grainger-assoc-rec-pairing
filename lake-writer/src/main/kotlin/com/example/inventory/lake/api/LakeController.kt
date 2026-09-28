package com.example.inventory.lake.api

import com.example.inventory.contract.LakeWriteRequest
import com.example.inventory.contract.LakeWriteResponse
import com.example.inventory.lake.LakeWriterService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/lake")
class LakeController(
    private val lakeWriterService: LakeWriterService,
) {
    @PostMapping("/records")
    fun writeRecord(
        @RequestBody request: LakeWriteRequest,
    ): ResponseEntity<LakeWriteResponse> = ResponseEntity.status(HttpStatus.OK).body(lakeWriterService.write(request))
}
