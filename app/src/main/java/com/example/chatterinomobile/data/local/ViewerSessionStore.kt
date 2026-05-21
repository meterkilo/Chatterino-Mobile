package com.example.chatterinomobile.data.local

import android.content.Context

class ViewerSessionStore(context: Context) {

    private val sharedPreferences = context.applicationContext.getSharedPreferences(
        FILE_NAME,
        Context.MODE_PRIVATE
    )

    fun isGuestViewer(): Boolean =
        sharedPreferences.getBoolean(KEY_GUEST_VIEWER, false)

    fun setGuestViewer(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_GUEST_VIEWER, enabled)
            .apply()
    }

    fun clear() {
        sharedPreferences.edit().clear().apply()
    }

    private companion object {
        const val FILE_NAME = "viewer_session_store"
        const val KEY_GUEST_VIEWER = "guest_viewer"
    }
}
