package net.wiedekopf.cert_checker

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.unit.dp
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.wiedekopf.cert_checker.checker.Checker
import net.wiedekopf.cert_checker.checker.onRequestCheck
import net.wiedekopf.cert_checker.checker.onRequestCheckAll
import net.wiedekopf.cert_checker.model.Endpoint
import net.wiedekopf.cert_checker.model.EndpointTable
import net.wiedekopf.cert_checker.model.SortMode
import org.apache.commons.validator.routines.DomainValidator
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.lang.Thread.sleep

private val logger = KotlinLogging.logger {}

@Suppress("FunctionName")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopUi(
    database: Database,
    endpointList: SnapshotStateList<Endpoint>,
    checker: Checker,
    onChangeDb: () -> Unit,
    focusRequester: FocusRequester,
    coroutineScope: CoroutineScope,
    toggleDarkTheme: () -> Unit,
    onClickSort: () -> Unit,
    changeSearch: (String?) -> Unit,
    sortMode: SortMode,
    isDarkTheme: Boolean
) {

    val endpointHostname = remember { mutableStateOf("") }
    val endpointPort = remember { mutableStateOf("443") }

    val feedbackBus = remember {
        mutableStateListOf<FeedbackMessage>()
    }

    var checkAllProgress: Int? by remember { mutableStateOf(null) }

    if (feedbackBus.isNotEmpty()) {
        val ourMessage = feedbackBus.first()
        val icon = when (ourMessage.isError) {
            true -> Icons.Default.Warning
            else -> Icons.Default.Add
        }
        val iconColor = when (ourMessage.isError) {
            true -> colorScheme.error
            else -> colorScheme.primary
        }
        val onDismiss: () -> Unit = {
            feedbackBus.removeFirst()
        }
        AlertDialog(
            icon = {
                Icon(icon, contentDescription = null, tint = iconColor)
            },
            title = {
                Text(text = ourMessage.title)
            },
            text = {
                Text(text = ourMessage.message)
            },
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = onDismiss
                ) {
                    Text("OK")
                }
            },
            dismissButton = null
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = endpointHostname.value,
            onValueChange = {
                endpointHostname.value = it
                changeSearch(it)
            },
            label = {
                Text("Hostname")
            },
            modifier = Modifier.weight(0.6f).focusRequester(focusRequester).focusable(),
        )
        TextField(
            value = endpointPort.value,
            onValueChange = {
                endpointPort.value = it
            },
            label = { Text("Port") },
            modifier = Modifier.width(100.dp).focusRequester(focusRequester).focusable(),
            isError = endpointPort.value.isNotBlank() && portIsError(endpointPort.value)
        )
        Button(
            onClick = {
                val result = addEntryToDb(database, endpointHostname.value, endpointPort.value)
                if (result.endpoint != null) {
                    coroutineScope.launch {
                        onRequestCheck(checker, result.endpoint, onChangeDb, onError = {
                            feedbackBus.add(
                                FeedbackMessage(
                                    message = "Error checking ${it.host}:${it.port}: ${it.message}",
                                    isError = true,
                                    title = "Error"
                                )
                            )
                        })
                    }
                }
                if (!result.isError) {
                    endpointHostname.value = ""
                    endpointPort.value = "443"
                    onChangeDb()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = colorScheme.primaryContainer,
                contentColor = colorScheme.onPrimaryContainer,
                disabledContainerColor = colorScheme.primaryContainer.copy(alpha = 0.5f),
                disabledContentColor = colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
            ),
            modifier = Modifier.width(IntrinsicSize.Min).focusRequester(focusRequester).focusTarget(),
            enabled = !hostnameIsError(endpointHostname.value) && !portIsError(endpointPort.value) && endpointList.none {
                it.port == endpointPort.value.toInt() && it.name == endpointHostname.value
            }
        ) {
            Text("Add")
        }
        Button(
            onClick = {
                checkAllProgress = 0
                coroutineScope.launch {
                    onRequestCheckAll(
                        allEndpoints = endpointList,
                        checker = checker,
                        onChangeDb = onChangeDb,
                        onError = {
                            feedbackBus.add(
                                FeedbackMessage(
                                    message = "Error checking ${it.host}:${it.port}: ${it.message}",
                                    isError = true,
                                    title = "Error"
                                )
                            )
                        },
                        onResult = { _ ->
                            checkAllProgress = checkAllProgress?.let {
                                it + 1
                            } ?: 1
                        }
                    )
                    sleep(2000)
                    checkAllProgress = null
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.secondary, contentColor = colorScheme.onSecondary),
            enabled = endpointList.isNotEmpty()
        ) {
            Text("Check all")
        }
        IconButton(
            onClick = onClickSort,
        ) {
            TooltipArea(tooltip = {
                Surface(
                    modifier = Modifier.shadow(4.dp).padding(4.dp),
                    color = colorScheme.primaryContainer, shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "Sort mode, currently set to ${sortMode.title}",
                        color = colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }) {
                Icon(
                    when (sortMode) {
                        SortMode.ID -> Icons.Default.FormatListNumbered
                        SortMode.ALPHABETIC -> Icons.Default.SortByAlpha
                        SortMode.EXPIRY -> Icons.Default.Event
                    }, contentDescription = "Sort order", tint = colorScheme.primary
                )
            }

        }
        IconButton(
            onClick = toggleDarkTheme
        ) {
            Icon(
                imageVector = when (isDarkTheme) {
                    false -> Icons.Default.DarkMode
                    true -> Icons.Default.LightMode

                }, contentDescription = "Toggle Dark Mode", tint = colorScheme.inversePrimary
            )
        }
    }
    when {
        checkAllProgress != null -> {
            LinearProgressIndicator(
                progress = { checkAllProgress?.toFloat()?.div(endpointList.size) ?: 1f },
                modifier = Modifier.fillMaxWidth().padding(2.dp).height(6.dp)
            )
        }

        else -> {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

data class FeedbackMessage(
    val message: String,
    val isError: Boolean,
    val title: String
)

private fun addEntryToDb(database: Database, hostname: String, port: String): AddResult {
    logger.info { "Adding entry to DB: $hostname:$port" }
    val portInt = port.toInt()
    val existing = transaction(database) {
        Endpoint.find {
            EndpointTable.name eq hostname and (EndpointTable.port eq portInt)
        }.firstOrNull()
    }
    if (existing != null) {
        return AddResult(endpoint = null, message = "Entry already exists", isError = true)
    }
    val newEndpoint = transaction(database) {
        Endpoint.new {
            name = hostname
            this.port = portInt
        }
    }
    return AddResult(endpoint = newEndpoint, message = "Entry added: $hostname:$port", isError = false)
}

private data class AddResult(val endpoint: Endpoint?, val message: String, val isError: Boolean)

private fun hostnameIsError(value: String): Boolean {
    val validator = DomainValidator.getInstance()
    return !validator.isValid(value)
}

private fun portIsError(value: String): Boolean {
    try {
        val intValue = value.toInt()
        return intValue !in 1..65535
    } catch (e: NumberFormatException) {
        logger.error(e) { "Port is not a number" }
        return true
    }
}
