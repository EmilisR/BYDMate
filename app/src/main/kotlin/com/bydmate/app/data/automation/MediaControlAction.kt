package com.bydmate.app.data.automation

import org.json.JSONObject

/**
 * The `media_control` automation action: drive a media player through its MediaSession.
 *
 * Payload (JSON):
 *  - `op`      play | pause | toggle | stop | next | previous | play_search
 *  - `package` target app; empty = the active player (the one playing, else the most recent)
 *  - `launch`  open [package] first and wait for its session (so "open YT Music and play" is
 *              one action)
 *  - `query`   what to play, for `play_search`
 *
 * Sessions are read through the notification-listener foothold BYDMate already self-grants
 * (MediaSessionListenerService). When the target has no session even after launching, play /
 * pause / next / previous fall back to a system media key, which reaches the last media app.
 */
object MediaControlAction {
    const val KIND = "media_control"

    val OPS = listOf("play", "pause", "toggle", "stop", "next", "previous", "play_search")

    data class Player(val packageName: String, val label: String)

    /** Players offered by name in the editor (only the installed ones are shown). */
    val KNOWN_PLAYERS = listOf(
        Player("com.google.android.apps.youtube.music", "YouTube Music"),
        Player("app.revanced.android.apps.youtube.music", "YouTube Music (ReVanced)"),
        Player("app.rvx.android.apps.youtube.music", "YouTube Music (RVX)"),
        Player("anddea.youtube.music", "YouTube Music (anddea)"),
        Player("com.spotify.music", "Spotify"),
        Player("ru.yandex.music", "Yandex Music"),
        Player("com.google.android.youtube", "YouTube"),
        Player("anddea.youtube", "YouTube (anddea)"),
        Player("deezer.android.app", "Deezer"),
    )

    data class Spec(val op: String, val packageName: String, val launch: Boolean, val query: String)

    fun parse(payload: String?): Spec? = try {
        val json = JSONObject(payload ?: return null)
        val op = json.optString("op")
        if (op !in OPS) null
        else Spec(
            op = op,
            packageName = json.optString("package").trim(),
            launch = json.optBoolean("launch", false),
            query = json.optString("query"),
        )
    } catch (_: Exception) {
        null
    }

    fun payload(spec: Spec): String = JSONObject()
        .put("op", spec.op)
        .put("package", spec.packageName)
        .put("launch", spec.launch)
        .put("query", spec.query)
        .toString()

    fun opLabel(op: String): String = when (op) {
        "play" -> "Play"
        "pause" -> "Pause"
        "toggle" -> "Play / pause"
        "stop" -> "Stop"
        "next" -> "Next track"
        "previous" -> "Previous track"
        "play_search" -> "Play search"
        else -> op
    }

    fun playerLabel(packageName: String): String =
        if (packageName.isBlank()) "active player"
        else KNOWN_PLAYERS.firstOrNull { it.packageName == packageName }?.label ?: packageName

    /** The rule line, e.g. "Media: Play search «lofi» — YouTube Music (open)". */
    fun displayName(spec: Spec): String = buildString {
        append("Media: ").append(opLabel(spec.op))
        if (spec.op == "play_search" && spec.query.isNotBlank()) append(" «").append(spec.query.trim()).append('»')
        append(" — ").append(playerLabel(spec.packageName))
        if (spec.launch && spec.packageName.isNotBlank()) append(" (open)")
    }

    /** Session-state snapshot for [pickSession]: PlaybackState.STATE_* or null. */
    data class Session(val packageName: String, val state: Int?)

    private const val STATE_NONE = 0
    private const val STATE_PLAYING = 3

    /**
     * Which session to drive: the one of [packageName] when set (null if it has none), else the
     * playing one, else one with any state, else the system's first. Pure, like KnobPlayPause.
     */
    fun pickSession(sessions: List<Session>, packageName: String): Int? {
        if (packageName.isNotBlank()) return sessions.indexOfFirst { it.packageName == packageName }.takeIf { it >= 0 }
        if (sessions.isEmpty()) return null
        sessions.indexOfFirst { it.state == STATE_PLAYING }.takeIf { it >= 0 }?.let { return it }
        sessions.indexOfFirst { (it.state ?: STATE_NONE) != STATE_NONE }.takeIf { it >= 0 }?.let { return it }
        return 0
    }
}
