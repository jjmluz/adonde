package com.jjmluz.adonde

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjmluz.adonde.shared.R
import java.time.LocalDate
import java.util.Locale

private val RecordShape = RoundedCornerShape(10.dp)

/** A trophy, drawn in lines. */
@Composable
fun TrophyIcon(iconSize: Dp, color: Color = Accent) {
    Canvas(Modifier.size(iconSize)) {
        val unit = size.width / 24
        fun at(x: Float, y: Float) = Offset(x * unit, y * unit)
        val line = Stroke(2.2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val cup = Path().apply {
            moveTo(7 * unit, 4 * unit)
            lineTo(17 * unit, 4 * unit)
            lineTo(17 * unit, 9 * unit)
            arcTo(Rect(at(7f, 4f), at(17f, 14f)), 0f, 180f, false)
            close()
            // The handles.
            moveTo(7 * unit, 6 * unit)
            lineTo(4 * unit, 6 * unit)
            quadraticTo(4 * unit, 10 * unit, 7 * unit, 10 * unit)
            moveTo(17 * unit, 6 * unit)
            lineTo(20 * unit, 6 * unit)
            quadraticTo(20 * unit, 10 * unit, 17 * unit, 10 * unit)
        }
        drawPath(cup, color, style = line)
        drawLine(color, at(12f, 14f), at(12f, 19f), line.width, StrokeCap.Round)
        drawLine(color, at(8f, 20f), at(16f, 20f), line.width, StrokeCap.Round)
    }
}

/** The yellow tag on the card of a player who has just made the closest guess ever. */
@Composable
fun NewRecordTag(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(50)).background(Accent).padding(start = 7.dp, end = 9.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TrophyIcon(13.dp, Background)
        Label(stringResource(R.string.new_record).uppercase(), 11.sp, Background, FontWeight.Bold)
    }
}

/** The records a game broke, for the final results. */
@Composable
fun NewRecordsCard(records: List<NewRecord>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RecordShape).background(Card).border(2.dp, Accent, RecordShape).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrophyIcon(20.dp)
            Label(stringResource(R.string.new_records), 17.sp, Accent, FontWeight.Bold)
        }
        records.forEach { record ->
            val (detail, value) = when (record.kind) {
                RecordKind.CLOSEST -> listOf(stringResource(R.string.closest_ever), countryName(record.country))
                    .filter { it.isNotEmpty() }.joinToString(" · ") to km(record.value)
                RecordKind.WINS -> stringResource(R.string.most_wins) to "${record.value.toInt()}"
                RecordKind.CLOSE_ROUNDS -> stringResource(R.string.close_rounds) to "${record.value.toInt()}"
            }
            RecordRow(record.holder, detail, value, avatarSize = 30.dp, nameSize = 15.sp)
        }
    }
}

/** One of the records screen's lists: a yellow title, then its rows, or a line saying there are none yet. */
@Composable
fun RecordList(title: String, empty: Boolean, modifier: Modifier = Modifier, rows: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.clip(RecordShape).background(Card).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Label(title, 17.sp, Accent, FontWeight.Bold)
        if (empty) Label(stringResource(R.string.no_records), 15.sp, Muted) else rows()
    }
}

/** The closest guesses ever, numbered, with where and when. */
@Composable
fun ClosestList(records: Records, modifier: Modifier = Modifier) {
    val today = LocalDate.now().toEpochDay()
    RecordList(stringResource(R.string.closest_ever), records.closest.isEmpty(), modifier) {
        records.closest.forEachIndexed { index, guess ->
            val detail = listOf(countryName(guess.country), dayLabel(guess.day, today)).filter { it.isNotEmpty() }.joinToString(" · ")
            RecordRow(guess.holder, detail, km(guess.km), rank = index + 1)
        }
    }
}

/** A count for each player, most first: wins or close rounds. Only the first few fit. */
@Composable
fun TallyList(title: String, tallies: List<Tally>, count: (Tally) -> Int, modifier: Modifier = Modifier) {
    RecordList(title, tallies.isEmpty(), modifier) {
        tallies.take(CLOSEST_KEPT).forEach { RecordRow(it.holder, "", "${count(it)}") }
    }
}

/** A holder's avatar and name, a [detail] under the name if there is one, and [value] at the end. */
@Composable
fun RecordRow(
    holder: Holder,
    detail: String,
    value: String,
    avatarSize: Dp = 32.dp,
    nameSize: TextUnit = 16.sp,
    rank: Int? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (rank != null) Label("$rank", 15.sp, Muted, FontWeight.Bold, Modifier.width(14.dp))
        // Saved records come from text, so they're kept to the avatars and colours there are.
        Avatar(holder.avatar.coerceIn(AVATARS.indices), holder.slot.coerceIn(PLAYER_COLORS.indices), avatarSize)
        Column(Modifier.weight(1f)) {
            Name(holder.name, nameSize)
            // Left-aligned rather than a centred Label, as it can wrap onto a second line in a narrow card.
            if (detail.isNotEmpty()) BasicText(detail, style = TextStyle(color = Muted, fontSize = 13.sp), maxLines = 2)
        }
        Label(value, 17.sp, Foreground, FontWeight.Bold)
    }
}

/** A country's name in the language of the game's text, from its two-letter code. */
@Composable
fun countryName(code: String): String {
    if (code.isEmpty()) return ""
    val language = Locale.forLanguageTag(stringResource(R.string.language))
    return runCatching { Locale.Builder().setRegion(code).build().getDisplayCountry(language) }.getOrDefault(code)
}

/** "7 nov", or "7 nov 2025" in another year than [today]'s. */
@Composable
fun dayLabel(epochDay: Long, today: Long): String {
    val date = calendarDay(epochDay)
    val month = stringArrayResource(R.array.months)[date.month - 1]
    return if (date.year == calendarDay(today).year) {
        stringResource(R.string.record_date, date.day, month)
    } else {
        stringResource(R.string.record_date_year, date.day, month, date.year)
    }
}
