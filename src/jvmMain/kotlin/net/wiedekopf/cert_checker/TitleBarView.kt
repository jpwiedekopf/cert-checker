package net.wiedekopf.cert_checker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import net.wiedekopf.cert_checker.theme.AppTypography
import net.wiedekopf.cert_checker.theme.onSurfaceDark
import net.wiedekopf.cert_checker.theme.onSurfaceLight
import net.wiedekopf.cert_checker.theme.surfaceBrightDark
import net.wiedekopf.cert_checker.theme.surfaceBrightLight
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.light
import org.jetbrains.jewel.window.DecoratedWindowScope
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.newFullscreenControls
import org.jetbrains.jewel.window.styling.TitleBarColors
import org.jetbrains.jewel.window.styling.TitleBarStyle


@Composable
fun DecoratedWindowScope.TitleBarView(
    toggleDarkTheme: () -> Unit,
    isDarkTheme: Boolean,
    appVersion: String,
    updateAvailable: Boolean,
    remoteVersion: String?
) {
    @Suppress("DuplicatedCode") TitleBar(
        modifier = Modifier.newFullscreenControls(), style = when (isDarkTheme) {
            true -> TitleBarStyle.dark(
                colors = TitleBarColors(
                    background = surfaceBrightDark,
                    inactiveBackground = surfaceBrightDark.copy(alpha = 0.9f),
                    content = onSurfaceDark,
                    border = surfaceBrightDark,
                    fullscreenControlButtonsBackground = surfaceBrightDark,
                    titlePaneButtonHoveredBackground = surfaceBrightDark.copy(alpha = 0.5f),
                    titlePaneButtonPressedBackground = surfaceBrightDark.copy(alpha = 0.5f),
                    iconButtonHoveredBackground = surfaceBrightDark.copy(alpha = 0.5f),
                    iconButtonPressedBackground = surfaceBrightDark.copy(alpha = 0.5f),
                    dropdownHoveredBackground = surfaceBrightDark.copy(alpha = 0.5f),
                    dropdownPressedBackground = surfaceBrightDark.copy(alpha = 0.5f),
                    titlePaneCloseButtonHoveredBackground = surfaceBrightDark.copy(alpha = 0.5f),
                    titlePaneCloseButtonPressedBackground = surfaceBrightDark.copy(alpha = 0.5f),
                )
            )

            else -> TitleBarStyle.light(
                colors = TitleBarColors(
                    background = surfaceBrightLight,
                    inactiveBackground = surfaceBrightLight.copy(alpha = 0.9f),
                    content = onSurfaceLight,
                    border = surfaceBrightLight,
                    fullscreenControlButtonsBackground = surfaceBrightLight,
                    titlePaneButtonHoveredBackground = surfaceBrightLight.copy(alpha = 0.5f),
                    titlePaneButtonPressedBackground = surfaceBrightLight.copy(alpha = 0.5f),
                    iconButtonHoveredBackground = surfaceBrightLight.copy(alpha = 0.5f),
                    iconButtonPressedBackground = surfaceBrightLight.copy(alpha = 0.5f),
                    dropdownHoveredBackground = surfaceBrightLight.copy(alpha = 0.5f),
                    dropdownPressedBackground = surfaceBrightLight.copy(alpha = 0.5f),
                    titlePaneCloseButtonHoveredBackground = surfaceBrightLight.copy(alpha = 0.5f),
                    titlePaneCloseButtonPressedBackground = surfaceBrightLight.copy(alpha = 0.5f),
                )
            )
        }
    ) {
        val contentColor by derivedStateOf {
            when (isDarkTheme) {
                true -> onSurfaceDark
                else -> onSurfaceLight
            }
        }
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                modifier = Modifier.align(Alignment.Start).padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title, fontWeight = FontWeight.Bold
                )
                Text(
                    text = buildAnnotatedString {
                        append("Version $appVersion")
                        if (updateAvailable) {
                            append(" - ")
                            withStyle(AppTypography.bodySmall.toSpanStyle().copy(fontWeight = FontWeight.Bold)) {
                                append("Update available: $remoteVersion")
                            }
                        }
                    },
                    style = AppTypography.bodySmall,
                )
            }
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if ((updateAvailable && canDoOnlineUpdates) || appVersion == "Development") {
                    TextButton(
                        enabled = updateController != null && canDoOnlineUpdates,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = contentColor,
                            disabledContentColor = contentColor.copy(alpha = 0.5f)
                        ),
                        onClick = {
                            updateController?.triggerUpdateCheckUI()
                        }) {
                        Text("Update")
                    }
                }
                IconButton(
                    onClick = toggleDarkTheme
                ) {
                    Icon(
                        imageVector = when (isDarkTheme) {
                            false -> Icons.Default.DarkMode
                            true -> Icons.Default.LightMode
                        },
                        contentDescription = "Toggle Dark Mode",
                    )
                }
            }
        }
    }
}