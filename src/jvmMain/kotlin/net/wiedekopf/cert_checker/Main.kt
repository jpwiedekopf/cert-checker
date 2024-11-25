@file:Suppress("FunctionName")

package net.wiedekopf.cert_checker

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.window.application
import com.willowtreeapps.fuzzywuzzy.diffutils.FuzzySearch
import dev.hydraulic.conveyor.control.SoftwareUpdateController
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.harawata.appdirs.AppDirsFactory
import net.wiedekopf.cert_checker.checker.Checker
import net.wiedekopf.cert_checker.model.CertificateDetailsTable
import net.wiedekopf.cert_checker.model.Endpoint
import net.wiedekopf.cert_checker.model.EndpointTable
import net.wiedekopf.cert_checker.model.SortMode
import net.wiedekopf.cert_checker.theme.AppTheme
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.light
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.styling.TitleBarStyle
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.notExists

private val logger = KotlinLogging.logger {}

const val STORAGE_VERSION = "1.0.0"

val updateController: SoftwareUpdateController? = SoftwareUpdateController.getInstance()
val canDoOnlineUpdates get() = updateController?.canTriggerUpdateCheckUI() == SoftwareUpdateController.Availability.AVAILABLE

@OptIn(ExperimentalResourceApi::class)
fun main() = application {
    val version by remember {
        mutableStateOf(
            updateController?.currentVersion?.version ?: "Development"
        )
    }
    var remoteVersion by remember {
        mutableStateOf<String?>(null)
    }
    var updateAvailable by remember {
        mutableStateOf(false)
    }

    val appDir = remember {
        AppDirsFactory.getInstance().getUserDataDir("cert-checker", STORAGE_VERSION, "Wiedekopf").let {
            Path.of(it)
        }
    }
    val db = remember {
        getDb(appDir)
    }
    val checker = remember {
        Checker()
    }
    val coroutineScope = rememberCoroutineScope()

    val isSystemInDarkTheme = isSystemInDarkTheme()

    var isDarkTheme by remember {
        mutableStateOf(isSystemInDarkTheme) // default to system theme
    }

    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val remoteVersionObj: SoftwareUpdateController.Version? = updateController?.currentVersionFromRepository
                remoteVersion = remoteVersionObj?.version ?: "Unknown"
                updateAvailable = (remoteVersionObj?.compareTo(updateController.currentVersion) ?: 0) > 0
            } catch (e: Exception) {
                remoteVersion = "Error: ${e.message}"
            }
        }
    }

    val toggleDarkTheme = {
        isDarkTheme = !isDarkTheme
    }

    var changeCounter by remember {
        mutableStateOf(0)
    }
    val onChangeDb = {
        changeCounter += 1
        logger.debug { "Database at revision $changeCounter since app start" }
    }

    val focusRequester = remember { FocusRequester() }

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
                    FuzzySearch.partialRatio(search, it.name) > 75
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

    IntUiTheme(
        theme = when (isDarkTheme) {
            true -> JewelTheme.darkThemeDefinition()
            false -> JewelTheme.lightThemeDefinition()
        }, swingCompatMode = true, styling = ComponentStyling.default().decoratedWindow(
            titleBarStyle = when (isDarkTheme) {
                false -> TitleBarStyle.light()
                else -> TitleBarStyle.dark()
            }
        )
    ) {
        DecoratedWindow(
            onCloseRequest = ::exitApplication,
            title = "Cert Checker",
        ) {
            TitleBarView(
                toggleDarkTheme = toggleDarkTheme,
                isDarkTheme = isDarkTheme,
                appVersion = version,
                updateAvailable = updateAvailable,
                remoteVersion = remoteVersion
            )
            AppTheme(darkTheme = isDarkTheme) {
                Column(modifier = Modifier.fillMaxSize().background(colorScheme.surfaceBright)) {
                    CompositionLocalProvider(LocalContentColor provides colorScheme.onSurface) {
                        App(
                            db = db,
                            changeCounter = changeCounter,
                            onChangeDb = onChangeDb,
                            checker = checker,
                            coroutineScope = coroutineScope,
                            focusRequester = focusRequester,
                            allEndpoints = allEndpoints,
                            currentEndpoints = currentEndpoints,
                            currentSearch = currentSearch,
                            onChangeSearch = {
                                currentSearch = it
                            },
                            sortMode = sortMode,
                            onChangeSort = {
                                val ordinal = sortMode.ordinal
                                sortMode = SortMode.entries.toTypedArray()[(ordinal + 1) % SortMode.entries.size]
                            }
                        )
                    }
                }
            }
        }
    }
}


private fun getDb(appDir: Path): Database {
    val jdbcUri = "jdbc:sqlite:$appDir/cert-checker.db"
    logger.info { "Using JDBC URI: $jdbcUri" }
    if (appDir.notExists()) {
        appDir.toFile().mkdirs()
        logger.info { "Created app directory ${appDir.absolutePathString()}" }
    }
    val databasePath = appDir.resolve("cert-checker.db")
    val dbExists = databasePath.toFile().exists()
    return Database.connect(
        url = jdbcUri, driver = "org.sqlite.JDBC"
    ).also {
        logger.info { "Connected to DB" }
        if (!dbExists) {
            logger.info { "Creating tables in DB" }
            transaction(db = it) {
                SchemaUtils.create(EndpointTable, CertificateDetailsTable)
            }
        }
    }
}