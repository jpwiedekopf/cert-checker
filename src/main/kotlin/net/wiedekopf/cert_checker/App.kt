package net.wiedekopf.cert_checker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import net.wiedekopf.cert_checker.checker.Checker
import org.jetbrains.exposed.sql.Database

private val logger = KotlinLogging.logger {}

@Composable
fun App(db: Database, checker: Checker, coroutineScope: CoroutineScope) {
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
        Column(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            AddEndpointUi(database = db, onChangeDb = onChangeDb, focusRequester = focusRequester)
            EndpointList(
                db = db,
                checker = checker,
                coroutineScope = coroutineScope,
                onChangeDb = onChangeDb,
                onShowError = {
                    errorText = it
                },
                changeCounter = changeCounter
            )
        }
    }
}