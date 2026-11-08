package net.cybercoretech.ghostdeck.home

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLabelStyleTest {
    @Test fun labelCarriesDarkShadowForLightWallpapers() {
        val style = homeLabelStyle(Color(0xFFE7E3F4))
        assertNotNull("label has a shadow", style.shadow)
        val shadow = style.shadow!!
        assertTrue("shadow is dark", shadow.color.red < 0.2f && shadow.color.alpha >= 0.5f)
        assertTrue("shadow is soft", shadow.blurRadius > 0f)
    }
}
