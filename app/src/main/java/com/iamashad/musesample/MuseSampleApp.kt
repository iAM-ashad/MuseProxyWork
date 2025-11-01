package com.iamashad.musesample

import android.app.Application
import com.iamashad.musesample.repository.SessionRepository

/**
 * Custom Application class.
 *
 * Startup tasks:
 * - Initialize the Room-backed repository so it has an application context available for
 *   opening the encrypted SQLCipher database.
 *
 * If we choose to add DI later (Hilt), we can move to @HiltAndroidApp and the repository will be
 * provided via modules instead of a static init call.
 */
class MuseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SessionRepository.init(this)
    }
}
