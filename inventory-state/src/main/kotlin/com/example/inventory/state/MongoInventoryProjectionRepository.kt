package com.example.inventory.state

import com.example.inventory.contract.AbsoluteUpdateRequest
import com.example.inventory.contract.DeltaUpdateRequest
import com.example.inventory.contract.InventoryKey
import com.example.inventory.contract.InventoryState
import com.example.inventory.contract.InventoryUpdateResponse
import com.example.inventory.contract.UpdateStatus
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * MongoDB-backed projection document. Distinct from the wire-level [InventoryState]
 * contract because it additionally tracks [appliedEventIds] for durable deduplication
 * of every applied event, which is a storage concern, not a public field.
 */
@Document(collection = "inventory_projections")
data class InventoryProjectionDocument(
    val sku: String,
    val facilityId: String,
    val quantity: Int,
    val sequence: Long,
    val version: Long,
    val appliedEventIds: Set<UUID> = emptySet(),
) {
    fun toState(): InventoryState = InventoryState(sku, facilityId, quantity, sequence, version)
}

/**
 * Production [InventoryProjectionRepository] backed by MongoDB. The conditional update
 * methods use a single atomic `findAndModify` filtered by (sku, facilityId,
 * expectedVersion, sequence-lower-bound, distinct event id) with the new quantity
 * computed server-side via `$inc`/`$set` operators — no read-then-write race window,
 * since the match filter and the mutation commit as one atomic Mongo operation. When
 * the filtered update fails to match any document, a follow-up read classifies the
 * reason (duplicate event id, stale sequence, version conflict, or an absent document)
 * for the response.
 *
 * Requires a unique index on (sku, facilityId) — created in [ensureIndexes] on startup —
 * so first-write creation races resolve to exactly one winner.
 */
@Repository
class MongoInventoryProjectionRepository(
    private val mongoTemplate: MongoTemplate,
) : InventoryProjectionRepository {
    init {
        ensureIndexes()
    }

    override fun find(key: InventoryKey): InventoryState? = findDocument(key)?.toState()

    override fun save(state: InventoryState): InventoryState {
        mongoTemplate.findAndModify(
            Query(Criteria.where("sku").`is`(state.sku).and("facilityId").`is`(state.facilityId)),
            Update()
                .setOnInsert("sku", state.sku)
                .setOnInsert("facilityId", state.facilityId)
                .setOnInsert("appliedEventIds", emptySet<UUID>())
                .set("quantity", state.quantity)
                .set("sequence", state.sequence)
                .set("version", state.version),
            FindAndModifyOptions.options().upsert(true).returnNew(true),
            InventoryProjectionDocument::class.java,
        )
        return state
    }

    override fun updateDeltaIfVersion(request: DeltaUpdateRequest): InventoryUpdateResponse = applyConditionally(
        key = request.key,
        eventId = request.eventId,
        sequence = request.sequence,
        expectedVersion = request.expectedVersion,
        initialQuantity = request.quantityDelta,
        quantityMutation = { update -> update.inc("quantity", request.quantityDelta) },
    )

    override fun updateAbsoluteIfVersion(request: AbsoluteUpdateRequest): InventoryUpdateResponse = applyConditionally(
        key = request.key,
        eventId = request.eventId,
        sequence = request.sequence,
        expectedVersion = request.expectedVersion,
        initialQuantity = request.quantity,
        quantityMutation = { update -> update.set("quantity", request.quantity) },
    )

    private fun applyConditionally(
        key: InventoryKey,
        eventId: UUID,
        sequence: Long,
        expectedVersion: Long,
        initialQuantity: Int,
        quantityMutation: (Update) -> Update,
    ): InventoryUpdateResponse {
        if (expectedVersion == 0L) {
            val created = tryCreate(key, eventId, sequence, initialQuantity)
            if (created != null) {
                return InventoryUpdateResponse(UpdateStatus.APPLIED, created.toState())
            }
            // A document already exists (version is no longer 0 for this key); fall
            // through to the conditional-update path so it is classified normally.
        }

        val matchFilter =
            Criteria
                .where("sku").`is`(key.sku)
                .and("facilityId").`is`(key.facilityId)
                .and("version").`is`(expectedVersion)
                .and("sequence").lt(sequence)
                .and("appliedEventIds").ne(eventId)

        val update =
            quantityMutation(
                Update()
                    .set("sequence", sequence)
                    .addToSet("appliedEventIds", eventId)
                    .inc("version", 1L),
            )

        val updated =
            mongoTemplate.findAndModify(
                Query(matchFilter),
                update,
                FindAndModifyOptions.options().returnNew(true),
                InventoryProjectionDocument::class.java,
            )

        if (updated != null) {
            return InventoryUpdateResponse(UpdateStatus.APPLIED, updated.toState())
        }

        val current = findDocument(key)
        val classification =
            when {
                current == null -> UpdateStatus.CONFLICT
                eventId in current.appliedEventIds -> UpdateStatus.DUPLICATE
                current.version != expectedVersion -> UpdateStatus.CONFLICT
                sequence <= current.sequence -> UpdateStatus.STALE
                else -> UpdateStatus.CONFLICT
            }
        return InventoryUpdateResponse(classification, current?.toState())
    }

    /**
     * Atomically creates the first document for [key] when the caller expects version
     * 0 (no prior state). The unique index on (sku, facilityId) makes concurrent first
     * inserts for the same key resolve to exactly one winner. A duplicate-key loser
     * falls back to the standard conditional path to classify against the winner's
     * document. Using an insert (rather than a `version = 0` upsert) also preserves the
     * semantics of an existing version-zero state written through the general [save]
     * method: that state must be updated, not mistaken for a missing document.
     */
    private fun tryCreate(
        key: InventoryKey,
        eventId: UUID,
        sequence: Long,
        quantity: Int,
    ): InventoryProjectionDocument? {
        val document =
            InventoryProjectionDocument(
                sku = key.sku,
                facilityId = key.facilityId,
                quantity = quantity,
                sequence = sequence,
                version = 1L,
                appliedEventIds = setOf(eventId),
            )
        return try {
            mongoTemplate.insert(document)
        } catch (_: DuplicateKeyException) {
            null
        }
    }

    private fun findDocument(key: InventoryKey): InventoryProjectionDocument? = mongoTemplate.findOne(
        Query(Criteria.where("sku").`is`(key.sku).and("facilityId").`is`(key.facilityId)),
        InventoryProjectionDocument::class.java,
    )

    private fun ensureIndexes() {
        mongoTemplate.indexOps(InventoryProjectionDocument::class.java).createIndex(
            Index()
                .on("sku", Sort.Direction.ASC)
                .on("facilityId", Sort.Direction.ASC)
                .unique(),
        )
    }
}
