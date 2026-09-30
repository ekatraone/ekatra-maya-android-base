package org.ekatra.alfred.data.repository

import org.ekatra.alfred.data.local.SavedAnswerDao
import org.ekatra.alfred.data.local.SavedChartDao
import org.ekatra.alfred.data.model.SavedAnswer
import org.ekatra.alfred.data.model.SavedChart

class SavedContentRepository(
    private val savedAnswerDao: SavedAnswerDao,
    private val savedChartDao: SavedChartDao
) {
    suspend fun saveAnswer(question: String, answer: String, subject: String?): Long {
        val entity = SavedAnswer(
            question = question,
            answer = answer,
            subject = subject
        )
        return savedAnswerDao.insert(entity)
    }

    suspend fun getAnswers(): List<SavedAnswer> = savedAnswerDao.getAll()

    suspend fun searchAnswers(query: String, subject: String?): List<SavedAnswer> {
        val normalized = query.trim().lowercase()
        return savedAnswerDao.getAll().filter { answer ->
            val subjectOk = subject.isNullOrBlank() || answer.subject.equals(subject, ignoreCase = true)
            val queryOk = normalized.isBlank() ||
                answer.question.lowercase().contains(normalized) ||
                answer.answer.lowercase().contains(normalized)
            subjectOk && queryOk
        }
    }

    suspend fun toggleFavorite(answerId: Long): Boolean {
        val current = savedAnswerDao.getAll().firstOrNull { it.id == answerId } ?: return false
        val updated = current.copy(isFavorite = !current.isFavorite)
        savedAnswerDao.update(updated)
        return updated.isFavorite
    }

    suspend fun deleteAnswer(id: Long) {
        savedAnswerDao.delete(id)
    }

    suspend fun saveChart(title: String, imageData: ByteArray?, relatedAnswerId: Long?): Long {
        val entity = SavedChart(
            title = title,
            imageData = imageData,
            relatedAnswerId = relatedAnswerId
        )
        return savedChartDao.insert(entity)
    }

    suspend fun getCharts(): List<SavedChart> = savedChartDao.getAll()
}
