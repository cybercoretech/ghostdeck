package net.cybercoretech.ghostdeck.settings

private const val TAPS_NEEDED = 7
private const val TAP_WINDOW_MS = 3_000L
private const val HOLD_MS = 2_000L

/**
 * The two secret ways into Labs (spec 10a.7): seven taps on the version within 3 s, or Volume Down held for 2 s.
 * Both return true exactly once, at the moment of unlocking; [lock] re-arms them.
 */
class LabsUnlock(initiallyUnlocked: Boolean = false, private val clock: () -> Long) {
    private val taps = ArrayDeque<Long>()
    private var holdStart: Long? = null
    private var unlocked = initiallyUnlocked
    private var cancelTimer: (() -> Unit)? = null

    /**
     * Runs [action] after the delay and returns a cancel function. Set by the host (a main-thread handler), so
     * a single Volume Down press held for 2 s unlocks even when no key-repeat events arrive.
     */
    var scheduler: (delayMs: Long, action: () -> Unit) -> () -> Unit = { _, _ -> {} }

    /** Called when the hold timer, rather than a key event, completes the unlock. */
    var onHoldUnlocked: () -> Unit = {}

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
            cancelTimer?.invoke()
            cancelTimer = null
            return false
        }
        val now = clock()
        val start = holdStart ?: now.also {
            holdStart = it
            cancelTimer = scheduler(HOLD_MS) { if (holdStart != null && unlock()) onHoldUnlocked() }
        }
        return if (now - start >= HOLD_MS) unlock() else false
    }

    /** Drops a pending hold without touching the unlocked state (Settings closed, Home pressed). */
    fun cancelHold() {
        cancelTimer?.invoke()
        cancelTimer = null
        holdStart = null
    }

    /** Mirrors the persisted flag once prefs have loaded, so a restart does not unlock (and toast) again. */
    fun syncUnlocked(persisted: Boolean) {
        unlocked = persisted
    }

    fun lock() {
        cancelTimer?.invoke()
        cancelTimer = null
        unlocked = false
        taps.clear()
        holdStart = null
    }

    private fun unlock(): Boolean {
        taps.clear()
        holdStart = null
        cancelTimer = null
        if (unlocked) return false
        unlocked = true
        return true
    }
}
