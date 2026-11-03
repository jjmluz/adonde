package com.jjmluz.adonde

import android.app.AlertDialog
import android.text.InputFilter
import android.text.InputType
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

val Good = Color(0xFF4ADE80)
val REVEAL_MARGIN = 60.dp
val PLAYER_COLORS = listOf(
    Color(0xFFE5484D),
    Color(0xFF3E8BFF),
    Color(0xFFF2A516),
    Color(0xFF9D5CE8),
    Color(0xFF12A594),
    Color(0xFFE93D82),
)

private val Background = Color(0xFF0B1220)
private val Foreground = Color(0xFFF4F6FB)
private val Muted = Color(0xFFA5AFC3)
private val Dim = Color(0xFF5B6781)
private val Accent = Color(0xFFFFC83D)
private val Card = Color(0xFF1C2A44)
private val Scrim = Color(0xE60B1220)
private val LogoFont = FontFamily(
    Font(R.font.outfit, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

private val REVEAL_HEADER_HEIGHT = 44.dp
private val REVEAL_ROW_HEIGHT = 58.dp
private const val SCORES_PER_ROW = 3
private const val NAME_LENGTH = 14
private const val DIALOG_MARGIN_DP = 24
private const val PIN_TAIL = 0.28f
private const val SPLASH_MILLIS = 3000L

@Composable
fun AdondeApp(viewModel: GameViewModel) {
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
private fun SplashScreen(viewModel: GameViewModel) {
    LaunchedEffect(Unit) {
        delay(SPLASH_MILLIS)
        viewModel.leaveSplash()
    }
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Globe(viewModel.globe, Modifier.size(480.dp))
        Logo(128.sp, shadow = Shadow(Background, Offset(0f, 4f), 36f))
    }
}

/** The name with a pin for its O, whose tip hangs below the line of letters. */
@Composable
private fun Logo(size: TextUnit, modifier: Modifier = Modifier, shadow: Shadow? = null) {
    val style = TextStyle(
        color = Foreground,
        fontSize = size,
        fontFamily = LogoFont,
        fontWeight = FontWeight.ExtraBold,
        shadow = shadow,
    )
    val height = with(LocalDensity.current) { size.toDp() }
    Row(modifier) {
        BasicText("AD", Modifier.alignByBaseline(), style)
        Canvas(
            Modifier
                .alignBy { it.measuredHeight - (it.measuredHeight * PIN_TAIL).roundToInt() }
                .padding(horizontal = height * 0.03f)
                .size(height * 0.74f, height),
        ) { drawPath(pinPath(this.size), Accent) }
        BasicText("NDE?", Modifier.alignByBaseline(), style)
    }
}

private fun pinPath(size: Size) = Path().apply {
    val unit = size.width / 80
    fillType = PathFillType.EvenOdd
    moveTo(40 * unit, 0f)
    cubicTo(62 * unit, 0f, 80 * unit, 18 * unit, 80 * unit, 40 * unit)
    cubicTo(80 * unit, 66 * unit, 52 * unit, 88 * unit, 40 * unit, 108 * unit)
    cubicTo(28 * unit, 88 * unit, 0f, 66 * unit, 0f, 40 * unit)
    cubicTo(0f, 18 * unit, 18 * unit, 0f, 40 * unit, 0f)
    close()
    addOval(Rect(Offset(40 * unit, 40 * unit), 17 * unit))
}

@Composable
private fun SetupScreen(viewModel: GameViewModel) {
    // Margins keep everything inside the TV's overscan-safe area.
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 170.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Logo(50.sp, Modifier.padding(bottom = 8.dp))
        Stepper("Rondas", viewModel.rounds, viewModel.selectedRow == ROUNDS_ROW)
        Stepper("Jogadores", viewModel.playerCount, viewModel.selectedRow == PLAYERS_ROW)
        viewModel.players.take(viewModel.playerCount).forEachIndexed { index, player ->
            PlayerRow(index, player, viewModel.selectedRow == FIRST_PLAYER_ROW + index)
        }
        Spacer(Modifier.height(2.dp))
        val selected = viewModel.selectedRow == viewModel.startRow
        SetupRow(selected, if (selected) Accent else Card) {
            Label(
                text = "Começar",
                size = 22.sp,
                color = if (selected) Background else Foreground,
                weight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    viewModel.editingName?.let { NameDialog(viewModel.players[it].name, viewModel::onNameTyped) }
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
        Label(player.name, 20.sp, Foreground)
        Spacer(Modifier.weight(1f))
        if (selected) Label("OK muda o nome", 15.sp, Muted)
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

/** The system dialog brings up the TV's keyboard, and takes the keys for as long as it's open. */
@Composable
private fun NameDialog(name: String, onDone: (String) -> Unit) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val input = EditText(context).apply {
            hint = name
            setSingleLine()
            filters = arrayOf(InputFilter.LengthFilter(NAME_LENGTH))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        val margin = (DIALOG_MARGIN_DP * context.resources.displayMetrics.density).toInt()
        val frame = FrameLayout(context).apply {
            setPadding(margin, 0, margin, 0)
            addView(input)
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle("Nome do jogador")
            .setView(frame)
            .setPositiveButton("OK") { _, _ -> onDone(input.text.toString()) }
            .setOnCancelListener { onDone(name) }
            .create()
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) {
                onDone(input.text.toString())
                true
            } else {
                false
            }
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.show()
        input.requestFocus()
        onDispose { dialog.dismiss() }
    }
}

@Composable
private fun ExploreScreen(viewModel: GameViewModel) {
    Box(Modifier.fillMaxSize()) {
        Panorama(viewModel)
        if (viewModel.spot == null) {
            Message(Modifier.background(Background)) {
                if (viewModel.searchFailed) {
                    Label("Não foi possível encontrar um local", 36.sp, Foreground, FontWeight.Bold)
                    Label("Confirma a ligação à Internet", 24.sp, Muted)
                    Label("Carrega em OK para tentar outra vez", 28.sp, Accent, FontWeight.Bold)
                } else {
                    Globe(viewModel.globe, Modifier.padding(bottom = 16.dp).size(300.dp))
                    Label("A procurar um local…", 30.sp, Foreground, FontWeight.Bold)
                }
            }
        } else {
            ControlHint(
                viewModel = viewModel,
                move = "Olhar em volta",
                hold = "Manter premido para adivinhar",
                holding = "Continuar a premir para adivinhar",
            )
        }
    }
}

@Composable
private fun GuessScreen(viewModel: GameViewModel) {
    val index = viewModel.guesserIndex ?: return
    val player = viewModel.guesser ?: return
    Box(Modifier.fillMaxSize()) {
        GuessMap(viewModel, player.avatar, index)
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
            Label("Vez de ${player.name}", 22.sp, Foreground, FontWeight.Bold)
        }
        ControlHint(
            viewModel = viewModel,
            move = "Mover o mapa",
            hold = "Manter premido para confirmar",
            holding = "Continuar a premir para confirmar",
        )
    }
}

/**
 * What the arrows and OK do right now, naming [move] as the other mode of the zoom, with the round in a tab
 * hanging below. It turns into a progress bar while OK is held.
 */
@Composable
private fun BoxScope.ControlHint(viewModel: GameViewModel, move: String, hold: String, holding: String) {
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
            Label("Ronda ${viewModel.round} de ${viewModel.rounds}", 14.sp, Foreground, FontWeight.Bold)
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
private fun HintPill(viewModel: GameViewModel, move: String, hold: String, holding: String) {
    val progress = viewModel.holdProgress
    val zooming = viewModel.control == Control.ZOOM
    if (progress < HOLD_MESSAGE_PROGRESS) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(26.dp))
                .background(Scrim)
                .padding(start = 10.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DpadIcon(upDown = true, sides = !zooming)
            Label(if (zooming) "Zoom" else move, 14.sp, Foreground, FontWeight.Bold)
            Label("·", 14.sp, Muted, modifier = Modifier.padding(horizontal = 4.dp))
            DpadIcon(centre = true)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Label(if (zooming) move else "Zoom", 14.sp, Foreground, FontWeight.Bold)
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
private fun RevealScreen(viewModel: GameViewModel) {
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
                Label("Ronda ${viewModel.round} de ${viewModel.rounds}", 20.sp, Foreground, FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Label(
                    text = if (viewModel.isLastRound) "OK para ver o resultado final" else "OK para a próxima ronda",
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
                            main = formatKm(score.roundKm),
                            detail = "Total ${formatKm(score.totalKm)}",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreRow(score: Score, player: Player, main: String, detail: String, modifier: Modifier = Modifier.fillMaxWidth()) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Card)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Avatar(player.avatar, score.player, 36.dp)
        Column(Modifier.weight(1f)) {
            Name(player.name)
            Label(detail, 13.sp, Muted)
        }
        Label(main, 18.sp, Foreground, FontWeight.Bold)
    }
}

@Composable
private fun Name(name: String) {
    BasicText(
        text = name,
        style = TextStyle(color = Foreground, fontSize = 17.sp, fontWeight = FontWeight.Bold),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun FinalScreen(viewModel: GameViewModel) {
    val standings = viewModel.standings
    Message {
        Label("Resultado final", 26.sp, Muted, FontWeight.Bold)
        if (standings.size > 1) {
            Label("${viewModel.players[standings.first().player].name} ganhou!", 36.sp, Accent, FontWeight.Bold)
        }
        Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            standings.forEachIndexed { position, score ->
                ScoreRow(score, viewModel.players[score.player], formatKm(score.totalKm), "${position + 1}.º lugar")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DpadIcon(centre = true, iconSize = 34.dp)
            Label("Voltar ao início", 22.sp, Foreground, FontWeight.Bold)
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
            Label("Continuar", 28.sp, Foreground, FontWeight.Bold)
            Spacer(Modifier.width(40.dp))
            BackIcon(44.dp)
            Label("Sair", 28.sp, Foreground, FontWeight.Bold)
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

@Composable
private fun Avatar(avatar: Int, colorIndex: Int, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(PLAYER_COLORS[colorIndex]), Alignment.Center) {
        BasicText(AVATARS[avatar], style = TextStyle(fontSize = (size.value * 0.55f).sp))
    }
}

@Composable
private fun Message(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) { content() }
}

@Composable
private fun Label(
    text: String,
    size: TextUnit,
    color: Color,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
) {
    BasicText(text, modifier, TextStyle(color = color, fontSize = size, fontWeight = weight, textAlign = TextAlign.Center))
}
