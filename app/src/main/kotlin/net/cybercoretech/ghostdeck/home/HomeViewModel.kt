package net.cybercoretech.ghostdeck.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.cybercoretech.ghostdeck.catalog.AppCatalog
import net.cybercoretech.ghostdeck.catalog.CatalogEvent
import net.cybercoretech.ghostdeck.catalog.CatalogState
import net.cybercoretech.ghostdeck.catalog.reconcile
import net.cybercoretech.ghostdeck.catalog.resolveStale
import net.cybercoretech.ghostdeck.data.ConfigStore
import net.cybercoretech.ghostdeck.data.LoadResult
import net.cybercoretech.ghostdeck.model.Deck
import net.cybercoretech.ghostdeck.model.GhostdeckConfig
import net.cybercoretech.ghostdeck.model.Layout
import net.cybercoretech.ghostdeck.model.activeDeck
import net.cybercoretech.ghostdeck.model.defaultLayout
import net.cybercoretech.ghostdeck.model.updateActiveLayout
import net.cybercoretech.ghostdeck.platform.DefaultAppsResolver

/** Config and catalog state are touched only on [io], which must be single-threaded. */
class HomeViewModel(
    private val store: ConfigStore,
    catalog: AppCatalog,
    private val defaults: DefaultAppsResolver,
    private val io: CoroutineDispatcher,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState(Layout(), emptyMap(), emptySet()))
    val state: StateFlow<HomeUiState> = _state

    private var config: GhostdeckConfig? = null
    private var catalogState = CatalogState()

    init {
        viewModelScope.launch(io) {
            load()
            catalog.events.collect(::onCatalogEvent)
        }
    }

    private fun load() {
        when (val result = store.load()) {
            is LoadResult.Loaded -> publish(result.config, null)
            LoadResult.Missing -> publish(freshConfig(), null, save = true)
            is LoadResult.Corrupt -> publish(freshConfig(), "Config was unreadable (${result.reason}); kept as ${result.movedTo.name}", save = true)
        }
    }

    private fun freshConfig(): GhostdeckConfig = GhostdeckConfig(
        decks = listOf(Deck("home", "Home", "home", layout = defaultLayout(defaults.resolve()))),
        activeDeckId = "home",
    )

    private fun publish(next: GhostdeckConfig, issue: String?, save: Boolean = false) {
        config = next
        var problem = issue
        if (save) runCatching { store.save(next) }.onFailure { problem = "Could not save: ${it.message}" }
        _state.update {
            it.copy(
                layout = next.activeDeck().layout, prefs = next.prefs, loadIssue = problem ?: it.loadIssue,
                apps = catalogState.entries, unavailableUsers = catalogState.unavailableUsers,
            )
        }
    }

    private fun onCatalogEvent(event: CatalogEvent) {
        catalogState = catalogState.reduce(event)
        val current = config
        if (current == null) return
        val before = current.activeDeck().layout
        val after = before.reconcile(event, catalogState).resolveStale(catalogState)
        if (after != before) publish(current.updateActiveLayout { after }, null, save = true)
        else _state.update { it.copy(apps = catalogState.entries, unavailableUsers = catalogState.unavailableUsers) }
    }

    /** Applies [f] to the active deck's layout and persists it. */
    fun updateLayout(f: (Layout) -> Layout) {
        viewModelScope.launch(io) {
            val current = config ?: return@launch
            publish(current.updateActiveLayout(f), null, save = true)
        }
    }

    fun setEditing(on: Boolean) = _state.update { it.copy(editing = on) }

    fun goToPage(i: Int) = _state.update { it.copy(currentPage = i) }
}
