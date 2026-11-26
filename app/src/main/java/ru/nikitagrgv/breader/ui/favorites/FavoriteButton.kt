package ru.nikitagrgv.breader.ui.favorites

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.nikitagrgv.breader.R
import ru.nikitagrgv.breader.data.FavoriteKind
import ru.nikitagrgv.breader.ui.moreOptionsMark

/**
 * The star that keeps a text in the favorites. A tap keeps it with the best there is, or removes it, if it's kept; a
 * long press opens what it can be kept with, each added or removed on its own.
 * @param kinds what the text is kept with; null if it isn't kept.
 * @param saving whether it's being kept, which may take the models a while.
 */
@Composable
fun FavoriteButton(
    kinds: Set<FavoriteKind>?,
    saving: Boolean,
    onClick: () -> Unit,
    onKindClick: (FavoriteKind) -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val favorite = kinds != null
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .combinedClickable(
                interactionSource = null,
                indication = ripple(bounded = false, radius = 24.dp),
                enabled = !saving,
                role = Role.Button,
                onLongClickLabel = stringResource(R.string.favorite_keep_as),
                onLongClick = { menuOpen = true },
                onClick = onClick,
            ),
    ) {
        if (saving) {
            CircularProgressIndicator(Modifier.size(iconSize - 4.dp), strokeWidth = 2.dp)
        } else {
            val tint = if (favorite) MaterialTheme.colorScheme.primary else LocalContentColor.current
            Icon(
                painterResource(if (favorite) R.drawable.ic_star else R.drawable.ic_star_outline),
                contentDescription = stringResource(if (favorite) R.string.remove_from_favorites else R.string.add_to_favorites),
                tint = tint,
                modifier = Modifier
                    .size(iconSize)
                    .moreOptionsMark(tint),
            )
        }
        FavoriteKindsMenu(
            expanded = menuOpen,
            kinds = kinds.orEmpty(),
            onKindClick = {
                menuOpen = false
                onKindClick(it)
            },
            onDismiss = { menuOpen = false },
        )
    }
}

/** What a text can be kept with, checked if it's kept with it; picking one adds it, or removes it. */
@Composable
private fun FavoriteKindsMenu(
    expanded: Boolean,
    kinds: Set<FavoriteKind>,
    onKindClick: (FavoriteKind) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.favorite_keep_as),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        for (kind in FavoriteKind.entries) {
            DropdownMenuItem(
                text = { Text(stringResource(kind.label)) },
                leadingIcon = { Icon(painterResource(kind.icon), contentDescription = null) },
                trailingIcon = { Checkbox(checked = kind in kinds, onCheckedChange = null) },
                onClick = { onKindClick(kind) },
            )
        }
    }
}

@get:StringRes
val FavoriteKind.label: Int
    get() = when (this) {
        FavoriteKind.DICTIONARY -> R.string.favorite_kind_dictionary
        FavoriteKind.AI -> R.string.favorite_kind_ai
        FavoriteKind.MACHINE -> R.string.favorite_kind_machine
    }

@get:DrawableRes
val FavoriteKind.icon: Int
    get() = when (this) {
        FavoriteKind.DICTIONARY -> R.drawable.ic_dictionary
        FavoriteKind.AI -> R.drawable.ic_translate
        FavoriteKind.MACHINE -> R.drawable.ic_language
    }
