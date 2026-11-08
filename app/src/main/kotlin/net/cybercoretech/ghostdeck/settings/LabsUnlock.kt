package net.cybercoretech.ghostdeck.settings

private const val TAPS_NEEDED = 7
private const val TAP_WINDOW_MS = 3_000L
private const val HOLD_MS = 2_000L

/**
 * The two secret ways into Labs (spec 10a.7): seven taps on the version within 3 s, or Volume Down held for 2 s.
 * Both return true exactly once, at the moment of unlocking; [lock] re-arms them.
 */
class LabsUnlock(private val clock: () -> Long) {
    private val taps = ArrayDeque<Long>()
    private var holdStart: Long? = null
    private var unlocked = false

    fun onVersionTap(): Boolean {
        val now = clock()
        taps.addLast(now)
        while (taps.isNotEmpty() && now - taps.first() > TAP_WINDOW_MS) taps.removeFirst()
        return if (taps.size >= TAPS_NEEDED) unlock() else false
    }

    /** Call with true for every press and key-repeat event, false on release. */
    fun onVolumeDown(down: Boolean): Boolean {
        if (!down) {
            holdStart = null
            return false
        }
        val now = clock()
        val start = holdStart ?: now.also { holdStart = it }
        return if (now - start >= HOLD_MS) unlock() else false
    }

    fun lock() {
        unlocked = false
        taps.clear()
        holdStart = null
    }

    private fun unlock(): Boolean {
        taps.clear()
        holdStart = null
        if (unlocked) return false
        unlocked = true
        return true
    }
}
