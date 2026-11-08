package net.cybercoretech.ghostdeck.icons

import android.content.ComponentName
import org.xmlpull.v1.XmlPullParser

/** Reads the `<item component="ComponentInfo{pkg/cls}" drawable="name"/>` entries of an ADW/Nova appfilter. */
object AppFilterParser {
    private const val PREFIX = "ComponentInfo{"

    /** Drawable name per component. Malformed entries are skipped; a later duplicate replaces an earlier one. */
    fun parse(parser: XmlPullParser): Map<ComponentName, String> {
        val out = LinkedHashMap<ComponentName, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                val component = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                if (!drawable.isNullOrBlank()) component?.let(::componentOf)?.let { out[it] = drawable }
            }
            event = parser.next()
        }
        return out
    }

    private fun componentOf(raw: String): ComponentName? {
        if (!raw.startsWith(PREFIX) || !raw.endsWith("}")) return null
        val flat = raw.substring(PREFIX.length, raw.length - 1)
        if ('/' !in flat) return null
        return ComponentName.unflattenFromString(flat)
    }
}
