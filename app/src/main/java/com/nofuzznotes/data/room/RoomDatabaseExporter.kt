package com.nofuzznotes.data.room

import java.io.File

class RoomDatabaseExporter(private val databaseFile: File) {
    // Copy the SQLite file to the requested destination because backup exports the complete notebook database.
    fun exportTo(destination: File): File {
        assert(databaseFile.exists())
        destination.parentFile?.mkdirs()
        databaseFile.copyTo(destination, overwrite = true)
        assert(destination.length() > 0L)
        return destination
    }
}
