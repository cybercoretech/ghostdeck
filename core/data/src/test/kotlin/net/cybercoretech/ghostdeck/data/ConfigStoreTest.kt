package net.cybercoretech.ghostdeck.data

import java.io.File
import net.cybercoretech.ghostdeck.model.Deck
import net.cybercoretech.ghostdeck.model.GhostdeckConfig
import net.cybercoretech.ghostdeck.model.GhostdeckJson
import net.cybercoretech.ghostdeck.model.Layout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConfigStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun config() = GhostdeckConfig(
        decks = listOf(Deck(id = "main", name = "Main", icon = "home", layout = Layout())),
        activeDeckId = "main",
    )

    private fun store() = ConfigStore(tmp.root, clock = { 1234L })

    @Test fun missingReturnsMissing() {
        assertEquals(LoadResult.Missing, store().load())
    }

    @Test fun saveThenLoadRoundTrips() {
        val s = store()
        val c = config()
        s.save(c)
        assertEquals(LoadResult.Loaded(c), s.load())
        assertFalse(File(tmp.root, "config.json.tmp").exists())
    }

    @Test fun corruptFileIsMovedAsideAndReported() {
        val s = store()
        s.file.writeText("{not json")
        val r = s.load()
        assertTrue(r is LoadResult.Corrupt)
        r as LoadResult.Corrupt
        assertEquals("config.corrupt-1234.json", r.movedTo.name)
        assertEquals("{not json", r.movedTo.readText())
        assertFalse(s.file.exists())
    }

    @Test fun newerSchemaTreatedAsCorrupt() {
        val s = store()
        s.file.writeText(GhostdeckJson.encodeToString(GhostdeckConfig.serializer(), config().copy(schema = 2)))
        val r = s.load()
        assertTrue(r is LoadResult.Corrupt)
        assertFalse(s.file.exists())
        assertTrue((r as LoadResult.Corrupt).movedTo.exists())
    }

    @Test fun invalidGridValuesTreatedAsCorrupt() {
        val s = store()
        val json = GhostdeckJson.encodeToString(GhostdeckConfig.serializer(), config())
            .replace("\"cols\": 5", "\"cols\": 99")
        assertTrue(json.contains("\"cols\": 99"))
        s.file.writeText(json)
        val r = s.load()
        assertTrue(r is LoadResult.Corrupt)
        assertFalse(s.file.exists())
        assertTrue((r as LoadResult.Corrupt).movedTo.exists())
    }

    @Test fun leftoverTmpIsIgnored() {
        val s = store()
        val c = config()
        s.save(c)
        File(tmp.root, "config.json.tmp").writeText("garbage")
        assertEquals(LoadResult.Loaded(c), s.load())
    }
}
