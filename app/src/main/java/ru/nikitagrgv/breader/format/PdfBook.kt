package ru.nikitagrgv.breader.format

import android.graphics.Bitmap
import android.graphics.Color
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfPage
import io.legere.pdfiumandroid.PdfTextPage
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.math.roundToInt

/** The size of a PDF page as it's shown, in points. */
data class PdfPageSize(val width: Float, val height: Float) {
    /** The height in px of the page shown [width] px wide. */
    fun heightFor(width: Int): Int = (width * height / this.width).roundToInt().coerceAtLeast(1)
}

/**
 * An open PDF file. Unlike the other formats, it's not read as a whole: the pages are rendered, and their text is
 * read, when they're needed. Each page is one of the [paragraphs], which has no text until the page is read.
 */
class PdfBook private constructor(
    private val document: PdfDocument,
    metadata: BookMetadata,
    val pageSizes: List<PdfPageSize>,
    private val pages: PageParagraphs,
) : BookContent(metadata, pages), Closeable {
    /** PDFium can only do one thing at a time. Unlike with a lock, the requests that are no longer needed drop out. */
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)
    private val texts = LruCache<Int, PdfPageText>(TEXT_CACHE_PAGES)

    /** Only used on [dispatcher]. */
    private var closed = false

    /** The text of a page if it's at hand, which it is for the pages on the screen. */
    fun loadedText(page: Int): PdfPageText? = texts[page]

    /** Null if the text can't be read. A page without any text, such as a scan, has an empty text. */
    suspend fun text(page: Int): PdfPageText? = loadedText(page) ?: withContext(dispatcher) {
        loadedText(page) ?: readSafely("the text of page $page") {
            readText(page).also {
                texts.put(page, it)
                pages[page] = it.text
            }
        }
    }

    /**
     * Renders a part of a page, in px of the page shown [pageWidth] px wide.
     * @return an opaque image, null if it can't be rendered.
     */
    suspend fun render(page: Int, pageWidth: Int, left: Int, top: Int, width: Int, height: Int): Bitmap? =
        withContext(dispatcher) {
            readSafely("page $page") {
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                openPage(page).use {
                    it.renderPageBitmap(
                        bitmap = bitmap,
                        startX = -left,
                        startY = -top,
                        drawSizeX = pageWidth,
                        drawSizeY = pageSizes[page].heightFor(pageWidth),
                        renderAnnot = true,
                        canvasColor = Color.WHITE,
                        pageBackgroundColor = Color.WHITE,
                    )
                }
                bitmap
            }
        }

    /** Doesn't wait for what's being rendered; anything asked for later gets null. */
    override fun close() {
        CoroutineScope(dispatcher).launch {
            closed = true
            document.close()
        }
    }

    /** Running out of memory is expected too: a page is big, and there's nothing to lose but its image. */
    private inline fun <T : Any> readSafely(what: String, read: () -> T): T? {
        if (closed) return null
        return try {
            read()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read $what", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Failed to read $what", e)
            null
        }
    }

    private fun openPage(page: Int): PdfPage = document.openPage(page) ?: throw IOException("Can't open page $page")

    private fun readText(index: Int): PdfPageText = openPage(index).use { page ->
        page.openTextPage().use { textPage -> readText(textPage, PageTransform.of(page, pageSizes[index])) }
    }

    private fun readText(page: PdfTextPage, transform: PageTransform): PdfPageText {
        val count = page.textPageCountChars().coerceAtLeast(0)
        val chars = CharArray(count)
        val boxes = FloatArray(count * 4)
        for (index in 0 until count) {
            val char = page.textPageGetUnicode(index)
            chars[index] = char
            // What PDFium says about the spaces and line ends it makes up isn't reliable, and they're not drawn anyway.
            if (!char.isWhitespace()) page.textPageGetCharBox(index)?.let { transform.apply(it, boxes, index * 4) }
        }
        return PdfPageText.of(String(chars), boxes)
    }

    /** The PDF's own coordinates to [PdfRect] ones: PDF's y goes up, and its origin and rotation vary from page to page. */
    private class PageTransform(
        private val xx: Float,
        private val xy: Float,
        private val x0: Float,
        private val yx: Float,
        private val yy: Float,
        private val y0: Float,
    ) {
        fun apply(box: android.graphics.RectF, out: FloatArray, at: Int) {
            val x1 = xx * box.left + xy * box.top + x0
            val y1 = yx * box.left + yy * box.top + y0
            val x2 = xx * box.right + xy * box.bottom + x0
            val y2 = yx * box.right + yy * box.bottom + y0
            out[at] = minOf(x1, x2)
            out[at + 1] = minOf(y1, y2)
            out[at + 2] = maxOf(x1, x2)
            out[at + 3] = maxOf(y1, y2)
        }

        companion object {
            /** The API maps to whole units, so the points are mapped with a precision of a tenth, over a long way. */
            private const val UNITS_PER_POINT = 10
            private const val PROBE = 1000.0

            fun of(page: PdfPage, size: PdfPageSize): PageTransform {
                val width = (size.width * UNITS_PER_POINT).roundToInt()
                val height = (size.height * UNITS_PER_POINT).roundToInt()
                val origin = page.mapPageCoordsToDevice(0, 0, width, height, 0, 0.0, 0.0)
                val alongX = page.mapPageCoordsToDevice(0, 0, width, height, 0, PROBE, 0.0)
                val alongY = page.mapPageCoordsToDevice(0, 0, width, height, 0, 0.0, PROBE)
                val scale = (PROBE * UNITS_PER_POINT).toFloat()
                return PageTransform(
                    xx = (alongX.x - origin.x) / scale,
                    xy = (alongY.x - origin.x) / scale,
                    x0 = origin.x.toFloat() / UNITS_PER_POINT,
                    yx = (alongX.y - origin.y) / scale,
                    yy = (alongY.y - origin.y) / scale,
                    y0 = origin.y.toFloat() / UNITS_PER_POINT,
                )
            }
        }
    }

    /** Written on [dispatcher], read on the main thread. */
    private class PageParagraphs(override val size: Int) : AbstractList<Paragraph>() {
        private val paragraphs = AtomicReferenceArray<Paragraph>(size)

        override fun get(index: Int): Paragraph = paragraphs[index] ?: UNREAD

        operator fun set(index: Int, text: String) = paragraphs.set(index, Paragraph(text))

        private companion object {
            val UNREAD = Paragraph("")
        }
    }

    companion object {
        private const val TAG = "PdfBook"

        /** Enough to tell the proportions of a page, which come as whole numbers. */
        private const val SIZE_DPI = 7200
        private const val POINTS_PER_INCH = 72f

        /** A page's boxes take about 50 KB; the pages' texts are kept anyway. */
        private const val TEXT_CACHE_PAGES = 64

        private val FILE_NAME_TITLE = Regex(
            ".*\\.(pdf|docx?|odt|rtf|txt|tex|dvi|ps|indd|qxd|pptx?|xlsx?)|untitled.*|microsoft (word|powerpoint) .*",
            RegexOption.IGNORE_CASE,
        )

        // Blocks until the native libraries are loaded, so it's not done before a PDF is opened.
        private val core by lazy { PdfiumCore() }

        /** Takes over [descriptor], which has to stay open for reading the pages. */
        fun open(descriptor: ParcelFileDescriptor): PdfBook {
            val document = try {
                core.newDocument(descriptor)
            } catch (e: Exception) {
                descriptor.close()
                throw e
            }
            try {
                val sizes = document.getPageSizes(SIZE_DPI).map {
                    PdfPageSize(
                        width = (it.width * POINTS_PER_INCH / SIZE_DPI).coerceAtLeast(1f),
                        height = (it.height * POINTS_PER_INCH / SIZE_DPI).coerceAtLeast(1f),
                    )
                }
                if (sizes.isEmpty()) throw IOException("The PDF has no pages")
                val meta = document.getDocumentMeta()
                val metadata = BookMetadata(cleanTitle(meta.title), meta.author?.trim()?.ifEmpty { null })
                return PdfBook(document, metadata, sizes, PageParagraphs(sizes.size))
            } catch (e: Exception) {
                document.close()
                throw e
            }
        }

        /** Null for what's rather left over from making the file, which is often what PDFs have for a title. */
        internal fun cleanTitle(title: String?): String? =
            title?.trim()?.takeUnless { it.isEmpty() || FILE_NAME_TITLE.matches(it) }
    }
}
