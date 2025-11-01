package com.iamashad.musesample

/**
 * Centralized log tags used across the app.
 * Usage:
 *   Log.d(TAG_PCG_GEN, "message")
 *   Log.i(TAG_MUSE_DB, "db opened")
 */
const val TAG_PCG_GEN = "PCG_GEN"   // High-level PCG report generation flow/timing.
const val TAG_PCG_WAV = "PCG_WAV"   // WAV parsing/PCM extraction specifics.
const val TAG_MUSE_DB = "MUSE_DB"   // Room/SQLCipher database lifecycle and DAO ops.
const val TAG_MUSE_SEC = "MUSE_SEC" // Security & key management (EncryptedSharedPreferences).
