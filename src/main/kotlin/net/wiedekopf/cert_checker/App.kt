package net.wiedekopf.cert_checker

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.AnnotatedString
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import net.wiedekopf.cert_checker.checker.Checker
import net.wiedekopf.cert_checker.model.Endpoint
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction

private val logger = KotlinLogging.logger {}

@Composable
fun ColumnScope.App(db: Database, checker: Checker, coroutineScope: CoroutineScope, toggleDarkTheme: () -> Unit, isDarkTheme: Boolean) {
    MaterialTheme {
        var changeCounter by remember {
            mutableStateOf(0)
        }
        val onChangeDb = {
            changeCounter += 1
            logger.debug { "Database at revision $changeCounter since app start" }
        }
        val focusRequester = remember { FocusRequester() }
        var errorText by remember { mutableStateOf<CharSequence?>(null) }
        if (errorText != null) {
            AlertDialog(
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Error") },
                text = {
                    when (errorText) {
                        is String -> Text(errorText as String)
                        else -> Text(errorText as AnnotatedString)
                    }
                },
                onDismissRequest = { errorText = null },
                confirmButton = {
                    Button(onClick = { errorText = null }) {
                        Text("OK")
                    }
                }
            )
        }

        val allEndpoints = remember {
            mutableStateListOf<Endpoint>()
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

        TopUi(
            database = db,
            onChangeDb = onChangeDb,
            focusRequester = focusRequester,
            toggleDarkTheme = toggleDarkTheme,
            isDarkTheme = isDarkTheme,
            coroutineScope = coroutineScope,
            checker = checker,
            allEndpoints = allEndpoints
        )
        EndpointList(
            db = db,
            checker = checker,
            allEndpoints = allEndpoints,
            coroutineScope = coroutineScope,
            onChangeDb = onChangeDb,
            onShowError = {
                errorText = it
            },
            changeCounter = changeCounter
        )
    }

}
