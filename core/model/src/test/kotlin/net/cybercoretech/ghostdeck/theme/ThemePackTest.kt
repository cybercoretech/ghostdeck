package net.cybercoretech.ghostdeck.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePackTest {
    private fun json(skip: String? = null, override: Pair<String, String>? = null): String =
        ThemeRoles.REQUIRED.filter { it != skip }.joinToString(",", "{", "}") {
            val v = if (override?.first == it) override.second else "0f0d1a"
            "\"$it\": \"$v\""
        }

    @Test fun parsesRegistryJson() {
        val pack = parseThemePack("x", "X", json()).getOrThrow()
        assertEquals("x", pack.id)
        assertEquals("X", pack.name)
        assertEquals(ThemeRoles.REQUIRED.size, pack.roles.size)
        assertEquals("0f0d1a", pack.roles["bg"])
    }

    @Test fun missingRoleFails() = assertTrue(parseThemePack("x", "X", json(skip = "accent_hot")).isFailure)

    @Test fun badHexFails() {
        assertTrue(parseThemePack("x", "X", json(override = "bg" to "#07060d")).isFailure)
        assertTrue(parseThemePack("x", "X", json(override = "bg" to "zzzzzz")).isFailure)
        assertTrue(parseThemePack("x", "X", "not json").isFailure)
    }
}
