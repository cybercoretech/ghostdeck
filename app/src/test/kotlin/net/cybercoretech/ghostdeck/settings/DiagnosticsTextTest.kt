package net.cybercoretech.ghostdeck.settings

import net.cybercoretech.ghostdeck.model.GridSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DiagnosticsTextTest {
    @Test fun containsOnlyAllowedFields() {
        val text = diagnosticsText("0.1.0", "15", 35, GridSize(5, 6), "ghostdeck", null)
        assertEquals(
            "Ghostdeck 0.1.0\nAndroid 15 (API 35)\nGrid 5x6\nTheme pack ghostdeck\nIcon pack system\n",
            text,
        )
        assertFalse(text.contains("dock", ignoreCase = true))
        assertFalse(text.contains("com."))
    }

    @Test fun namesTheSelectedIconPack() {
        assertEquals(true, diagnosticsText("1", "14", 34, GridSize(4, 5), "system", "org.example.pack").contains("Icon pack org.example.pack"))
    }
}
