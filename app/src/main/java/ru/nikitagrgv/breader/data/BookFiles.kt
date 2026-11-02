package ru.nikitagrgv.breader.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import ru.nikitagrgv.breader.format.BookContent
import ru.nikitagrgv.breader.format.BookParser
import ru.nikitagrgv.breader.format.BookSource
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/** Access to book files picked by the user. All methods block, call them off the main thread. */
class BookFiles(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    /** Reads the book's metadata and keeps read access to the file, so it can be opened after a restart. */
    fun importBook(uri: Uri): Book {
        val fileName = displayName(uri) ?: uri.lastPathSegment.orEmpty()
        val format = BookParser.detectFormat(fileName, resolver.getType(uri))
            ?: throw IOException("Unsupported file: $fileName")
        val metadata = BookParser.readMetadata(format, source(uri))
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // The provider doesn't support persistable grants; the file stays readable until the app restarts.
        }
        return Book(
            id = UUID.randomUUID().toString(),
            uri = uri,
            fileName = fileName,
            format = format,
            title = metadata.title ?: BookParser.titleFromFileName(fileName),
            author = metadata.author,
            addedAt = System.currentTimeMillis(),
        )
    }

    /** A [java.io.Closeable] content is open until it's closed. */
    fun readContent(book: Book): BookContent = BookParser.parse(book.format, source(book.uri))

    fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun source(uri: Uri) = object : BookSource {
        override fun openStream(): InputStream =
            resolver.openInputStream(uri) ?: throw FileNotFoundException("Can't open $uri")

        override fun openDescriptor(): ParcelFileDescriptor =
            resolver.openFileDescriptor(uri, "r") ?: throw FileNotFoundException("Can't open $uri")
    }
}
