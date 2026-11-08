package net.cybercoretech.ghostdeck.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigSerializationTest {
    private val k = AppKey("p.a", "C", 0)

    private fun sample(): GhostdeckConfig {
        val items = listOf(
            Placed(AppItem("a", k, "Alpha", LaunchApp(AppKey("p.b", "C", 10))), cell(0, 0)),
            Placed(ShortcutItem("s", "p.c", "sc1", 0, "Short", LaunchShortcut("p.d", "sc2", 0)), Cell(3, 1)),
            Placed(
                FolderItem("f", "Fold", listOf(AppItem("c1", k, swipeUp = ShowShortcuts), AppItem("c2", AppKey("p.e", "C", 0)))),
                cell(2, 2),
            ),
        )
        val layout = Layout(
            pages = listOf(Page(items)),
            dock = Dock(listOf(AppItem("d", k), null, null)),
            hidden = setOf(AppKey("p.h", "C", 0)),
        )
        return GhostdeckConfig(decks = listOf(Deck("d1", "Home", "h", 0xFFA78BFAL, layout)), activeDeckId = "d1")
    }

    @Test fun roundTripsEveryItemAndActionSubtype() {
        val cfg = sample()
        val text = GhostdeckJson.encodeToString(cfg)
        assertEquals(cfg, GhostdeckJson.decodeFromString<GhostdeckConfig>(text))
    }

    @Test fun jsonShape() {
        val text = GhostdeckJson.encodeToString(sample())
        val root = Json.parseToJsonElement(text).jsonObject
        assertEquals("1", root["schema"]!!.jsonPrimitive.content)
        assertTrue(types(text).containsAll(setOf("app", "shortcut", "folder", "launchApp", "launchShortcut", "shortcutList")))
    }

    @Test fun ignoresUnknownKeys() {
        val text = GhostdeckJson.encodeToString(sample()).replaceFirst("{", "{\"futureField\": 1,")
        assertEquals(sample(), GhostdeckJson.decodeFromString<GhostdeckConfig>(text))
    }

    private fun types(text: String): Set<String> {
        val out = mutableSetOf<String>()
        fun walk(e: kotlinx.serialization.json.JsonElement) {
            when (e) {
                is JsonObject -> {
                    e["type"]?.jsonPrimitive?.content?.let(out::add)
                    e.values.forEach(::walk)
                }
                is kotlinx.serialization.json.JsonArray -> e.forEach(::walk)
                else -> Unit
            }
        }
        walk(Json.parseToJsonElement(text))
        return out
    }
}
