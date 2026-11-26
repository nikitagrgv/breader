package ru.nikitagrgv.breader.ui.reader

import android.util.LruCache
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.magnifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import ru.nikitagrgv.breader.R
import ru.nikitagrgv.breader.format.PdfBook
import ru.nikitagrgv.breader.format.PdfPageSize
import ru.nikitagrgv.breader.format.PdfPageText
import ru.nikitagrgv.breader.format.PdfRect
import ru.nikitagrgv.breader.translation.TextPosition
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 6f

/** Images of whole pages are kept to this size; if more detail is needed, it's rendered just for what's visible. */
private const val MAX_PAGE_IMAGE_PIXELS = 8_000_000L
private const val MAX_PAGE_IMAGE_SIDE = 8192

/** About 10 pages on a phone. */
private const val PAGE_CACHE_BYTES = 64 * 1024 * 1024

/** The more detailed image is only rendered once the page has stopped moving. */
private const val DETAIL_DELAY_MS = 120L

private val PageGap = 8.dp
private val HandleRadius = 11.dp
private val HandleTouchRadius = 32.dp

/** How far from the text a long press still selects it. */
private val LongPressReach = 24.dp

/** How close to the edges a dragged selection scrolls the pages; the bottom is covered by the selection's actions. */
private val DragScrollZoneTop = 56.dp
private val DragScrollZoneBottom = 120.dp
private val DragScrollZoneSide = 32.dp

/** The most the pages move in a frame when the selection is dragged to the very edge. */
private val DragScrollSpeed = 12.dp

/** A plain color, since the pages are white in every theme; multiplied with the page, so the text stays dark. */
private val SelectionColor = Color(0xFFA8CBFF)

/** Plain as well: the theme's color may be too pale to see on the page. */
private val HandleColor = Color(0xFF1A73E8)

/** How much the pages are enlarged and where they are sideways. */
@Stable
internal class PdfViewport(initialZoom: Float, initialPan: Float) {
    var zoom by mutableFloatStateOf(initialZoom.coerceIn(MIN_ZOOM, MAX_ZOOM))
        private set

    /** Where the view is, as it was asked for: [pan] depends on the size of the screen too. */
    private var requestedPan by mutableFloatStateOf(initialPan)

    /** The size of the view, in px. */
    var width by mutableIntStateOf(0)
    var height by mutableIntStateOf(0)
    var isPinching by mutableStateOf(false)

    val panState = ScrollableState { delta ->
        val before = pan
        requestedPan = (before - delta).coerceIn(0f, maxPan)
        before - requestedPan
    }

    /** Whether the view is still moving, for what's to be done when it stops. */
    val isBusy: Boolean get() = isPinching || panState.isScrollInProgress

    /** How far the left edge of the view is from the left edge of the pages, in px. */
    val pan: Float get() = requestedPan.coerceIn(0f, maxPan)

    private val maxPan: Float get() = (pageWidth(width) - width).toFloat().coerceAtLeast(0f)

    /** The width in px of the pages shown in a view that's [viewWidth] px wide. */
    fun pageWidth(viewWidth: Int): Int = (viewWidth * zoom).roundToInt()

    fun panBy(delta: Float) {
        requestedPan = (pan + delta).coerceIn(0f, maxPan)
    }

    suspend fun animatePanTo(target: Float) {
        panState.animateScrollBy(pan - target.coerceIn(0f, maxPan), tween(JUMP_ANIMATION_MS, easing = FastOutSlowInEasing))
    }

    /** Puts the part of the pages from [left] to [right] in the middle of the view, unless it's in the view already. */
    suspend fun animatePanToShow(left: Float, right: Float) {
        if (left < pan || right > pan + width) animatePanTo((left + right - width) / 2)
    }

    /** Applies a pinch, keeping what's under the fingers ([center]) under the fingers. */
    fun pinch(zoomChange: Float, center: Offset, move: Offset, list: LazyListState) {
        val oldPan = pan
        val newZoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val ratio = newZoom / zoom
        zoom = newZoom
        requestedPan = ((oldPan + center.x) * ratio - center.x - move.x).coerceIn(0f, maxPan)
        val top = center.y - list.layoutInfo.beforeContentPadding
        val offset = (list.firstVisibleItemScrollOffset + top) * ratio - top - move.y
        // The list applies it together with the new size of the pages, in the same frame.
        list.requestScrollToItem(list.firstVisibleItemIndex, offset.roundToInt())
    }

    companion object {
        val Saver = listSaver<PdfViewport, Float>(
            save = { listOf(it.zoom, it.requestedPan) },
            restore = { PdfViewport(it[0], it[1]) },
        )
    }
}

/**
 * The pages of a PDF one under another, as images. They fit the width of the screen and can be enlarged by pinching,
 * as there's no font size to set. Text is selected as usual: by a long press, which goes on by dragging, and by the
 * handles.
 */
@Composable
internal fun PdfPages(
    selection: PdfSelection,
    listState: LazyListState,
    viewport: PdfViewport,
    fade: SelectionFade,
    onToggleChrome: () -> Unit,
    onHideChrome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val book = selection.book
    val currentOnToggleChrome by rememberUpdatedState(onToggleChrome)
    val currentOnHideChrome by rememberUpdatedState(onHideChrome)
    val canPan by remember(viewport) { derivedStateOf { viewport.zoom > MIN_ZOOM } }
    val panInteractions = remember { MutableInteractionSource() }
    val cutout = WindowInsets.displayCutout
    val pageCache = remember(book) { PageImageCache() }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val drag = remember(selection, listState, viewport, density) {
        SelectionDrag(selection, PageGeometry(book, listState, viewport), with(density) { LongPressReach.toPx() })
    }

    LaunchedEffect(panInteractions) {
        panInteractions.interactions.collect {
            if (it is DragInteraction.Start) currentOnHideChrome()
        }
    }
    DragScrollEffect(drag, listState, viewport)

    Box(
        modifier
            .fillMaxSize()
            // Pages are white in every theme, so there has to be something else around them.
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .windowInsetsPadding(cutout.only(WindowInsetsSides.Horizontal))
            .onSizeChanged {
                viewport.width = it.width
                viewport.height = it.height
            }
            .magnifier(sourceCenter = { drag.point ?: Offset.Unspecified })
            .pointerInput(viewport, listState, drag) {
                detectPinch(
                    isEnabled = { drag.point == null },
                    onStart = {
                        viewport.isPinching = true
                        currentOnHideChrome()
                    },
                    onPinch = { zoom, center, move -> viewport.pinch(zoom, center, move, listState) },
                    onEnd = { viewport.isPinching = false },
                )
            }
            .scrollable(
                state = viewport.panState,
                orientation = Orientation.Horizontal,
                enabled = canPan,
                interactionSource = panInteractions,
            )
            .pointerInput(drag) {
                detectPageGestures(
                    drag = drag,
                    handleTouchRadius = HandleTouchRadius.toPx(),
                    handleRadius = HandleRadius.toPx(),
                    // A tap that clears the selection shouldn't toggle the bars as well.
                    onTap = { if (selection.hasSelection) selection.clear() else currentOnToggleChrome() },
                    onLongPress = { point ->
                        when (drag.startAtWord(point)) {
                            SelectionDrag.Start.SELECTED -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            SelectionDrag.Start.NO_TEXT_ON_PAGE ->
                                Toast.makeText(context, R.string.pdf_page_no_text, Toast.LENGTH_SHORT).show()
                            SelectionDrag.Start.NOTHING_THERE -> Unit
                        }
                    },
                )
            },
    ) {
        LazyColumn(
            state = listState,
            // Only the camera cutout is avoided: the system bars are hidden while reading.
            contentPadding = cutout.only(WindowInsetsSides.Vertical).asPaddingValues(),
            verticalArrangement = Arrangement.spacedBy(PageGap),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(count = book.pageSizes.size) { index ->
                PdfPage(book, index, listState, viewport, selection, fade, pageCache)
            }
        }
        // Draws only, so that the touches go through to the pages; [detectPageGestures] takes those on the handles.
        Canvas(Modifier.fillMaxSize()) {
            for (isStart in listOf(true, false)) {
                drag.geometry.handle(selection, isStart)?.let { drawHandle(it) }
            }
        }
    }
}

/** The whole-page images of the pages recently shown, for going back and forth without any waiting. */
private class PageImageCache : LruCache<PageImageCache.Key, ImageBitmap>(PAGE_CACHE_BYTES) {
    data class Key(val page: Int, val width: Int)

    override fun sizeOf(key: Key, value: ImageBitmap): Int = value.asAndroidBitmap().allocationByteCount
}

/** A part of a page, in px of the page shown [pageWidth] px wide. */
private data class PageArea(val pageWidth: Int, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun covers(other: PageArea) = pageWidth == other.pageWidth &&
        left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom
}

private class PageDetail(val image: ImageBitmap, val area: PageArea)

/** The image of a whole page is as wide as the view unless that makes it too big. */
private fun pageImageWidth(viewWidth: Int, size: PdfPageSize): Int {
    val height = size.heightFor(viewWidth)
    val scale = minOf(
        1f,
        sqrt(MAX_PAGE_IMAGE_PIXELS.toFloat() / (viewWidth.toLong() * height)),
        MAX_PAGE_IMAGE_SIDE.toFloat() / height,
    )
    return (viewWidth * scale).toInt().coerceAtLeast(1)
}

/** What's visible of a page when the whole-page image isn't detailed enough for it, with some extra around it. */
private fun wantedDetail(index: Int, size: PdfPageSize, imageWidth: Int, list: LazyListState, viewport: PdfViewport): PageArea? {
    val viewWidth = viewport.width
    val pageWidth = viewport.pageWidth(viewWidth)
    if (pageWidth <= imageWidth) return null
    val layout = list.layoutInfo
    val item = layout.visibleItemsInfo.firstOrNull { it.index == index } ?: return null
    val left = viewport.pan.roundToInt()
    val top = max(layout.viewportStartOffset - item.offset, 0)
    val bottom = min(layout.viewportEndOffset - item.offset, item.size)
    if (bottom <= top) return null
    return PageArea(pageWidth, left, top, left + viewWidth, bottom)
}

/** The same, with some of the neighborhood, so that moving a little doesn't need a new image. */
private fun PageArea.withMargin(size: PdfPageSize): PageArea {
    val marginX = (right - left) / 4
    val marginY = (bottom - top) / 4
    return copy(
        left = max(left - marginX, 0),
        top = max(top - marginY, 0),
        right = min(right + marginX, pageWidth),
        bottom = min(bottom + marginY, size.heightFor(pageWidth)),
    )
}

@Composable
private fun PdfPage(
    book: PdfBook,
    index: Int,
    list: LazyListState,
    viewport: PdfViewport,
    selection: PdfSelection,
    fade: SelectionFade,
    cache: PageImageCache,
) {
    val size = book.pageSizes[index]
    val viewWidth = viewport.width
    val imageWidth = remember(viewWidth, size) { if (viewWidth > 0) pageImageWidth(viewWidth, size) else 0 }
    val image by produceState(cache[PageImageCache.Key(index, imageWidth)], book, index, imageWidth) {
        if (imageWidth == 0) return@produceState
        val key = PageImageCache.Key(index, imageWidth)
        val rendered = cache[key]
            ?: book.render(index, imageWidth, 0, 0, imageWidth, size.heightFor(imageWidth))?.asImageBitmap()?.also { cache.put(key, it) }
        if (rendered != null) value = rendered
    }
    val detail by produceState<PageDetail?>(null, book, index, imageWidth) {
        snapshotFlow { wantedDetail(index, size, imageWidth, list, viewport) }
            .distinctUntilChanged()
            .collectLatest { wanted ->
                val current = value
                if (wanted == null) {
                    value = null
                } else if (current == null || !current.area.covers(wanted)) {
                    delay(DETAIL_DELAY_MS)
                    val area = wanted.withMargin(size)
                    val rendered = book.render(index, area.pageWidth, area.left, area.top, area.right - area.left, area.bottom - area.top)
                    if (rendered != null) value = PageDetail(rendered.asImageBitmap(), area)
                }
            }
    }
    val text by produceState<PdfPageText?>(null, book, index) {
        value = book.text(index)
        // A selection's context comes from the neighboring pages as well.
        if (index > 0) book.text(index - 1)
        if (index < book.pageSizes.lastIndex) book.text(index + 1)
    }
    val selectedRects by remember(selection, index) {
        derivedStateOf { text?.let { selection.rectsOn(index, it) }.orEmpty() }
    }
    val fadingRects by remember(fade, index) {
        derivedStateOf { text?.let { fade.place?.rectsOn(index, it) }.orEmpty() }
    }

    Spacer(
        Modifier
            .drawBehind {
                val pageWidth = viewport.pageWidth(this.size.width.roundToInt())
                val pageSize = IntSize(pageWidth, this.size.height.roundToInt())
                translate(left = -viewport.pan.roundToInt().toFloat()) {
                    drawRect(Color.White, size = pageSize.toSize())
                    image?.let { drawImage(it, dstSize = pageSize) }
                    detail?.let { drawDetail(it, pageWidth) }
                    val pointScale = pageWidth / size.width
                    // The alpha changes with every frame, which only the pages that show it need to know.
                    if (fadingRects.isNotEmpty()) drawTextRects(fadingRects, pointScale, SelectionFadeColor, fade.alpha)
                    drawTextRects(selectedRects, pointScale, SelectionColor)
                }
            }
            // The height depends on the zoom, while the width is that of the list: the page is drawn past it.
            .layout { measurable, constraints ->
                val width = constraints.maxWidth
                val placeable = measurable.measure(Constraints())
                layout(width, size.heightFor(viewport.pageWidth(width))) { placeable.place(0, 0) }
            },
    )
}

private fun IntSize.toSize() = Size(width.toFloat(), height.toFloat())

/** Multiplied with the page, so that the text stays dark. */
private fun DrawScope.drawTextRects(rects: List<PdfRect>, pointScale: Float, color: Color, alpha: Float = 1f) {
    for (rect in rects) {
        drawRect(
            color = color,
            topLeft = Offset(rect.left * pointScale, rect.top * pointScale),
            size = Size((rect.right - rect.left) * pointScale, (rect.bottom - rect.top) * pointScale),
            alpha = alpha,
            blendMode = BlendMode.Multiply,
        )
    }
}

/** At the current zoom it's pixel for pixel; while the zoom is changing, it's scaled along with the page. */
private fun DrawScope.drawDetail(detail: PageDetail, pageWidth: Int) {
    val area = detail.area
    val scale = pageWidth.toFloat() / area.pageWidth
    drawImage(
        image = detail.image,
        dstOffset = IntOffset((area.left * scale).roundToInt(), (area.top * scale).roundToInt()),
        dstSize = IntSize(((area.right - area.left) * scale).roundToInt(), ((area.bottom - area.top) * scale).roundToInt()),
    )
}

/** A teardrop whose corner points at the end of the selection. */
private fun DrawScope.drawHandle(handle: SelectionHandle) {
    val radius = HandleRadius.toPx()
    val center = handle.center(radius)
    drawCircle(HandleColor, radius, center)
    drawRect(HandleColor, Offset(min(handle.tip.x, center.x), handle.tip.y), Size(radius, radius))
}

/** A point of a page, in points. */
private class PagePoint(val page: Int, val x: Float, val y: Float, val pxPerPoint: Float)

/** A selection handle where it's on the screen, or rather in the view of the pages. */
private class SelectionHandle(val isStart: Boolean, val tip: Offset, val lineCenter: Offset) {
    /** It hangs from its [tip], outward from the selection. */
    fun center(radius: Float) = Offset(if (isStart) tip.x - radius else tip.x + radius, tip.y + radius)
}

/** Converts between points of the pages and the view of the pages. */
private class PageGeometry(private val book: PdfBook, private val list: LazyListState, private val viewport: PdfViewport) {
    private fun pxPerPoint(page: Int) = viewport.pageWidth(viewport.width) / book.pageSizes[page].width

    /** The point of the page at [point] of the view; the closest page if it's between the pages. */
    fun pageAt(point: Offset): PagePoint? {
        val layout = list.layoutInfo
        val y = point.y.roundToInt() + layout.viewportStartOffset
        val item = layout.visibleItemsInfo.minByOrNull { item ->
            max(max(item.offset - y, y - (item.offset + item.size)), 0)
        } ?: return null
        val scale = pxPerPoint(item.index)
        return PagePoint(item.index, (point.x + viewport.pan) / scale, (y - item.offset) / scale, scale)
    }

    private fun viewPoint(page: Int, x: Float, y: Float): Offset? {
        val layout = list.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == page } ?: return null
        val scale = pxPerPoint(page)
        return Offset(x * scale - viewport.pan, y * scale + item.offset - layout.viewportStartOffset)
    }

    /** Null if there's no such handle on the screen. */
    fun handle(selection: PdfSelection, isStart: Boolean): SelectionHandle? {
        val range = selection.range ?: return null
        val position = if (isStart) range.start else range.end
        val page = position.paragraph
        val rects = selection.rectsOn(page, book.loadedText(page) ?: return null)
        val line: PdfRect = (if (isStart) rects.firstOrNull() else rects.lastOrNull()) ?: return null
        val x = if (isStart) line.left else line.right
        return SelectionHandle(
            isStart = isStart,
            tip = viewPoint(page, x, line.bottom) ?: return null,
            lineCenter = viewPoint(page, x, (line.top + line.bottom) / 2) ?: return null,
        )
    }
}

/** Changes the selection as a finger is moved over the pages. */
@Stable
private class SelectionDrag(private val selection: PdfSelection, val geometry: PageGeometry, private val longPressReach: Float) {
    enum class Start { SELECTED, NO_TEXT_ON_PAGE, NOTHING_THERE }

    private sealed interface Mode {
        /** Word by word from the word that was pressed. */
        class Words(val first: PdfTextRange) : Mode

        class Handle(val isStart: Boolean) : Mode
    }

    private var mode: Mode? = null

    /** The point of the view being dragged to; null when nothing is dragged. */
    var point by mutableStateOf<Offset?>(null)
        private set

    /** The selection handle being touched at [at], if any. */
    fun handleNear(at: Offset, touchRadius: Float, handleRadius: Float): SelectionHandle? =
        listOfNotNull(geometry.handle(selection, isStart = true), geometry.handle(selection, isStart = false))
            .filter { (it.center(handleRadius) - at).getDistance() <= touchRadius }
            .minByOrNull { (it.center(handleRadius) - at).getDistance() }

    fun startAtWord(at: Offset): Start {
        val hit = geometry.pageAt(at) ?: return Start.NOTHING_THERE
        val text = selection.book.loadedText(hit.page) ?: return Start.NOTHING_THERE
        if (text.text.isBlank()) return Start.NO_TEXT_ON_PAGE
        val char = text.charAt(hit.x, hit.y, maxDistance = longPressReach / hit.pxPerPoint)
        if (char < 0) return Start.NOTHING_THERE
        val word = wordRange(hit.page, text, char)
        selection.select(word)
        mode = Mode.Words(word)
        point = at
        return Start.SELECTED
    }

    fun startAtHandle(handle: SelectionHandle) {
        mode = Mode.Handle(handle.isStart)
        point = handle.lineCenter
    }

    fun moveTo(to: Offset) {
        if (mode == null) return
        point = to
        update()
    }

    /** Applies the point again, which matters if the pages have moved under it. */
    fun update() {
        val to = point ?: return
        val hit = geometry.pageAt(to) ?: return
        val text = selection.book.loadedText(hit.page) ?: return
        when (val mode = mode) {
            is Mode.Words -> {
                val char = text.charAt(hit.x, hit.y)
                if (char >= 0) selection.select(mode.first.union(wordRange(hit.page, text, char)))
            }
            is Mode.Handle -> {
                val caret = text.caretAt(hit.x, hit.y)
                if (caret >= 0) selection.moveEnd(mode.isStart, TextPosition(hit.page, caret))
            }
            null -> Unit
        }
    }

    fun end() {
        mode = null
        point = null
    }

    private fun wordRange(page: Int, text: PdfPageText, char: Int): PdfTextRange {
        val (start, end) = text.wordAt(char)
        return PdfTextRange(TextPosition(page, start), TextPosition(page, end))
    }
}

/** Scrolls the pages while the selection is dragged close to the edges, so it can go on to what isn't visible yet. */
@Composable
private fun DragScrollEffect(drag: SelectionDrag, list: LazyListState, viewport: PdfViewport) {
    val density = LocalDensity.current
    val isDragging by remember(drag) { derivedStateOf { drag.point != null } }
    LaunchedEffect(isDragging, density) {
        if (!isDragging) return@LaunchedEffect
        val speed = with(density) { DragScrollSpeed.toPx() }
        val zoneTop = with(density) { DragScrollZoneTop.toPx() }
        val zoneBottom = with(density) { DragScrollZoneBottom.toPx() }
        val zoneSide = with(density) { DragScrollZoneSide.toPx() }
        while (true) {
            withFrameNanos { }
            val point = drag.point ?: break
            val up = (zoneTop - point.y) / zoneTop
            val down = (point.y - (viewport.height - zoneBottom)) / zoneBottom
            val left = (zoneSide - point.x) / zoneSide
            val right = (point.x - (viewport.width - zoneSide)) / zoneSide
            val dy = speed * (down.coerceIn(0f, 1f) - up.coerceIn(0f, 1f))
            val dx = speed * (right.coerceIn(0f, 1f) - left.coerceIn(0f, 1f))
            if (dy != 0f) list.dispatchRawDelta(dy)
            if (dx != 0f) viewport.panBy(dx)
            if (dy != 0f || dx != 0f) drag.update()
        }
    }
}

/**
 * Takes two-finger gestures away from what's below, which sees only one finger at a time otherwise.
 * @param onPinch gets the change of the zoom, the middle of the fingers and how far it has moved.
 */
private suspend fun PointerInputScope.detectPinch(
    isEnabled: () -> Boolean,
    onStart: () -> Unit,
    onPinch: (zoomChange: Float, center: Offset, move: Offset) -> Unit,
    onEnd: () -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var pinching = false
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.none { it.pressed }) break
                if (event.changes.count { it.pressed } < 2 || !isEnabled()) continue
                if (!pinching) {
                    pinching = true
                    onStart()
                }
                onPinch(event.calculateZoom(), event.calculateCentroid(), event.calculatePan())
                event.changes.forEach { it.consume() }
            }
        } finally {
            if (pinching) onEnd()
        }
    }
}

/**
 * Tells apart what a finger does on the pages: touching a selection handle, a long press and a tap. What moves the
 * pages is left to what's below. A touch on a handle or a long press is taken away from there as soon as it's
 * recognized, so that the pages don't scroll under the finger. Works in the view of the pages, which doesn't move,
 * unlike the pages and the handles.
 */
private suspend fun PointerInputScope.detectPageGestures(
    drag: SelectionDrag,
    handleTouchRadius: Float,
    handleRadius: Float,
    onTap: () -> Unit,
    onLongPress: (Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val handle = drag.handleNear(down.position, handleTouchRadius, handleRadius)
        // The offset from the finger, so that the handle doesn't jump to it and the finger doesn't cover the text.
        val grip: Offset
        if (handle != null) {
            down.consume()
            drag.startAtHandle(handle)
            grip = handle.lineCenter - down.position
        } else {
            when (awaitPress(down)) {
                Press.TAP -> {
                    onTap()
                    return@awaitEachGesture
                }
                // It's a scroll or a pinch, which isn't for here.
                Press.CANCELLED -> return@awaitEachGesture
                Press.LONG -> onLongPress(down.position)
            }
            grip = Offset.Zero
        }
        try {
            trackPointer(down.id) { drag.moveTo(it + grip) }
        } finally {
            drag.end()
        }
    }
}

private enum class Press { TAP, LONG, CANCELLED }

/**
 * Waits to see what becomes of a touch. Not the ready-made gesture detectors: a tap detector consumes the touch, which
 * cancels a long press detector, and neither of them minds the finger moving as long as nothing else has taken it.
 */
private suspend fun AwaitPointerEventScope.awaitPress(down: PointerInputChange): Press =
    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull Press.CANCELLED
            if (event.changes.any { it.isConsumed } || event.changes.count { it.pressed } > 1) {
                return@withTimeoutOrNull Press.CANCELLED
            }
            if (!change.pressed) {
                change.consume()
                return@withTimeoutOrNull Press.TAP
            }
            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                return@withTimeoutOrNull Press.CANCELLED
            }
        }
        @Suppress("UNREACHABLE_CODE")
        Press.CANCELLED
    } ?: Press.LONG

/** Follows a pointer until it's lifted, with all that happens meanwhile being taken from what's below. */
private suspend fun AwaitPointerEventScope.trackPointer(id: PointerId, onMove: (Offset) -> Unit) {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == id } ?: break
        event.changes.forEach { it.consume() }
        if (!change.pressed) break
        if (change.position != change.previousPosition) onMove(change.position)
    }
}
