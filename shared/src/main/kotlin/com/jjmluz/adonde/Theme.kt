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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjmluz.adonde.shared.R
import kotlin.math.roundToInt

// The "Noite" palette.
val Background = Color(0xFF0B1220)
val Foreground = Color(0xFFF4F6FB)
val Muted = Color(0xFFA5AFC3)
val Dim = Color(0xFF5B6781)
val Accent = Color(0xFFFFC83D)
val Card = Color(0xFF1C2A44)
val Scrim = Color(0xE60B1220)
val Good = Color(0xFF4ADE80)
val PLAYER_COLORS = listOf(
    Color(0xFFE5484D),
    Color(0xFF3E8BFF),
    Color(0xFFF2A516),
    Color(0xFF9D5CE8),
    Color(0xFF12A594),
    Color(0xFFE93D82),
)

private val LogoFont = FontFamily(
    Font(R.font.outfit, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

private const val NAME_LENGTH = 14
private const val DIALOG_MARGIN_DP = 24
private const val PIN_TAIL = 0.28f

/** The name with a pin for its O, whose tip hangs below the line of letters. */
@Composable
fun Logo(size: TextUnit, modifier: Modifier = Modifier, shadow: Shadow? = null) {
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

/** The system dialog brings up the keyboard, and takes the keys for as long as it's open. */
@Composable
fun NameDialog(name: String, onDone: (String) -> Unit) {
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
            .setTitle(R.string.player_name)
            .setView(frame)
            .setPositiveButton(android.R.string.ok) { _, _ -> onDone(input.text.toString()) }
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

/** One player on a scoreboard: their avatar and name, a [detail] under the name, and [main] at the end. */
@Composable
fun ScoreRow(score: Score, player: Player, main: String, detail: String, modifier: Modifier = Modifier.fillMaxWidth()) {
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
            Name(nameOf(player, score.player))
            Label(detail, 13.sp, Muted)
        }
        Label(main, 18.sp, Foreground, FontWeight.Bold)
    }
}

@Composable
fun Name(name: String, size: TextUnit = 17.sp) {
    BasicText(
        text = name,
        style = TextStyle(color = Foreground, fontSize = size, fontWeight = FontWeight.Bold),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun Avatar(avatar: Int, colorIndex: Int, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(PLAYER_COLORS[colorIndex]), Alignment.Center) {
        BasicText(AVATARS[avatar], style = TextStyle(fontSize = (size.value * 0.55f).sp))
    }
}

/** The player's name, or "Player N" in the device's language when they haven't chosen one. */
@Composable
fun nameOf(player: Player, index: Int): String =
    player.name.ifEmpty { stringResource(R.string.default_player, index + 1) }

@Composable
fun km(km: Double): String =
    formatKm(km, stringResource(R.string.decimal_mark), stringResource(R.string.group_mark))

/** A full-screen column of centred lines. */
@Composable
fun Message(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) { content() }
}

@Composable
fun Label(
    text: String,
    size: TextUnit,
    color: Color,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
) {
    BasicText(text, modifier, TextStyle(color = color, fontSize = size, fontWeight = weight, textAlign = TextAlign.Center))
}
