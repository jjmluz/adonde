package com.jjmluz.adonde

import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.random.Random

/** How far from a town a search can start, in degrees of latitude: about 3 km. */
private const val SPREAD_DEGREES = 0.03

class Seed(val country: String, val place: Place)

/** Parses the lines of assets/places.csv: country code, latitude, longitude. */
fun parseSeeds(lines: Sequence<String>): List<Seed> = lines
    .filter { it.isNotBlank() }
    .map { line ->
        val (country, lat, lng) = line.split(",")
        Seed(country, Place(lat.toDouble(), lng.toDouble()))
    }
    .toList()

/**
 * Picks where to look for a panorama. Countries are weighted by the square root of their number of
 * towns, so that the United States comes up more than Andorra, but not 200 times more.
 */
class PlacePicker(seeds: List<Seed>, private val random: Random) {
    private val countries = seeds.groupBy { it.country }.values.toList()
    private val weights = countries.map { sqrt(it.size.toDouble()) }
    private val totalWeight = weights.sum()

    fun next(): Place {
        var left = random.nextDouble(totalWeight)
        val index = weights.indexOfFirst { weight ->
            left -= weight
            left < 0
        }
        val town = countries[if (index >= 0) index else countries.lastIndex].random(random).place
        val lat = (town.lat + spread()).coerceIn(-85.0, 85.0)
        val lng = town.lng + spread() / cos(radians(town.lat)).coerceAtLeast(0.1)
        return Place(lat, ((lng + 540) % 360) - 180)
    }

    private fun spread() = random.nextDouble(-SPREAD_DEGREES, SPREAD_DEGREES)
}
