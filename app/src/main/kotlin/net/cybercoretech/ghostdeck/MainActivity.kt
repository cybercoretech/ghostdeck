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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.cybercoretech.ghostdeck.ui.theme.AmbientOrbs
import net.cybercoretech.ghostdeck.ui.theme.DEFAULT_PACK_ID
import net.cybercoretech.ghostdeck.ui.theme.GhostdeckFonts
import net.cybercoretech.ghostdeck.ui.theme.GhostdeckTheme
import net.cybercoretech.ghostdeck.ui.theme.GlassSurface
import net.cybercoretech.ghostdeck.ui.theme.LocalMotionPolicy
import net.cybercoretech.ghostdeck.ui.theme.LocalTokens
import net.cybercoretech.ghostdeck.ui.theme.ThemeRepository
import net.cybercoretech.ghostdeck.ui.theme.materialize
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
            val pack = remember { ThemeRepository(applicationContext).load(DEFAULT_PACK_ID) }
            GhostdeckTheme(pack) {
                val tokens = LocalTokens.current
                Box(Modifier.fillMaxSize().background(tokens.ground)) {
                    AmbientOrbs(Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val policy = LocalMotionPolicy.current
                        GlassSurface(Modifier.materialize(visible = true, reduceMotion = policy.reduceMotion)) {
                            Text(
                                "Ghostdeck",
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 20.dp),
                                color = tokens.text,
                                fontFamily = GhostdeckFonts.display,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 28.sp,
                            )
                        }
                    }
                }
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
