@file:Suppress("FunctionName")

package net.wiedekopf.cert_checker

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import net.wiedekopf.cert_checker.checker.CheckError
import net.wiedekopf.cert_checker.checker.Checker
import net.wiedekopf.cert_checker.checker.onRequestCheck
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
                    val deleted = candidateForDeletion.value!!.details.onEach {
                        it.delete()
                    }
                    candidateForDeletion.value!!.delete()
                    logger.warn { "Deleted entity with ID ${candidateForDeletion.value!!.id.value} and ${deleted.count()} certificate entrie(-s)" }
                }
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
    val scrollState = rememberScrollState()

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

    EndpointListContent(
        allEndpoints,
        displayedInstant,
        checker,
        coroutineScope,
        scrollState,
        onChangeDb,
        candidateForDeletion,
        onShowDetails = { endpoint, detailsString ->
            detailsData = endpoint to detailsString
        },
        createError
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.EndpointListContent(
    allEndpoints: SnapshotStateList<Endpoint>,
    displayedInstant: Instant,
    checker: Checker,
    coroutineScope: CoroutineScope,
    scrollState: ScrollState,
    onChangeDb: () -> Unit,
    candidateForDeletion: MutableState<Endpoint?>,
    onShowDetails: (Endpoint, String) -> Unit,
    createError: (CheckError) -> Unit
) {

    Row(modifier = Modifier.fillMaxWidth().weight(1f, fill = true)) {
        FlowRow(
            modifier = Modifier.weight(1f).verticalScroll(scrollState).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            allEndpoints.forEach { endpoint ->
                EndpointCard(
                    endpoint = endpoint,
                    displayedInstant = displayedInstant,
                    onCheckClick = {
                        coroutineScope.launch {
                            onRequestCheck(
                                checker = checker,
                                endpoint = endpoint,
                                onChangeDb = onChangeDb,
                                onError = createError
                            )
                        }
                    },
                    onClickDelete = {
                        candidateForDeletion.value = endpoint
                    },
                    onClickShowDetails = {
                        val detailsData = transaction {
                            endpoint.details.firstOrNull()?.certificateInfo
                        }
                        if (detailsData != null) {
                            onShowDetails(endpoint, detailsData)
                        }
                    }
                )
            }
        }
        VerticalScrollbar(
            modifier = Modifier.width(16.dp).padding(4.dp).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowScope.EndpointCard(
    endpoint: Endpoint,
    displayedInstant: Instant,
    onCheckClick: () -> Unit,
    onClickDelete: () -> Unit,
    onClickShowDetails: () -> Unit
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

    val expiryPercent by remember(endpointDetailsState) {
        mutableStateOf(
            endpointDetailsState?.let { state ->
                expiryPercent(
                    notBefore = state.notBefore,
                    notAfter = state.notAfter,
                    now = displayedInstant
                )
            }
        )
    }
    val details by remember(endpointDetailsState) {
        mutableStateOf(
            endpointDetailsState?.let { state ->
                val lastCheck = endpoint.lastCheck?.toString() ?: buildAnnotatedString {
                    withStyle(style = SpanStyle(fontStyle = FontStyle.Italic)) {
                        append("Never")
                    }
                }
                mapOf(
                    "Last check" to lastCheck,
                    "Type" to state.authType,
                    "DN" to state.dn,
                    "Not before" to buildAnnotatedString {
                        append(state.notBefore.toString())
                        withStyle(style = SpanStyle(fontStyle = FontStyle.Italic)) {
                            val delta = state.notBefore.formatDeltaDays(displayedInstant)
                            append(" ($delta)")
                        }
                    },
                    "Not after" to buildAnnotatedString {
                        append(state.notAfter.toString())
                        withStyle(style = SpanStyle(fontStyle = FontStyle.Italic)) {
                            val delta = state.notAfter.formatDeltaDays(displayedInstant)
                            append(" ($delta)")
                        }
                    },
                    "Issuer" to endpointDetailsState!!.issuer
                )
            }
        )
    }
    Card(modifier = Modifier.padding(4.dp).fillMaxRowHeight().weight(1f)) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontSize = MaterialTheme.typography.headlineSmall.fontSize.times(0.7f))) {
                    append("#${endpoint.id.value + 1}")
                }
                append(" ")
                withStyle(MaterialTheme.typography.headlineSmall.toSpanStyle()) {
                    append("${endpoint.name}:${endpoint.port}")
                }
            },
            style = TextStyle(textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )
        details?.let { dets ->
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                AttributeTable(attributes = dets, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                expiryPercent?.let { expiry ->
                    Column(modifier = Modifier.width(IntrinsicSize.Min)) {
                        CircularProgressIndicator(
                            progress = {
                                expiry
                            },
                            modifier = Modifier.size(32.dp),
                            trackColor = colorScheme.primary.copy(alpha = 0.25f),
                            color = colorScheme.error.copy(alpha = expiry)
                        )
                        Text(
                            "${(expiry * 100).roundToInt()}% expired",
                            style = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.Center)
                        )
                    }
                }
            }
        } ?: run {
            Text(
                "No data available",
                style = MaterialTheme.typography.bodyLarge.copy(
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth().padding(8.dp).fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    onCheckClick()
                    buttonContentState = ButtonContentState.DONE
                },
                modifier = Modifier.width(80.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                when (buttonContentState) {
                    ButtonContentState.TEXT -> Text("Check")
                    ButtonContentState.DONE ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            Icon(
                                Icons.Default.Done,
                                contentDescription = "Done",
                                tint = colorScheme.secondary
                            )
                        }
                }

            }
            TextButton(
                onClick = {
                    onClickShowDetails()
                }
            ) {
                Text("View details")
            }
            IconButton(onClick = {
                onClickDelete()
            }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colorScheme.error)
            }
        }
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
fun AttributeTable(
    attributes: Map<String, CharSequence?>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        attributes.entries.forEachIndexed { index, (key, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.Start),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
            if (index < attributes.size - 1) {
                HorizontalDivider(thickness = 1.dp, color = colorScheme.onSurface.copy(alpha = 0.4f))
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
    return (1f - (remaining.toFloat() / total.toFloat())).coerceAtMost(1f)
}

enum class ButtonContentState {
    TEXT,
    DONE
}