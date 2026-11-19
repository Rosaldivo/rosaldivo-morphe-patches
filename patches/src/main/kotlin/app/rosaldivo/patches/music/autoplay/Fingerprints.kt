/*
 * Copyright 2026 Rosaldivo.
 * https://github.com/Rosaldivo/rosaldivo-morphe-patches
 *
 * Licensed under the GNU General Public License v3.0.
 */

package app.rosaldivo.patches.music.autoplay

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

/**
 * Applies the server's default autoplay mode when a new queue loads. The method is:
 *
 * ```
 * if (prefs.getBoolean("has_user_changed_default_autoplay_mode", false)) {
 *     setAutoplay(prefs.getBoolean("autoplay_enabled", true))   // Keep the user's choice.
 *     return
 * }
 * // Otherwise overwrite "autoplay_enabled" with the server default (usually ON).
 * ```
 *
 * The Up next switch sets the "has_user_changed" flag, but the switch in Settings > Playback
 * (9.3x) only stores "autoplay_enabled". Turning autoplay off there is then undone as soon as
 * the next track starts.
 *
 * The other method that reads the flag (the Up next switch listener) takes parameters.
 */
internal object ApplyDefaultAutoplayModeFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        string("has_user_changed_default_autoplay_mode"),
        // SharedPreferences.getBoolean() result.
        opcode(Opcode.MOVE_RESULT, location = MatchAfterWithin(3))
    ),
    strings = listOf("autoplay_enabled")
)
