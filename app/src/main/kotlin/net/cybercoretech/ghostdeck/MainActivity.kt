package net.cybercoretech.ghostdeck

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class MainActivity : ComponentActivity() {
    private val _homePresses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when Home is pressed while Ghostdeck is already the foreground home screen. */
    val homePresses: SharedFlow<Unit> = _homePresses

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // At the root of a launcher, Back does nothing.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        setContent {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Ghostdeck", color = Color.White)
            }
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
