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

/** The deck named by [GhostdeckConfig.activeDeckId]; falls back to the first deck so a bad id can never crash home. */
fun GhostdeckConfig.activeDeck(): Deck = decks.firstOrNull { it.id == activeDeckId } ?: decks.first()

/**
 * Why this config can't be used, or null when it can. Checks what decoding can't: at least one deck,
 * an [GhostdeckConfig.activeDeckId] that names one of them, and item ids unique within each deck.
 */
fun GhostdeckConfig.validationError(): String? {
    if (decks.isEmpty()) return "no decks"
    if (decks.none { it.id == activeDeckId }) return "active deck '$activeDeckId' does not exist"
    for (deck in decks) {
        val l = deck.layout
        val ids = (l.pages.flatMap { p -> p.placed.map { it.item } } + l.dock.items.filterNotNull())
            .flatMap { if (it is FolderItem) listOf(it) + it.children else listOf(it) }
            .map { it.id }
        val dup = ids.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }
        if (dup != null) return "duplicate item id '${dup.key}' in deck '${deck.id}'"
    }
    return null
}

fun GhostdeckConfig.updateActiveLayout(f: (Layout) -> Layout): GhostdeckConfig =
    copy(decks = decks.map { if (it.id == activeDeckId) it.copy(layout = f(it.layout)) else it })
