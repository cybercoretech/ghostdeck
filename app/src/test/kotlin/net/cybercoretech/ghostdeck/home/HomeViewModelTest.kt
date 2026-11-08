package net.cybercoretech.ghostdeck.home

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.cybercoretech.ghostdeck.catalog.AppEntry
import net.cybercoretech.ghostdeck.catalog.CatalogEvent
import net.cybercoretech.ghostdeck.catalog.ProfileKind
import net.cybercoretech.ghostdeck.data.ConfigStore
import net.cybercoretech.ghostdeck.data.LoadResult
import net.cybercoretech.ghostdeck.edit.DragSource
import net.cybercoretech.ghostdeck.edit.DropZone
import net.cybercoretech.ghostdeck.edit.EditController
import net.cybercoretech.ghostdeck.model.AppItem
import net.cybercoretech.ghostdeck.model.Target
import net.cybercoretech.ghostdeck.model.AppKey
import net.cybercoretech.ghostdeck.model.Deck
import net.cybercoretech.ghostdeck.model.Dock
import net.cybercoretech.ghostdeck.model.GhostdeckConfig
import net.cybercoretech.ghostdeck.model.Layout
import net.cybercoretech.ghostdeck.model.Page
import net.cybercoretech.ghostdeck.model.Placed
import net.cybercoretech.ghostdeck.model.allAppKeys
import net.cybercoretech.ghostdeck.model.cell
import net.cybercoretech.ghostdeck.platform.DefaultAppsResolver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule val tmp = TemporaryFolder()
    private val dispatcher = UnconfinedTestDispatcher()
    private val catalog = FakeCatalog()
    private val phone = AppKey("com.phone", "com.phone.Main", 0)
    private val maps = AppKey("com.maps", "com.maps.Main", 0)
    private val vault = AppKey("com.vault", "com.vault.Main", 10)
    private lateinit var store: ConfigStore

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = ConfigStore(File(tmp.root, "config"))
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(defaults: List<AppKey> = listOf(phone)) =
        HomeViewModel(store, catalog, DefaultAppsResolver { defaults }, dispatcher)

    private fun entry(key: AppKey, profile: ProfileKind = ProfileKind.PERSONAL) = AppEntry(key, key.packageName, profile)

    private fun seed(layout: Layout) {
        store.save(GhostdeckConfig(decks = listOf(Deck("home", "Home", "home", layout = layout)), activeDeckId = "home"))
    }

    private fun saved(): Layout =
        ((store.load() as LoadResult.Loaded).config.decks.single().layout)

    @Test fun missingConfigCreatesDefaultAndSaves() {
        val vm = vm()
        val dock = vm.state.value.layout.dock.items
        assertEquals(phone, (dock.first() as AppItem).app)
        assertNull(vm.state.value.loadIssue)
        assertEquals(vm.state.value.layout, saved())
    }

    @Test fun corruptConfigSurfacesLoadIssue() {
        store.file.parentFile!!.mkdirs()
        store.file.writeText("{ not json")
        val vm = vm()
        assertNotNull(vm.state.value.loadIssue)
        assertEquals(phone, (vm.state.value.layout.dock.items.first() as AppItem).app)
        assertTrue("corrupt file is kept aside", store.file.parentFile!!.listFiles()!!.any { it.name.startsWith("config.corrupt-") })
    }

    @Test fun dismissLoadIssueClearsIt() {
        store.file.parentFile!!.mkdirs()
        store.file.writeText("{ not json")
        val vm = vm()
        assertTrue(vm.state.value.loadIssue!!.contains("config.corrupt-"))
        vm.dismissLoadIssue()
        assertNull(vm.state.value.loadIssue)
    }

    @Test fun packageRemovedUpdatesAndPersists() {
        seed(Layout(pages = listOf(Page(listOf(Placed(AppItem("a", maps), cell(1, 1)))))))
        val vm = vm()
        catalog.flow.tryEmit(CatalogEvent.Snapshot(0, ProfileKind.PERSONAL, listOf(entry(maps))))
        assertEquals(setOf(maps), vm.state.value.layout.allAppKeys())
        catalog.flow.tryEmit(CatalogEvent.PackageRemoved(0, "com.maps"))
        assertTrue(vm.state.value.layout.allAppKeys().isEmpty())
        assertTrue(saved().allAppKeys().isEmpty())
    }

    @Test fun privateProfileLockKeepsItems() {
        seed(Layout(
            pages = listOf(Page(listOf(Placed(AppItem("v", vault), cell(0, 0))))),
            dock = Dock(listOf(AppItem("dv", vault), null, null)),
        ))
        val vm = vm()
        catalog.flow.tryEmit(CatalogEvent.Snapshot(10, ProfileKind.PRIVATE, listOf(entry(vault, ProfileKind.PRIVATE))))
        catalog.flow.tryEmit(CatalogEvent.ProfileAvailability(10, false))
        assertEquals(setOf(10L), vm.state.value.unavailableUsers)
        assertEquals(setOf(vault), vm.state.value.layout.allAppKeys())
        assertEquals(setOf(vault), saved().allAppKeys())
        assertTrue(vm.state.value.layout.dock.items.first() is AppItem)
    }

    @Test fun updateLayoutPersistsToActiveDeck() {
        val vm = vm()
        vm.updateLayout { it.copy(pages = it.pages + Page()) }
        assertEquals(2, vm.state.value.layout.pages.size)
        assertEquals(2, saved().pages.size)
    }

    @Test fun packageRemovedDuringDragIsNotResurrected() {
        val dragged = AppItem("a", phone)
        seed(Layout(pages = listOf(Page(listOf(Placed(dragged, cell(0, 0)), Placed(AppItem("b", maps), cell(1, 1)))))))
        val vm = vm()
        catalog.flow.tryEmit(CatalogEvent.Snapshot(0, ProfileKind.PERSONAL, listOf(entry(phone), entry(maps))))

        val controller = EditController(vm.state.value.layout)
        controller.startDrag(DragSource.Existing("a"))
        assertTrue(controller.hover(DropZone.OnTarget(Target.Grid(0, cell(3, 3)))))
        val commit = controller.pendingTransform()

        catalog.flow.tryEmit(CatalogEvent.PackageRemoved(0, "com.maps"))
        controller.drop()
        vm.updateLayout(commit)

        assertEquals(setOf(phone), vm.state.value.layout.allAppKeys())
        assertEquals(setOf(phone), saved().allAppKeys())
        assertEquals(cell(3, 3), saved().pages[0].placed.single().cell)
    }
}
