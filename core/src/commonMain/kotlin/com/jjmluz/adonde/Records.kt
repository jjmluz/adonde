package com.jjmluz.adonde

/** How many of the closest guesses ever are kept. */
const val CLOSEST_KEPT = 5

/** A round counts as close under this distance. */
const val CLOSE_ROUND_KM = 100.0

/**
 * Who holds a record: the name, and how they last looked, so the records screen shows the avatar and
 * colour they play with now. Records go by name, so a player without one of their own has none.
 */
data class Holder(val name: String, val avatar: Int, val slot: Int)

/** One of the closest guesses ever: [country] is the place's country code, [day] the day since 1970. */
data class CloseGuess(val holder: Holder, val km: Double, val country: String, val day: Long)

/** What a player has collected over every game. */
data class Tally(val holder: Holder, val wins: Int, val closeRounds: Int)

enum class RecordKind { CLOSEST, WINS, CLOSE_ROUNDS }

/** A record broken in a game: [value] is the distance for [RecordKind.CLOSEST], a count otherwise. */
data class NewRecord(val kind: RecordKind, val holder: Holder, val value: Double, val country: String = "")

/** Every game's records, saved as one piece of text. Unnamed players never appear in them. */
data class Records(val closest: List<CloseGuess> = emptyList(), val tallies: List<Tally> = emptyList()) {
    val byWins: List<Tally>
        get() = tallies.filter { it.wins > 0 }.sortedByDescending { it.wins }

    val byCloseRounds: List<Tally>
        get() = tallies.filter { it.closeRounds > 0 }.sortedByDescending { it.closeRounds }

    /** One line per record, its fields between tabs. */
    fun encode(): String = buildString {
        closest.forEach { line("C", it.holder, it.km, it.country, it.day) }
        tallies.forEach { line("T", it.holder, it.wins, it.closeRounds) }
    }

    private fun StringBuilder.line(kind: String, holder: Holder, vararg fields: Any) {
        // A tab or a line break in a name would split it into other fields.
        val name = holder.name.replace('\t', ' ').replace('\n', ' ')
        append((listOf(kind, name, holder.avatar, holder.slot) + fields).joinToString("\t")).append('\n')
    }

    companion object {
        /** Reads what [encode] wrote, skipping any line it can't make sense of. */
        fun decode(text: String?): Records {
            val closest = mutableListOf<CloseGuess>()
            val tallies = mutableListOf<Tally>()
            text.orEmpty().lineSequence().forEach { line ->
                val fields = line.split('\t')
                val holder = fields.getOrNull(3)?.toIntOrNull()?.let { slot ->
                    fields[2].toIntOrNull()?.let { Holder(fields[1], it, slot) }
                } ?: return@forEach
                when {
                    fields[0] == "C" && fields.size == 7 -> {
                        val km = fields[4].toDoubleOrNull() ?: return@forEach
                        val day = fields[6].toLongOrNull() ?: return@forEach
                        closest += CloseGuess(holder, km, fields[5], day)
                    }
                    fields[0] == "T" && fields.size == 6 -> {
                        val wins = fields[4].toIntOrNull() ?: return@forEach
                        val close = fields[5].toIntOrNull() ?: return@forEach
                        tallies += Tally(holder, wins, close)
                    }
                }
            }
            return Records(closest.sortedBy { it.km }.take(CLOSEST_KEPT), tallies)
        }
    }
}

/** Names match whatever their case or the spaces around them, so "ana" and "Ana" are the same player. */
private fun key(name: String) = name.trim().lowercase()

/**
 * Adds one game's rounds and result to the records, and remembers how they stood when it started, to tell
 * which records it broke. [players] are the game's, in order, so a score's player is their slot.
 */
class RecordKeeper(private val start: Records, private val players: List<Player>) {
    private val holders = players.mapIndexed { slot, player -> Holder(player.name.trim(), player.avatar, slot) }
    private var closestThisGame: CloseGuess? = null

    var records: Records = start.let { records ->
        // Everyone named in this game is shown as they look now, in every record they hold.
        val now = holders.filter { it.name.isNotEmpty() }.associateBy { key(it.name) }
        val known = records.tallies.map { key(it.holder.name) }.toSet()
        Records(
            closest = records.closest.map { guess -> now[key(guess.holder.name)]?.let { guess.copy(holder = it) } ?: guess },
            tallies = records.tallies.map { tally -> now[key(tally.holder.name)]?.let { tally.copy(holder = it) } ?: tally } +
                now.filterKeys { it !in known }.values.map { Tally(it, 0, 0) },
        )
    }
        private set

    private fun named(player: Int) = holders[player].name.isNotEmpty()

    /**
     * Adds a round's guesses to the records. Returns the player whose guess is now the closest ever, if one's is.
     */
    fun addRound(scores: List<Score>, country: String, day: Long): Int? {
        val guessed = scores.filter { it.guess != null && named(it.player) }
        val best = records.closest.firstOrNull()?.km
        val closest = (records.closest + guessed.map { CloseGuess(holders[it.player], it.roundKm, country, day) })
            // Stable, so an older guess stays ahead of an equal new one.
            .sortedBy { it.km }
            .take(CLOSEST_KEPT)
        val close = guessed.filter { it.roundKm < CLOSE_ROUND_KM }.map { key(holders[it.player].name) }.toSet()
        records = Records(
            closest = closest,
            tallies = records.tallies.map { if (key(it.holder.name) in close) it.copy(closeRounds = it.closeRounds + 1) else it },
        )
        val first = closest.firstOrNull() ?: return null
        if (best != null && first.km >= best) return null
        val winner = guessed.firstOrNull { holders[it.player] == first.holder && it.roundKm == first.km } ?: return null
        closestThisGame = first
        return winner.player
    }

    /** Counts the win. A game played alone has no winner. */
    fun addGame(standings: List<Score>) {
        if (players.size < 2) return
        val winner = standings.firstOrNull()?.player?.takeIf { named(it) } ?: return
        val name = key(holders[winner].name)
        records = records.copy(tallies = records.tallies.map { if (key(it.holder.name) == name) it.copy(wins = it.wins + 1) else it })
    }

    /** The records this game has broken so far: the closest guess ever, and the most wins or close rounds ever. */
    val broken: List<NewRecord>
        get() = buildList {
            closestThisGame?.takeIf { it == records.closest.firstOrNull() }?.let {
                add(NewRecord(RecordKind.CLOSEST, it.holder, it.km, it.country))
            }
            val wins = records.tallies.maxByOrNull { it.wins }
            if (wins != null && wins.wins > (start.tallies.maxOfOrNull { it.wins } ?: 0)) {
                add(NewRecord(RecordKind.WINS, wins.holder, wins.wins.toDouble()))
            }
            val close = records.tallies.maxByOrNull { it.closeRounds }
            if (close != null && close.closeRounds > (start.tallies.maxOfOrNull { it.closeRounds } ?: 0)) {
                add(NewRecord(RecordKind.CLOSE_ROUNDS, close.holder, close.closeRounds.toDouble()))
            }
        }
}

/** A day of the calendar. */
data class CalendarDay(val year: Int, val month: Int, val day: Int)

/** The calendar day of [epochDay], the days since 1 January 1970; `java.time` isn't available on iOS. */
fun calendarDay(epochDay: Long): CalendarDay {
    // Howard Hinnant's civil_from_days, with years starting in March so the leap day comes last.
    val z = epochDay + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val dayOfEra = z - era * 146097
    val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val shifted = (5 * dayOfYear + 2) / 153
    val day = dayOfYear - (153 * shifted + 2) / 5 + 1
    val month = if (shifted < 10) shifted + 3 else shifted - 9
    val year = yearOfEra + era * 400 + if (month <= 2) 1 else 0
    return CalendarDay(year.toInt(), month.toInt(), day.toInt())
}
