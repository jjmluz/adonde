package com.jjmluz.adonde

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.random.Random

const val HOLD_MESSAGE_PROGRESS = 0.12f

private const val HOLD_MILLIS = 1200L
private const val SEARCH_TIMEOUT_MILLIS = 10000L
private const val SEARCH_ATTEMPTS = 15
private const val DEFAULT_ROUNDS = 5
private const val DEFAULT_PLAYERS = 2
private const val BOOST_SECONDS = 2f

val AVATARS = listOf("🦊", "🐼", "🐸", "🦁", "🐧", "🐙", "🦉", "🐢", "🦄", "🐝", "🐬", "🐨")

enum class Screen { SPLASH, SETUP, EXPLORE, GUESS, REVEAL, FINAL }

/** What the D-pad does on the two playing screens. OK switches between them. */
enum class Control { MOVE, ZOOM }

/** The rows of the title screen, in order: rounds, players, each player, then start. */
const val ROUNDS_ROW = 0
const val PLAYERS_ROW = 1
const val FIRST_PLAYER_ROW = 2

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("adonde", Context.MODE_PRIVATE)
    private val picker = PlacePicker(
        parseSeeds(app.assets.open("places.csv").bufferedReader().readLines().asSequence()),
        Random.Default,
    )
    val globe = parseGlobe(app.assets.open("globe.csv").bufferedReader().readLines().asSequence())
    private val ok = OkButton(HOLD_MILLIS)
    private val heldKeys = linkedSetOf<Int>()
    private var heldSince = 0L
    private var game: Game? = null
    private var searchTimeout: Job? = null
    private var searchesLeft = 0

    var screen by mutableStateOf(Screen.SPLASH)
        private set

    val players = mutableStateListOf<Player>().apply {
        // An empty name is the default, "Player N" in whatever language the TV is in.
        repeat(MAX_PLAYERS) { add(Player(savedName(prefs.getString("name$it", null), it), prefs.getInt("avatar$it", it))) }
    }
    var playerCount by mutableIntStateOf(prefs.getInt("players", DEFAULT_PLAYERS))
        private set
    var rounds by mutableIntStateOf(prefs.getInt("rounds", DEFAULT_ROUNDS))
        private set
    var selectedRow by mutableIntStateOf(FIRST_PLAYER_ROW + playerCount)
        private set
    /** The player whose name is being typed, on the title screen. */
    var editingName by mutableStateOf<Int?>(null)
        private set

    init {
        makeAvatarsUnique()
    }

    var paused by mutableStateOf(false)
        private set
    var control by mutableStateOf(Control.MOVE)
        private set
    /** How far in the panorama or map is zoomed, from 0 (all the way out) to 1, for the zoom scale. */
    var zoomLevel by mutableFloatStateOf(0f)
    /** How far through a long press of OK, from 0 to 1. */
    var holdProgress by mutableFloatStateOf(0f)
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

    val startRow: Int
        get() = FIRST_PLAYER_ROW + playerCount

    /** The direction of the arrows being held: -1, 0 or 1 on each axis. */
    val horizontal: Int
        get() = arrow(KeyEvent.KEYCODE_DPAD_RIGHT) - arrow(KeyEvent.KEYCODE_DPAD_LEFT)
    val vertical: Int
        get() = arrow(KeyEvent.KEYCODE_DPAD_DOWN) - arrow(KeyEvent.KEYCODE_DPAD_UP)

    /** Movement speeds up from 1 to 3 the longer an arrow is held. */
    val boost: Float
        get() = 1 + min((SystemClock.elapsedRealtime() - heldSince) / 1000f, BOOST_SECONDS)

    private val controlsActive: Boolean
        get() = !paused && (screen == Screen.GUESS || (screen == Screen.EXPLORE && spot != null))

    /** Returns true when the key was used, so it doesn't also reach the system. */
    fun onKeyDown(keyCode: Int, repeatCount: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> arrowDown(keyCode, repeatCount)
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_BUTTON_A -> {
            if (repeatCount == 0) okDown()
            true
        }
        KeyEvent.KEYCODE_BACK -> if (repeatCount == 0) back() else screen != Screen.SETUP
        else -> false
    }

    fun onKeyUp(keyCode: Int) {
        heldKeys -= keyCode
        val isOk = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A
        if (isOk && ok.release(SystemClock.elapsedRealtime()) && controlsActive) {
            control = if (control == Control.MOVE) Control.ZOOM else Control.MOVE
        }
        if (isOk) holdProgress = 0f
    }

    /** Runs on every frame of a playing screen. */
    fun tick() {
        if (!controlsActive) return
        holdProgress = ok.progress(SystemClock.elapsedRealtime())
        if (holdProgress >= 1f) {
            holdProgress = 0f
            if (screen == Screen.EXPLORE) startGuessing() else placeGuess()
        }
    }

    fun leaveSplash() {
        if (screen == Screen.SPLASH) screen = Screen.SETUP
    }

    /** The app lost the screen, so no key-up is coming for anything held. */
    fun releaseKeys() {
        heldKeys.clear()
        ok.cancel()
        holdProgress = 0f
    }

    fun onNameTyped(name: String) {
        val index = editingName ?: return
        if (name.isNotBlank()) players[index] = players[index].copy(name = name.trim())
        editingName = null
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

    private fun arrow(keyCode: Int) = if (keyCode in heldKeys) 1 else 0

    private fun arrowDown(keyCode: Int, repeatCount: Int): Boolean {
        if (paused) return true
        when (screen) {
            Screen.SETUP -> if (repeatCount == 0) moveOnSetup(keyCode)
            Screen.EXPLORE, Screen.GUESS -> if (repeatCount == 0 && controlsActive) {
                if (heldKeys.isEmpty()) heldSince = SystemClock.elapsedRealtime()
                heldKeys += keyCode
            }
            Screen.SPLASH, Screen.REVEAL, Screen.FINAL -> Unit
        }
        return true
    }

    private fun moveOnSetup(keyCode: Int) {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> selectedRow = (selectedRow - 1).coerceAtLeast(ROUNDS_ROW)
            KeyEvent.KEYCODE_DPAD_DOWN -> selectedRow = (selectedRow + 1).coerceAtMost(startRow)
            KeyEvent.KEYCODE_DPAD_LEFT -> changeSelected(-1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> changeSelected(1)
        }
    }

    private fun changeSelected(step: Int) {
        when (selectedRow) {
            ROUNDS_ROW -> rounds = (rounds + step).coerceIn(MIN_ROUNDS, MAX_ROUNDS)
            PLAYERS_ROW -> {
                playerCount = (playerCount + step).coerceIn(MIN_PLAYERS, MAX_PLAYERS)
                makeAvatarsUnique()
            }
            startRow -> Unit
            else -> changeAvatar(selectedRow - FIRST_PLAYER_ROW, step)
        }
    }

    /** Moves to the next avatar that nobody else in this game has. */
    private fun changeAvatar(index: Int, step: Int) {
        val taken = players.take(playerCount).map { it.avatar }.toSet()
        var avatar = players[index].avatar
        do {
            avatar = (avatar + step + AVATARS.size) % AVATARS.size
        } while (avatar in taken)
        players[index] = players[index].copy(avatar = avatar)
    }

    /** A player who comes back when the count goes up may have kept an avatar someone else has taken since. */
    private fun makeAvatarsUnique() {
        withUniqueAvatars(players, playerCount, AVATARS.size).forEachIndexed { index, player ->
            if (players[index] != player) players[index] = player
        }
    }

    private fun okDown() {
        if (paused) {
            paused = false
            return
        }
        when (screen) {
            Screen.SPLASH -> leaveSplash()
            Screen.SETUP -> when (selectedRow) {
                ROUNDS_ROW, PLAYERS_ROW -> Unit
                startRow -> startGame()
                else -> editingName = selectedRow - FIRST_PLAYER_ROW
            }
            Screen.EXPLORE -> if (searchFailed) startSearch() else ok.press(SystemClock.elapsedRealtime())
            Screen.GUESS -> ok.press(SystemClock.elapsedRealtime())
            Screen.REVEAL -> next()
            Screen.FINAL -> screen = Screen.SETUP
        }
    }

    private fun back(): Boolean {
        when {
            screen == Screen.SPLASH || screen == Screen.SETUP -> return false
            screen == Screen.FINAL -> screen = Screen.SETUP
            paused -> {
                paused = false
                screen = Screen.SETUP
            }
            else -> {
                releaseKeys()
                paused = true
            }
        }
        return true
    }

    private fun startGame() {
        saveSetup()
        game = Game(players.take(playerCount).toList(), rounds)
        startExploring()
    }

    private fun startExploring() {
        val current = game ?: return
        round = current.round
        spot = null
        control = Control.MOVE
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

    private fun startGuessing() {
        heldKeys.clear()
        showGuesser()
        screen = Screen.GUESS
    }

    private fun placeGuess() {
        val current = game ?: return
        val position = pinPosition?.invoke() ?: return
        current.guess(position)
        heldKeys.clear()
        if (current.guesser == null) {
            roundScores = current.roundScores
            standings = current.standings
            screen = Screen.REVEAL
        } else {
            showGuesser()
        }
    }

    private fun showGuesser() {
        val current = game ?: return
        guesserIndex = current.guesser
        guesser = current.guesser?.let { current.players[it] }
        control = Control.MOVE
    }

    private fun next() {
        val current = game ?: return
        if (current.isLastRound) {
            screen = Screen.FINAL
        } else {
            current.nextRound()
            startExploring()
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
