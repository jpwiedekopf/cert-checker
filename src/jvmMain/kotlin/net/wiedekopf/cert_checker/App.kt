@file:Suppress("FunctionName")

package net.wiedekopf.cert_checker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.willowtreeapps.fuzzywuzzy.diffutils.FuzzySearch
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import net.wiedekopf.cert_checker.checker.Checker
import net.wiedekopf.cert_checker.model.Endpoint
import net.wiedekopf.cert_checker.model.SortMode
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction

private val logger = KotlinLogging.logger {}

@Suppress("FunctionName")
@Composable
fun ColumnScope.App(
    db: Database,
    checker: Checker,
    coroutineScope: CoroutineScope,
    toggleDarkTheme: () -> Unit,
    isDarkTheme: Boolean,
    appVersion: String,
    updateAvailable: Boolean,
    remoteVersion: String?
) {
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
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = colorScheme.error) },
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

        var sortMode by remember {
            mutableStateOf(SortMode.EXPIRY)
        }

        var currentSearch by remember {
            mutableStateOf<String?>(null)
        }

        val currentEndpoints = remember {
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

        LaunchedEffect(allEndpoints, changeCounter, currentSearch, sortMode) {
            val filteredList = currentSearch?.let { search ->
                when (search.isBlank()) {
                    true -> allEndpoints
                    else -> allEndpoints.filter {
                        FuzzySearch.partialRatio(search, it.name) > 50
                    }
                }

            } ?: allEndpoints
            @Suppress("UNNECESSARY_SAFE_CALL") val sortedList = filteredList.sortedWith { o1, o2 ->
                if (o1 == null || o2 == null) {
                    return@sortedWith 0
                }
                when (sortMode) {
                    SortMode.ID -> o1.id.value.compareTo(o2.id.value)
                    SortMode.ALPHABETIC -> o1.name.compareTo(o2.name)
                    SortMode.EXPIRY -> {
                        transaction(db) {
                            val sortAfter1 = o1.details?.firstOrNull()?.notAfter
                            val sortAfter2 = o2.details?.firstOrNull()?.notAfter
                            if (sortAfter1 == null || sortAfter2 == null) {
                                return@transaction 0
                            }
                            sortAfter1.compareTo(sortAfter2)
                        }
                    }
                }
            }
            currentEndpoints.clear()
            currentEndpoints.addAll(sortedList)
            logger.debug { "Filtered ${allEndpoints.size} endpoints to ${currentEndpoints.size} endpoints" }
        }

        TopUi(
            database = db,
            onChangeDb = onChangeDb,
            focusRequester = focusRequester,
            toggleDarkTheme = toggleDarkTheme,
            isDarkTheme = isDarkTheme,
            coroutineScope = coroutineScope,
            checker = checker,
            endpointList = allEndpoints,
            sortMode = sortMode,
            changeSearch = {
                currentSearch = it
            },
            onClickSort = {
                val ordinal = sortMode.ordinal
                sortMode = SortMode.entries.toTypedArray()[(ordinal + 1) % SortMode.entries.size]
            }
        )

        EndpointList(
            db = db,
            checker = checker,
            endpoints = currentEndpoints,
            coroutineScope = coroutineScope,
            onChangeDb = onChangeDb,
            onShowError = {
                errorText = it
            },
            search = currentSearch,
            changeCounter = changeCounter
        )

        FooterRow(appVersion = appVersion, updateAvailable = updateAvailable, remoteVersion = remoteVersion)
    }

}

@Composable
fun FooterRow(appVersion: String, updateAvailable: Boolean, remoteVersion: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(colorScheme.primaryContainer),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = buildAnnotatedString {
                append("Version $appVersion")
                if (updateAvailable) {
                    append(" - Update available: $remoteVersion")
                }
            },
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp),
            style = typography.bodySmall,
            color = colorScheme.onPrimaryContainer
        )
        if (updateController != null && updateAvailable && canDoOnlineUpdates) {
            TextButton(
                onClick = {
                    updateController.triggerUpdateCheckUI()
                }
            ) {
                Text("Update", color = colorScheme.onPrimaryContainer)
            }
        }
    }
}
