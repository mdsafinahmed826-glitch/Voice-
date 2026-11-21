package com.example.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.model.OutputFormat
import com.example.data.model.ThemeMode
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.PipelineKitScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.VoxCloneTheme
import kotlinx.coroutines.launch

const val ROUTE_STUDIO = "studio"
const val ROUTE_HISTORY = "history"
const val ROUTE_SETTINGS = "settings"
const val ROUTE_PIPELINE = "pipeline"

private data class NavDestinationItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val navItems = listOf(
    NavDestinationItem(ROUTE_STUDIO, "Studio", Icons.Rounded.GraphicEq, Icons.Outlined.GraphicEq),
    NavDestinationItem(ROUTE_HISTORY, "History", Icons.Rounded.History, Icons.Outlined.History),
    NavDestinationItem(ROUTE_SETTINGS, "Settings", Icons.Rounded.Settings, Icons.Outlined.Settings),
    NavDestinationItem(ROUTE_PIPELINE, "Backend", Icons.Rounded.Terminal, Icons.Outlined.Terminal)
)

@Composable
fun VoxCloneApp(
    viewModel: StudioViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val historyRecords by viewModel.historyFlow.collectAsStateWithLifecycle()
    val settings by viewModel.settingsFlow.collectAsStateWithLifecycle()

    val useDark = when (settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    VoxCloneTheme(darkTheme = useDark) {
        val context = LocalContext.current
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        var pendingExportFilePath by remember { mutableStateOf<String?>(null) }
        val createDocLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("audio/*")
        ) { uri ->
            val src = pendingExportFilePath
            pendingExportFilePath = null
            if (uri != null && src != null) {
                viewModel.exportAudioToUri(src, uri)
            }
        }

        LaunchedEffect(Unit) {
            viewModel.snackbarEvents.collect { message ->
                snackbarHostState.showSnackbar(message)
            }
        }

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        fun navigateTo(route: String) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 680.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            navItems.forEach { item ->
                                val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTo(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = { Text(item.label) },
                                    modifier = Modifier.testTag("nav_${item.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            navItems.forEach { item ->
                                val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTo(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = { Text(item.label) },
                                    modifier = Modifier.testTag("rail_${item.route}")
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_STUDIO,
                            modifier = Modifier.widthIn(max = 760.dp)
                        ) {
                            composable(ROUTE_STUDIO) {
                                StudioScreen(
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    onExportFile = { path, suggestedName, _ ->
                                        pendingExportFilePath = path
                                        createDocLauncher.launch(suggestedName)
                                    },
                                    onShareFile = { path, mimeType ->
                                        val shareIntent = viewModel.buildShareIntent(path, mimeType)
                                        if (shareIntent != null) {
                                            context.startActivity(
                                                Intent.createChooser(shareIntent, "Share cloned audio")
                                            )
                                        } else {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Audio file missing")
                                            }
                                        }
                                    },
                                    onShowMessage = { msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            }

                            composable(ROUTE_HISTORY) {
                                HistoryScreen(
                                    records = historyRecords,
                                    playbackState = uiState.playbackState,
                                    onPlayRecord = { record ->
                                        viewModel.togglePlayFile(record.outputFilePath)
                                    },
                                    onLoadIntoStudio = { record ->
                                        viewModel.loadHistoryRecord(record)
                                        navigateTo(ROUTE_STUDIO)
                                    },
                                    onExportRecord = { record ->
                                        val fmt = OutputFormat.entries.firstOrNull {
                                            it.extension.equals(record.outputFormat, ignoreCase = true)
                                        } ?: OutputFormat.WAV
                                        pendingExportFilePath = record.outputFilePath
                                        createDocLauncher.launch("voxclone_${record.id}.${fmt.extension}")
                                    },
                                    onDeleteRecord = { record ->
                                        viewModel.deleteHistoryRecord(record)
                                    },
                                    onClearAll = {
                                        viewModel.clearAllHistory()
                                    }
                                )
                            }

                            composable(ROUTE_SETTINGS) {
                                SettingsScreen(
                                    uiState = uiState,
                                    viewModel = viewModel
                                )
                            }

                            composable(ROUTE_PIPELINE) {
                                PipelineKitScreen(
                                    viewModel = viewModel,
                                    onShowMessage = { msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
