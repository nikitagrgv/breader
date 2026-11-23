package ru.nikitagrgv.breader.ui.settings

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.nikitagrgv.breader.R
import ru.nikitagrgv.breader.translation.RequestKind
import ru.nikitagrgv.breader.translation.RequestUsage
import ru.nikitagrgv.breader.translation.UsageLog
import ru.nikitagrgv.breader.ui.translation.errorText
import ru.nikitagrgv.breader.ui.translation.shortModelName
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.NumberFormat
import kotlin.math.abs

/** A full-screen view of what's left on the OpenRouter key, and of the requests made with it. */
@Composable
fun UsageDialog(
    apiKey: String,
    onDismiss: () -> Unit,
    viewModel: UsageViewModel = viewModel(factory = UsageViewModel.Factory),
) {
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    val requests by viewModel.requests.collectAsStateWithLifecycle()
    var confirmingClear by rememberSaveable { mutableStateOf(false) }
    // Anew each time it's opened: the key may be used elsewhere too.
    LaunchedEffect(apiKey) { viewModel.refreshBalance(apiKey) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.close))
                    }
                    Text(
                        text = stringResource(R.string.usage_dialog_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                    )
                    IconButton(onClick = { viewModel.refreshBalance(apiKey) }, enabled = balance != Balance.Loading) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.refresh))
                    }
                }
                HorizontalDivider()

                LazyColumn(Modifier.fillMaxSize()) {
                    item(key = "balance") {
                        BalanceSection(balance, onRetry = { viewModel.refreshBalance(apiKey) })
                    }
                    item(key = "requests") {
                        RequestsHeader(
                            count = requests?.totals?.count ?: 0,
                            cost = requests?.totals?.cost ?: 0.0,
                            onClear = { confirmingClear = true },
                        )
                    }
                    val shown = requests
                    when {
                        shown == null -> item(key = "loading") { Loading() }
                        shown.requests.isEmpty() -> item(key = "empty") {
                            Text(
                                text = stringResource(R.string.usage_requests_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        else -> {
                            items(shown.requests, key = { it.id }) { RequestItem(it.usage) }
                            if (!shown.endReached) {
                                item(key = "more") {
                                    // Seen when scrolled to the end: the next page is read then.
                                    LaunchedEffect(shown.requests.size) { viewModel.loadMore() }
                                    Loading()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { Text(stringResource(R.string.usage_clear_title)) },
            text = { Text(stringResource(R.string.usage_clear_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clear()
                        confirmingClear = false
                    },
                ) { Text(stringResource(R.string.usage_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClear = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun BalanceSection(balance: Balance, onRetry: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        when (balance) {
            Balance.Loading -> Loading()
            is Balance.Failed -> {
                Text(
                    text = stringResource(R.string.usage_balance_failed, errorText(balance.error)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            is Balance.Loaded -> LoadedBalance(balance)
        }
    }
}

@Composable
private fun LoadedBalance(balance: Balance.Loaded) {
    val key = balance.key
    val credits = balance.credits
    val remaining = balance.remaining
    Text(
        text = stringResource(R.string.usage_left),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = if (remaining != null) formatUsd(remaining) else stringResource(R.string.usage_no_limit),
        style = MaterialTheme.typography.displaySmall,
        color = if (remaining != null && remaining <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
    if (key.limitRemaining != null && credits != null) {
        Text(
            text = stringResource(R.string.usage_left_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Spacer(Modifier.height(12.dp))
    InfoRow(
        R.string.usage_key_limit,
        if (key.limit != null && key.limitRemaining != null) {
            stringResource(R.string.usage_amount_left, formatUsd(key.limitRemaining), formatUsd(key.limit))
        } else {
            stringResource(R.string.usage_key_limit_none)
        },
    )
    if (key.limit != null && key.limitReset != null) {
        InfoRow(
            R.string.usage_limit_reset,
            when (key.limitReset) {
                "daily" -> stringResource(R.string.usage_reset_daily)
                "weekly" -> stringResource(R.string.usage_reset_weekly)
                "monthly" -> stringResource(R.string.usage_reset_monthly)
                else -> key.limitReset
            },
        )
    }
    if (credits != null) {
        InfoRow(R.string.usage_credits, stringResource(R.string.usage_amount_left, formatUsd(credits.remaining), formatUsd(credits.total)))
    }
    key.freeRequests?.let { free ->
        val format = NumberFormat.getIntegerInstance()
        InfoRow(R.string.usage_free_requests, stringResource(R.string.usage_free_requests_value, format.format(free.used), format.format(free.limit)))
    }
    key.expiresAt?.let { expiresAt ->
        val context = LocalContext.current
        val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_TIME
        InfoRow(R.string.usage_expires, DateUtils.formatDateTime(context, expiresAt.toEpochMilli(), flags))
    }

    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.usage_spent), style = MaterialTheme.typography.titleSmall)
    InfoRow(R.string.usage_today, formatUsd(key.usageDaily))
    InfoRow(R.string.usage_week, formatUsd(key.usageWeekly))
    InfoRow(R.string.usage_month, formatUsd(key.usageMonthly))
    InfoRow(R.string.usage_all_time, formatUsd(key.usage))
    Text(
        text = stringResource(R.string.usage_utc_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun InfoRow(@StringRes label: Int, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RequestsHeader(count: Int, cost: Double, onClear: () -> Unit) = Column {
    HorizontalDivider()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.usage_requests),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = pluralStringResource(R.plurals.usage_requests_total, count, count, formatUsd(cost)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        TextButton(onClick = onClear, enabled = count > 0) {
            Text(stringResource(R.string.usage_clear))
        }
    }
    Text(
        text = stringResource(R.string.usage_requests_hint, NumberFormat.getIntegerInstance().format(UsageLog.MAX_SIZE)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun RequestItem(usage: RequestUsage) {
    val context = LocalContext.current
    val time = DateUtils.formatDateTime(
        context,
        usage.time,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
    )
    val tokens = NumberFormat.getIntegerInstance()
    ListItem(
        overlineContent = {
            Text(
                text = stringResource(
                    R.string.usage_request_title,
                    stringResource(
                        when (usage.kind) {
                            RequestKind.TRANSLATION -> R.string.usage_kind_translation
                            RequestKind.GRAMMAR -> R.string.usage_kind_grammar
                        },
                    ),
                    usage.model.shortModelName(),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        headlineContent = { Text(usage.fragment, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(stringResource(R.string.usage_request_details, time, tokens.format(usage.promptTokens), tokens.format(usage.completionTokens)))
        },
        trailingContent = {
            Text(
                text = when (usage.cost) {
                    null -> stringResource(R.string.usage_cost_unknown)
                    0.0 -> stringResource(R.string.price_free)
                    else -> formatUsd(usage.cost)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (usage.cost == 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        },
    )
}

@Composable
private fun Loading() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        CircularProgressIndicator()
    }
}

/**
 * "$12.35", "$0.45", "$0.012", "$0.0000035": cents for a dollar or more, two significant digits below, so that a request
 * costing a fraction of a cent isn't shown as $0.00.
 */
internal fun formatUsd(amount: Double): String {
    if (!amount.isFinite()) return "?"
    val value = BigDecimal(abs(amount))
    val rounded = when {
        amount == 0.0 -> BigDecimal.ZERO.setScale(2)
        value >= BigDecimal.ONE -> value.setScale(2, RoundingMode.HALF_UP)
        else -> value.round(MathContext(2, RoundingMode.HALF_UP)).stripTrailingZeros().let { if (it.scale() < 2) it.setScale(2) else it }
    }
    return (if (amount < 0) "-$" else "$") + rounded.toPlainString()
}
