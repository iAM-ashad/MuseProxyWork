package com.iamashad.musesample.wrapper

import android.app.Application

object TaalSdkHolder {
    @Volatile
    private var _wrapper: TaalWrapper? = null

    fun get(app: Application): TaalWrapper =
        _wrapper ?: synchronized(this) {
            _wrapper ?: TaalWrapper(app).also { _wrapper = it }
        }
}
