package com.jjmluz.adonde

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.StreetViewPanorama
import com.google.android.gms.maps.StreetViewPanoramaOptions
import com.google.android.gms.maps.StreetViewPanoramaView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.StreetViewPanoramaCamera
import com.google.android.gms.maps.model.StreetViewSource
import kotlin.random.Random

/** How far from the chosen point a panorama can be, in metres. */
private const val SEARCH_RADIUS_METRES = 2000
private const val GOOGLE_PANO_ID_LENGTH = 22
private const val LOOK_DEGREES_PER_SECOND = 50f
private const val PANORAMA_ZOOM_PER_SECOND = 1.5f
private const val MAX_PANORAMA_ZOOM = 3.5f
private const val MAX_TILT = 80f
private const val MAP_SCREENS_PER_SECOND = 0.3f
private const val MAP_ZOOM_PER_SECOND = 1.2f
private const val MAX_REVEAL_ZOOM = 11f
private const val PIN_WIDTH = 44
private const val PIN_HEIGHT = 58

private val WORLD_CENTRE = LatLng(25.0, 10.0)

private fun Place.toLatLng() = LatLng(lat, lng)

private fun LatLng.toPlace() = Place(latitude, longitude)

/** Passes the activity's lifecycle on to a Google view, then takes it down with the composition. */
@Composable
private fun ForwardLifecycle(forward: (Lifecycle.Event) -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> forward(event) }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            forward(Lifecycle.Event.ON_PAUSE)
            forward(Lifecycle.Event.ON_STOP)
            forward(Lifecycle.Event.ON_DESTROY)
        }
    }
}

/** Calls [move] each frame with the arrows held, boosted, and the seconds since the last frame. */
@Composable
private fun ControlLoop(viewModel: GameViewModel, move: (x: Float, y: Float, seconds: Float) -> Unit) {
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

/** Full-screen Street View: searches until it lands on a road, then looks around and zooms. */
@Composable
fun Panorama(viewModel: GameViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = remember {
        val options = StreetViewPanoramaOptions()
            .userNavigationEnabled(false)
            .streetNamesEnabled(false)
            .panningGesturesEnabled(false)
            .zoomGesturesEnabled(false)
        StreetViewPanoramaView(context, options).apply { onCreate(null) }
    }
    ForwardLifecycle { event ->
        when (event) {
            Lifecycle.Event.ON_START -> view.onStart()
            Lifecycle.Event.ON_RESUME -> view.onResume()
            Lifecycle.Event.ON_PAUSE -> view.onPause()
            Lifecycle.Event.ON_STOP -> view.onStop()
            Lifecycle.Event.ON_DESTROY -> view.onDestroy()
            else -> Unit
        }
    }
    var panorama by remember { mutableStateOf<StreetViewPanorama?>(null) }
    LaunchedEffect(view) {
        view.getStreetViewPanoramaAsync { ready ->
            // People's own uploads have longer ids, and their credit can name the place outright.
            ready.setOnStreetViewPanoramaChangeListener { location ->
                val official = location?.takeIf { it.panoId.length == GOOGLE_PANO_ID_LENGTH && !it.links.isNullOrEmpty() }
                viewModel.onPanoramaFound(official?.position?.toPlace())
            }
            panorama = ready
        }
    }
    AndroidView({ view }, modifier.fillMaxSize())

    val current = panorama ?: return
    LaunchedEffect(current, viewModel.searchNear) {
        viewModel.searchNear?.let {
            current.setPosition(it.toLatLng(), SEARCH_RADIUS_METRES, StreetViewSource.OUTDOOR)
        }
    }
    var zoom by remember { mutableFloatStateOf(0f) }
    var tilt by remember { mutableFloatStateOf(0f) }
    var bearing by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(current, viewModel.spot) {
        zoom = 0f
        tilt = 0f
        bearing = Random.nextFloat() * 360
        current.animateTo(StreetViewPanoramaCamera(zoom, tilt, bearing), 0)
    }
    ControlLoop(viewModel) { x, y, seconds ->
        when (viewModel.control) {
            Control.MOVE -> {
                bearing = (bearing + x * LOOK_DEGREES_PER_SECOND * seconds + 360) % 360
                tilt = (tilt - y * LOOK_DEGREES_PER_SECOND * seconds).coerceIn(-MAX_TILT, MAX_TILT)
            }
            Control.ZOOM -> zoom = (zoom - y * PANORAMA_ZOOM_PER_SECOND * seconds).coerceIn(0f, MAX_PANORAMA_ZOOM)
        }
        current.animateTo(StreetViewPanoramaCamera(zoom, tilt, bearing), 0)
    }
}

@Composable
private fun rememberMap(modifier: Modifier = Modifier): Pair<MapView, GoogleMap?> {
    val context = LocalContext.current
    val view = remember {
        val options = GoogleMapOptions()
            .compassEnabled(false)
            .mapToolbarEnabled(false)
            .zoomControlsEnabled(false)
            .rotateGesturesEnabled(false)
            .tiltGesturesEnabled(false)
            .scrollGesturesEnabled(false)
            .zoomGesturesEnabled(false)
        MapView(context, options).apply { onCreate(null) }
    }
    ForwardLifecycle { event ->
        when (event) {
            Lifecycle.Event.ON_START -> view.onStart()
            Lifecycle.Event.ON_RESUME -> view.onResume()
            Lifecycle.Event.ON_PAUSE -> view.onPause()
            Lifecycle.Event.ON_STOP -> view.onStop()
            Lifecycle.Event.ON_DESTROY -> view.onDestroy()
            else -> Unit
        }
    }
    var map by remember { mutableStateOf<GoogleMap?>(null) }
    LaunchedEffect(view) { view.getMapAsync { map = it } }
    AndroidView({ view }, modifier.fillMaxSize())
    return view to map
}

/** The full-screen map with the pin fixed in the middle. Moving the map is what places the pin. */
@Composable
fun GuessMap(viewModel: GameViewModel, avatar: Int, colorIndex: Int, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        val (view, map) = rememberMap()
        val context = LocalContext.current
        val pin = remember(avatar, colorIndex) {
            pinBitmap(context, PLAYER_COLORS[colorIndex].toArgb(), AVATARS[avatar]).asImageBitmap()
        }
        // The pin's tip, not its middle, marks the centre of the map.
        Image(
            bitmap = pin,
            contentDescription = null,
            modifier = Modifier.align(Alignment.Center).offset { IntOffset(0, -pin.height / 2) },
        )
        val current = map ?: return@Box
        LaunchedEffect(current, viewModel.guesserIndex) {
            current.moveCamera(CameraUpdateFactory.newLatLngZoom(WORLD_CENTRE, current.minZoomLevel))
        }
        DisposableEffect(current) {
            viewModel.pinPosition = { current.cameraPosition.target.toPlace() }
            onDispose { viewModel.pinPosition = null }
        }
        ControlLoop(viewModel) { x, y, seconds ->
            when (viewModel.control) {
                Control.MOVE -> {
                    val speed = view.width * MAP_SCREENS_PER_SECOND * seconds
                    current.moveCamera(CameraUpdateFactory.scrollBy(x * speed, y * speed))
                }
                Control.ZOOM -> current.moveCamera(CameraUpdateFactory.zoomBy(-y * MAP_ZOOM_PER_SECOND * seconds))
            }
        }
    }
}

/** The answer: the real place, everyone's pin, and a line between them. */
@Composable
fun RevealMap(place: Place, scores: List<Score>, players: List<Player>, boardHeight: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val (_, map) = rememberMap(modifier)
    val current = map ?: return
    LaunchedEffect(current, place) {
        current.clear()
        current.setPadding(0, 0, 0, with(density) { boardHeight.roundToPx() })
        val target = place.toLatLng()
        val points = listOf(target) + scores.mapNotNull { it.guess?.toLatLng() }
        scores.forEach { score ->
            val guess = score.guess?.toLatLng() ?: return@forEach
            current.addPolyline(
                PolylineOptions().add(guess, target).geodesic(true).width(5f)
                    .color(PLAYER_COLORS[score.player].toArgb()),
            )
            val pin = pinBitmap(context, PLAYER_COLORS[score.player].toArgb(), AVATARS[players[score.player].avatar])
            current.addMarker(MarkerOptions().position(guess).icon(BitmapDescriptorFactory.fromBitmap(pin)))
        }
        val goal = pinBitmap(context, Good.toArgb(), "⭐")
        current.addMarker(MarkerOptions().position(target).zIndex(1f).icon(BitmapDescriptorFactory.fromBitmap(goal)))
        current.setOnMapLoadedCallback {
            val bounds = LatLngBounds.Builder().apply { points.forEach(::include) }.build()
            val margin = with(density) { REVEAL_MARGIN.roundToPx() }
            current.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, margin))
            if (current.cameraPosition.zoom > MAX_REVEAL_ZOOM) {
                current.moveCamera(CameraUpdateFactory.zoomTo(MAX_REVEAL_ZOOM))
            }
            // The map never zooms out to the whole world, so far-apart pins may not fit. The answer
            // always has to show; the others are still on the board, with their lines leading off.
            val visible = current.projection.visibleRegion.latLngBounds
            if (!points.all(visible::contains)) current.moveCamera(CameraUpdateFactory.newLatLng(target))
        }
    }
}

/** A drop-shaped pin with an avatar in its head and its tip at the bottom middle of the bitmap. */
fun pinBitmap(context: Context, color: Int, label: String): Bitmap {
    val scale = context.resources.displayMetrics.density
    val width = PIN_WIDTH * scale
    val height = PIN_HEIGHT * scale
    val border = 2 * scale
    val bitmap = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = width / 2 - border
    val middle = width / 2
    val head = RectF(border, border, width - border, border + 2 * radius)
    val drop = Path().apply {
        arcTo(head, 150f, 240f)
        lineTo(middle, height - border)
        close()
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = color
    canvas.drawPath(drop, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = border
    paint.color = android.graphics.Color.WHITE
    canvas.drawPath(drop, paint)
    paint.style = Paint.Style.FILL
    canvas.drawCircle(middle, border + radius, radius * 0.72f, paint)
    paint.typeface = Typeface.DEFAULT
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = radius
    val baseline = border + radius - (paint.descent() + paint.ascent()) / 2
    canvas.drawText(label, middle, baseline, paint)
    return bitmap
}
