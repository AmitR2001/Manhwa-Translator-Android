package com.manhwatranslator.translation

interface Translator {
    suspend fun translate(koreanText: String): String
    suspend fun ensureReady()

    /** Releases any underlying native resources. Call when no longer needed (e.g. session
     * stop) - leaving it unclosed across repeated create/stop cycles can degrade or break
     * translation until the process restarts. */
    fun close() {}
}
