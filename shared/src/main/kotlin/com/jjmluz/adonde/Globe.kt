package com.jjmluz.adonde

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

private const val START_LONGITUDE = -12f
private const val DEGREES_PER_SECOND = 22f
private const val DOT_SIZE = 0.011f
private const val RIM = 0.985f

private val SIN_TILT = sin(Math.toRadians(22.0)).toFloat()
private val COS_TILT = cos(Math.toRadians(22.0)).toFloat()
private val Ocean = Color(0xFF15253F)
private val Land = Color(0xFF6F8FC0)

/** The Earth turning west to east, with the far side hidden and the near side brightest. */
@Composable
fun Globe(dots: List<GlobeDot>, modifier: Modifier = Modifier) {
    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { seconds = (it - start) / 1000f }
    }
    Canvas(modifier.clip(CircleShape).background(Ocean)) {
        val radius = size.minDimension / 2 * RIM
        val facing = Math.toRadians((START_LONGITUDE - seconds * DEGREES_PER_SECOND).toDouble()).toFloat()
        dots.forEach { dot ->
            val across = dot.longitude - facing
            val front = dot.cosLatitude * cos(across)
            val depth = dot.sinLatitude * SIN_TILT + front * COS_TILT
            if (depth > 0) {
                val x = dot.cosLatitude * sin(across)
                val y = dot.sinLatitude * COS_TILT - front * SIN_TILT
                drawCircle(
                    color = Land,
                    radius = radius * DOT_SIZE * (0.5f + 0.5f * depth),
                    center = Offset(center.x + x * radius, center.y - y * radius),
                    alpha = 0.35f + 0.65f * depth,
                )
            }
        }
    }
}
