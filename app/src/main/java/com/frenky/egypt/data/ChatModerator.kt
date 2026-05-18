package com.frenky.egypt.data

object ChatModerator {
    fun isModerator(displayName: String): Boolean =
        displayName.trim().equals("frenk", ignoreCase = true)
}
