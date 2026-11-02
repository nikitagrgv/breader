package ru.nikitagrgv.breader.ui.reader

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ru.nikitagrgv.breader.R

/**
 * Actions for the selected text, shown at the bottom instead of the floating text toolbar. The main ones are
 * always there; the rest are in an extra row above them, opened by the arrow. There's no button to clear the
 * selection: a tap on the text does it.
 * @param moreOpen whether the extra row is open.
 * @param favorite whether the selected word is a favorite.
 */
@Composable
fun SelectionBar(
    moreOpen: Boolean,
    onMoreOpenChange: (Boolean) -> Unit,
    onCopy: () -> Unit,
    onGrammar: () -> Unit,
    onLookUp: () -> Unit,
    onTranslate: () -> Unit,
    onQuickTranslate: () -> Unit,
    autoTranslate: Boolean,
    onAutoTranslateChange: (Boolean) -> Unit,
    favorite: Boolean,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
            AnimatedVisibility(visible = moreOpen, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                // There's room for the labels here.
                Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp)) {
                    LabeledAction(R.drawable.ic_copy, R.string.copy, onCopy)
                    LabeledAction(R.drawable.ic_grammar, R.string.grammar, onGrammar)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                IconButton(onClick = { onMoreOpenChange(!moreOpen) }) {
                    Icon(
                        painterResource(if (moreOpen) R.drawable.ic_expand_more else R.drawable.ic_expand_less),
                        contentDescription = stringResource(if (moreOpen) R.string.fewer_actions else R.string.more_actions),
                    )
                }
                SelectionAction(
                    icon = if (favorite) R.drawable.ic_star else R.drawable.ic_star_outline,
                    label = if (favorite) R.string.remove_from_favorites else R.string.add_to_favorites,
                    onClick = onFavorite,
                    tint = if (favorite) MaterialTheme.colorScheme.primary else Color.Unspecified,
                )
                TranslateButtons(
                    onTranslate = onTranslate,
                    onQuickTranslate = onQuickTranslate,
                    autoTranslate = autoTranslate,
                    onAutoTranslateChange = onAutoTranslateChange,
                    modifier = Modifier.weight(1.6f),
                )
                SelectionAction(R.drawable.ic_dictionary, R.string.dictionary, onLookUp)
            }
        }
    }
}

@Composable
private fun LabeledAction(@DrawableRes icon: Int, @StringRes label: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(label))
    }
}

/** An equal share of the bar; the action's name is shown on a long press. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowScope.SelectionAction(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    onClick: () -> Unit,
    tint: Color = Color.Unspecified,
) {
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
                val color = if (tint == Color.Unspecified) LocalContentColor.current else tint
                Icon(painterResource(icon), contentDescription = text, tint = color, modifier = Modifier.size(28.dp))
            }
        }
    }
}

/**
 * One button split in two: the translation in detail, and the quick one at the top of the reader. A long press
 * on the quick one opens the switch of auto-translation; the small triangle in the corner of its icon hints at
 * that, as toolbars commonly mark buttons with more options.
 */
@Composable
private fun TranslateButtons(
    onTranslate: () -> Unit,
    onQuickTranslate: () -> Unit,
    autoTranslate: Boolean,
    onAutoTranslateChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier.padding(horizontal = 4.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(48.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .combinedClickable(role = Role.Button, onClick = onTranslate),
                ) {
                    Icon(painterResource(R.drawable.ic_translate), contentDescription = stringResource(R.string.translate), modifier = Modifier.size(26.dp))
                }
                VerticalDivider(
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.3f),
                    modifier = Modifier.height(24.dp),
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .combinedClickable(
                            role = Role.Button,
                            onLongClickLabel = stringResource(R.string.translation_options),
                            onLongClick = { menuOpen = true },
                            onClick = onQuickTranslate,
                        ),
                ) {
                    val markColor = LocalContentColor.current
                    Icon(
                        painterResource(R.drawable.ic_bolt),
                        contentDescription = stringResource(R.string.quick_translation),
                        modifier = Modifier
                            .size(26.dp)
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
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            Text(
                text = stringResource(R.string.quick_translation),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
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
