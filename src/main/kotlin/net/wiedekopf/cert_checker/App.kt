package net.wiedekopf.cert_checker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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
        Column(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            AddEndpointUi(database = db, onChangeDb = onChangeDb, focusRequester = focusRequester)
            EndpointList(
                db = db,
                checker = checker,
                coroutineScope = coroutineScope,
                onChangeDb = onChangeDb,
                changeCounter = changeCounter
            )
        }
    }
}