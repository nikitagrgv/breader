package ru.nikitagrgv.breader.translation

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** A request kept in [UsageLog]; [id]s grow with time. */
data class LoggedRequest(val id: Long, val usage: RequestUsage)

/** The number of the requests in [UsageLog] and what they cost in all, in USD. */
data class UsageTotals(val count: Int, val cost: Double)

/**
 * The billed requests to the models, the latest [MAX_SIZE] of them, kept across launches. OpenRouter has no list
 * of requests that an ordinary key may see, so the app keeps its own.
 */
class UsageLog(context: Context) {
    private val helper = object : SQLiteOpenHelper(context.applicationContext, "usage.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE requests (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    time INTEGER NOT NULL,
                    kind TEXT NOT NULL,
                    fragment TEXT NOT NULL,
                    model TEXT NOT NULL,
                    prompt_tokens INTEGER NOT NULL,
                    completion_tokens INTEGER NOT NULL,
                    cost REAL
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private val _changes = MutableStateFlow(0)

    /** Changes each time the log does. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    suspend fun add(usage: RequestUsage) {
        io("add a request") {
            val values = ContentValues().apply {
                put("time", usage.time)
                put("kind", usage.kind.name)
                put("fragment", usage.fragment.take(MAX_FRAGMENT_LENGTH))
                put("model", usage.model)
                put("prompt_tokens", usage.promptTokens)
                put("completion_tokens", usage.completionTokens)
                put("cost", usage.cost)
            }
            val db = helper.writableDatabase
            db.insertOrThrow("requests", null, values)
            db.execSQL("DELETE FROM requests WHERE id <= (SELECT id FROM requests ORDER BY id DESC LIMIT 1 OFFSET $MAX_SIZE)")
        }
        _changes.update { it + 1 }
    }

    /** The latest [count] requests, the latest first. */
    suspend fun latest(count: Int): List<LoggedRequest> = io("read the requests") {
        helper.readableDatabase.rawQuery(
            "SELECT id, time, kind, fragment, model, prompt_tokens, completion_tokens, cost FROM requests ORDER BY id DESC LIMIT $count",
            null,
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) cursor.toRequest()?.let(::add) }
        }
    } ?: emptyList()

    suspend fun totals(): UsageTotals = io("count the requests") {
        helper.readableDatabase.rawQuery("SELECT COUNT(*), TOTAL(cost) FROM requests", null).use { cursor ->
            cursor.moveToFirst()
            UsageTotals(cursor.getInt(0), cursor.getDouble(1))
        }
    } ?: UsageTotals(0, 0.0)

    suspend fun clear() {
        io("clear the requests") { helper.writableDatabase.delete("requests", null, null) }
        _changes.update { it + 1 }
    }

    private fun Cursor.toRequest(): LoggedRequest? {
        // Unknown if it was written by a later version of the app.
        val kind = RequestKind.entries.find { it.name == getString(2) } ?: return null
        return LoggedRequest(
            id = getLong(0),
            usage = RequestUsage(
                time = getLong(1),
                kind = kind,
                fragment = getString(3),
                model = getString(4),
                promptTokens = getInt(5),
                completionTokens = getInt(6),
                cost = if (isNull(7)) null else getDouble(7),
            ),
        )
    }

    /** Null if [block] fails: the log is a nicety, it isn't worth failing a translation or the settings for. */
    private suspend fun <T> io(what: String, block: () -> T): T? = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: SQLException) {
            Log.e(TAG, "Failed to $what", e)
            null
        }
    }

    companion object {
        private const val TAG = "UsageLog"

        const val MAX_SIZE = 10_000

        /** A selection may be a whole page: the start is enough to tell it. */
        private const val MAX_FRAGMENT_LENGTH = 200
    }
}
