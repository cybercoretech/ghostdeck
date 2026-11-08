package net.cybercoretech.ghostdeck.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Shared by tests and the config store. */
val GhostdeckJson: Json = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

@Serializable
data class Deck(
    val id: String,
    val name: String,
    val icon: String,
    val accentArgb: Long? = null,
    val layout: Layout,
)

@Serializable
data class Prefs(
    val iconPack: String? = null,
    val badges: Boolean = false,
    val onboardingDone: Boolean = false,
    val themePack: String = "ghostdeck",
    val fineGrid: Boolean = false,
    val swipeMarks: Boolean = true,
    val labsUnlocked: Boolean = false,
    /** Labs: outlines every grid cell on the home screen. */
    val debugGridBounds: Boolean = false,
)

@Serializable
data class GhostdeckConfig(
    val schema: Int = 1,
    val decks: List<Deck>,
    val activeDeckId: String,
    val prefs: Prefs = Prefs(),
)

fun GhostdeckConfig.activeDeck(): Deck = decks.first { it.id == activeDeckId }

fun GhostdeckConfig.updateActiveLayout(f: (Layout) -> Layout): GhostdeckConfig =
    copy(decks = decks.map { if (it.id == activeDeckId) it.copy(layout = f(it.layout)) else it })
