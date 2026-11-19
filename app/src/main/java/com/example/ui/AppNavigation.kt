package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.data.ai.AiExtractionRepository
import com.example.data.repository.FlashcardRepository
import com.example.data.repository.SettingsRepository
import com.example.domain.model.ThemeMode
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.screens.AiImportScreen
import com.example.ui.screens.DeckDetailScreen
import com.example.ui.screens.DecksScreen
import com.example.ui.screens.ProgressScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StudyScreen
import com.example.ui.theme.FlashCraftTheme
import com.example.ui.viewmodel.FlashCraftViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object Routes {
    const val DECKS = "decks"
    const val AI_IMPORT = "ai_import"
    const val PROGRESS = "progress"
    const val SETTINGS = "settings"
    const val DECK_DETAIL = "deck/{deckId}"
    const val STUDY = "study/{deckId}/{practiceAll}"

    fun deckDetail(deckId: String) = "deck/$deckId"
    fun study(deckId: String, practiceAll: Boolean) = "study/$deckId/$practiceAll"
}

private enum class TopTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    DECKS(Routes.DECKS, "Decks", Icons.Rounded.Style, Icons.Outlined.Style, "nav_decks"),
    AI_IMPORT(Routes.AI_IMPORT, "AI Extract", Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_ai_extract"),
    PROGRESS(Routes.PROGRESS, "Progress", Icons.Rounded.BarChart, Icons.Outlined.BarChart, "nav_progress"),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Rounded.Settings, Icons.Outlined.Settings, "nav_settings")
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun FlashCraftApp() {
    val context = LocalContext.current
    val settingsRepo = remember(context) { SettingsRepository(context) }
    val globalSettings by settingsRepo.settings.collectAsStateWithLifecycle(
        initialValue = com.example.domain.model.UserSettings()
    )

    val authStateUser by remember {
        Firebase.auth.authStateFlow()
    }.collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)
    var signedInFallbackUser by remember { mutableStateOf<FirebaseUser?>(null) }
    val user = authStateUser ?: signedInFallbackUser

    FlashCraftTheme(themeMode = globalSettings.themeMode) {
        if (user == null) {
            AuthScreen(
                onAuthSuccess = { signedInFallbackUser = Firebase.auth.currentUser }
            )
        } else {
            key(user.uid) {
                AuthenticatedAppScaffold(
                    currentUser = user,
                    onSignOutCompleted = { signedInFallbackUser = null }
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedAppScaffold(
    currentUser: FirebaseUser,
    onSignOutCompleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val viewModel: FlashCraftViewModel = viewModel(
        key = currentUser.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                FlashCraftViewModel(
                    repository = FlashcardRepository(db),
                    settingsRepository = SettingsRepository(app),
                    aiRepository = AiExtractionRepository(),
                    currentUserId = currentUser.uid
                )
            }
        }
    )

    val decksState by viewModel.decksState.collectAsStateWithLifecycle()
    val cardsState by viewModel.cardsState.collectAsStateWithLifecycle()
    val studyLogsState by viewModel.studyLogsState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topRoutes = remember { TopTab.entries.map { it.route }.toSet() }
    val showNavigationChrome = currentRoute in topRoutes

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showNavigationChrome && !useRail) {
                    NavigationBar {
                        TopTab.entries.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
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
                if (showNavigationChrome && useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        TopTab.entries.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    NavHost(
                        navController = navController,
                        startDestination = Routes.DECKS
                    ) {
                        composable(Routes.DECKS) {
                            DecksScreen(
                                decksState = decksState,
                                cardsState = cardsState,
                                studyLogsState = studyLogsState,
                                settings = settings,
                                onOpenDeck = { deckId ->
                                    navController.navigate(Routes.deckDetail(deckId))
                                },
                                onStudyAllDue = {
                                    navController.navigate(Routes.study("all", false))
                                },
                                onNavigateToAiImport = {
                                    navigateToTab(Routes.AI_IMPORT)
                                },
                                onCreateDeck = viewModel::createDeck,
                                onCreateStarterDeck = viewModel::createStarterDeck
                            )
                        }

                        composable(Routes.AI_IMPORT) {
                            val decks = (decksState as? UiState.Success)?.data.orEmpty()
                            AiImportScreen(
                                aiState = aiState,
                                existingDecks = decks,
                                onFilePicked = { uri -> viewModel.selectFileForAi(context, uri) },
                                onExtractClicked = { count, style, depth, custom ->
                                    viewModel.extractFlashcards(
                                        context = context,
                                        cardCount = count,
                                        style = style,
                                        depth = depth,
                                        customInstructions = custom
                                    )
                                },
                                onToggleDraft = viewModel::toggleDraftSelection,
                                onUpdateDraft = viewModel::updateDraft,
                                onUpdateTargetDeck = viewModel::updateTargetDeckForExtraction,
                                onSaveCards = viewModel::saveExtractedDrafts,
                                onReset = viewModel::resetAiExtraction,
                                onOpenSavedDeck = { deckId ->
                                    viewModel.resetAiExtraction()
                                    navController.navigate(Routes.deckDetail(deckId))
                                }
                            )
                        }

                        composable(Routes.PROGRESS) {
                            ProgressScreen(
                                decksState = decksState,
                                cardsState = cardsState,
                                studyLogsState = studyLogsState,
                                settings = settings
                            )
                        }

                        composable(Routes.SETTINGS) {
                            SettingsScreen(
                                settings = settings,
                                userEmail = currentUser.email,
                                userDisplayName = currentUser.displayName,
                                onSelectAlgorithm = viewModel::updateAlgorithm,
                                onChangeRetention = viewModel::updateTargetRetention,
                                onChangeNewCardsPerDay = viewModel::updateNewCardsPerDay,
                                onChangeDailyGoal = viewModel::updateDailyReviewGoal,
                                onToggleIntervalPreviews = viewModel::updateShowIntervalPreviews,
                                onToggleAutoPlayAudio = viewModel::updateAutoPlayAudio,
                                onToggleShowHints = viewModel::updateShowHintsAutomatically,
                                onSelectTheme = viewModel::updateThemeMode,
                                onResetDefaults = viewModel::resetSettingsDefaults,
                                onSignOut = {
                                    signOutUser(
                                        credentialManager = credentialManager,
                                        scope = scope,
                                        onSignOutComplete = onSignOutCompleted
                                    )
                                }
                            )
                        }

                        composable(
                            route = Routes.DECK_DETAIL,
                            arguments = listOf(navArgument("deckId") { type = NavType.StringType })
                        ) { entry ->
                            val deckId = entry.arguments?.getString("deckId").orEmpty()
                            val decks = (decksState as? UiState.Success)?.data.orEmpty()
                            val cards = (cardsState as? UiState.Success)?.data.orEmpty()
                            val deck = decks.firstOrNull { it.id == deckId }
                            val deckCards = remember(cards, deckId) {
                                cards.filter { it.deckId == deckId }
                            }
                            DeckDetailScreen(
                                deck = deck,
                                cards = deckCards,
                                onBack = { navController.popBackStack() },
                                onStudyDue = {
                                    navController.navigate(Routes.study(deckId, false))
                                },
                                onPracticeAll = {
                                    navController.navigate(Routes.study(deckId, true))
                                },
                                onUpdateDeck = { title, desc, colorHex, iconName ->
                                    viewModel.updateDeck(deckId, title, desc, colorHex, iconName)
                                },
                                onDeleteDeck = {
                                    viewModel.deleteDeck(deckId)
                                    navController.popBackStack()
                                },
                                onResetProgress = {
                                    viewModel.resetDeckProgress(deckCards)
                                },
                                onAddCard = { front, back, hint, tagsRaw, fUri, fType, fName, bUri, bType, bName ->
                                    viewModel.addCard(
                                        deckId, front, back, hint, tagsRaw,
                                        fUri, fType, fName, bUri, bType, bName
                                    )
                                },
                                onUpdateCard = viewModel::updateCard,
                                onDeleteCard = { cardId ->
                                    viewModel.deleteCard(deckId, cardId)
                                }
                            )
                        }

                        composable(
                            route = Routes.STUDY,
                            arguments = listOf(
                                navArgument("deckId") { type = NavType.StringType },
                                navArgument("practiceAll") { type = NavType.BoolType }
                            )
                        ) { entry ->
                            val deckId = entry.arguments?.getString("deckId").orEmpty()
                            val practiceAll = entry.arguments?.getBoolean("practiceAll") ?: false
                            val decks = (decksState as? UiState.Success)?.data.orEmpty()
                            val cards = (cardsState as? UiState.Success)?.data.orEmpty()

                            val sessionTitle = if (deckId == "all") {
                                "All Due Cards"
                            } else {
                                decks.firstOrNull { it.id == deckId }?.title ?: "Study"
                            }
                            val sessionCards = remember(cards, deckId) {
                                if (deckId == "all") cards else cards.filter { it.deckId == deckId }
                            }

                            StudyScreen(
                                title = sessionTitle,
                                cards = sessionCards,
                                practiceAll = practiceAll,
                                settings = settings,
                                onSubmitReview = viewModel::submitReview,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
