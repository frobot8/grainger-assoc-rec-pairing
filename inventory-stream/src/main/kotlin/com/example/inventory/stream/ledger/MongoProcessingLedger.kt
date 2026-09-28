package com.example.inventory.stream.ledger

import com.example.inventory.contract.InventoryKey
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.annotation.Id
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Document("processed_inventory_events")
data class ProcessedInventoryEvent(
    @Id val eventId: UUID,
    val sku: String,
    val facilityId: String,
    val stateVersion: Long,
)

@Repository
class MongoProcessingLedger(
    private val mongoTemplate: MongoTemplate,
) : ProcessingLedger {
    override fun isProcessed(eventId: UUID): Boolean = mongoTemplate.exists(
        Query.query(Criteria.where("_id").`is`(eventId)),
        ProcessedInventoryEvent::class.java,
    )

    override fun latestVersion(key: InventoryKey): Long? = mongoTemplate
        .findOne(
            Query.query(
                Criteria.where("sku").`is`(key.sku).and("facilityId").`is`(key.facilityId),
            ).with(Sort.by(Sort.Direction.DESC, "stateVersion")),
            ProcessedInventoryEvent::class.java,
        )
        ?.stateVersion

    override fun markProcessed(
        eventId: UUID,
        key: InventoryKey,
        stateVersion: Long,
    ) {
        try {
            mongoTemplate.insert(
                ProcessedInventoryEvent(
                    eventId = eventId,
                    sku = key.sku,
                    facilityId = key.facilityId,
                    stateVersion = stateVersion,
                ),
            )
        } catch (_: DuplicateKeyException) {
            // Another delivery completed the same idempotent work first.
        }
    }
}
