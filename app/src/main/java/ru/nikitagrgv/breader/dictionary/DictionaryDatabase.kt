package ru.nikitagrgv.breader.dictionary

import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import java.io.Closeable
import java.io.File

/*
 * A dictionary is a SQLite file:
 * - entries: the words with their senses as JSON;
 * - forms: what the entries are found by, i.e. the keys of their words and of all their forms;
 * - meta: what the dictionary is, see DictionaryMeta.
 */

/** Keys of [DictionaryWriter.finish]'s meta. */
internal object DictionaryMeta {
    const val NAME = "name"
    const val EDITION = "edition"
    const val LANGUAGE_CODE = "language_code"
    const val ENTRIES = "entries"
    const val CREATED = "created"
    const val SOURCE = "source"
}

/** Makes a dictionary file out of Wiktionary entries; the file is complete after [finish]. */
internal class DictionaryWriter(file: File) : Closeable {
    private val db = SQLiteDatabase.openOrCreateDatabase(file, null)
    private val insertEntry = run {
        db.execSQL("PRAGMA synchronous = OFF")
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY, word TEXT NOT NULL, word_key TEXT NOT NULL, pos TEXT NOT NULL, forms TEXT NOT NULL, senses TEXT NOT NULL)")
        db.execSQL("CREATE TABLE forms (form TEXT NOT NULL, entry INTEGER NOT NULL)")
        // Inflected forms that are entries of their own, mapped to the keys of their base words.
        db.execSQL("CREATE TABLE form_of (form TEXT NOT NULL, target TEXT NOT NULL)")
        // A single transaction: much faster, and the file is useless until it's complete anyway.
        db.beginTransaction()
        db.compileStatement("INSERT INTO entries (word, word_key, pos, forms, senses) VALUES (?, ?, ?, ?, ?)")
    }
    private val insertForm = db.compileStatement("INSERT INTO forms (form, entry) VALUES (?, ?)")
    private val insertFormOf = db.compileStatement("INSERT INTO form_of (form, target) VALUES (?, ?)")

    /** The number of entries added. */
    var entries = 0
        private set

    fun add(line: WiktionaryLine) {
        val entry = line.entry
        val key = dictionaryKey(entry.word)
        for (target in line.formOf) {
            insertFormOf.bindString(1, key)
            insertFormOf.bindString(2, dictionaryKey(target))
            insertFormOf.executeInsert()
        }
        if (entry.senses.isEmpty()) return
        insertEntry.bindString(1, entry.word)
        insertEntry.bindString(2, key)
        insertEntry.bindString(3, entry.pos)
        insertEntry.bindString(4, JSONArray(entry.forms).toString())
        insertEntry.bindString(5, SensesJson.write(entry.senses))
        val id = insertEntry.executeInsert()
        for (form in (entry.forms.map(::dictionaryKey) + key).distinct()) {
            insertForm.bindString(1, form)
            insertForm.bindLong(2, id)
            insertForm.executeInsert()
        }
        entries++
    }

    fun finish(meta: Map<String, String>) {
        db.execSQL("CREATE INDEX entries_word_key ON entries (word_key)")
        db.execSQL("INSERT INTO forms (form, entry) SELECT f.form, e.id FROM form_of f JOIN entries e ON e.word_key = f.target")
        db.execSQL("DROP TABLE form_of")
        db.execSQL("CREATE INDEX forms_form ON forms (form)")
        val insertMeta = db.compileStatement("INSERT INTO meta (key, value) VALUES (?, ?)")
        for ((key, value) in meta) {
            insertMeta.bindString(1, key)
            insertMeta.bindString(2, value)
            insertMeta.executeInsert()
        }
        db.setTransactionSuccessful()
        db.endTransaction()
    }

    override fun close() {
        if (db.inTransaction()) db.endTransaction()
        db.close()
    }
}

/** Looks words up in a dictionary file; safe to use from several threads. */
internal class DictionaryReader(file: File) : Closeable {
    private val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)

    val meta: Map<String, String> = db.rawQuery("SELECT key, value FROM meta", null).use { cursor ->
        buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1)) }
    }

    /** The entries found by any of [keys], ones whose word is a key first: "saw" the noun before "see". */
    fun lookup(keys: List<String>): List<DictionaryEntry> {
        if (keys.isEmpty()) return emptyList()
        val placeholders = keys.joinToString { "?" }
        val sql = "SELECT word, pos, forms, senses FROM entries " +
            "WHERE id IN (SELECT entry FROM forms WHERE form IN ($placeholders)) " +
            "ORDER BY word_key IN ($placeholders) DESC, id"
        return db.rawQuery(sql, (keys + keys).toTypedArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        DictionaryEntry(
                            word = cursor.getString(0),
                            pos = cursor.getString(1),
                            forms = JSONArray(cursor.getString(2)).strings(),
                            senses = SensesJson.read(cursor.getString(3)),
                        ),
                    )
                }
            }
        }
    }

    override fun close() = db.close()
}
