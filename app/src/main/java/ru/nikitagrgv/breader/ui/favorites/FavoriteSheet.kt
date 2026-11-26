package ru.nikitagrgv.breader.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ru.nikitagrgv.breader.R
import ru.nikitagrgv.breader.data.Favorite
import ru.nikitagrgv.breader.data.FavoriteKind
import ru.nikitagrgv.breader.translation.Excerpt
import ru.nikitagrgv.breader.translation.MachineTranslationService
import ru.nikitagrgv.breader.translation.combine
import ru.nikitagrgv.breader.ui.BottomSheet
import ru.nikitagrgv.breader.ui.translation.Translations

/**
 * A favorite in detail: all it's kept with, the text around it in the book, and the way back there. The star adds what
 * it isn't kept with yet, by a long press, as anywhere else.
 * @param bookTitle the title of the book it was selected in, if it's in the library; null if it isn't any more, and so
 * can't be gone back to.
 * @param saving whether something is being added to it.
 * @param onOpenDictionary shows what the dictionaries have for it, in full.
 */
@Composable
fun FavoriteSheet(
    favorite: Favorite,
    bookTitle: String?,
    saving: Boolean,
    onFavorite: () -> Unit,
    onFavoriteKind: (FavoriteKind) -> Unit,
    onOpenDictionary: () -> Unit,
    onGoToPlace: () -> Unit,
    onDismiss: () -> Unit,
) {
    BottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 12.dp, bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = favorite.word,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                FavoriteButton(kinds = favorite.kinds, saving = saving, onClick = onFavorite, onKindClick = onFavoriteKind)
            }
            Column(verticalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(top = 16.dp, end = 12.dp)) {
                val kinds = favorite.kinds
                // The translation fitting the context first, as in the list.
                if (FavoriteKind.AI in kinds) {
                    Section(FavoriteKind.AI) {
                        val combined = remember(favorite.translations) { combine(favorite.translations) }
                        // Lets the translations be copied.
                        SelectionContainer { Translations(combined) }
                    }
                }
                favorite.meanings?.let { meanings ->
                    Section(FavoriteKind.DICTIONARY) {
                        SelectionContainer { Text(meanings, style = MaterialTheme.typography.bodyLarge) }
                        TextButton(onClick = onOpenDictionary) { Text(stringResource(R.string.favorite_open_dictionary)) }
                    }
                }
                favorite.machine?.let { machine ->
                    Section(FavoriteKind.MACHINE, detail = stringResource(machine.service.label)) {
                        SelectionContainer { Text(machine.text, style = MaterialTheme.typography.titleLarge) }
                    }
                }
                if (kinds.isEmpty()) {
                    Text(
                        text = stringResource(R.string.favorite_plain_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                favorite.context?.takeIf { it.before.isNotBlank() || it.after.isNotBlank() }?.let { context ->
                    Column {
                        Text(
                            text = stringResource(R.string.favorite_in_the_book),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        ContextText(context)
                    }
                }
                favorite.place?.let { place ->
                    Column {
                        FilledTonalButton(onClick = onGoToPlace, enabled = bookTitle != null) {
                            Icon(painterResource(R.drawable.ic_book), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.favorite_go_to_place))
                        }
                        Text(
                            text = bookTitle ?: stringResource(R.string.favorite_book_missing, place.bookTitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** What the favorite is kept with as [kind], under its name. */
@Composable
private fun Section(kind: FavoriteKind, detail: String? = null, content: @Composable () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Icon(
                painterResource(kind.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = listOfNotNull(stringResource(kind.label), detail).joinToString(" · "),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        content()
    }
}

/** The excerpt with the fragment in bold, cut to the sentence or so around it: the models get more than that. */
@Composable
private fun ContextText(context: Excerpt) {
    val text = remember(context) {
        val before = context.before.takeLast(CONTEXT_CHARS).let { if (it.length < context.before.length) "…$it" else it }
        val after = context.after.take(CONTEXT_CHARS).let { if (it.length < context.after.length) "$it…" else it }
        buildAnnotatedString {
            append(before)
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(context.fragment) }
            append(after)
        }
    }
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

private val MachineTranslationService.label: Int
    get() = when (this) {
        MachineTranslationService.GOOGLE -> R.string.machine_google
        MachineTranslationService.MYMEMORY -> R.string.machine_mymemory
    }

private const val CONTEXT_CHARS = 160
