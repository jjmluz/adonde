package com.jjmluz.adonde

import android.app.Application
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.min

const val HOLD_MESSAGE_PROGRESS = 0.12f

private const val HOLD_MILLIS = 1200L
private const val BOOST_SECONDS = 2f

/** What the D-pad does on the two playing screens. OK switches between them. */
enum class Control { MOVE, ZOOM }

/** The rows of the title screen, in order: rounds, players, each player, then start. */
const val ROUNDS_ROW = 0
const val PLAYERS_ROW = 1
const val FIRST_PLAYER_ROW = 2

/** The game played with the remote: what each key does, and the state of the keys being held. */
class TvViewModel(app: Application) : GameViewModel(app) {
    private val ok = OkButton(HOLD_MILLIS)
    private val heldKeys = linkedSetOf<Int>()
    private var heldSince = 0L

    var selectedRow by mutableIntStateOf(FIRST_PLAYER_ROW + playerCount)
        private set
    var control by mutableStateOf(Control.MOVE)
        private set
    /** How far in the panorama or map is zoomed, from 0 (all the way out) to 1, for the zoom scale. */
    var zoomLevel by mutableFloatStateOf(0f)
    /** How far through a long press of OK, from 0 to 1. */
    var holdProgress by mutableFloatStateOf(0f)
        private set

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
        if (isOk && ok.release(SystemClock.elapsedRealtime()) && playing) {
            control = if (control == Control.MOVE) Control.ZOOM else Control.MOVE
        }
        if (isOk) holdProgress = 0f
    }

    /** Runs on every frame of a playing screen. */
    fun tick() {
        if (!playing) return
        holdProgress = ok.progress(SystemClock.elapsedRealtime())
        if (holdProgress >= 1f) {
            holdProgress = 0f
            if (screen == Screen.EXPLORE) startGuessing() else placeGuess()
        }
    }

    /** The app lost the screen, so no key-up is coming for anything held. */
    fun releaseKeys() {
        heldKeys.clear()
        ok.cancel()
        holdProgress = 0f
    }

    override fun onControlsReset() {
        heldKeys.clear()
        control = Control.MOVE
    }

    private fun arrow(keyCode: Int) = if (keyCode in heldKeys) 1 else 0

    private fun arrowDown(keyCode: Int, repeatCount: Int): Boolean {
        if (paused) return true
        when (screen) {
            Screen.SETUP -> if (repeatCount == 0) moveOnSetup(keyCode)
            Screen.EXPLORE, Screen.GUESS -> if (repeatCount == 0 && playing) {
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
            ROUNDS_ROW -> changeRounds(step)
            PLAYERS_ROW -> changePlayerCount(step)
            startRow -> Unit
            else -> changeAvatar(selectedRow - FIRST_PLAYER_ROW, step)
        }
    }

    private fun okDown() {
        if (paused) {
            resume()
            return
        }
        when (screen) {
            Screen.SPLASH -> leaveSplash()
            Screen.SETUP -> when (selectedRow) {
                ROUNDS_ROW, PLAYERS_ROW -> Unit
                startRow -> startGame()
                else -> editName(selectedRow - FIRST_PLAYER_ROW)
            }
            Screen.EXPLORE -> if (searchFailed) retrySearch() else ok.press(SystemClock.elapsedRealtime())
            Screen.GUESS -> ok.press(SystemClock.elapsedRealtime())
            Screen.REVEAL -> next()
            Screen.FINAL -> backToStart()
        }
    }

    private fun back(): Boolean {
        when {
            screen == Screen.SPLASH || screen == Screen.SETUP -> return false
            screen == Screen.FINAL || paused -> backToStart()
            else -> {
                releaseKeys()
                pause()
            }
        }
        return true
    }
}
