package com.nofuzznotes.domain.repository.fake

import com.nofuzznotes.core.model.SimpleRecord

class FakeSimpleRecordRepository {
    private val records = linkedMapOf<Long, SimpleRecord>()
    private var nextId = 1L

    // Create records in memory so service tests can run before the database exists.
    fun create(content: String): SimpleRecord {
        assert(nextId > 0L)
        val record = SimpleRecord(id = nextId, content = content)
        records[nextId] = record
        nextId += 1L
        return record
    }

    // Read by id so tests can exercise repository boundaries without Android dependencies.
    fun read(id: Long): SimpleRecord? {
        assert(id > 0L)
        return records[id]
    }

    // Return all records so service tests can inspect fake persistence state directly.
    fun readAll(): List<SimpleRecord> = records.values.toList()

    // Update records by replacement because fake persistence should expose simple invariants.
    fun update(record: SimpleRecord) {
        assert(record.id > 0L)
        assert(records.containsKey(record.id))
        records[record.id] = record
    }

    // Delete records by id because later repository fakes need this basic lifecycle shape.
    fun delete(id: Long) {
        assert(id > 0L)
        assert(records.containsKey(id))
        records.remove(id)
    }
}
