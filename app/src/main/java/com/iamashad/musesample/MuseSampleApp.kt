package com.iamashad.musesample

import android.app.Application
import com.iamashad.musesample.repository.SessionRepository

class MuseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SessionRepository.init(this)
    }
}
