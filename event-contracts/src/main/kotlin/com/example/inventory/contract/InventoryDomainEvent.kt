package com.example.inventory.contract

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant
import java.util.UUID

/**
 * Canonical envelope for every inventory event flowing through the platform.
 *
 * Wire representations are distinguished by the `type` property (see [JsonTypeInfo]);
 * the concrete subtype set is fixed to the events currently supported by the domain
 * contract and is not meant to grow ad hoc.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = InventoryDeltaEvent::class, name = "inventory.delta.v1"),
    JsonSubTypes.Type(value = InventoryAdjustmentEvent::class, name = "inventory.adjustment.v1"),
)
sealed class InventoryDomainEvent {
    abstract val eventId: UUID
    abstract val sku: String
    abstract val facilityId: String
    abstract val eventTime: Instant
    abstract val sequence: Long
}

/**
 * A relative change to on-hand quantity, e.g. from a shipment or sale.
 *
 * Wire type: `inventory.delta.v1`. The type name is part of the stable contract:
 * it must never change once published.
 */
@JsonTypeName("inventory.delta.v1")
data class InventoryDeltaEvent(
    override val eventId: UUID,
    override val sku: String,
    override val facilityId: String,
    override val eventTime: Instant,
    override val sequence: Long,
    val quantityDelta: Int,
) : InventoryDomainEvent()

/**
 * An authoritative, absolute quantity correction for a sku/facility.
 *
 * Wire type: `inventory.adjustment.v1`. The type name is part of the stable contract:
 * it must never change once published.
 */
@JsonTypeName("inventory.adjustment.v1")
data class InventoryAdjustmentEvent(
    override val eventId: UUID,
    override val sku: String,
    override val facilityId: String,
    override val eventTime: Instant,
    override val sequence: Long,
    val quantity: Int,
) : InventoryDomainEvent()
