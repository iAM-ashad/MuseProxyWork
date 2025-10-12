package com.iamashad.musesample.screens.metadata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iamashad.musesample.model.Session
import com.iamashad.musesample.repository.SessionRepository
import kotlinx.coroutines.launch

class MetadataViewModel : ViewModel() {
    fun saveSession(meta: Session) {
        viewModelScope.launch { SessionRepository.add(meta) }
    }
}