package ru.nikitagrgv.breader.ui.reader

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ru.nikitagrgv.breader.R

/**
 * Actions for the selected text, shown at the bottom instead of the floating text toolbar. The main ones
 * are larger icons that share the width between Copy and Clear: with labels, they don't fit a phone.
 */
@Composable
fun SelectionBar(
    onCopy: () -> Unit,
    onTranslate: () -> Unit,
    autoTranslate: Boolean,
    onAutoTranslateChange: (Boolean) -> Unit,
    onLookUp: () -> Unit,
    onGrammar: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onCopy) {
                Icon(painterResource(R.drawable.ic_copy), contentDescription = stringResource(R.string.copy))
            }
            SelectionAction(R.drawable.ic_grammar, R.string.grammar, onGrammar)
            SelectionAction(R.drawable.ic_dictionary, R.string.dictionary, onLookUp)
            TranslateAction(onTranslate, autoTranslate, onAutoTranslateChange)
            IconButton(onClick = onClear) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.clear_selection))
            }
        }
    }
}

/** An equal share of the bar; the action's name is shown on a long press. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowScope.SelectionAction(@DrawableRes icon: Int, @StringRes label: Int, onClick: () -> Unit) {
    val text = stringResource(label)
    // The weight is on a box of its own: TooltipBox doesn't pass it to the row.
    Box(Modifier.weight(1f)) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(text) } },
            state = rememberTooltipState(),
        ) {
            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Icon(painterResource(icon), contentDescription = text, modifier = Modifier.size(28.dp))
            }
        }
    }
}

/**
 * Like [SelectionAction], but a long press opens a menu with the switch of auto-translation. The small
 * triangle in the corner of the icon hints at it: toolbars commonly mark buttons with more options that way.
 */
@Composable
private fun RowScope.TranslateAction(onClick: () -> Unit, autoTranslate: Boolean, onAutoTranslateChange: (Boolean) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(Modifier.weight(1f)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                // The shape of the other buttons' ripple.
                .clip(CircleShape)
                .combinedClickable(
                    role = Role.Button,
                    onLongClickLabel = stringResource(R.string.translation_options),
                    onLongClick = { menuOpen = true },
                    onClick = onClick,
                ),
        ) {
            val markColor = LocalContentColor.current
            Icon(
                painterResource(R.drawable.ic_translate),
                contentDescription = stringResource(R.string.translate),
                modifier = Modifier
                    .size(28.dp)
                    .drawWithContent {
                        drawContent()
                        val leg = 5.dp.toPx()
                        val corner = Offset(size.width + 3.dp.toPx(), size.height + 3.dp.toPx())
                        val mark = Path().apply {
                            moveTo(corner.x, corner.y)
                            lineTo(corner.x, corner.y - leg)
                            lineTo(corner.x - leg, corner.y)
                            close()
                        }
                        drawPath(mark, markColor)
                    },
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.auto_translate)) },
                trailingIcon = { Switch(checked = autoTranslate, onCheckedChange = null) },
                onClick = {
                    onAutoTranslateChange(!autoTranslate)
                    menuOpen = false
                },
            )
        }
    }
}
