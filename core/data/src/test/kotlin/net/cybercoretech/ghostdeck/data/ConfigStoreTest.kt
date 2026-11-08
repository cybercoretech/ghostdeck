package net.cybercoretech.ghostdeck.data

import java.io.File
import net.cybercoretech.ghostdeck.model.Deck
import net.cybercoretech.ghostdeck.model.GhostdeckConfig
import net.cybercoretech.ghostdeck.model.GhostdeckJson
import net.cybercoretech.ghostdeck.model.AppItem
import net.cybercoretech.ghostdeck.model.AppKey
import net.cybercoretech.ghostdeck.model.Layout
import net.cybercoretech.ghostdeck.model.Page
import net.cybercoretech.ghostdeck.model.Placed
import net.cybercoretech.ghostdeck.model.cell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        assertEquals("config.corrupt-1234.json", r.movedTo!!.name)
        assertEquals("{not json", r.movedTo!!.readText())
        assertFalse(s.file.exists())
    }

    @Test fun newerSchemaTreatedAsCorrupt() {
        val s = store()
        s.file.writeText(GhostdeckJson.encodeToString(GhostdeckConfig.serializer(), config().copy(schema = 2)))
        val r = s.load()
        assertTrue(r is LoadResult.Corrupt)
        assertFalse(s.file.exists())
        assertTrue((r as LoadResult.Corrupt).movedTo!!.exists())
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
        assertTrue((r as LoadResult.Corrupt).movedTo!!.exists())
    }

    @Test fun leftoverTmpIsIgnored() {
        val s = store()
        val c = config()
        s.save(c)
        File(tmp.root, "config.json.tmp").writeText("garbage")
        assertEquals(LoadResult.Loaded(c), s.load())
    }

    private fun assertCorruptAndMoved(s: ConfigStore, raw: String) {
        s.file.writeText(raw)
        val r = s.load()
        assertTrue("expected Corrupt, got $r", r is LoadResult.Corrupt)
        assertFalse(s.file.exists())
        assertEquals(raw, (r as LoadResult.Corrupt).movedTo!!.readText())
    }

    @Test fun emptyDecksTreatedAsCorrupt() {
        assertCorruptAndMoved(store(), """{"schema":1,"decks":[],"activeDeckId":"home"}""")
    }

    @Test fun unknownActiveDeckTreatedAsCorrupt() {
        val s = store()
        assertCorruptAndMoved(s, GhostdeckJson.encodeToString(GhostdeckConfig.serializer(), config().copy(activeDeckId = "gone")))
    }

    @Test fun duplicateItemIdsTreatedAsCorrupt() {
        val k = AppKey("com.a", "com.a.Main", 0)
        val layout = Layout(pages = listOf(Page(listOf(Placed(AppItem("x", k), cell(0, 0)), Placed(AppItem("x", k), cell(1, 0))))))
        val bad = config().copy(decks = listOf(Deck("main", "Main", "home", layout = layout)))
        assertCorruptAndMoved(store(), GhostdeckJson.encodeToString(GhostdeckConfig.serializer(), bad))
    }

    @Test fun moveFailureStillReportsCorrupt() {
        val s = store()
        s.file.writeText("{not json")
        tmp.root.setWritable(false)
        try {
            val r = s.load()
            assertTrue("expected Corrupt, got $r", r is LoadResult.Corrupt)
            assertNull("nothing was moved", (r as LoadResult.Corrupt).movedTo)
        } finally {
            tmp.root.setWritable(true)
        }
    }
}
