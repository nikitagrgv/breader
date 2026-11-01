package ru.nikitagrgv.breader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * [ModalBottomSheet] that the back button closes at once. The sheet itself only collapses to half the
 * screen when it's expanded, so that it takes two presses to close.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheet(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState) {
        // The sheet is a dialog, whose back callbacks are dispatched to this one before the sheet's own.
        BackHandler {
            scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismissRequest() }
        }
        content()
    }
}
