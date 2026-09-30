package org.ekatra.alfred.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import org.ekatra.alfred.R
import org.ekatra.alfred.persona.ActivePersona

/** Applies the persona's reading direction to everything below it. Wrap each activity's content once. */
@Composable
fun PersonaDirection(content: @Composable () -> Unit) {
    val direction = if (ActivePersona.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction, content = content)
}

/** Back navigation icon that points the right way in both LTR and RTL layouts. */
@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
    }
}
