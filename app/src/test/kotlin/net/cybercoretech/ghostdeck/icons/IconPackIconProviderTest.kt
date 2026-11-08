package net.cybercoretech.ghostdeck.icons

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import net.cybercoretech.ghostdeck.home.FakeIcons
import net.cybercoretech.ghostdeck.model.AppKey
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IconPackIconProviderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val key = AppKey("com.a", "com.a.Main", 0)

    @Test fun noPackSelectedPassesThroughToBase() {
        val base = FakeIcons()
        val provider = IconPackIconProvider(base, IconPackRepository(context), context, packProvider = { null })
        assertEquals(48, provider.icon(key, 48).width)
    }

    @Test fun missingPackFallsBackToBaseIcon() {
        val base = FakeIcons()
        val provider = IconPackIconProvider(base, IconPackRepository(context), context, packProvider = { "com.not.installed" })
        assertEquals(48, provider.icon(key, 48).width)
    }
}
