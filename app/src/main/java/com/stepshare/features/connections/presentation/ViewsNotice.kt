package com.Consnow.features.connections.presentation

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

const val NOTICE_VISIBLE_MS = 6_000L

/** One yes/no flag that survives app restarts. */
interface FlagStore {
    fun isSet(): Boolean
    fun set()
}

class PrefsFlagStore(context: Context, private val key: String) : FlagStore {
    private val prefs = context.applicationContext.getSharedPreferences("consnow_notices", Context.MODE_PRIVATE)
    override fun isSet() = prefs.getBoolean(key, false)
    override fun set() { prefs.edit().putBoolean(key, true).apply() }
}

/** Lets the notice through exactly once, ever. */
class ViewsNoticeGate(private val store: FlagStore) {
    fun consume(): Boolean {
        if (store.isSet()) return false
        store.set()
        return true
    }
}

/** A short, one-time heads-up that friends can see when you open their profile. */
@Composable
fun ViewsNotice(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (ViewsNoticeGate(PrefsFlagStore(context, "views_notice_seen")).consume()) {
            visible = true
            delay(NOTICE_VISIBLE_MS)
            visible = false
        }
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = "Heads up: friends you connect with can see when you open their profile.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}
