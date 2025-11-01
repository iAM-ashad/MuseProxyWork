package com.iamashad.musesample.permissions

import android.Manifest

/**
 * Central place to reference runtime permissions used by the app.
 *
 * Keeping these in one object:
 *  - Prevents typos in scattered strings.
 *  - Makes it easy to audit which permissions we request.
 */
object AppPermissions {
    /** Needed to capture audio in the Recording screen. */
    const val RECORD_AUDIO = Manifest.permission.RECORD_AUDIO
}
