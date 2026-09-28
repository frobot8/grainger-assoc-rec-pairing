package com.example.inventory.integration.support

import java.time.Instant
import java.util.UUID

/** Shared fixtures kept identical across scenarios so tests stay deterministic. */
object Fixtures {
    const val SKU = "SKU-1001"
    const val FACILITY_ID = "FAC-01"
    val BASE_TIME: Instant = Instant.parse("2024-03-15T10:30:00Z")
    val EVENT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    val SECOND_EVENT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    val THIRD_EVENT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000003")
}
