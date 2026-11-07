package com.jjmluz.adonde

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SEARCH_TIMEOUT_MILLIS = 10000L
private const val SEARCH_ATTEMPTS = 15
private const val DEFAULT_ROUNDS = 5
private const val DEFAULT_PLAYERS = 2

val AVATARS = listOf("🦊", "🐼", "🐸", "🦁", "🐧", "🐙", "🦉", "🐢", "🦄", "🐝", "🐬", "🐨")

enum class Screen { SPLASH, SETUP, EXPLORE, GUESS, REVEAL, FINAL }

/**
 * The game's state and flow, shared by the TV and the phone. Each app drives it in its own way: the TV
 * from the remote's keys (`TvViewModel`), the phone from taps and the maps' own gestures.
 */
open class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("adonde", Context.MODE_PRIVATE)
    private val picker = PlacePicker(
        parseSeeds(app.assets.open("places.csv").bufferedReader().readLines().asSequence()),
        Random.Default,
    )
    val globe = parseGlobe(app.assets.open("globe.csv").bufferedReader().readLines().asSequence())
    private var game: Game? = null
    private var searchTimeout: Job? = null
    private var searchesLeft = 0

    var screen by mutableStateOf(Screen.SPLASH)
        private set

    val players = mutableStateListOf<Player>().apply {
        // An empty name is the default, "Player N" in whatever language the device is in.
        repeat(MAX_PLAYERS) { add(Player(savedName(prefs.getString("name$it", null), it), prefs.getInt("avatar$it", it))) }
    }
    var playerCount by mutableIntStateOf(prefs.getInt("players", DEFAULT_PLAYERS))
        private set
    var rounds by mutableIntStateOf(prefs.getInt("rounds", DEFAULT_ROUNDS))
        private set
    /** The player whose name is being typed, on the title screen. */
    var editingName by mutableStateOf<Int?>(null)
        private set

    init {
        makeAvatarsUnique()
    }

    var paused by mutableStateOf(false)
        private set

    var round by mutableIntStateOf(1)
        private set
    /** Where to search for this round's panorama next. */
    var searchNear by mutableStateOf<Place?>(null)
        private set
    /** The place of this round, once a panorama has been found. */
    var spot by mutableStateOf<Place?>(null)
        private set
    var searchFailed by mutableStateOf(false)
        private set
    var guesser by mutableStateOf<Player?>(null)
        private set
    /** The guesser's position in [players], or null between guesses. */
    var guesserIndex by mutableStateOf<Int?>(null)
        private set
    var roundScores by mutableStateOf<List<Score>>(emptyList())
        private set
    var standings by mutableStateOf<List<Score>>(emptyList())
        private set
    val isLastRound: Boolean
        get() = game?.isLastRound == true

    /** Reads the point under the pin. Set by the map while a player is guessing. */
    var pinPosition: (() -> Place)? = null

    /** Whether the panorama or the guess map is on screen and taking input. */
    val playing: Boolean
        get() = !paused && (screen == Screen.GUESS || (screen == Screen.EXPLORE && spot != null))

    fun leaveSplash() {
        if (screen == Screen.SPLASH) screen = Screen.SETUP
    }

    fun changeRounds(step: Int) {
        rounds = (rounds + step).coerceIn(MIN_ROUNDS, MAX_ROUNDS)
    }

    fun changePlayerCount(step: Int) {
        playerCount = (playerCount + step).coerceIn(MIN_PLAYERS, MAX_PLAYERS)
        makeAvatarsUnique()
    }

    /** Moves to the next avatar that nobody else in this game has. */
    fun changeAvatar(index: Int, step: Int) {
        val taken = players.take(playerCount).map { it.avatar }.toSet()
        var avatar = players[index].avatar
        do {
            avatar = (avatar + step + AVATARS.size) % AVATARS.size
        } while (avatar in taken)
        players[index] = players[index].copy(avatar = avatar)
    }

    fun editName(index: Int) {
        editingName = index
    }

    fun onNameTyped(name: String) {
        val index = editingName ?: return
        if (name.isNotBlank()) players[index] = players[index].copy(name = name.trim())
        editingName = null
    }

    fun startGame() {
        saveSetup()
        game = Game(players.take(playerCount).toList(), rounds)
        startExploring()
    }

    /** The panorama answered a search: with where it landed, or null if nothing was there. */
    fun onPanoramaFound(position: Place?) {
        if (spot != null || screen != Screen.EXPLORE) return
        searchTimeout?.cancel()
        if (position != null) {
            spot = position
            game?.startRound(position)
        } else {
            search()
        }
    }

    fun retrySearch() {
        if (screen == Screen.EXPLORE && searchFailed) startSearch()
    }

    /** Everyone has seen enough: on to the first player's guess. */
    fun startGuessing() {
        if (screen != Screen.EXPLORE || spot == null) return
        showGuesser()
        screen = Screen.GUESS
    }

    /** Takes the point under the pin as the current player's guess. */
    fun placeGuess() {
        val current = game ?: return
        if (screen != Screen.GUESS) return
        val position = pinPosition?.invoke() ?: return
        current.guess(position)
        if (current.guesser == null) {
            roundScores = current.roundScores
            standings = current.standings
            screen = Screen.REVEAL
        } else {
            showGuesser()
        }
    }

    /** From the reveal, on to the next round or the final results. */
    fun next() {
        val current = game ?: return
        if (screen != Screen.REVEAL) return
        if (current.isLastRound) {
            screen = Screen.FINAL
        } else {
            current.nextRound()
            startExploring()
        }
    }

    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
    }

    /** Leaves the game, or the final results, for the title screen. */
    fun backToStart() {
        paused = false
        screen = Screen.SETUP
    }

    /** Called when a round's search starts and when a new player takes the map. */
    protected open fun onControlsReset() = Unit

    private fun startExploring() {
        val current = game ?: return
        round = current.round
        spot = null
        onControlsReset()
        startSearch()
        screen = Screen.EXPLORE
    }

    private fun startSearch() {
        searchFailed = false
        searchesLeft = SEARCH_ATTEMPTS
        search()
    }

    private fun search() {
        if (searchesLeft == 0) {
            searchFailed = true
            return
        }
        searchesLeft--
        searchNear = picker.next()
        // The panorama stays silent if it can't reach Google, so there's no point in retrying.
        searchTimeout?.cancel()
        searchTimeout = viewModelScope.launch {
            delay(SEARCH_TIMEOUT_MILLIS)
            if (screen == Screen.EXPLORE && spot == null) searchFailed = true
        }
    }

    private fun showGuesser() {
        val current = game ?: return
        guesserIndex = current.guesser
        guesser = current.guesser?.let { current.players[it] }
        onControlsReset()
    }

    /** A player who comes back when the count goes up may have kept an avatar someone else has taken since. */
    private fun makeAvatarsUnique() {
        withUniqueAvatars(players, playerCount, AVATARS.size).forEachIndexed { index, player ->
            if (players[index] != player) players[index] = player
        }
    }

    private fun saveSetup() {
        prefs.edit {
            putInt("rounds", rounds)
            putInt("players", playerCount)
            players.forEachIndexed { index, player ->
                putString("name$index", player.name)
                putInt("avatar$index", player.avatar)
            }
        }
    }
}
