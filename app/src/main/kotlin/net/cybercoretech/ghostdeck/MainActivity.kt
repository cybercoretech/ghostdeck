package net.cybercoretech.ghostdeck

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import net.cybercoretech.ghostdeck.home.HomeScreen
import net.cybercoretech.ghostdeck.home.HomeViewModel
import net.cybercoretech.ghostdeck.platform.expandNotificationShade
import net.cybercoretech.ghostdeck.ui.theme.ThemeRepository

class MainActivity : ComponentActivity() {
    private val _homePresses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when Home is pressed while Ghostdeck is already the foreground home screen. */
    val homePresses: SharedFlow<Unit> = _homePresses

    private val container get() = (application as GhostdeckApp).container

    private val viewModel: HomeViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(container.store, container.catalog, container.defaults, container.io) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // At the root of a launcher, Back does nothing.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val themes = remember { ThemeRepository(applicationContext) }
            HomeScreen(
                state = state,
                pack = remember(state.prefs.themePack) { themes.load(state.prefs.themePack) },
                launcher = container.launcher,
                icons = container.icons,
                homePresses = homePresses,
                onPageChanged = viewModel::goToPage,
                onDismissIssue = viewModel::dismissLoadIssue,
                onSwipeUp = {}, // Command Bar arrives in a later milestone
                onSwipeDown = { expandNotificationShade(this) },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val isHome = intent.action == Intent.ACTION_MAIN && Intent.CATEGORY_HOME in intent.categories.orEmpty()
        if (isHome && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            _homePresses.tryEmit(Unit)
        }
    }
}
