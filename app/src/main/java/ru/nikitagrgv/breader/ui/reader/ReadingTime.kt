package ru.nikitagrgv.breader.ui.reader

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.nikitagrgv.breader.R
import java.text.NumberFormat

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

/** "12,300" in English, "12 300" in Russian. */
fun formatCount(count: Number): String = NumberFormat.getIntegerInstance().format(count)

/** "12,300 / 72,000 words": how many words of a book are read. */
@Composable
fun wordsProgress(read: Int, total: Int): String =
    pluralStringResource(R.plurals.words_progress, total, formatCount(read.coerceIn(0, total)), formatCount(total))

/**
 * Reports how long it's composed while the app is resumed, i.e. shown and in use, and where in the book that time was
 * spent: each [percent] gets the time while the position was in it, so the time so far is reported whenever it changes.
 * Reported every [REPORT_INTERVAL_MS] too, so the time shown keeps up and little is lost if the app is killed, and at
 * the end.
 * @param percent where the reader is, see [ru.nikitagrgv.breader.data.percentOf]; null if it isn't known yet.
 */
@Composable
internal fun ReadingTimeTracker(percent: Int?, onReadingTime: (ms: Long, percent: Int?) -> Unit) {
    val currentOnReadingTime by rememberUpdatedState(onReadingTime)
    val clock = remember { ReadingClock() }
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(Unit) {
        clock.start()
        val job = scope.launch {
            while (isActive) {
                delay(REPORT_INTERVAL_MS)
                clock.report(currentOnReadingTime)
            }
        }
        onPauseOrDispose {
            job.cancel()
            clock.report(currentOnReadingTime)
            clock.stop()
        }
    }
    LaunchedEffect(percent) {
        if (clock.percent != percent) {
            clock.report(currentOnReadingTime)
            clock.percent = percent
        }
    }
}

private class ReadingClock {
    /** Since when the time hasn't been reported; null while the app isn't in use. Not the wall clock: it can jump. */
    private var since: Long? = null
    var percent: Int? = null

    fun start() {
        since = SystemClock.elapsedRealtime()
    }

    fun stop() {
        since = null
    }

    fun report(onReadingTime: (ms: Long, percent: Int?) -> Unit) {
        val start = since ?: return
        val now = SystemClock.elapsedRealtime()
        since = now
        onReadingTime(now - start, percent)
    }
}

private const val REPORT_INTERVAL_MS = 30_000L
