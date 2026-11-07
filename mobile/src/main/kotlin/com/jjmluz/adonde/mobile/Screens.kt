package com.jjmluz.adonde.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.StreetViewPanoramaCamera
import com.jjmluz.adonde.Accent
import com.jjmluz.adonde.Avatar
import com.jjmluz.adonde.Background
import com.jjmluz.adonde.Card
import com.jjmluz.adonde.Dim
import com.jjmluz.adonde.Foreground
import com.jjmluz.adonde.GameViewModel
import com.jjmluz.adonde.Globe
import com.jjmluz.adonde.GuessMap
import com.jjmluz.adonde.Label
import com.jjmluz.adonde.Logo
import com.jjmluz.adonde.MAX_PLAYERS
import com.jjmluz.adonde.MAX_ROUNDS
import com.jjmluz.adonde.MIN_PLAYERS
import com.jjmluz.adonde.MIN_ROUNDS
import com.jjmluz.adonde.Message
import com.jjmluz.adonde.Muted
import com.jjmluz.adonde.Name
import com.jjmluz.adonde.NameDialog
import com.jjmluz.adonde.Panorama
import com.jjmluz.adonde.Player
import com.jjmluz.adonde.RevealMap
import com.jjmluz.adonde.ScoreRow
import com.jjmluz.adonde.Screen
import com.jjmluz.adonde.Scrim
import com.jjmluz.adonde.km
import com.jjmluz.adonde.nameOf
import com.jjmluz.adonde.shared.R
import kotlin.random.Random
import kotlinx.coroutines.delay

private const val SPLASH_MILLIS = 2500L
/** The logo is about this many times as wide as its font size. */
private const val LOGO_WIDTH_PER_SIZE = 4.4f
/** Below this height, the setup and the scoreboard go side by side rather than one above the other. */
private val SHORT_SCREEN = 560.dp
private val MAX_COLUMN_WIDTH = 560.dp
/** Google's logo and notices sit along the bottom of the maps, and must stay visible under the buttons. */
private val GOOGLE_NOTICES = 44.dp
private val PillShape = RoundedCornerShape(28.dp)

@Composable
fun MobileApp(viewModel: GameViewModel) {
    Box(Modifier.fillMaxSize().background(Background)) {
        when (viewModel.screen) {
            Screen.SPLASH -> SplashScreen(viewModel)
            Screen.SETUP -> SetupScreen(viewModel)
            Screen.EXPLORE -> ExploreScreen(viewModel)
            Screen.GUESS -> GuessScreen(viewModel)
            Screen.REVEAL -> RevealScreen(viewModel)
            Screen.FINAL -> FinalScreen(viewModel)
        }
        if (viewModel.paused) PausedOverlay(viewModel)
    }
    // Back pauses a game, and closes the pause again; leaving takes the Quit button, so a stray swipe can't end it.
    BackHandler(enabled = viewModel.screen != Screen.SPLASH && viewModel.screen != Screen.SETUP) {
        when {
            viewModel.paused -> viewModel.resume()
            viewModel.screen == Screen.FINAL -> viewModel.backToStart()
            else -> viewModel.pause()
        }
    }
}

@Composable
private fun SplashScreen(viewModel: GameViewModel) {
    LaunchedEffect(Unit) {
        delay(SPLASH_MILLIS)
        viewModel.leaveSplash()
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) { viewModel.leaveSplash() },
        contentAlignment = Alignment.Center,
    ) {
        val globe = minOf(maxWidth, maxHeight) * 0.8f
        val logo = with(LocalDensity.current) { (minOf(maxWidth * 0.85f, globe * 1.15f) / LOGO_WIDTH_PER_SIZE).toSp() }
        Globe(viewModel.globe, Modifier.size(globe))
        Logo(logo, shadow = Shadow(Background, Offset(0f, 4f), 36f))
    }
}

@Composable
private fun SetupScreen(viewModel: GameViewModel) {
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (maxWidth > maxHeight && maxHeight < SHORT_SCREEN) {
            // A phone on its side: the game's settings on the left, the players on the right.
            Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                ) {
                    Logo(40.sp, Modifier.padding(bottom = 8.dp))
                    Settings(viewModel)
                    Spacer(Modifier.height(4.dp))
                    StartButton(viewModel)
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                ) { PlayerList(viewModel) }
            }
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center).widthIn(max = MAX_COLUMN_WIDTH).fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    // From the top, so adding a player doesn't move the rows under the finger.
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Logo(52.sp, Modifier.padding(top = 48.dp, bottom = 24.dp))
                    Settings(viewModel)
                    Spacer(Modifier.height(4.dp))
                    PlayerList(viewModel)
                }
                Spacer(Modifier.height(12.dp))
                StartButton(viewModel)
            }
        }
    }
    viewModel.editingName?.let { NameDialog(nameOf(viewModel.players[it], it), viewModel::onNameTyped) }
}

@Composable
private fun ColumnScope.Settings(viewModel: GameViewModel) {
    Stepper(stringResource(R.string.rounds), viewModel.rounds, MIN_ROUNDS, MAX_ROUNDS, viewModel::changeRounds)
    Stepper(stringResource(R.string.players), viewModel.playerCount, MIN_PLAYERS, MAX_PLAYERS, viewModel::changePlayerCount)
}

@Composable
private fun ColumnScope.PlayerList(viewModel: GameViewModel) {
    viewModel.players.take(viewModel.playerCount).forEachIndexed { index, player ->
        PlayerRow(index, player, viewModel)
    }
    Label(stringResource(R.string.tap_to_rename), 14.sp, Muted, modifier = Modifier.align(Alignment.CenterHorizontally))
}

@Composable
private fun StartButton(viewModel: GameViewModel) {
    ActionButton(stringResource(R.string.start), Modifier.fillMaxWidth(), onClick = viewModel::startGame)
}

@Composable
private fun Stepper(title: String, value: Int, min: Int, max: Int, change: (Int) -> Unit) {
    SetupRow {
        Label(title, 18.sp, Foreground)
        Spacer(Modifier.weight(1f))
        RoundButton("−", enabled = value > min) { change(-1) }
        Label("$value", 22.sp, Foreground, FontWeight.Bold, Modifier.width(48.dp))
        RoundButton("+", enabled = value < max) { change(1) }
    }
}

@Composable
private fun PlayerRow(index: Int, player: Player, viewModel: GameViewModel) {
    SetupRow(Modifier.clickable(onClickLabel = stringResource(R.string.player_name)) { viewModel.editName(index) }) {
        // The avatar has its own tap: the next animal nobody else has.
        Avatar(
            avatar = player.avatar,
            colorIndex = index,
            size = 40.dp,
            modifier = Modifier.clickable(role = Role.Button) { viewModel.changeAvatar(index, 1) },
        )
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) { Name(nameOf(player, index), 18.sp) }
        PencilIcon()
    }
}

@Composable
private fun SetupRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Card)
            .then(modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun ExploreScreen(viewModel: GameViewModel) {
    Box(Modifier.fillMaxSize()) {
        Panorama(viewModel, gestures = true, Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) { panorama ->
            LaunchedEffect(panorama, viewModel.spot) {
                panorama.animateTo(StreetViewPanoramaCamera(0f, 0f, Random.nextFloat() * 360), 0)
            }
        }
        if (viewModel.spot == null) {
            Message(Modifier.background(Background).blockTouches()) {
                if (viewModel.searchFailed) {
                    Label(stringResource(R.string.no_place), 26.sp, Foreground, FontWeight.Bold)
                    Label(stringResource(R.string.check_internet), 18.sp, Muted)
                    Spacer(Modifier.height(8.dp))
                    ActionButton(stringResource(R.string.retry), onClick = viewModel::retrySearch)
                } else {
                    Globe(viewModel.globe, Modifier.padding(bottom = 16.dp).size(220.dp))
                    Label(stringResource(R.string.searching), 24.sp, Foreground, FontWeight.Bold)
                }
            }
        } else {
            PlayingOverlay(viewModel, stringResource(R.string.drag_to_look), stringResource(R.string.guess), viewModel::startGuessing) {
                Chip { Label(stringResource(R.string.round_of, viewModel.round, viewModel.rounds), 15.sp, Foreground, FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun GuessScreen(viewModel: GameViewModel) {
    val index = viewModel.guesserIndex ?: return
    val player = viewModel.guesser ?: return
    Box(Modifier.fillMaxSize()) {
        GuessMap(viewModel, player.avatar, index, gestures = true, Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) { _, _ -> }
        PlayingOverlay(viewModel, stringResource(R.string.drag_map), stringResource(R.string.confirm), viewModel::placeGuess) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Chip {
                    Avatar(player.avatar, index, 30.dp, Modifier.padding(end = 8.dp))
                    Label(stringResource(R.string.turn, nameOf(player, index)), 17.sp, Foreground, FontWeight.Bold)
                }
                Label(
                    text = stringResource(R.string.round_of, viewModel.round, viewModel.rounds),
                    size = 13.sp,
                    color = Foreground,
                    weight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Scrim).padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/**
 * What goes over the panorama and the guess map: the pause button, [top] in the middle at the top, and at
 * the bottom a [hint] above the button that moves the game on. The bottom edge is left to Google's notices.
 */
@Composable
private fun PlayingOverlay(
    viewModel: GameViewModel,
    hint: String,
    action: String,
    onAction: () -> Unit,
    top: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
        IconButton(stringResource(R.string.pause), Modifier.align(Alignment.TopStart), onClick = viewModel::pause) {
            val bar = Size(size.width * 0.12f, size.height * 0.4f)
            val radius = CornerRadius(bar.width / 2)
            drawRoundRect(Foreground, Offset(size.width * 0.34f, size.height * 0.3f), bar, radius)
            drawRoundRect(Foreground, Offset(size.width * 0.54f, size.height * 0.3f), bar, radius)
        }
        Box(Modifier.align(Alignment.TopCenter).padding(horizontal = 56.dp)) { top() }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = GOOGLE_NOTICES - 12.dp)
                .widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Chip { Label(hint, 14.sp, Foreground) }
            ActionButton(action, Modifier.widthIn(min = 200.dp), onClick = onAction)
        }
    }
}

@Composable
private fun RevealScreen(viewModel: GameViewModel) {
    val place = viewModel.spot ?: return
    var boardWidth by remember { mutableIntStateOf(0) }
    var boardHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // On a phone on its side the board goes down the right; otherwise along the bottom.
        val side = maxWidth > maxHeight && maxHeight < SHORT_SCREEN
        val columns = if (side || maxWidth < 600.dp) 1 else if (maxWidth < 900.dp) 2 else 3
        with(density) {
            RevealMap(
                place = place,
                scores = viewModel.roundScores,
                players = viewModel.players,
                bottom = if (side) 0.dp else boardHeight.toDp(),
                right = if (side) boardWidth.toDp() else 0.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            )
        }
        val board = if (side) {
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(minOf(340.dp, maxWidth * 0.45f))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Vertical))
        } else {
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = maxHeight * 0.6f)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
        }
        Column(
            modifier = Modifier
                .onSizeChanged {
                    boardWidth = it.width
                    boardHeight = it.height
                }
                .background(Background)
                .then(board)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Label(stringResource(R.string.round_of, viewModel.round, viewModel.rounds), 18.sp, Foreground, FontWeight.Bold)
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                viewModel.roundScores.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { score ->
                            ScoreRow(
                                score = score,
                                player = viewModel.players[score.player],
                                main = km(score.roundKm),
                                detail = stringResource(R.string.total, km(score.totalKm)),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Keeps the last row's cards as wide as the others.
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            ActionButton(
                text = stringResource(if (viewModel.isLastRound) R.string.final_result else R.string.next_round),
                modifier = Modifier.fillMaxWidth(),
                onClick = viewModel::next,
            )
        }
    }
}

@Composable
private fun FinalScreen(viewModel: GameViewModel) {
    val standings = viewModel.standings
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Label(stringResource(R.string.final_result), 20.sp, Muted, FontWeight.Bold)
        if (standings.size > 1) {
            val winner = standings.first().player
            Label(stringResource(R.string.wins, nameOf(viewModel.players[winner], winner)), 30.sp, Accent, FontWeight.Bold)
        }
        val places = stringArrayResource(R.array.places)
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            standings.forEachIndexed { position, score ->
                ScoreRow(score, viewModel.players[score.player], km(score.totalKm), places[position])
            }
            Spacer(Modifier.height(8.dp))
            ActionButton(stringResource(R.string.back_to_start), Modifier.fillMaxWidth(), onClick = viewModel::backToStart)
        }
    }
}

@Composable
private fun PausedOverlay(viewModel: GameViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().background(Scrim).blockTouches().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Canvas(Modifier.padding(bottom = 16.dp).size(96.dp)) {
            val bar = Size(size.width * 0.3f, size.height)
            val radius = CornerRadius(bar.width * 0.25f)
            drawRoundRect(Foreground, Offset(size.width * 0.1f, 0f), bar, radius)
            drawRoundRect(Foreground, Offset(size.width * 0.6f, 0f), bar, radius)
        }
        ActionButton(stringResource(R.string.resume), Modifier.width(240.dp), onClick = viewModel::resume)
        ActionButton(stringResource(R.string.quit), Modifier.width(240.dp), primary = false, onClick = viewModel::backToStart)
    }
}

/** The big pill that moves the game on: yellow, or a card's colour when it isn't the main thing to do. */
@Composable
private fun ActionButton(text: String, modifier: Modifier = Modifier, primary: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = if (pressed) 0.96f else 1f
                scaleY = scaleX
            }
            .heightIn(min = 56.dp)
            .clip(PillShape)
            .background(if (primary) Accent else Card)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Label(text, 18.sp, if (primary) Background else Foreground, FontWeight.Bold)
    }
}

/** A round − or + on the setup screen, dimmed at its limit. */
@Composable
private fun RoundButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (enabled) Background else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Label(symbol, 24.sp, if (enabled) Accent else Dim, FontWeight.Bold)
    }
}

/** A round button over the map, drawn with [icon]. */
@Composable
private fun IconButton(description: String, modifier: Modifier = Modifier, onClick: () -> Unit, icon: DrawScope.() -> Unit) {
    Canvas(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Scrim)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        onDraw = icon,
    )
}

/** A small rounded label over the map. */
@Composable
private fun Chip(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Scrim)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** Says a name can be changed. */
@Composable
private fun PencilIcon(iconSize: Dp = 20.dp) {
    Canvas(Modifier.size(iconSize)) {
        val unit = size.width / 20
        val line = 2.2f * unit
        drawLine(Muted, Offset(5 * unit, 15 * unit), Offset(15 * unit, 5 * unit), line * 2, StrokeCap.Round)
        val tip = Path().apply {
            moveTo(2.5f * unit, 17.5f * unit)
            lineTo(3.5f * unit, 13.5f * unit)
            lineTo(6.5f * unit, 16.5f * unit)
            close()
        }
        drawPath(tip, Muted)
    }
}

/** Keeps touches from reaching the map or panorama underneath. */
private fun Modifier.blockTouches() = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent().changes.forEach { it.consume() }
    }
}
