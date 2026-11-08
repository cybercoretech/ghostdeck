package net.cybercoretech.ghostdeck.theme

import kotlin.math.pow
import kotlin.math.roundToInt

/** WCAG contrast helpers on packed 0xRRGGBB ints. */
object Contrast {
    private const val WHITE = 0xFFFFFF
    private const val BLACK = 0x000000
    private const val MIN_RATIO = 4.5

    fun luminance(rgb: Int): Double {
        fun lin(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * lin((rgb shr 16) and 0xFF) + 0.7152 * lin((rgb shr 8) and 0xFF) + 0.0722 * lin(rgb and 0xFF)
    }

    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** [top] drawn at [alpha] over [under]. */
    fun composite(top: Int, alpha: Float, under: Int): Int {
        fun ch(shift: Int): Int {
            val t = (top shr shift) and 0xFF
            val u = (under shr shift) and 0xFF
            return (t * alpha + u * (1f - alpha)).roundToInt().coerceIn(0, 255)
        }
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /**
     * Lowest surface opacity in 0.55..0.95 (0.01 steps) at which [text] reaches 4.5:1 on [surface]
     * composited over both pure white and pure black (the worst-case wallpapers). Null means the pack fails.
     */
    fun glassAlpha(text: Int, surface: Int): Float? {
        for (pct in 55..95) {
            val a = pct / 100f
            if (ratio(text, composite(surface, a, WHITE)) >= MIN_RATIO && ratio(text, composite(surface, a, BLACK)) >= MIN_RATIO) return a
        }
        return null
    }
}
