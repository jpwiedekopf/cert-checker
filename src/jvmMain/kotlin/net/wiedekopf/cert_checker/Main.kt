package net.wiedekopf.cert_checker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import net.wiedekopf.cert_checker.theme.AppTheme
import io.github.oshai.kotlinlogging.KotlinLogging
import net.harawata.appdirs.AppDirsFactory
import net.wiedekopf.cert_checker.checker.Checker
import net.wiedekopf.cert_checker.model.CertificateDetailsTable
import net.wiedekopf.cert_checker.model.EndpointTable
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.notExists

private val logger = KotlinLogging.logger {}

const val STORAGE_VERSION = "1.0.0"

@OptIn(ExperimentalResourceApi::class)
fun main() = application {
    val version = System.getProperty("app.version") ?: "Development"
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

    var isDarkTheme by remember {
        mutableStateOf(true)
    }

    val appIcon = remember {
        System.getProperty("app.dir")
            ?.let { Paths.get(it, "icon-512.png") }
            ?.takeIf { it.exists() }
            ?.inputStream()
            ?.buffered()
            ?.use { BitmapPainter(it.readAllBytes().decodeToImageBitmap()) }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = buildString {
            append("Cert Checker")
            if (version != "Development") {
                append(" v$version")
            }
        },
        icon = appIcon
    ) {
        AppTheme(darkTheme = isDarkTheme) {
            Column(modifier = Modifier.fillMaxSize().background(colorScheme.surfaceBright).padding(4.dp)) {
                CompositionLocalProvider(LocalContentColor provides colorScheme.onSurface) {
                    App(
                        db = db,
                        checker = checker,
                        coroutineScope = coroutineScope,
                        toggleDarkTheme = {
                            isDarkTheme = !isDarkTheme
                        },
                        isDarkTheme = isDarkTheme
                    )
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
        url = jdbcUri,
        driver = "org.sqlite.JDBC"
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
