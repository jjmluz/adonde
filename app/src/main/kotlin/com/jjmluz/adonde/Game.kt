package com.jjmluz.adonde

import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

const val MIN_ROUNDS = 1
const val MAX_ROUNDS = 10
const val MIN_PLAYERS = 1
const val MAX_PLAYERS = 6

private const val EARTH_RADIUS_KM = 6371.0088

data class Place(val lat: Double, val lng: Double)

data class Player(val name: String, val avatar: Int)

/**
 * Gives each of the first [count] players an avatar that no earlier player has, moving on to the next free
 * one when it's taken. A player who comes back when the count goes up keeps their avatar if it's still free.
 */
fun withUniqueAvatars(players: List<Player>, count: Int, avatars: Int): List<Player> {
    val taken = mutableSetOf<Int>()
    return players.mapIndexed { index, player ->
        if (index >= count) return@mapIndexed player
        var avatar = player.avatar
        while (avatar in taken) avatar = (avatar + 1) % avatars
        taken += avatar
        player.copy(avatar = avatar)
    }
}

/** Great-circle distance, which is what makes guessing across the 180° meridian fair. */
fun distanceKm(a: Place, b: Place): Double {
    val latA = Math.toRadians(a.lat)
    val latB = Math.toRadians(b.lat)
    val h = sin((latB - latA) / 2).pow(2) +
        cos(latA) * cos(latB) * sin(Math.toRadians(b.lng - a.lng) / 2).pow(2)
    return 2 * EARTH_RADIUS_KM * asin(min(1.0, sqrt(h)))
}

/**
 * One decimal under 100 km, then whole kilometres, with the marks of the game's language: "12,3 km" and
 * "1 234 km" in Portuguese, "12.3 km" and "1,234 km" in English. The marks come from the strings rather
 * than the system locale, so the numbers match the language the text is in when it falls back to English.
 */
fun formatKm(km: Double, decimalMark: String, groupMark: String): String {
    val decimals = if (km < 100) 1 else 0
    val digits = String.format(Locale.ROOT, "%,.${decimals}f", km)
    return buildString {
        digits.forEach {
            when (it) {
                ',' -> append(groupMark)
                '.' -> append(decimalMark)
                else -> append(it)
            }
        }
        append(" km")
    }
}

/**
 * The saved name of the player at [index], or "" for the default, which is shown in the current language.
 * "Jogador N" is what older versions saved for the default, in Portuguese only.
 */
fun savedName(saved: String?, index: Int): String =
    if (saved == null || saved == "Jogador ${index + 1}") "" else saved

/** One player's line on a scoreboard. */
data class Score(val player: Int, val guess: Place?, val roundKm: Double, val totalKm: Double)

/**
 * The rules. Every player guesses the same place each round, one at a time, and a different
 * player goes first each round because later guessers can see the pins placed before theirs.
 */
class Game(val players: List<Player>, val rounds: Int) {
    private val guesses = mutableMapOf<Int, Place>()
    private val totals = DoubleArray(players.size)

    var round = 1
        private set
    var place: Place? = null
        private set

    /** The player whose turn it is, or null once everyone has guessed this round. */
    val guesser: Int?
        get() = if (guesses.size == players.size) null else (round - 1 + guesses.size) % players.size

    val isLastRound: Boolean
        get() = round == rounds

    /** This round's scoreboard, closest guess first. */
    val roundScores: List<Score>
        get() = players.indices
            .map { player ->
                val roundKm = guesses[player]?.let { distanceKm(it, checkNotNull(place)) } ?: 0.0
                Score(player, guesses[player], roundKm, totals[player])
            }
            .sortedBy { it.roundKm }

    /** The running scoreboard, with the fewest cumulative kilometres first. */
    val standings: List<Score>
        get() = roundScores.sortedBy { it.totalKm }

    fun startRound(place: Place) {
        this.place = place
        guesses.clear()
    }

    fun guess(guess: Place) {
        val player = checkNotNull(guesser) { "Everyone has already guessed" }
        guesses[player] = guess
        totals[player] += distanceKm(guess, checkNotNull(place))
    }

    fun nextRound() {
        check(!isLastRound) { "The game is over" }
        round++
        place = null
        guesses.clear()
    }
}

/**
 * Tells a short press of OK from a long one, by the clock rather than the system's long-press
 * flag, which isn't reliable on every remote. Only a press that started here counts, so the OK
 * still held from the previous screen does nothing when it's let go.
 */
class OkButton(private val holdMillis: Long) {
    private var pressedAt: Long? = null

    fun press(now: Long) {
        pressedAt = now
    }

    /** Returns true when this was a short press. */
    fun release(now: Long): Boolean {
        val start = pressedAt ?: return false
        pressedAt = null
        return now - start < holdMillis
    }

    fun cancel() {
        pressedAt = null
    }

    /** From 0 to 1 while held. Reaching 1 ends the press, so it can't fire twice. */
    fun progress(now: Long): Float {
        val start = pressedAt ?: return 0f
        val progress = (now - start).toFloat() / holdMillis
        if (progress >= 1f) pressedAt = null
        return progress.coerceAtMost(1f)
    }
}
