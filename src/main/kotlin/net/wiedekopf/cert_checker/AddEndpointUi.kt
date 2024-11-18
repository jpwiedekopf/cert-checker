package net.wiedekopf.cert_checker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.oshai.kotlinlogging.KotlinLogging
import net.wiedekopf.cert_checker.model.Endpoint
import net.wiedekopf.cert_checker.model.EndpointTable
import org.apache.commons.validator.routines.DomainValidator
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction

private val logger = KotlinLogging.logger {}

@Composable
fun AddEndpointUi(database: Database, onChangeDb: () -> Unit, focusRequester: FocusRequester) {

    val endpointHostname = remember { mutableStateOf("") }
    val endpointPort = remember { mutableStateOf("443") }

    var feedbackMessage by remember {
        mutableStateOf<String?>(null)
    }
    var feedbackIsError by remember {
        mutableStateOf<Boolean?>(null)
    }

    if (feedbackIsError != null && feedbackMessage != null) {
        val icon = when (feedbackIsError!!) {
            true -> Icons.Default.Warning
            else -> Icons.Default.Add
        }
        val iconColor = when (feedbackIsError!!) {
            true -> colorScheme.error
            else -> colorScheme.primary
        }
        val title = when (feedbackIsError) {
            true -> "Error"
            else -> "Success"
        }
        val onDismiss: () -> Unit = {
            feedbackMessage = null
            feedbackIsError = null
        }
        AlertDialog(
            icon = {
                Icon(icon, contentDescription = null, tint = iconColor)
            },
            title = {
                Text(text = title)
            },
            text = {
                Text(text = feedbackMessage!!)
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
            onValueChange = { endpointHostname.value = it },
            label = {
                Text(
                    "Hostname",
                    style = TextStyle(color = if (hostnameIsError(endpointHostname.value)) colorScheme.error else colorScheme.onPrimaryContainer)
                )
            },
            modifier = Modifier.weight(0.59f).focusRequester(focusRequester).focusTarget(),
            isError = endpointHostname.value.isNotBlank() && hostnameIsError(endpointHostname.value)
        )
        TextField(
            value = endpointPort.value,
            onValueChange = {
                endpointPort.value = it
            },
            label = { Text("Port") },
            modifier = Modifier.weight(0.19f),
            isError = endpointPort.value.isNotBlank() && portIsError(endpointPort.value)
        )
        Button(
            onClick = {
                val (resultMessage, resultError) = addEntryToDb(database, endpointHostname.value, endpointPort.value)
                feedbackMessage = resultMessage
                feedbackIsError = resultError
                if (!resultError) {
                    endpointHostname.value = ""
                    endpointPort.value = "443"
                    onChangeDb()
                }
            },
            modifier = Modifier.weight(0.19f).focusRequester(focusRequester).focusTarget(),
            enabled = !hostnameIsError(endpointHostname.value) && !portIsError(endpointPort.value)
        ) {
            Text("Add")
        }
    }
}

private fun addEntryToDb(database: Database, hostname: String, port: String): Pair<String, Boolean> {
    logger.info { "Adding entry to DB: $hostname:$port" }
    val portInt = port.toInt()
    val existing = transaction(database) {
        Endpoint.find {
            EndpointTable.name eq hostname and (EndpointTable.port eq portInt)
        }.firstOrNull()
    }
    if (existing != null) {
        return "Entry already exists" to true
    }
    transaction(database) {
        Endpoint.new {
            name = hostname
            this.port = portInt
        }
    }
    return "Entry added: $hostname:$port" to false
}

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
