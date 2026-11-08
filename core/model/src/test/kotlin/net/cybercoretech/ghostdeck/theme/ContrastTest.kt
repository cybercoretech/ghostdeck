package net.cybercoretech.ghostdeck.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    private val black = 0x000000
    private val white = 0xFFFFFF

    @Test fun blackOnWhiteIs21() = assertEquals(21.0, Contrast.ratio(black, white), 0.001)

    @Test fun compositeFullAlphaIsTop() = assertEquals(0x0F0D1A, Contrast.composite(0x0F0D1A, 1f, white))

    @Test fun glassAlphaFindsLowestPassing() {
        val a = Contrast.glassAlpha(0xE7E3F4, 0x0F0D1A)
        assertNotNull(a)
        assertTrue(a!! in 0.55f..0.95f)
        // lowest: one step lower must fail (unless already at the floor)
        if (a > 0.55f) {
            val lower = a - 0.01f
            val ok = listOf(white, black).all { Contrast.ratio(0xE7E3F4, Contrast.composite(0x0F0D1A, lower, it)) >= 4.5 }
            assertTrue(!ok)
        }
    }

    @Test fun glassAlphaNullWhenImpossible() = assertNull(Contrast.glassAlpha(0x777777, 0x808080))
}
