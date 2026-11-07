package com.jjmluz.adonde

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.StreetViewPanoramaCamera
import kotlin.random.Random

private const val LOOK_DEGREES_PER_SECOND = 50f
private const val PANORAMA_ZOOM_PER_SECOND = 1.5f
private const val MAX_PANORAMA_ZOOM = 3.5f
private const val MAX_TILT = 80f
private const val MAP_SCREENS_PER_SECOND = 0.3f
private const val MAP_ZOOM_PER_SECOND = 1.2f

/** Calls [move] each frame with the arrows held, boosted, and the seconds since the last frame. */
@Composable
private fun ControlLoop(viewModel: TvViewModel, move: (x: Float, y: Float, seconds: Float) -> Unit) {
    val currentMove by rememberUpdatedState(move)
    LaunchedEffect(Unit) {
        var last = withFrameMillis { it }
        while (true) {
            withFrameMillis { now ->
                val seconds = (now - last) / 1000f
                last = now
                viewModel.tick()
                val x = viewModel.horizontal
                val y = viewModel.vertical
                if (x != 0 || y != 0) currentMove(x * viewModel.boost, y * viewModel.boost, seconds)
            }
        }
    }
}

/** Street View turned and zoomed with the D-pad. */
@Composable
fun TvPanorama(viewModel: TvViewModel) {
    Panorama(viewModel, gestures = false) { current ->
        var zoom by remember { mutableFloatStateOf(0f) }
        var tilt by remember { mutableFloatStateOf(0f) }
        var bearing by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(current, viewModel.spot) {
            zoom = 0f
            tilt = 0f
            bearing = Random.nextFloat() * 360
            viewModel.zoomLevel = 0f
            current.animateTo(StreetViewPanoramaCamera(zoom, tilt, bearing), 0)
        }
        ControlLoop(viewModel) { x, y, seconds ->
            // Left and right turn in both modes, so zooming in on something doesn't mean switching back to follow it.
            bearing = (bearing + x * LOOK_DEGREES_PER_SECOND * seconds + 360) % 360
            when (viewModel.control) {
                Control.MOVE -> tilt = (tilt - y * LOOK_DEGREES_PER_SECOND * seconds).coerceIn(-MAX_TILT, MAX_TILT)
                Control.ZOOM -> {
                    zoom = (zoom - y * PANORAMA_ZOOM_PER_SECOND * seconds).coerceIn(0f, MAX_PANORAMA_ZOOM)
                    viewModel.zoomLevel = zoom / MAX_PANORAMA_ZOOM
                }
            }
            current.animateTo(StreetViewPanoramaCamera(zoom, tilt, bearing), 0)
        }
    }
}

/** The guess map moved and zoomed with the D-pad. */
@Composable
fun TvGuessMap(viewModel: TvViewModel, avatar: Int, colorIndex: Int) {
    GuessMap(viewModel, avatar, colorIndex, gestures = false) { view, current ->
        LaunchedEffect(current, viewModel.guesserIndex) { viewModel.zoomLevel = 0f }
        ControlLoop(viewModel) { x, y, seconds ->
            // Left and right move the map in both modes, the same as turning in Street View.
            val speed = view.width * MAP_SCREENS_PER_SECOND * seconds
            val down = if (viewModel.control == Control.MOVE) y else 0f
            if (x != 0f || down != 0f) current.moveCamera(CameraUpdateFactory.scrollBy(x * speed, down * speed))
            if (viewModel.control == Control.ZOOM && y != 0f) {
                current.moveCamera(CameraUpdateFactory.zoomBy(-y * MAP_ZOOM_PER_SECOND * seconds))
                val zoom = current.cameraPosition.zoom
                viewModel.zoomLevel =
                    ((zoom - current.minZoomLevel) / (current.maxZoomLevel - current.minZoomLevel)).coerceIn(0f, 1f)
            }
        }
    }
}
