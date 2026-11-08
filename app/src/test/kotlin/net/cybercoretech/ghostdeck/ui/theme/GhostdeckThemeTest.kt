package net.cybercoretech.ghostdeck.ui.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import net.cybercoretech.ghostdeck.testutil.RobolectricMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GhostdeckThemeTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before fun animationsOff() = RobolectricMotion.disableAnimations(ctx)

    @Test fun typographyIsStableAcrossRecompositions() {
        val repo = ThemeRepository(ctx)
        val packs = repo.bundled()
        var pack by mutableStateOf(packs[0])
        val seen = mutableListOf<Typography>()
        rule.setContent { GhostdeckTheme(pack) { seen += MaterialTheme.typography } }
        pack = packs[1] // recomposes the theme with new tokens
        rule.waitForIdle()
        assertSame(seen.first(), seen.last())
    }

    @Test fun systemPackFollowsWallpaperColourChange() {
        var accent = Color(0xFF112233)
        val repo = ThemeRepository(ctx, dynamicScheme = { darkColorScheme(primary = accent) })
        var version by mutableIntStateOf(0)
        val seen = mutableListOf<Color>()
        rule.setContent { seen += tokensFor(rememberThemePack(repo, SYSTEM_PACK_ID, version)).accent }
        assertEquals(Color(0xFF112233), seen.last())

        accent = Color(0xFF445566) // the wallpaper changed; nothing else did
        version++ // what MainActivity does from the colours-changed callback
        rule.waitForIdle()
        assertEquals(Color(0xFF445566), seen.last())
    }
}
