package net.cybercoretech.ghostdeck.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.cybercoretech.ghostdeck.R
import net.cybercoretech.ghostdeck.theme.ThemePack
import net.cybercoretech.ghostdeck.ui.components.GhostButton
import net.cybercoretech.ghostdeck.ui.theme.GhostdeckFonts
import net.cybercoretech.ghostdeck.ui.theme.GhostdeckTheme
import net.cybercoretech.ghostdeck.ui.theme.LocalTokens

/**
 * First run: the Privacy Charter, then an offer to become the home screen. Neither step requests a permission;
 * the home-role prompt only appears when the user taps "Set as home".
 *
 * [onSetAsHome] starts the system role request and calls [onFinish] when it is answered (or at once if there is
 * nothing to ask). [onFinish] is also what "Not now" calls.
 */
@Composable
fun OnboardingScreen(pack: ThemePack, onSetAsHome: () -> Unit, onFinish: () -> Unit) {
    GhostdeckTheme(pack) {
        val tokens = LocalTokens.current
        var step by rememberSaveable { mutableStateOf(0) }
        Column(
            Modifier.fillMaxSize().background(tokens.ground).windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (step == 0) {
                Text(
                    stringResource(R.string.charter_title), color = tokens.accent,
                    fontFamily = GhostdeckFonts.display, fontSize = 28.sp,
                )
                stringArrayResource(R.array.charter_lines).forEachIndexed { i, line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${i + 1}", color = tokens.accentAlt, fontFamily = GhostdeckFonts.mono, fontSize = 14.sp)
                        Text(line, color = tokens.text, fontFamily = GhostdeckFonts.ui, fontSize = 15.sp, lineHeight = 21.sp)
                    }
                }
                GhostButton("Continue", { step = 1 }, Modifier.fillMaxWidth(), tag = "onboarding-continue", filled = true)
            } else {
                Text(
                    "Make Ghostdeck your home screen", color = tokens.accent,
                    fontFamily = GhostdeckFonts.display, fontSize = 28.sp,
                )
                Text(
                    "Android will ask you to confirm. You can change this any time in system settings.",
                    color = tokens.text, fontFamily = GhostdeckFonts.ui, fontSize = 15.sp, lineHeight = 21.sp,
                )
                GhostButton("Set as home", onSetAsHome, Modifier.fillMaxWidth(), tag = "onboarding-set-home", filled = true)
                GhostButton("Not now", onFinish, Modifier.fillMaxWidth(), tag = "onboarding-not-now")
            }
        }
    }
}
