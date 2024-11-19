package net.wiedekopf.cert_checker

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.datetime.*
import net.wiedekopf.cert_checker.checker.*
import net.wiedekopf.cert_checker.model.Endpoint
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.math.roundToInt

private val logger = KotlinLogging.logger {}

@Composable
fun ColumnScope.EndpointList(
    db: Database,
    checker: Checker,
    coroutineScope: CoroutineScope,
    onChangeDb: () -> Unit,
    onShowError: (CharSequence) -> Unit,
    changeCounter: Int,
    allEndpoints: SnapshotStateList<Endpoint>
) {
    var detailsData by remember {
        mutableStateOf<Pair<Endpoint, String>?>(null)
    }
    val onDismissDetailsData = {
        detailsData = null
    }

    val candidateForDeletion = remember {
        mutableStateOf<Endpoint?>(null)
    }
    val displayedInstant = remember(changeCounter) {
        Clock.System.now()
    }

    if (candidateForDeletion.value != null) {
        ConfirmDeleteDialog(
            endpoint = candidateForDeletion.value!!,
            onDelete = {
                transaction(db) {
                    candidateForDeletion.value!!.delete()
                }
                logger.warn { "Deleted entity with ID ${candidateForDeletion.value!!.id.value}" }
                onChangeDb()
            },
            onDismiss = {
                candidateForDeletion.value = null
            }
        )
    }

    if (detailsData != null) {
        val (endpoint, result) = detailsData!!
        ResultDataDialog(endpoint = endpoint, certificateInfo = result, onDismiss = onDismissDetailsData)
    }
    val listState = rememberLazyListState()

    val createError: (CheckError) -> Unit = {
        onShowError(buildAnnotatedString {
            append("Error checking endpoint ")
            withStyle(style = SpanStyle(fontFamily = FontFamily.Monospace)) {
                append("${it.host}:${it.port}")
            }
            appendLine(":")
            if (it.errorClass != null) {
                append(it.errorClass)
                append(" — ")
            }
            appendLine(it.message ?: "Unknown error")
        })
    }

    Row(modifier = Modifier.fillMaxWidth().weight(1f, fill = true)) {
        LazyColumn(modifier = Modifier.fillMaxHeight().weight(1f), state = listState) {
            itemsIndexed(allEndpoints.toList()) { index, endpoint ->
                EndpointListItem(
                    index = index,
                    endpoint = endpoint,
                    onCheckClick = {
                        onRequestCheck(
                            checker = checker,
                            coroutineScope = coroutineScope,
                            endpoint = endpoint,
                            onChangeDb = onChangeDb,
                            onError = createError
                        )
                    },
                    displayedInstant = displayedInstant,
                    onClickDelete = {
                        candidateForDeletion.value = it
                    },
                    onClickShowDetails = {
                        detailsData = transaction {
                            val details = it.details.firstOrNull()?.certificateInfo
                            return@transaction details?.let {
                                Pair(endpoint, details)
                            }
                        }
                    }
                )
                if (index < allEndpoints.size - 1) {
                    HorizontalDivider(thickness = 3.dp, color = colorScheme.secondary)
                }
            }
        }
        VerticalScrollbar(
            modifier = Modifier.width(16.dp).padding(4.dp).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(listState),
            style = ScrollbarStyle(
                minimalHeight = 16.dp,
                thickness = 4.dp,
                shape = RoundedCornerShape(8.dp),
                hoverColor = colorScheme.secondary.copy(alpha = 0.5f),
                unhoverColor = colorScheme.secondary.copy(alpha = 0.3f),
                hoverDurationMillis = 1000
            )
        )
    }
}


@Composable
private fun ResultDataDialog(endpoint: Endpoint, certificateInfo: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true, usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
            ) {
                Text("${endpoint.name}:${endpoint.port}", style = MaterialTheme.typography.headlineSmall)
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    SelectionContainer {
                        Text(
                            text = certificateInfo,
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            maxLines = Int.MAX_VALUE,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        )

                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Button(onClick = onDismiss) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmDeleteDialog(endpoint: Endpoint, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        icon = {
            Icon(Icons.Default.Delete, contentDescription = null, tint = colorScheme.error)
        },
        title = {
            Text("Delete endpoint?")
        },
        text = {
            Text("Do you really want to delete endpoint ${endpoint.name}:${endpoint.port}?")
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onDelete()
                    onDismiss()
                }
            ) {
                Text("Delete", color = colorScheme.error, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EndpointListItem(
    index: Int,
    endpoint: Endpoint,
    displayedInstant: Instant,
    onCheckClick: (Endpoint) -> Unit,
    onClickDelete: (Endpoint) -> Unit,
    onClickShowDetails: (Endpoint) -> Unit
) {
    val endpointDetailsState by remember(endpoint) {
        mutableStateOf(transaction {
            endpoint.details.firstOrNull()
        })
    }
    var buttonContentState by remember { mutableStateOf(ButtonContentState.TEXT) }
    LaunchedEffect(buttonContentState) {
        if (buttonContentState == ButtonContentState.DONE) {
            delay(2000)
            buttonContentState = ButtonContentState.TEXT
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row {
                Text(text = "${index + 1}.")
                Text(text = "Name:")
                SelectionContainer {
                    Text(text = buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)) {
                            append(endpoint.name)
                        }
                        append(":")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(endpoint.port.toString())
                        }
                    })
                }
            }
            Row {
                OutlinedButton(
                    onClick = {
                        onCheckClick(endpoint)
                        buttonContentState = ButtonContentState.DONE
                    },
                    modifier = Modifier.width(100.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Crossfade(buttonContentState) { fadedValue ->
                            when (fadedValue) {
                                ButtonContentState.TEXT -> Text("Check")
                                ButtonContentState.DONE -> Icon(Icons.Default.Done, contentDescription = "Done", tint = colorScheme.secondary)
                            }
                        }
                    }

                }
                OutlinedButton(
                    onClick = {
                        onClickShowDetails(endpoint)
                    }
                ) {
                    Text("View details")
                }
                IconButton(onClick = {
                    onClickDelete(endpoint)
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colorScheme.error)
                }
            }
        }
        if (endpointDetailsState != null) {
            val lastCheck = endpoint.lastCheck?.toString() ?: buildAnnotatedString {
                withStyle(style = SpanStyle(fontStyle = FontStyle.Italic)) {
                    append("Never")
                }
            }
            val attributes = mapOf(
                "Last check" to lastCheck,
                "Type" to endpointDetailsState!!.authType,
                "DN" to endpointDetailsState!!.dn,
                "Not before" to buildAnnotatedString {
                    append(endpointDetailsState!!.notBefore.toString())
                    withStyle(style = SpanStyle(fontStyle = FontStyle.Italic)) {
                        val delta = endpointDetailsState!!.notBefore.formatDeltaDays(displayedInstant)
                        append(" ($delta)")
                    }
                },
                "Not after" to buildAnnotatedString {
                    append(endpointDetailsState!!.notAfter.toString())
                    withStyle(style = SpanStyle(fontStyle = FontStyle.Italic)) {
                        val delta = endpointDetailsState!!.notAfter.formatDeltaDays(displayedInstant)
                        append(" ($delta)")
                    }
                },
                "Issuer" to endpointDetailsState!!.issuer
            )
            AttributeTable(attributes = attributes)
            val expiryPercent = expiryPercent(
                notBefore = endpointDetailsState!!.notBefore,
                notAfter = endpointDetailsState!!.notAfter,
                now = displayedInstant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(buildAnnotatedString {
                    append(expiryPercent.times(100).roundToInt().toString())
                    append("% expired")
                    withStyle(style = SpanStyle(color = colorScheme.error)) {
                        when {
                            expiryPercent >= 0.5f && expiryPercent < 0.75f -> append(" (!)")
                            expiryPercent >= 0.75f -> append(" (!!!)")
                        }
                    }
                })
                LinearProgressIndicator(
                    progress = {
                        expiryPercent
                    },
                    modifier = Modifier.weight(1f),
                    color = when {
                        expiryPercent < 0.5f -> colorScheme.primary
                        expiryPercent < 0.75f -> colorScheme.error.copy(alpha = 0.5f)
                        else -> colorScheme.error
                    }
                )
            }
        }
    }
}

@Composable
fun AttributeTable(
    attributes: Map<String, CharSequence?>
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        attributes.forEach { (key, value) ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.Start)) {
                Text(text = "${key}:", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = true))
                Box(modifier = Modifier.weight(5f, fill = true)) {
                    SelectionContainer {
                        Text(text = buildAnnotatedString {
                            if (value != null) {
                                withStyle(style = SpanStyle(fontFamily = FontFamily.Monospace)) {
                                    append(value)
                                }
                            } else {
                                withStyle(style = SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append("Unknown")
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                    }
                }
            }
        }
    }
}

fun Instant.formatDeltaDays(from: Instant): String {
    return when {
        this < from -> "${this.daysUntil(from, TimeZone.UTC)} days ago"
        this > from -> "in ${from.daysUntil(this, TimeZone.UTC)} days"
        else -> "now"
    }
}

fun expiryPercent(notBefore: Instant, notAfter: Instant, now: Instant): Float {
    val total = notAfter.daysUntil(notBefore, TimeZone.UTC)
    val remaining = notAfter.daysUntil(now, TimeZone.UTC)
    return 1f - (remaining.toFloat() / total.toFloat())
}

enum class ButtonContentState {
    TEXT,
    DONE
}