package com.jjmluz.adonde

import kotlin.math.cos
import kotlin.math.sin

/** A dot of land on the splash screen's globe, with its latitude's sine and cosine worked out once. */
class GlobeDot(val longitude: Float, val sinLatitude: Float, val cosLatitude: Float)

/** Parses the lines of assets/globe.csv: latitude, longitude. */
fun parseGlobe(lines: Sequence<String>): List<GlobeDot> = lines
    .filter { it.isNotBlank() }
    .map { line ->
        val (lat, lng) = line.split(",").map { radians(it.toDouble()).toFloat() }
        GlobeDot(lng, sin(lat), cos(lat))
    }
    .toList()
