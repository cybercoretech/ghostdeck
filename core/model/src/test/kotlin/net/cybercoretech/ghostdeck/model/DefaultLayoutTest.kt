package net.cybercoretech.ghostdeck.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultLayoutTest {
    private fun key(n: Int) = AppKey("pkg.$n", "pkg.$n.Main", 0)

    @Test fun fourAppsFillFirstFourOfFiveDockSlots() {
        val layout = defaultLayout((1..4).map(::key))
        assertEquals(5, layout.dock.items.size)
        assertEquals((1..4).map(::key), layout.dock.items.take(4).map { (it as AppItem).app })
        assertNull(layout.dock.items[4])
        assertTrue(layout.pages.single().placed.isEmpty())
    }

    @Test fun dockTakesAtMostFive() {
        val layout = defaultLayout((1..8).map(::key))
        assertEquals((1..5).map(::key), layout.dock.items.map { (it as AppItem).app })
    }
}
