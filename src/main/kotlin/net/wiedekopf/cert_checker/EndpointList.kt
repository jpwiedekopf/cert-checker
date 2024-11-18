package net.wiedekopf.cert_checker


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.MaterialTheme.colors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
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
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import net.wiedekopf.cert_checker.checker.ChainCheckResult
import net.wiedekopf.cert_checker.checker.Checker
import net.wiedekopf.cert_checker.model.Endpoint
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.math.exp
import kotlin.math.roundToInt

private val logger = KotlinLogging.logger {}

@Composable
fun ColumnScope.EndpointList(
    db: Database,
    checker: Checker,
    coroutineScope: CoroutineScope,
    onChangeDb: () -> Unit,
    changeCounter: Int
) {
    val allEndpoints = remember {
        mutableStateListOf<Endpoint>()
    }
    var resultData by remember {
        mutableStateOf<ChainCheckResult?>(null)
    }
    val onDismissResultAlert = {
        resultData = null
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

    if (resultData != null) {
        ResultDataDialog(resultData, onDismissResultAlert)
    }
    LaunchedEffect(changeCounter) {
        allEndpoints.clear()
        transaction(db) {
            val endpointList = Endpoint.all().toList()
            logger.info {
                "Loaded ${endpointList.size} endpoints from DB"
            }
            allEndpoints.addAll(endpointList)
        }
    }
    val listState = rememberLazyListState()

    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), state = listState) {
        item {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = {
                        checkAllEndpoints(allEndpoints = allEndpoints, checker = checker, coroutineScope = coroutineScope) {

                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.5f),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.secondary, contentColor = colors.onSecondary),
                    enabled = allEndpoints.isNotEmpty()
                ) {
                    Text("Check all")
                }
                HorizontalDivider(thickness = 3.dp, color = colors.secondary)
            }
        }
        itemsIndexed(allEndpoints.toList()) { index, endpoint ->
            EndpointListItem(
                index = index,
                endpoint = endpoint,
                onCheckClick = { ep ->
                    coroutineScope.launch {
                        checker.checkHost(ep) {
                            resultData = it
                            if (it.clientCert != null) {
                                it.clientCert.writeToDb(endpoint)
                                onChangeDb()
                            }
                        }
                    }
                },
                displayedInstant = displayedInstant,
                onClickDelete = {
                    candidateForDeletion.value = it
                },
                onClickShowDetails = {
                    //detailsData.value = endpoint
                }
            )
            if (index < allEndpoints.size - 1) {
                HorizontalDivider(thickness = 3.dp, color = colors.primary)
            }
        }
    }
}

fun checkAllEndpoints(
    allEndpoints: SnapshotStateList<Endpoint>,
    coroutineScope: CoroutineScope,
    checker: Checker,
    onCheckResult: (ChainCheckResult) -> Unit
) {
    allEndpoints.forEach { ep ->
        logger.info { "Checking endpoint ${ep.name}:${ep.port}" }
        coroutineScope.launch {
            checker.checkHost(endpoint = ep, onResult = onCheckResult)
        }
    }
}

@Composable
private fun ResultDataDialog(resultData: ChainCheckResult?, onDismissResultAlert: () -> Unit) {
    AlertDialog(
        icon = {
            Icon(Icons.Default.Check, contentDescription = null, tint = colors.secondary)
        },
        title = {
            Text(text = "Check result")
        },
        text = {
            Text(text = resultData!!.clientCert?.certificateInfo ?: "No client cert found")
        },
        onDismissRequest = onDismissResultAlert,
        confirmButton = {
            TextButton(
                onClick = onDismissResultAlert
            ) {
                Text("OK")
            }
        },
        dismissButton = null
    )
}

@Composable
fun ConfirmDeleteDialog(endpoint: Endpoint, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        icon = {
            Icon(Icons.Default.Delete, contentDescription = null, tint = colors.error)
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
                Text("Delete", color = colors.error, fontWeight = FontWeight.Bold)
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            Button(onClick = {
                onCheckClick(endpoint)
            }) {
                Text("Check")
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
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colors.error)
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
                    withStyle(style = SpanStyle(color = colors.error)) {
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
                        expiryPercent < 0.5f -> colors.primary
                        expiryPercent < 0.75f -> colors.error.copy(alpha = 0.5f)
                        else -> colors.error
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
    return (remaining.toFloat() / total.toFloat())
}