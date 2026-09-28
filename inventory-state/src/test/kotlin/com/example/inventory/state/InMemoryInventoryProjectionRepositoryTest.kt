package com.example.inventory.state

import com.example.inventory.contract.AbsoluteUpdateRequest
import com.example.inventory.contract.DeltaUpdateRequest
import com.example.inventory.contract.InventoryKey
import com.example.inventory.contract.UpdateStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class InMemoryInventoryProjectionRepositoryTest {
    private lateinit var repository: InMemoryInventoryProjectionRepository
    private val key = InventoryKey(sku = "SKU-1", facilityId = "FAC-1")

    @BeforeEach
    fun setUp() {
        repository = InMemoryInventoryProjectionRepository()
    }

    @Test
    fun `find returns null for unknown key`() {
        assertNull(repository.find(key))
    }

    @Test
    fun `initial delta creates state with version 1`() {
        val response =
            repository.updateDeltaIfVersion(
                DeltaUpdateRequest(UUID.randomUUID(), key, quantityDelta = 5, sequence = 1, expectedVersion = 0),
            )

        assertEquals(UpdateStatus.APPLIED, response.status)
        assertEquals(5, response.state?.quantity)
        assertEquals(1L, response.state?.version)
        assertEquals(1L, response.state?.sequence)
    }

    @Test
    fun `initial creation with nonzero expected version is a conflict`() {
        val response =
            repository.updateDeltaIfVersion(
                DeltaUpdateRequest(UUID.randomUUID(), key, quantityDelta = 5, sequence = 1, expectedVersion = 3),
            )

        assertEquals(UpdateStatus.CONFLICT, response.status)
        assertNull(response.state)
    }

    @Test
    fun `second delta with newer sequence and matching version applies`() {
        repository.updateDeltaIfVersion(
            DeltaUpdateRequest(UUID.randomUUID(), key, quantityDelta = 5, sequence = 1, expectedVersion = 0),
        )

        val second =
            repository.updateDeltaIfVersion(
                DeltaUpdateRequest(UUID.randomUUID(), key, quantityDelta = 3, sequence = 2, expectedVersion = 1),
            )

        assertEquals(UpdateStatus.APPLIED, second.status)
        assertEquals(8, second.state?.quantity)
        assertEquals(2L, second.state?.version)
    }

    @Test
    fun `replaying the same event id is a duplicate and does not reapply`() {
        val eventId = UUID.randomUUID()
        val first =
            repository.updateDeltaIfVersion(
                DeltaUpdateRequest(eventId, key, quantityDelta = 5, sequence = 1, expectedVersion = 0),
            )

        val replay =
            repository.updateDeltaIfVersion(
                DeltaUpdateRequest(eventId, key, quantityDelta = 5, sequence = 1, expectedVersion = 0),
            )

        assertEquals(UpdateStatus.DUPLICATE, replay.status)
        assertEquals(first.state?.quantity, replay.state?.quantity)
        assertEquals(first.state?.version, replay.state?.version)
    }

    @Test
    fun `replaying an older event id after a later update is still a duplicate`() {
        val firstEventId = UUID.randomUUID()
        repository.updateDeltaIfVersion(
            DeltaUpdateRequest(firstEventId, key, quantityDelta = 5, sequence = 1, expectedVersion = 0),
        )
        repository.updateDeltaIfVersion(
            DeltaUpdateRequest(UUID.randomUUID(), key, quantityDelta = 3, sequence = 2, expectedVersion = 1),
        )

        val replay =
            repository.updateDeltaIfVersion(
                DeltaUpdateRequest(firstEventId, key, quantityDelta = 5, sequence = 3, expectedVersion = 2),
            )

        assertEquals(UpdateStatus.DUPLICATE, replay.status)
        assertEquals(8, replay.state?.quantity)
        assertEquals(2L, replay.state?.version)
    }

    @Test
    fun `lower or equal sequence is rejected as stale and state is unchanged`() {
        repository.updateDeltaIfVersion(
            DeltaUpdateRequest(UUID.randomUUID(), key, quantityDelta = 5, sequence = 5, expectedVersion = 0),
        )

        val stale =
            repository.updateDeltaIfVersion(
                DeltaUpdateRequest(UUID.randomUUID(), key, quantityDelta = 100, sequence = 5, expectedVersion = 1),
            )

        assertEquals(UpdateStatus.STALE, stale.status)
        assertEquals(5, stale.state?.quantity)
        assertEquals(1L, stale.state?.version)
    }

    @Test
    fun `mismatched expected version is rejected as conflict and state is unchanged`() {
        repository.updateDeltaIfVersion(
            DeltaUpdateRequest(UUID.randomUUID(), key, quantityDelta = 5, sequence = 1, expectedVersion = 0),
        )

        val conflict =
            repository.updateDeltaIfVersion(
                DeltaUpdateRequest(UUID.randomUUID(), key, quantityDelta = 100, sequence = 2, expectedVersion = 99),
            )

        assertEquals(UpdateStatus.CONFLICT, conflict.status)
        assertEquals(5, conflict.state?.quantity)
        assertEquals(1L, conflict.state?.version)
    }

    @Test
    fun `absolute update replaces quantity and increments version`() {
        repository.updateDeltaIfVersion(
            DeltaUpdateRequest(UUID.randomUUID(), key, quantityDelta = 5, sequence = 1, expectedVersion = 0),
        )

        val absolute =
            repository.updateAbsoluteIfVersion(
                AbsoluteUpdateRequest(UUID.randomUUID(), key, quantity = 42, sequence = 2, expectedVersion = 1),
            )

        assertEquals(UpdateStatus.APPLIED, absolute.status)
        assertEquals(42, absolute.state?.quantity)
        assertEquals(2L, absolute.state?.version)
    }

    @Test
    fun `concurrent deltas against the same key all apply exactly once via optimistic retry`() {
        val threadCount = 16
        val executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount)
        val startBarrier = java.util.concurrent.CyclicBarrier(threadCount)
        val sequenceCounter = java.util.concurrent.atomic.AtomicLong(0)

        try {
            val futures =
                (1..threadCount).map {
                    executor.submit {
                        startBarrier.await()
                        // Each writer retries with the current version/sequence on
                        // conflict, mirroring a real optimistic-concurrency client;
                        // exactly one attempt per writer must ultimately succeed.
                        var applied = false
                        while (!applied) {
                            val current = repository.find(key)
                            val expectedVersion = current?.version ?: 0L
                            val sequence = sequenceCounter.incrementAndGet()
                            val response =
                                repository.updateDeltaIfVersion(
                                    DeltaUpdateRequest(
                                        eventId = UUID.randomUUID(),
                                        key = key,
                                        quantityDelta = 1,
                                        sequence = sequence,
                                        expectedVersion = expectedVersion,
                                    ),
                                )
                            applied = response.status == UpdateStatus.APPLIED
                        }
                    }
                }
            futures.forEach { it.get() }
        } finally {
            executor.shutdown()
        }

        // Every writer eventually wins exactly one conditional update, so the final
        // quantity and version both equal the writer count with no lost or duplicated
        // updates despite the concurrent contention.
        val finalState = repository.find(key)
        assertEquals(threadCount, finalState?.quantity)
        assertEquals(threadCount.toLong(), finalState?.version)
    }

    @Test
    fun `only one of two racing updates with the same expected version wins`() {
        repository.updateDeltaIfVersion(
            DeltaUpdateRequest(UUID.randomUUID(), key, quantityDelta = 10, sequence = 1, expectedVersion = 0),
        )

        val threadCount = 8
        val executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount)
        val startBarrier = java.util.concurrent.CyclicBarrier(threadCount)
        val statuses = java.util.concurrent.ConcurrentLinkedQueue<UpdateStatus>()

        try {
            val futures =
                (1..threadCount).map {
                    executor.submit {
                        startBarrier.await()
                        val response =
                            repository.updateDeltaIfVersion(
                                DeltaUpdateRequest(
                                    eventId = UUID.randomUUID(),
                                    key = key,
                                    quantityDelta = 1,
                                    sequence = 2,
                                    expectedVersion = 1,
                                ),
                            )
                        statuses.add(response.status)
                    }
                }
            futures.forEach { it.get() }
        } finally {
            executor.shutdown()
        }

        assertEquals(1, statuses.count { it == UpdateStatus.APPLIED })
        assertEquals(threadCount - 1, statuses.count { it == UpdateStatus.CONFLICT })

        val finalState = repository.find(key)
        assertEquals(11, finalState?.quantity)
        assertEquals(2L, finalState?.version)
    }
}
