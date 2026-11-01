package ru.nikitagrgv.breader.ui.reader

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
            SelectionAction(R.drawable.ic_translate, R.string.translate, onTranslate)
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
