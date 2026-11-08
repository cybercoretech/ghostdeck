package net.cybercoretech.ghostdeck.theme

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** A palette in the org theme registry's 20-role format. Values are 6-digit hex without '#'. */
@Serializable
data class ThemePack(val id: String, val name: String, val roles: Map<String, String>)

object ThemeRoles {
    val REQUIRED: List<String> = listOf(
        "bg", "surface", "panel", "border", "line", "selection", "hover", "white", "muted", "text_primary",
        "accent_hot", "accent_alt", "acid_green", "hot_pink", "purple", "cyan", "orange", "red",
        "success", "warning", "error",
    )
}

private val HEX6 = Regex("^[0-9a-fA-F]{6}$")

/** Parses a registry theme JSON (a flat role to hex object). Fails on a missing role or a non-6-hex value. */
fun parseThemePack(id: String, name: String, json: String): Result<ThemePack> = runCatching {
    val obj: JsonObject = Json.parseToJsonElement(json).jsonObject
    val roles = ThemeRoles.REQUIRED.associateWith { role ->
        val value = (obj[role] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw IllegalArgumentException("Theme '$id' is missing role '$role'")
        require(HEX6.matches(value)) { "Theme '$id' role '$role' is not a 6-digit hex value: '$value'" }
        value.lowercase()
    }
    ThemePack(id, name, roles)
}
