package com.example.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class HistoryRepository(private val dao: GenerationDao) {
    val historyFlow: Flow<List<GenerationRecord>> = dao.observeHistory()

    suspend fun saveRecord(record: GenerationRecord): Long {
        return dao.insert(record)
    }

    suspend fun deleteRecord(record: GenerationRecord) {
        dao.deleteById(record.id)
        withContext(Dispatchers.IO) {
            runCatching { File(record.outputFilePath).delete() }
        }
    }

    suspend fun clearAll(records: List<GenerationRecord>) {
        dao.clearAll()
        withContext(Dispatchers.IO) {
            records.forEach { record ->
                runCatching { File(record.outputFilePath).delete() }
            }
        }
    }
}
