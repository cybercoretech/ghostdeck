package net.cybercoretech.ghostdeck.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import net.cybercoretech.ghostdeck.theme.Contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeAssetsTest {
    private val repo = ThemeRepository(ApplicationProvider.getApplicationContext<Context>())

    private fun rgb(hex: String) = hex.toInt(16)

    @Test fun allBundledPacksParse() {
        val packs = repo.bundled()
        assertEquals(4, packs.size)
        assertEquals(setOf("ghostdeck", "cybercore-vortex-synthetic", "terminal-ghost-alpha", "cryo-terminal-blue-synthetic"), packs.map { it.id }.toSet())
    }

    @Test fun allBundledPacksPassContrast() {
        for (p in repo.bundled()) {
            assertNotNull("${p.id} fails contrast", Contrast.glassAlpha(rgb(p.roles.getValue("text_primary")), rgb(p.roles.getValue("surface"))))
        }
    }

    @Test fun unknownIdFallsBackToGhostdeck() = assertEquals("ghostdeck", repo.load("nope").id)

    @Test fun deckAccentOverridesPackAccent() {
        val pack = repo.load("ghostdeck")
        val deck = Color(0xFFFF0000)
        assertEquals(deck, tokensFor(pack, deck).accent)
        assertEquals(Color(0xFFA78BFA), tokensFor(pack).accent)
        assertEquals(Color(0xFF2DD4BF), tokensFor(pack, deck).accentAlt)
    }

    @Test fun availableIncludesSystemOnApi31() {
        assertEquals("system", repo.available().last().id)
    }

    @Test fun systemPackPassesContrastOrIsFlagged() {
        val system = repo.available().last()
        val measured = Contrast.glassAlpha(rgb(system.roles.getValue("text_primary")), rgb(system.roles.getValue("surface")))
        val tokens = tokensFor(system)
        if (measured != null) assertEquals(measured, tokens.glassAlpha, 0f)
        else assertTrue("a failing pack must be reported through contrastFallback", tokens.contrastFallback)
        assertEquals(measured == null, tokens.contrastFallback)
    }
}
