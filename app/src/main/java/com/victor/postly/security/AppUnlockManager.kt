package com.victor.postly.security

object AppUnlockManager {
    private var unlockedForSession = false

    fun isUnlocked(): Boolean = unlockedForSession

    fun markUnlocked() {
        unlockedForSession = true
    }

    fun reset() {
        unlockedForSession = false
    }
}
