package com.nothing.glyphmatrix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nothing.glyphmatrix.games.jump.settings.JumpGameSettingsRepository
import com.nothing.glyphmatrix.ui.theme.NothingGlyphMatrixTheme
import androidx.annotation.StringRes

private const val ROUTE_OVERVIEW = "overview"
private const val ROUTE_GAME_DETAIL = "game_detail"

private enum class GameId {
    JUMP,
    SNAKE;

    companion object {
        fun fromRoute(value: String?): GameId? = try {
            value?.let { GameId.valueOf(it) }
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

private data class GameInfo(
    val id: GameId,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val controlsRes: Int
)

private val gameCatalog = listOf(
    GameInfo(
        id = GameId.JUMP,
        titleRes = R.string.game_jump_title,
        summaryRes = R.string.game_jump_summary,
        descriptionRes = R.string.game_jump_description,
        controlsRes = R.string.game_jump_controls
    ),
    GameInfo(
        id = GameId.SNAKE,
        titleRes = R.string.game_snake_title,
        summaryRes = R.string.game_snake_summary,
        descriptionRes = R.string.game_snake_description,
        controlsRes = R.string.game_snake_controls
    )
)
private val gameMap = gameCatalog.associateBy { it.id }

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NothingGlyphMatrixTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = ROUTE_OVERVIEW,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(route = ROUTE_OVERVIEW) {
                        GamesOverviewScreen(
                            games = gameCatalog,
                            onGameSelected = { selected ->
                                navController.navigate("$ROUTE_GAME_DETAIL/${selected.name}")
                            }
                        )
                    }
                    composable(
                        route = "$ROUTE_GAME_DETAIL/{gameId}",
                        arguments = listOf(navArgument("gameId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val gameId = GameId.fromRoute(backStackEntry.arguments?.getString("gameId"))
                        val gameInfo = gameId?.let { gameMap[it] }
                        if (gameInfo != null) {
                            GameDetailScreen(
                                gameInfo = gameInfo,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        } else {
                            UnknownGameScreen(onNavigateBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GamesOverviewScreen(
    games: List<GameInfo>,
    modifier: Modifier = Modifier,
    onGameSelected: (GameId) -> Unit = {}
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) }
            )
        }
    ) { innerPadding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = stringResource(id = R.string.games_overview_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(id = R.string.games_overview_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            games.forEach { game ->
                GameOverviewCard(
                    gameInfo = game,
                    onClick = { onGameSelected(game.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameDetailScreen(
    gameInfo: GameInfo,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {}
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(id = gameInfo.titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.overview_back_content_description)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        GameDetailContent(
            gameInfo = gameInfo,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

@Composable
private fun GameDetailContent(
    gameInfo: GameInfo,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val min = JumpGameSettingsRepository.minHorizontalSensitivity()
    val max = JumpGameSettingsRepository.maxHorizontalSensitivity()
    var sensitivity by remember {
        mutableFloatStateOf(JumpGameSettingsRepository.getHorizontalSensitivity(context))
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .fillMaxHeight()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = stringResource(id = gameInfo.summaryRes),
            style = MaterialTheme.typography.bodyLarge
        )

        GameDescriptionSection(
            descriptionRes = gameInfo.descriptionRes,
            controlsRes = gameInfo.controlsRes
        )

        GameSettingsSection(
            sensitivity = sensitivity,
            onSensitivityChanged = {
                sensitivity = it
                JumpGameSettingsRepository.setHorizontalSensitivity(context, it)
            },
            min = min,
            max = max
        )
    }
}

@Composable
private fun GameDescriptionSection(
    @StringRes descriptionRes: Int,
    @StringRes controlsRes: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(id = R.string.game_detail_controls_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(id = descriptionRes),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(id = controlsRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GameSettingsSection(
    sensitivity: Float,
    onSensitivityChanged: (Float) -> Unit,
    min: Float,
    max: Float
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(id = R.string.game_detail_settings_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(id = R.string.game_detail_settings_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.settings_sensitivity_label),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = stringResource(
                R.string.settings_sensitivity_current,
                sensitivity.toDouble()
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = sensitivity,
            onValueChange = onSensitivityChanged,
            valueRange = min..max,
            steps = 8,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = stringResource(
                R.string.settings_sensitivity_range,
                min.toDouble(),
                max.toDouble()
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameOverviewCard(
    gameInfo: GameInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(id = gameInfo.titleRes),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(id = gameInfo.summaryRes),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(id = R.string.games_card_button))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnknownGameScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.overview_back_content_description)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(id = R.string.games_overview_subtitle),
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GamesOverviewPreview() {
    NothingGlyphMatrixTheme {
        GamesOverviewScreen(games = gameCatalog)
    }
}

@Preview(showBackground = true)
@Composable
private fun GameDetailPreview() {
    NothingGlyphMatrixTheme {
        GameDetailScreen(
            gameInfo = gameCatalog.first(),
            onNavigateBack = {}
        )
    }
}
