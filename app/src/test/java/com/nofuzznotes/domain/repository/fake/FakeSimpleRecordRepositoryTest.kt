package com.nofuzznotes.domain.repository.fake

import com.nofuzznotes.core.model.SimpleRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakeSimpleRecordRepositoryTest {
    // Cover the basic fake repository lifecycle because later services need in-memory persistence.
    @Test
    fun createsReadsUpdatesAndDeletesSimpleRecords() {
        val repository = FakeSimpleRecordRepository()

        val created = repository.create("first")
        assertEquals(SimpleRecord(id = 1L, content = "first"), created)
        assertEquals(created, repository.read(created.id))

        val updated = created.copy(content = "second")
        repository.update(updated)
        assertEquals(updated, repository.read(created.id))
        assertEquals(listOf(updated), repository.readAll())

        repository.delete(created.id)
        assertNull(repository.read(created.id))
        assertEquals(emptyList<SimpleRecord>(), repository.readAll())
    }
}
