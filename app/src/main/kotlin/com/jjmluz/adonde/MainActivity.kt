package com.jjmluz.adonde

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val viewModel: TvViewModel by viewModels()
    private val usedKeys = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { AdondeApp(viewModel) }
    }

    override fun onPause() {
        super.onPause()
        viewModel.releaseKeys()
    }

    // Keys are handled here rather than through Compose focus: nothing on screen is focusable, and
    // this way the map and the panorama never see the remote. A used key's release is swallowed too.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (viewModel.onKeyDown(keyCode, event.repeatCount)) usedKeys += keyCode
                if (keyCode in usedKeys) return true
            }
            KeyEvent.ACTION_UP -> {
                viewModel.onKeyUp(keyCode)
                if (usedKeys.remove(keyCode)) return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
