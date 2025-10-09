package com.iamashad.musesample.screens.session

import androidx.compose.runtime.mutableStateListOf
import com.iamashad.musesample.ViewModel
import com.iamashad.musesample.model.SessionItem

class SessionListViewModel : ViewModel() {
    private val _sessions = mutableStateListOf<SessionItem>()
    val sessions: List<SessionItem> get() = _sessions

    init {
        // Dummy seed data for week-1 demo
        _sessions.addAll(demoSessions())
    }

    fun addSession(session: SessionItem) {
        _sessions.add(0, session) // add new ones to the top
    }

    fun clearSessions() {
        _sessions.clear()
    }
}
