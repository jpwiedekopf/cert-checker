@file:Suppress("FunctionName")

package net.wiedekopf.cert_checker

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.CoroutineScope
import net.wiedekopf.cert_checker.checker.Checker
import net.wiedekopf.cert_checker.model.Endpoint
import net.wiedekopf.cert_checker.model.SortMode
import org.jetbrains.exposed.sql.Database

@Suppress("FunctionName")
@Composable
fun ColumnScope.App(
    db: Database,
    checker: Checker,
    coroutineScope: CoroutineScope,
    focusRequester: FocusRequester,
    allEndpoints: SnapshotStateList<Endpoint>,
    currentEndpoints: SnapshotStateList<Endpoint>,
    currentSearch: String?,
    onChangeSearch: (String?) -> Unit,
    sortMode: SortMode,
    onChangeSort: () -> Unit,
    changeCounter: Int,
    onChangeDb: () -> Unit
) {

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

    TopUi(
        database = db,
        onChangeDb = onChangeDb,
        focusRequester = focusRequester,
        coroutineScope = coroutineScope,
        checker = checker,
        endpointList = allEndpoints,
        sortMode = sortMode,
        changeSearch = onChangeSearch,
        onClickSort = onChangeSort
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
}
