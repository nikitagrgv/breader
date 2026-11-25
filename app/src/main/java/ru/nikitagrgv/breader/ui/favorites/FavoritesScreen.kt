package ru.nikitagrgv.breader.ui.favorites

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.nikitagrgv.breader.R
import ru.nikitagrgv.breader.data.Favorite
import ru.nikitagrgv.breader.data.FavoriteKind

/**
 * The favorite words and phrases with their meanings and translations. A tap shows one in detail; a long press starts
 * selecting them, e.g. to remove several at once.
 * @param onGoToPlace shows where in the book a favorite was selected.
 * @param onRemove gets the [Favorite.key]s.
 * @param onLookUp opens the dictionaries to look up a word the user types.
 */
@Composable
fun FavoritesScreen(
    favorites: List<Favorite>,
    contentPadding: PaddingValues,
    onOpen: (Favorite) -> Unit,
    onGoToPlace: (Favorite) -> Unit,
    onRemove: (Set<String>) -> Unit,
    onLookUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keys of the selected favorites; a list, as a set isn't saveable as is.
    var selectedKeys by rememberSaveable { mutableStateOf(emptyList<String>()) }
    // Removed ones can't stay selected.
    val selection = selectedKeys.filterTo(LinkedHashSet()) { key -> favorites.any { it.key == key } }
    val selecting = selection.isNotEmpty()
    var confirming by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = selecting) { selectedKeys = emptyList() }

    fun toggle(favorite: Favorite) {
        selectedKeys = if (favorite.key in selection) selection.minus(favorite.key).toList() else selection.plus(favorite.key).toList()
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        if (selecting) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                IconButton(onClick = { selectedKeys = emptyList() }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.cancel))
                }
                Text(
                    text = pluralStringResource(R.plurals.favorites_selected, selection.size, selection.size),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                )
                IconButton(onClick = { selectedKeys = favorites.map { it.key } }) {
                    Icon(painterResource(R.drawable.ic_select_all), contentDescription = stringResource(R.string.select_all))
                }
                IconButton(onClick = { confirming = true }) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.remove_from_favorites))
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
                Text(
                    text = stringResource(R.string.favorites_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp),
                )
                IconButton(onClick = onLookUp) {
                    Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.look_up_word))
                }
            }
        }

        if (favorites.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.favorites_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(favorites, key = { it.key }) { favorite ->
                    val checked = favorite.key in selection
                    ListItem(
                        headlineContent = { Text(favorite.word, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            // What it's kept with, in the order of the brief.
                            Row {
                                for (kind in listOf(FavoriteKind.AI, FavoriteKind.DICTIONARY, FavoriteKind.MACHINE)) {
                                    if (kind !in favorite.kinds) continue
                                    Icon(
                                        painterResource(kind.icon),
                                        contentDescription = stringResource(kind.label),
                                        modifier = Modifier
                                            .padding(top = 2.dp, end = 6.dp)
                                            .size(16.dp),
                                    )
                                }
                                Text(
                                    text = favorite.brief.ifEmpty { stringResource(R.string.favorite_no_translation) },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        leadingContent = if (selecting) {
                            { Checkbox(checked = checked, onCheckedChange = null) }
                        } else {
                            null
                        },
                        trailingContent = favorite.place?.takeUnless { selecting }?.let {
                            {
                                IconButton(onClick = { onGoToPlace(favorite) }) {
                                    Icon(painterResource(R.drawable.ic_book), contentDescription = stringResource(R.string.favorite_go_to_place))
                                }
                            }
                        },
                        colors = if (checked) {
                            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        } else {
                            ListItemDefaults.colors()
                        },
                        modifier = Modifier
                            .animateItem()
                            .combinedClickable(
                                role = if (selecting) Role.Checkbox else Role.Button,
                                onLongClickLabel = stringResource(R.string.select),
                                onLongClick = { toggle(favorite) },
                                onClick = { if (selecting) toggle(favorite) else onOpen(favorite) },
                            ),
                    )
                }
            }
        }
    }

    if (confirming) {
        val single = favorites.singleOrNull { it.key in selection }
        RemoveFavoritesDialog(
            word = single?.word,
            count = selection.size,
            onConfirm = {
                onRemove(selection)
                selectedKeys = emptyList()
                confirming = false
            },
            onDismiss = { confirming = false },
        )
    }
}

/**
 * @param word the text to remove, if it's one; otherwise [count] is shown.
 * @param kind what to remove from the favorite with the [word], if not the whole of it.
 */
@Composable
fun RemoveFavoritesDialog(word: String?, count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit, kind: FavoriteKind? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    word != null && kind != null -> stringResource(R.string.favorite_remove_kind_title, word, stringResource(kind.label))
                    word != null -> stringResource(R.string.favorite_remove_title, word)
                    else -> pluralStringResource(R.plurals.favorites_remove_title, count, count)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.remove)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
