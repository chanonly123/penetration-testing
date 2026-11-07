package com.you.secretvault

import java.io.File

/**
 * Deliberately WEAK on purpose (Day 1).
 *
 * Mirrors the iOS SecretVault from iOS_Security_Plan.md:
 *  - hardcoded secret
 *  - boolean gates (single point of failure — one return value to flip)
 *  - predictable, obvious symbol names
 *  - every decision made in-process, trusting the device
 *
 * You attack THIS, your own app, with Frida to see what instrumentation
 * can read and flip. Hardening happens in Day 2 (see the plan).
 */
class SecretVault {

    // Round 3 removes this single point of failure.
    fun isLicenseValid(): Boolean {
        return true
    }

    // Android analogue of the iOS jailbreak check: look for root signals.
    fun isRooted(): Boolean {
        val paths = listOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        return paths.any { File(it).exists() }
    }

    // Boolean gate + hardcoded secret. The weakest possible design.
    fun revealSecret(pin: String): String? {
        if (pin != "1337") return null
        return "FLAG{replace_me}"
    }
}
