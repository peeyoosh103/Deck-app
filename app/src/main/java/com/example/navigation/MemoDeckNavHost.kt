package com.example.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.AppContainer
import com.example.R
import com.example.decks.CreateDeckScreen
import com.example.decks.DeckDetailsScreen
import com.example.decks.DecksListScreen
import com.example.decks.DecksViewModel
import com.example.file_import.FileImportScreen
import com.example.file_import.FileImportViewModel
import com.example.flashcards.CardEditorScreen
import com.example.flashcards.CardListScreen
import com.example.flashcards.CardViewerScreen
import com.example.flashcards.FlashcardsViewModel
import com.example.home.HomeScreen
import com.example.home.HomeViewModel
import com.example.image_import.ImageImportScreen
import com.example.image_import.ImageImportViewModel
import com.example.pdf_import.PdfImportScreen
import com.example.pdf_import.PdfImportViewModel
import com.example.quiz.QuizFlowScreen
import com.example.quiz.QuizPhase
import com.example.quiz.QuizViewModel
import com.example.search.SearchScreen
import com.example.search.SearchViewModel
import com.example.settings.SettingsScreen
import com.example.settings.SettingsViewModel
import com.example.statistics.StatisticsScreen
import com.example.statistics.StatisticsViewModel
import com.example.study.StudyDeckSelectionScreen
import com.example.study.StudySessionScreen
import com.example.study.StudyViewModel
import com.example.weakcards.WeakCardsScreen
import com.example.weakcards.WeakCardsViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private data class BottomNavTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val MainBottomTabs = listOf(
    BottomNavTab(
        route = MemoDeckRoutes.ROUTE_HOME,
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "bottom_nav_home"
    ),
    BottomNavTab(
        route = MemoDeckRoutes.ROUTE_DECKS,
        labelRes = R.string.nav_decks,
        selectedIcon = Icons.Filled.Folder,
        unselectedIcon = Icons.Outlined.Folder,
        testTag = "bottom_nav_decks"
    ),
    BottomNavTab(
        route = MemoDeckRoutes.ROUTE_STUDY_TAB,
        labelRes = R.string.nav_study,
        selectedIcon = Icons.Filled.School,
        unselectedIcon = Icons.Outlined.School,
        testTag = "bottom_nav_study"
    ),
    BottomNavTab(
        route = MemoDeckRoutes.ROUTE_QUIZ_TAB,
        labelRes = R.string.nav_quiz,
        selectedIcon = Icons.Filled.Quiz,
        unselectedIcon = Icons.Outlined.Quiz,
        testTag = "bottom_nav_quiz"
    )
)

@Composable
fun MemoDeckAppScaffold(
    container: AppContainer,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    // Share QuizViewModel across Quiz tab so we hide bottom nav while an active quiz session is running
    val tabQuizViewModel: QuizViewModel = viewModel(
        factory = QuizViewModel.Factory(container.repository, container.settingsRepository)
    )
    val tabQuizState by tabQuizViewModel.uiState.collectAsStateWithLifecycle()

    val isQuizSessionActiveOnTab = currentRoute == MemoDeckRoutes.ROUTE_QUIZ_TAB &&
        tabQuizState.phase != QuizPhase.SETUP

    val showMainNavigation = currentRoute in MemoDeckRoutes.topLevelTabs && !isQuizSessionActiveOnTab

    fun navigateToTopTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 720.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useNavigationRail && showMainNavigation) {
                NavigationRail {
                    MainBottomTabs.forEach { tab ->
                        val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateToTopTab(tab.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = stringResource(tab.labelRes)
                                )
                            },
                            label = { Text(stringResource(tab.labelRes)) },
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                contentWindowInsets = if (showMainNavigation && !useNavigationRail) {
                    WindowInsets.safeDrawing
                } else {
                    WindowInsets(0, 0, 0, 0)
                },
                bottomBar = {
                    if (showMainNavigation && !useNavigationRail) {
                        NavigationBar(windowInsets = WindowInsets.navigationBars) {
                            MainBottomTabs.forEach { tab ->
                                val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTopTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = MemoDeckRoutes.ROUTE_HOME,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // TAB 1: HOME
                    composable(MemoDeckRoutes.ROUTE_HOME) {
                        val vm: HomeViewModel = viewModel(
                            factory = HomeViewModel.Factory(container.repository)
                        )
                        HomeScreen(
                            viewModel = vm,
                            onOpenSettings = { navController.navigate(MemoDeckRoutes.ROUTE_SETTINGS) },
                            onCreateDeck = { navController.navigate(MemoDeckRoutes.ROUTE_CREATE_DECK) },
                            onStartStudying = { navigateToTopTab(MemoDeckRoutes.ROUTE_STUDY_TAB) },
                            onStartQuiz = { navigateToTopTab(MemoDeckRoutes.ROUTE_QUIZ_TAB) },
                            onOpenWeakCards = { navController.navigate(MemoDeckRoutes.ROUTE_WEAK_CARDS) },
                            onOpenSearch = { navController.navigate(MemoDeckRoutes.ROUTE_SEARCH) },
                            onOpenStatistics = { navController.navigate(MemoDeckRoutes.ROUTE_STATISTICS) },
                            onOpenAllDecks = { navigateToTopTab(MemoDeckRoutes.ROUTE_DECKS) },
                            onOpenDeckDetails = { deckId ->
                                navController.navigate(MemoDeckRoutes.deckDetails(deckId))
                            }
                        )
                    }

                    // TAB 2: DECKS
                    composable(MemoDeckRoutes.ROUTE_DECKS) {
                        val vm: DecksViewModel = viewModel(
                            factory = DecksViewModel.Factory(container.repository)
                        )
                        DecksListScreen(
                            viewModel = vm,
                            onCreateDeck = { navController.navigate(MemoDeckRoutes.ROUTE_CREATE_DECK) },
                            onOpenDeck = { deckId -> navController.navigate(MemoDeckRoutes.deckDetails(deckId)) },
                            onViewCards = { deckId -> navController.navigate(MemoDeckRoutes.cardList(deckId)) }
                        )
                    }

                    // TAB 3: STUDY (Deck Selection)
                    composable(MemoDeckRoutes.ROUTE_STUDY_TAB) {
                        val vm: StudyViewModel = viewModel(
                            factory = StudyViewModel.Factory(container.repository, container.settingsRepository)
                        )
                        StudyDeckSelectionScreen(
                            viewModel = vm,
                            onSelectDeck = { deckId ->
                                navController.navigate(MemoDeckRoutes.studyDeck(deckId, 0))
                            },
                            onCreateDeck = { navController.navigate(MemoDeckRoutes.ROUTE_CREATE_DECK) }
                        )
                    }

                    // TAB 4: QUIZ (Deck Selection -> Setup -> Session -> Result -> Review)
                    composable(MemoDeckRoutes.ROUTE_QUIZ_TAB) {
                        QuizFlowScreen(
                            preselectedDeckId = null,
                            viewModel = tabQuizViewModel,
                            onCreateDeck = { navController.navigate(MemoDeckRoutes.ROUTE_CREATE_DECK) },
                            onBackToDeck = { deckId ->
                                navController.navigate(MemoDeckRoutes.deckDetails(deckId))
                            },
                            onExitToParent = null
                        )
                    }

                    // CREATE DECK
                    composable(MemoDeckRoutes.ROUTE_CREATE_DECK) {
                        val vm: DecksViewModel = viewModel(
                            factory = DecksViewModel.Factory(container.repository)
                        )
                        CreateDeckScreen(
                            viewModel = vm,
                            onDeckCreated = { newDeckId ->
                                navController.navigate(MemoDeckRoutes.deckDetails(newDeckId)) {
                                    popUpTo(MemoDeckRoutes.ROUTE_CREATE_DECK) { inclusive = true }
                                }
                            },
                            onCancel = { navController.popBackStack() }
                        )
                    }

                    // DECK DETAILS
                    composable(
                        route = MemoDeckRoutes.ROUTE_DECK_DETAILS,
                        arguments = listOf(navArgument("deckId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val deckId = backStackEntry.arguments?.getLong("deckId") ?: return@composable
                        val vm: DecksViewModel = viewModel(
                            factory = DecksViewModel.Factory(container.repository)
                        )
                        DeckDetailsScreen(
                            deckId = deckId,
                            viewModel = vm,
                            onNavigateBack = { navController.popBackStack() },
                            onStudyDeck = { id -> navController.navigate(MemoDeckRoutes.studyDeck(id, 0)) },
                            onQuizDeck = { id -> navController.navigate(MemoDeckRoutes.quizDeck(id)) },
                            onAddFlashcard = { id -> navController.navigate(MemoDeckRoutes.createCard(id)) },
                            onCreateFromImage = { id -> navController.navigate(MemoDeckRoutes.imageImport(id)) },
                            onImportFromFile = { id -> navController.navigate(MemoDeckRoutes.fileImport(id)) },
                            onCreateFromPdf = { id -> navController.navigate(MemoDeckRoutes.pdfImport(id)) },
                            onViewCards = { id -> navController.navigate(MemoDeckRoutes.cardList(id)) }
                        )
                    }

                    // CARD LIST
                    composable(
                        route = MemoDeckRoutes.ROUTE_CARD_LIST,
                        arguments = listOf(navArgument("deckId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val deckId = backStackEntry.arguments?.getLong("deckId") ?: return@composable
                        val vm: FlashcardsViewModel = viewModel(
                            factory = FlashcardsViewModel.Factory(container.repository)
                        )
                        CardListScreen(
                            deckId = deckId,
                            viewModel = vm,
                            onNavigateBack = { navController.popBackStack() },
                            onAddCard = { navController.navigate(MemoDeckRoutes.createCard(deckId)) },
                            onOpenCard = { cardId -> navController.navigate(MemoDeckRoutes.cardViewer(cardId)) },
                            onEditCard = { cardId -> navController.navigate(MemoDeckRoutes.editCard(deckId, cardId)) },
                            onStudyFromIndex = { index ->
                                navController.navigate(MemoDeckRoutes.studyDeck(deckId, index))
                            }
                        )
                    }

                    // CREATE CARD
                    composable(
                        route = MemoDeckRoutes.ROUTE_CREATE_CARD,
                        arguments = listOf(navArgument("deckId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val deckId = backStackEntry.arguments?.getLong("deckId") ?: return@composable
                        val vm: FlashcardsViewModel = viewModel(
                            factory = FlashcardsViewModel.Factory(container.repository)
                        )
                        CardEditorScreen(
                            deckId = deckId,
                            cardId = null,
                            viewModel = vm,
                            onSaved = { navController.popBackStack() },
                            onCancel = { navController.popBackStack() },
                            onCreateFromImage = {
                                navController.navigate(MemoDeckRoutes.imageImport(deckId))
                            },
                            onImportFromFile = {
                                navController.navigate(MemoDeckRoutes.fileImport(deckId))
                            },
                            onCreateFromPdf = {
                                navController.navigate(MemoDeckRoutes.pdfImport(deckId))
                            }
                        )
                    }

                    // EDIT CARD
                    composable(
                        route = MemoDeckRoutes.ROUTE_EDIT_CARD,
                        arguments = listOf(
                            navArgument("deckId") { type = NavType.LongType },
                            navArgument("cardId") { type = NavType.LongType }
                        )
                    ) { backStackEntry ->
                        val deckId = backStackEntry.arguments?.getLong("deckId") ?: return@composable
                        val cardId = backStackEntry.arguments?.getLong("cardId") ?: return@composable
                        val vm: FlashcardsViewModel = viewModel(
                            factory = FlashcardsViewModel.Factory(container.repository)
                        )
                        CardEditorScreen(
                            deckId = deckId,
                            cardId = cardId,
                            viewModel = vm,
                            onSaved = { navController.popBackStack() },
                            onCancel = { navController.popBackStack() },
                            onCreateFromImage = {},
                            onImportFromFile = {},
                            onCreateFromPdf = {}
                        )
                    }

                    // CARD VIEWER
                    composable(
                        route = MemoDeckRoutes.ROUTE_CARD_VIEWER,
                        arguments = listOf(navArgument("cardId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val cardId = backStackEntry.arguments?.getLong("cardId") ?: return@composable
                        val vm: FlashcardsViewModel = viewModel(
                            factory = FlashcardsViewModel.Factory(container.repository)
                        )
                        CardViewerScreen(
                            cardId = cardId,
                            viewModel = vm,
                            onNavigateBack = { navController.popBackStack() },
                            onEditCard = { deckId, id ->
                                navController.navigate(MemoDeckRoutes.editCard(deckId, id))
                            }
                        )
                    }

                    // STUDY DECK SESSION
                    composable(
                        route = MemoDeckRoutes.ROUTE_STUDY_DECK,
                        arguments = listOf(
                            navArgument("deckId") { type = NavType.LongType },
                            navArgument("startIndex") {
                                type = NavType.IntType
                                defaultValue = 0
                            }
                        )
                    ) { backStackEntry ->
                        val deckId = backStackEntry.arguments?.getLong("deckId") ?: return@composable
                        val startIndex = backStackEntry.arguments?.getInt("startIndex") ?: 0
                        val vm: StudyViewModel = viewModel(
                            factory = StudyViewModel.Factory(container.repository, container.settingsRepository)
                        )
                        StudySessionScreen(
                            deckId = deckId,
                            startIndex = startIndex,
                            isWeakMode = false,
                            viewModel = vm,
                            onClose = { navController.popBackStack() }
                        )
                    }

                    // STUDY WEAK CARDS SESSION
                    composable(MemoDeckRoutes.ROUTE_STUDY_WEAK) {
                        val vm: StudyViewModel = viewModel(
                            factory = StudyViewModel.Factory(container.repository, container.settingsRepository)
                        )
                        StudySessionScreen(
                            deckId = null,
                            startIndex = 0,
                            isWeakMode = true,
                            viewModel = vm,
                            onClose = { navController.popBackStack() }
                        )
                    }

                    // QUIZ FOR SPECIFIC DECK (FROM DECK DETAILS)
                    composable(
                        route = MemoDeckRoutes.ROUTE_QUIZ_DECK,
                        arguments = listOf(navArgument("deckId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val deckId = backStackEntry.arguments?.getLong("deckId") ?: return@composable
                        val vm: QuizViewModel = viewModel(
                            factory = QuizViewModel.Factory(container.repository, container.settingsRepository)
                        )
                        QuizFlowScreen(
                            preselectedDeckId = deckId,
                            viewModel = vm,
                            onCreateDeck = { navController.navigate(MemoDeckRoutes.ROUTE_CREATE_DECK) },
                            onBackToDeck = { navController.popBackStack() },
                            onExitToParent = { navController.popBackStack() }
                        )
                    }

                    // WEAK CARDS
                    composable(MemoDeckRoutes.ROUTE_WEAK_CARDS) {
                        val vm: WeakCardsViewModel = viewModel(
                            factory = WeakCardsViewModel.Factory(container.repository)
                        )
                        WeakCardsScreen(
                            viewModel = vm,
                            onNavigateBack = { navController.popBackStack() },
                            onStudyWeakCards = { navController.navigate(MemoDeckRoutes.ROUTE_STUDY_WEAK) },
                            onOpenCard = { cardId -> navController.navigate(MemoDeckRoutes.cardViewer(cardId)) }
                        )
                    }

                    // SEARCH & FILTER
                    composable(MemoDeckRoutes.ROUTE_SEARCH) {
                        val vm: SearchViewModel = viewModel(
                            factory = SearchViewModel.Factory(container.repository)
                        )
                        SearchScreen(
                            viewModel = vm,
                            onNavigateBack = { navController.popBackStack() },
                            onOpenDeck = { deckId -> navController.navigate(MemoDeckRoutes.deckDetails(deckId)) },
                            onOpenCard = { cardId -> navController.navigate(MemoDeckRoutes.cardViewer(cardId)) }
                        )
                    }

                    // STATISTICS
                    composable(MemoDeckRoutes.ROUTE_STATISTICS) {
                        val vm: StatisticsViewModel = viewModel(
                            factory = StatisticsViewModel.Factory(container.repository)
                        )
                        StatisticsScreen(
                            viewModel = vm,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    // SETTINGS & BACKUP
                    composable(MemoDeckRoutes.ROUTE_SETTINGS) {
                        val vm: SettingsViewModel = viewModel(
                            factory = SettingsViewModel.Factory(
                                container.repository,
                                container.settingsRepository,
                                container.backupManager
                            )
                        )
                        SettingsScreen(
                            viewModel = vm,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    // IMAGE OCR IMPORT
                    composable(
                        route = MemoDeckRoutes.ROUTE_IMAGE_IMPORT,
                        arguments = listOf(navArgument("deckId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val deckId = backStackEntry.arguments?.getLong("deckId") ?: return@composable
                        val vm: ImageImportViewModel = viewModel(
                            factory = ImageImportViewModel.Factory(
                                deckId = deckId,
                                repository = container.repository,
                                ocrEngine = container.ocrEngine
                            )
                        )
                        ImageImportScreen(
                            viewModel = vm,
                            onNavigateBack = { navController.popBackStack() },
                            onImportComplete = {
                                navController.popBackStack(MemoDeckRoutes.deckDetails(deckId), inclusive = false)
                            }
                        )
                    }

                    // JSON / CSV BULK IMPORT
                    composable(
                        route = MemoDeckRoutes.ROUTE_FILE_IMPORT,
                        arguments = listOf(navArgument("deckId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val deckId = backStackEntry.arguments?.getLong("deckId") ?: return@composable
                        val vm: FileImportViewModel = viewModel(
                            factory = FileImportViewModel.Factory(
                                deckId = deckId,
                                repository = container.repository
                            )
                        )
                        FileImportScreen(
                            viewModel = vm,
                            onNavigateBack = {
                                navController.popBackStack(MemoDeckRoutes.deckDetails(deckId), inclusive = false)
                            }
                        )
                    }

                    // PDF NOTES TO FLASHCARDS
                    composable(
                        route = MemoDeckRoutes.ROUTE_PDF_IMPORT,
                        arguments = listOf(navArgument("deckId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val deckId = backStackEntry.arguments?.getLong("deckId") ?: return@composable
                        val vm: PdfImportViewModel = viewModel(
                            factory = PdfImportViewModel.Factory(
                                deckId = deckId,
                                repository = container.repository,
                                pdfProcessor = container.pdfProcessor
                            )
                        )
                        PdfImportScreen(
                            viewModel = vm,
                            onNavigateBack = { navController.popBackStack() },
                            onImportComplete = {
                                navController.popBackStack(MemoDeckRoutes.deckDetails(deckId), inclusive = false)
                            }
                        )
                    }
                }
            }
        }
    }
}
