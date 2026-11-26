package ru.nikitagrgv.breader.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable

/**
 * [ModalBottomSheet] that opens at the height of its content, up to the whole screen, rather than at half of it.
 * Having no half-open state, it's also closed by a single back press: an expanded sheet would only collapse to it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheet(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        content = content,
    )
}
