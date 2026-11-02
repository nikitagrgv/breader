package ru.nikitagrgv.breader.ui.reader

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** "47m", "1h 12m", "4d 5h 7m": units that are zero are left out. Null under a minute. */
fun formatReadingTime(ms: Long): String? {
    val minutes = ms / 60_000
    if (minutes < 1) return null
    val days = minutes / (24 * 60)
    val hours = minutes / 60 % 24
    return buildList {
        if (days > 0) add("${days}d")
        if (hours > 0) add("${hours}h")
        if (minutes % 60 > 0) add("${minutes % 60}m")
    }.joinToString(" ")
}

/**
 * Reports how long it's composed while the app is resumed, i.e. shown and in use. Reported every
 * [REPORT_INTERVAL_MS], so the time shown keeps up and little is lost if the app is killed, and at the end.
 */
@Composable
internal fun ReadingTimeTracker(onReadingTime: (Long) -> Unit) {
    val currentOnReadingTime by rememberUpdatedState(onReadingTime)
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(Unit) {
        // Not the wall clock: it can jump when the time is set.
        var since = SystemClock.elapsedRealtime()
        fun report() {
            val now = SystemClock.elapsedRealtime()
            currentOnReadingTime(now - since)
            since = now
        }
        val job = scope.launch {
            while (isActive) {
                delay(REPORT_INTERVAL_MS)
                report()
            }
        }
        onPauseOrDispose {
            job.cancel()
            report()
        }
    }
}

private const val REPORT_INTERVAL_MS = 30_000L
