package com.jjmluz.adonde

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjmluz.adonde.shared.R
import kotlinx.coroutines.delay

private val REVEAL_HEADER_HEIGHT = 44.dp
private val REVEAL_ROW_HEIGHT = 58.dp
private const val SCORES_PER_ROW = 3
private const val SPLASH_MILLIS = 3000L

@Composable
fun AdondeApp(viewModel: TvViewModel) {
    Box(Modifier.fillMaxSize().background(Background)) {
        when (viewModel.screen) {
            Screen.SPLASH -> SplashScreen(viewModel)
            Screen.SETUP -> SetupScreen(viewModel)
            Screen.EXPLORE -> ExploreScreen(viewModel)
            Screen.GUESS -> GuessScreen(viewModel)
            Screen.REVEAL -> RevealScreen(viewModel)
            Screen.FINAL -> FinalScreen(viewModel)
        }
        if (viewModel.paused) PausedOverlay()
    }
}

@Composable
private fun SplashScreen(viewModel: TvViewModel) {
    LaunchedEffect(Unit) {
        delay(SPLASH_MILLIS)
        viewModel.leaveSplash()
    }
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Globe(viewModel.globe, Modifier.size(480.dp))
        Logo(128.sp, shadow = Shadow(Background, Offset(0f, 4f), 36f))
    }
}

@Composable
private fun SetupScreen(viewModel: TvViewModel) {
    // Margins keep everything inside the TV's overscan-safe area.
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 170.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Logo(50.sp, Modifier.padding(bottom = 8.dp))
        Stepper(stringResource(R.string.rounds), viewModel.rounds, viewModel.selectedRow == ROUNDS_ROW)
        Stepper(stringResource(R.string.players), viewModel.playerCount, viewModel.selectedRow == PLAYERS_ROW)
        viewModel.players.take(viewModel.playerCount).forEachIndexed { index, player ->
            PlayerRow(index, player, viewModel.selectedRow == FIRST_PLAYER_ROW + index)
        }
        Spacer(Modifier.height(2.dp))
        val selected = viewModel.selectedRow == viewModel.startRow
        SetupRow(selected, if (selected) Accent else Card) {
            Label(
                text = stringResource(R.string.start),
                size = 22.sp,
                color = if (selected) Background else Foreground,
                weight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    viewModel.editingName?.let { NameDialog(nameOf(viewModel.players[it], it), viewModel::onNameTyped) }
}

@Composable
private fun Stepper(title: String, value: Int, selected: Boolean) {
    SetupRow(selected) {
        Label(title, 20.sp, Foreground)
        Spacer(Modifier.weight(1f))
        Label(if (selected) "◀   $value   ▶" else "$value", 22.sp, if (selected) Accent else Foreground, FontWeight.Bold)
    }
}

@Composable
private fun PlayerRow(index: Int, player: Player, selected: Boolean) {
    SetupRow(selected) {
        if (selected) Label("◀", 16.sp, Accent)
        Avatar(player.avatar, index, 32.dp, Modifier.padding(horizontal = 6.dp))
        if (selected) Label("▶", 16.sp, Accent)
        Spacer(Modifier.width(10.dp))
        Label(nameOf(player, index), 20.sp, Foreground)
        Spacer(Modifier.weight(1f))
        if (selected) Label(stringResource(R.string.rename), 15.sp, Muted)
    }
}

@Composable
private fun SetupRow(selected: Boolean, color: Color = Card, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(shape)
            .background(color)
            .border(if (selected) 3.dp else 0.dp, if (selected) Accent else Color.Transparent, shape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun ExploreScreen(viewModel: TvViewModel) {
    Box(Modifier.fillMaxSize()) {
        TvPanorama(viewModel)
        if (viewModel.spot == null) {
            Message(Modifier.background(Background)) {
                if (viewModel.searchFailed) {
                    Label(stringResource(R.string.no_place), 36.sp, Foreground, FontWeight.Bold)
                    Label(stringResource(R.string.check_internet), 24.sp, Muted)
                    Label(stringResource(R.string.try_again), 28.sp, Accent, FontWeight.Bold)
                } else {
                    Globe(viewModel.globe, Modifier.padding(bottom = 16.dp).size(300.dp))
                    Label(stringResource(R.string.searching), 30.sp, Foreground, FontWeight.Bold)
                }
            }
        } else {
            ControlHint(
                viewModel = viewModel,
                move = stringResource(R.string.look_around),
                hold = stringResource(R.string.hold_to_guess),
                holding = stringResource(R.string.holding_to_guess),
            )
        }
    }
}

@Composable
private fun GuessScreen(viewModel: TvViewModel) {
    val index = viewModel.guesserIndex ?: return
    val player = viewModel.guesser ?: return
    Box(Modifier.fillMaxSize()) {
        TvGuessMap(viewModel, player.avatar, index)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Scrim)
                .padding(start = 6.dp, end = 16.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Avatar(player.avatar, index, 34.dp)
            Label(stringResource(R.string.turn, nameOf(player, index)), 22.sp, Foreground, FontWeight.Bold)
        }
        ControlHint(
            viewModel = viewModel,
            move = stringResource(R.string.move_map),
            hold = stringResource(R.string.hold_to_confirm),
            holding = stringResource(R.string.holding_to_confirm),
        )
    }
}

/**
 * What the arrows and OK do right now, with the round in a tab hanging below and the zoom scale at the
 * side. It turns into a progress bar while OK is held.
 */
@Composable
private fun BoxScope.ControlHint(viewModel: TvViewModel, move: String, hold: String, holding: String) {
    ZoomScale(viewModel, Modifier.align(Alignment.CenterEnd).padding(end = 32.dp))
    Column(
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HintPill(viewModel, move, hold, holding)
        Box(
            Modifier
                .background(Scrim, TabShape)
                .padding(horizontal = TAB_FLARE + 12.dp, vertical = 4.dp),
        ) {
            Label(stringResource(R.string.round_of, viewModel.round, viewModel.rounds), 14.sp, Foreground, FontWeight.Bold)
        }
    }
}

private val TAB_FLARE = 10.dp
private val TAB_CORNER = 8.dp

/** A browser tab upside down: flat on top, flaring out where it meets what it hangs from. */
private val TabShape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val flare = with(density) { TAB_FLARE.toPx() }
        val corner = with(density) { TAB_CORNER.toPx() }
        val (w, h) = size
        return Outline.Generic(
            Path().apply {
                moveTo(0f, 0f)
                arcTo(Rect(-flare, 0f, flare, 2 * flare), -90f, 90f, false)
                arcTo(Rect(flare, h - 2 * corner, flare + 2 * corner, h), 180f, -90f, false)
                arcTo(Rect(w - flare - 2 * corner, h - 2 * corner, w - flare, h), 90f, -90f, false)
                arcTo(Rect(w - flare, 0f, w + flare, 2 * flare), 180f, 90f, false)
                close()
            },
        )
    }
}

@Composable
private fun HintPill(viewModel: TvViewModel, move: String, hold: String, holding: String) {
    val progress = viewModel.holdProgress
    val zooming = viewModel.control == Control.ZOOM
    if (progress < HOLD_MESSAGE_PROGRESS) {
        Row(
            modifier = Modifier
                .clip(PillShape)
                .background(Scrim)
                // The yellow outline says zoom mode is on, along with the scale at the side.
                .border(2.dp, if (zooming) Accent else Color.Transparent, PillShape)
                .padding(start = 10.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (zooming) {
                // Left and right still move in zoom mode; only up and down zoom.
                DpadIcon(sides = true)
                Label(move, 14.sp, Foreground, FontWeight.Bold)
                Label("·", 14.sp, Muted, modifier = Modifier.padding(horizontal = 4.dp))
                DpadIcon(upDown = true)
                Label(stringResource(R.string.zoom), 14.sp, Accent, FontWeight.Bold)
            } else {
                DpadIcon(upDown = true, sides = true)
                Label(move, 14.sp, Foreground, FontWeight.Bold)
            }
            Label("·", 14.sp, Muted, modifier = Modifier.padding(horizontal = 4.dp))
            DpadIcon(centre = true)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Label(stringResource(if (zooming) R.string.exit_zoom else R.string.zoom), 14.sp, Foreground, FontWeight.Bold)
                Label("/ $hold", 14.sp, Foreground)
            }
        }
    } else {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Scrim)
                .padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Label(holding, 14.sp, Accent, FontWeight.Bold)
            Box(Modifier.width(420.dp).height(8.dp).clip(CircleShape).background(Card)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Accent))
            }
        }
    }
}

private val PillShape = RoundedCornerShape(26.dp)
private val ZOOM_SCALE_HEIGHT = 220.dp

/** A slim + / − scale at the side with a marker for how far in it is, shown only in zoom mode. */
@Composable
private fun ZoomScale(viewModel: TvViewModel, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(if (viewModel.control == Control.ZOOM) 1f else 0f, tween(200), label = "zoom scale")
    if (shown == 0f) return
    val level = viewModel.zoomLevel
    Column(
        modifier = modifier
            .graphicsLayer { alpha = shown }
            .clip(CircleShape)
            .background(Scrim)
            .border(2.dp, Accent, CircleShape)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Label("+", 20.sp, Foreground, FontWeight.Bold)
        Canvas(Modifier.width(20.dp).height(ZOOM_SCALE_HEIGHT)) {
            val track = 4.dp.toPx()
            val dot = 8.dp.toPx()
            val middle = size.width / 2
            val markerY = dot + (size.height - 2 * dot) * (1 - level)
            drawLine(Card, Offset(middle, dot), Offset(middle, size.height - dot), track, StrokeCap.Round)
            drawLine(Accent, Offset(middle, markerY), Offset(middle, size.height - dot), track, StrokeCap.Round)
            drawCircle(Accent, dot, Offset(middle, markerY))
        }
        Label("−", 20.sp, Foreground, FontWeight.Bold)
    }
}

/** The remote's round D-pad, with the parts that do something picked out. */
@Composable
private fun DpadIcon(upDown: Boolean = false, sides: Boolean = false, centre: Boolean = false, iconSize: Dp = 26.dp) {
    Canvas(Modifier.size(iconSize)) {
        val unit = size.width / 28
        val arrow = Path().apply {
            moveTo(14 * unit, 3.5f * unit)
            lineTo(17.5f * unit, 8 * unit)
            lineTo(10.5f * unit, 8 * unit)
            close()
        }
        drawCircle(Dim, 13 * unit, style = Stroke(1.5f * unit))
        listOf(0f, 180f).forEach { rotate(it) { drawPath(arrow, if (upDown) Accent else Dim) } }
        listOf(90f, 270f).forEach { rotate(it) { drawPath(arrow, if (sides) Accent else Dim) } }
        drawCircle(if (centre) Accent else Dim, (if (centre) 5.5f else 4.5f) * unit)
    }
}

@Composable
private fun RevealScreen(viewModel: TvViewModel) {
    val place = viewModel.spot ?: return
    val rows = viewModel.roundScores.chunked(SCORES_PER_ROW)
    // The board goes along the bottom because the world is wider than it is tall: at this zoom a
    // side panel leaves too little map to fit pins on opposite sides of it.
    val boardHeight = REVEAL_HEADER_HEIGHT + REVEAL_ROW_HEIGHT * rows.size
    Box(Modifier.fillMaxSize()) {
        RevealMap(place, viewModel.roundScores, viewModel.players, boardHeight)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(boardHeight)
                .background(Background)
                .padding(horizontal = 32.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(stringResource(R.string.round_of, viewModel.round, viewModel.rounds), 20.sp, Foreground, FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Label(
                    text = stringResource(if (viewModel.isLastRound) R.string.to_final else R.string.to_next_round),
                    size = 18.sp,
                    color = Accent,
                    weight = FontWeight.Bold,
                )
            }
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { score ->
                        ScoreRow(
                            score = score,
                            player = viewModel.players[score.player],
                            main = km(score.roundKm),
                            detail = stringResource(R.string.total, km(score.totalKm)),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FinalScreen(viewModel: TvViewModel) {
    val standings = viewModel.standings
    Message {
        Label(stringResource(R.string.final_result), 26.sp, Muted, FontWeight.Bold)
        if (standings.size > 1) {
            val winner = standings.first().player
            Label(stringResource(R.string.wins, nameOf(viewModel.players[winner], winner)), 36.sp, Accent, FontWeight.Bold)
        }
        val places = stringArrayResource(R.array.places)
        Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            standings.forEachIndexed { position, score ->
                ScoreRow(score, viewModel.players[score.player], km(score.totalKm), places[position])
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DpadIcon(centre = true, iconSize = 34.dp)
            Label(stringResource(R.string.back_to_start), 22.sp, Foreground, FontWeight.Bold)
        }
    }
}

@Composable
private fun PausedOverlay() {
    Message(Modifier.background(Scrim)) {
        Canvas(Modifier.padding(bottom = 36.dp).size(160.dp)) {
            val bar = Size(size.width * 0.3f, size.height)
            val radius = CornerRadius(bar.width * 0.25f)
            drawRoundRect(Foreground, Offset(size.width * 0.1f, 0f), bar, radius)
            drawRoundRect(Foreground, Offset(size.width * 0.6f, 0f), bar, radius)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DpadIcon(centre = true, iconSize = 44.dp)
            Label(stringResource(R.string.resume), 28.sp, Foreground, FontWeight.Bold)
            Spacer(Modifier.width(40.dp))
            BackIcon(44.dp)
            Label(stringResource(R.string.quit), 28.sp, Foreground, FontWeight.Bold)
        }
    }
}

/** The remote's round Back button, with its left arrow. */
@Composable
private fun BackIcon(iconSize: Dp) {
    Canvas(Modifier.size(iconSize)) {
        val unit = size.width / 28
        val line = Stroke(2.2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawCircle(Dim, 13 * unit, style = Stroke(1.5f * unit))
        drawLine(Accent, Offset(8.5f * unit, 14 * unit), Offset(19.5f * unit, 14 * unit), line.width, StrokeCap.Round)
        val head = Path().apply {
            moveTo(13 * unit, 9.5f * unit)
            lineTo(8.5f * unit, 14 * unit)
            lineTo(13 * unit, 18.5f * unit)
        }
        drawPath(head, Accent, style = line)
    }
}
