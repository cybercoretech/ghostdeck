package net.cybercoretech.ghostdeck.icons

import android.content.ComponentName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParserFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppFilterParserTest {
    private fun parse(xml: String): Map<ComponentName, String> {
        val p = XmlPullParserFactory.newInstance().newPullParser()
        p.setInput(xml.reader())
        return AppFilterParser.parse(p)
    }

    private fun fixture(): Map<ComponentName, String> =
        parse(javaClass.classLoader!!.getResourceAsStream("appfilter.xml")!!.bufferedReader().readText())

    @Test fun parsesComponentInfoEntries() {
        val map = fixture()
        assertEquals("a_icon", map[ComponentName("com.a", "com.a.Main")])
        assertEquals("b_icon", map[ComponentName("com.b", "com.b.Short")])
    }

    @Test fun skipsMalformedEntries() {
        val map = fixture()
        assertEquals(setOf("com.a", "com.b", "com.d"), map.keys.map { it.packageName }.toSet())
        assertNull(map[ComponentName("com.c", "com.c.Main")])
        assertNull(map[ComponentName("com.c", "com.c.Other")])
    }

    @Test fun lastDuplicateWins() {
        assertEquals("d_last", fixture()[ComponentName("com.d", "com.d.Main")])
    }
}
