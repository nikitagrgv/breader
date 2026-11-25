package ru.nikitagrgv.breader.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp

/**
 * A small triangle in the bottom right corner of an icon, which tells that a long press on its button has more options,
 * as toolbars commonly mark such buttons.
 */
fun Modifier.moreOptionsMark(color: Color): Modifier = drawWithContent {
    drawContent()
    val leg = 5.dp.toPx()
    val corner = Offset(size.width + 3.dp.toPx(), size.height + 3.dp.toPx())
    val mark = Path().apply {
        moveTo(corner.x, corner.y)
        lineTo(corner.x, corner.y - leg)
        lineTo(corner.x - leg, corner.y)
        close()
    }
    drawPath(mark, color)
}
