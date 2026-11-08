package net.cybercoretech.ghostdeck.icons

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class IconPack(val packageName: String, val label: String)

/** Finds installed ADW/Nova icon packs and parses their appfilter locally. No network involved. */
open class IconPackRepository(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    fun installed(): List<IconPack> = ACTIONS
        .flatMap { pm.queryIntentActivities(Intent(it), 0) }
        .map { it.activityInfo.packageName }
        .distinct()
        .map { IconPack(it, runCatching { pm.getApplicationInfo(it, 0).loadLabel(pm).toString() }.getOrDefault(it)) }
        .sortedBy { it.label.lowercase() }

    /** `res/xml/appfilter`, else `assets/appfilter.xml`; empty if the pack is gone or unreadable. */
    @Suppress("DiscouragedApi") // appfilter is looked up by name by design
    open fun load(pack: String): Map<ComponentName, String> = runCatching {
        val res = pm.getResourcesForApplication(pack)
        val id = res.getIdentifier("appfilter", "xml", pack)
        if (id != 0) res.getXml(id).use { AppFilterParser.parse(it) } else fromAssets(res)
    }.getOrDefault(emptyMap())

    private fun fromAssets(res: Resources): Map<ComponentName, String> =
        res.assets.open("appfilter.xml").use { stream ->
            val parser: XmlPullParser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(stream, null)
            AppFilterParser.parse(parser)
        }

    private companion object {
        val ACTIONS = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME")
    }
}
