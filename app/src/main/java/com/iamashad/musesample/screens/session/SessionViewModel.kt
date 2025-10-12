package com.iamashad.musesample.screens.session

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import com.iamashad.musesample.model.Session

class SessionListViewModel : ViewModel() {
    private val _sessions = mutableStateListOf<Session>()
    val sessions: List<Session> get() = _sessions

    init {
        // Dummy seed data for week-1 demo
        _sessions.addAll(listOf())
    }

    fun addSession(session: Session) {
        _sessions.add(0, session) // add new ones to the top
    }

    fun clearSessions() {
        _sessions.clear()
    }
}
