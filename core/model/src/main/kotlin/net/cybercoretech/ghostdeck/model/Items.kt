package net.cybercoretech.ghostdeck.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** M5 widens this to the full Action type. */
@Serializable
sealed interface ItemAction

@Serializable
@SerialName("launchApp")
data class LaunchApp(val app: AppKey) : ItemAction

@Serializable
@SerialName("launchShortcut")
data class LaunchShortcut(val packageName: String, val shortcutId: String, val userSerial: Long) : ItemAction

/** The app's own long-press shortcuts. */
@Serializable
@SerialName("shortcutList")
data object ShowShortcuts : ItemAction

@Serializable
sealed interface Item {
    val id: String
}

@Serializable
@SerialName("app")
data class AppItem(
    override val id: String,
    val app: AppKey,
    val customLabel: String? = null,
    val swipeUp: ItemAction? = null,
) : Item

@Serializable
@SerialName("shortcut")
data class ShortcutItem(
    override val id: String,
    val packageName: String,
    val shortcutId: String,
    val userSerial: Long,
    val label: String,
    val swipeUp: ItemAction? = null,
) : Item

/** Children never contain a [FolderItem]. */
@Serializable
@SerialName("folder")
data class FolderItem(
    override val id: String,
    val name: String,
    val children: List<Item>,
) : Item
