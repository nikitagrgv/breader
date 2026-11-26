package ru.nikitagrgv.breader.ui.reader

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ru.nikitagrgv.breader.R

private val ButtonWidth = 44.dp
private val ButtonHeight = 44.dp
private val IconSize = 20.dp

/** Their width, for what's next to them to keep clear of them. */
internal val SelectionHistoryButtonsWidth = ButtonWidth * 2

/**
 * Walking through the selections made in the book, like undo and redo. They're always in the same place, whatever else
 * comes and goes around them, so they can be pressed one time after another without looking. Each one tells how far it
 * can go, which also makes it clear where the walk is.
 * @param onReturn goes right back to where the walk began, by a long press.
 */
@Composable
internal fun SelectionHistoryButtons(
    stepsBack: Int,
    stepsForward: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReturn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(ButtonHeight / 2),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 2.dp,
        modifier = modifier,
    ) {
        Row {
            HistoryButton(R.drawable.ic_arrow_back, R.string.previous_selection, stepsBack, onBack)
            HistoryButton(R.drawable.ic_arrow_forward, R.string.next_selection, stepsForward, onForward, onLongClick = onReturn)
        }
    }
}

@Composable
private fun HistoryButton(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    steps: Int,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val enabled = steps > 0
    val color = LocalContentColor.current.copy(alpha = if (enabled) 1f else 0.38f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .size(ButtonWidth, ButtonHeight)
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onLongClickLabel = onLongClick?.let { stringResource(R.string.back_to_reading_place) },
                onLongClick = onLongClick,
                onClick = onClick,
            ),
    ) {
        Icon(painterResource(icon), contentDescription = stringResource(label), tint = color, modifier = Modifier.size(IconSize))
        // There even with no number in it, so that the arrows stay level whichever of them has one.
        Text(text = if (enabled) steps.toString() else "", style = MaterialTheme.typography.labelSmall, color = color)
    }
}
