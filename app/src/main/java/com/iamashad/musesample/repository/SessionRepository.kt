package com.iamashad.musesample.repository

import com.iamashad.musesample.model.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object SessionRepository {
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions = _sessions.asStateFlow()

    fun add(s: Session) { _sessions.value = _sessions.value + s }
    fun updatePdf(id: Long, pdfPath: String) {
        _sessions.value = _sessions.value.map { if (it.id == id) it.copy(pdfPath = pdfPath) else it }
    }
}

