package com.jjmluz.adonde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

private val LISBON = Place(38.7223, -9.1393)
private val PORTO = Place(41.1579, -8.6291)
private val LONDON = Place(51.5074, -0.1278)
private val NEW_YORK = Place(40.7128, -74.0060)

private fun players(count: Int) = List(count) { Player("Jogador ${it + 1}", it) }

class GlobeTest {
    @Test
    fun `reads dots as angles`() {
        val dots = parseGlobe(sequenceOf("90.0,180.0", "", "0.0,-90.0"))
        assertEquals(2, dots.size)
        assertEquals(Math.PI, dots[0].longitude.toDouble(), 0.001)
        assertEquals(1.0, dots[0].sinLatitude.toDouble(), 0.001)
        assertEquals(1.0, dots[1].cosLatitude.toDouble(), 0.001)
    }
}

class DistanceTest {
    @Test
    fun `matches known distances`() {
        assertEquals(274.0, distanceKm(LISBON, PORTO), 3.0)
        assertEquals(5570.0, distanceKm(LONDON, NEW_YORK), 10.0)
    }

    @Test
    fun `is zero for the same place`() {
        assertEquals(0.0, distanceKm(LISBON, LISBON), 0.0)
    }

    @Test
    fun `takes the short way across the 180 degree meridian`() {
        assertEquals(222.4, distanceKm(Place(0.0, 179.0), Place(0.0, -179.0)), 0.5)
    }

    @Test
    fun `handles opposite ends of the world`() {
        assertEquals(20015.0, distanceKm(Place(0.0, 0.0), Place(0.0, 180.0)), 5.0)
    }

    @Test
    fun `formats kilometres in Portuguese`() {
        assertEquals("12,3 km", formatKm(12.34, ",", " "))
        assertEquals("274 km", formatKm(274.2, ",", " "))
        assertEquals("1 235 km", formatKm(1234.6, ",", " "))
    }

    @Test
    fun `formats kilometres in English and Spanish`() {
        assertEquals("12.3 km", formatKm(12.34, ".", ","))
        assertEquals("12,345 km", formatKm(12345.2, ".", ","))
        assertEquals("12.345 km", formatKm(12345.2, ",", "."))
        assertEquals("9,9 km", formatKm(9.94, ",", "."))
    }
}

class NameTest {
    @Test
    fun `an unsaved or old Portuguese default name becomes the default`() {
        assertEquals("", savedName(null, 0))
        assertEquals("", savedName("Jogador 3", 2))
    }

    @Test
    fun `a chosen name is kept`() {
        assertEquals("Sara", savedName("Sara", 0))
        // Only the default for that seat counts.
        assertEquals("Jogador 3", savedName("Jogador 3", 0))
    }
}

class AvatarTest {
    @Test
    fun `a returning player whose avatar is taken gets the next free one`() {
        val saved = listOf(Player("Sara", 2), Player("Rui", 1), Player("Jogador 3", 2), Player("Jogador 4", 3))
        val fixed = withUniqueAvatars(saved, 3, 12)
        assertEquals(listOf(2, 1, 3, 3), fixed.map { it.avatar })
    }

    @Test
    fun `keeps avatars that are already different`() {
        assertEquals(players(6), withUniqueAvatars(players(6), 6, 12))
    }

    @Test
    fun `wraps around past the last avatar`() {
        val fixed = withUniqueAvatars(listOf(Player("A", 11), Player("B", 11)), 2, 12)
        assertEquals(listOf(11, 0), fixed.map { it.avatar })
    }
}

class GameTest {
    @Test
    fun `everyone guesses once each round, in order`() {
        val game = Game(players(3), rounds = 2)
        game.startRound(LISBON)

        assertEquals(0, game.guesser)
        game.guess(PORTO)
        assertEquals(1, game.guesser)
        game.guess(LONDON)
        assertEquals(2, game.guesser)
        game.guess(NEW_YORK)
        assertNull(game.guesser)
    }

    @Test
    fun `a different player goes first each round`() {
        val game = Game(players(3), rounds = 4)
        val firstGuessers = mutableListOf<Int?>()
        for (round in 1..4) {
            game.startRound(LISBON)
            firstGuessers += game.guesser
            repeat(3) { game.guess(PORTO) }
            if (!game.isLastRound) game.nextRound()
        }
        assertEquals(listOf(0, 1, 2, 0), firstGuessers)
    }

    @Test
    fun `a single player always guesses`() {
        val game = Game(players(1), rounds = 2)
        game.startRound(LISBON)
        assertEquals(0, game.guesser)
        game.guess(PORTO)
        assertNull(game.guesser)
        game.nextRound()
        game.startRound(PORTO)
        assertEquals(0, game.guesser)
    }

    @Test
    fun `the round scoreboard puts the closest guess first`() {
        val game = Game(players(2), rounds = 1)
        game.startRound(LISBON)
        game.guess(NEW_YORK)
        game.guess(PORTO)

        val scores = game.roundScores
        assertEquals(listOf(1, 0), scores.map { it.player })
        assertEquals(distanceKm(PORTO, LISBON), scores[0].roundKm, 0.001)
        assertEquals(PORTO, scores[0].guess)
    }

    @Test
    fun `kilometres add up and the lowest total leads`() {
        val game = Game(players(2), rounds = 2)
        game.startRound(LISBON)
        game.guess(LISBON) // Player 1 is exact,
        game.guess(PORTO) // player 2 is not.
        game.nextRound()
        game.startRound(LISBON)
        game.guess(PORTO) // Player 2 goes first this time, and is off again.
        game.guess(NEW_YORK) // Player 1 is way off, and loses the lead.

        val standings = game.standings
        assertEquals(listOf(1, 0), standings.map { it.player })
        assertEquals(2 * distanceKm(PORTO, LISBON), standings[0].totalKm, 0.001)
        assertEquals(distanceKm(NEW_YORK, LISBON), standings[1].totalKm, 0.001)
    }

    @Test
    fun `the game ends on the chosen number of rounds`() {
        val game = Game(players(1), rounds = 2)
        assertFalse(game.isLastRound)
        game.nextRound()
        assertTrue(game.isLastRound)
    }

    @Test(expected = IllegalStateException::class)
    fun `there is no round after the last`() {
        Game(players(1), rounds = 1).nextRound()
    }
}

class OkButtonTest {
    private val button = OkButton(holdMillis = 1000)

    @Test
    fun `a quick press is a short press`() {
        button.press(0)
        assertTrue(button.release(200))
    }

    @Test
    fun `a release that wasn't pressed here does nothing`() {
        assertFalse(button.release(500))
    }

    @Test
    fun `a held press only completes once, and its release does nothing`() {
        button.press(0)
        assertEquals(0.5f, button.progress(500), 0.001f)
        assertEquals(1f, button.progress(1000), 0.001f)
        assertEquals(0f, button.progress(1050), 0.001f)
        assertFalse(button.release(1100))
    }

    @Test
    fun `cancelling forgets the press`() {
        button.press(0)
        button.cancel()
        assertEquals(0f, button.progress(500), 0.001f)
        assertFalse(button.release(600))
    }
}

class PlacePickerTest {
    private val seeds = listOf(
        Seed("PT", LISBON),
        Seed("PT", PORTO),
        Seed("GB", LONDON),
        Seed("FJ", Place(-17.8, 179.99)),
    )

    @Test
    fun `parses the place list`() {
        val parsed = parseSeeds(sequenceOf("PT,38.722,-9.139", "", "GB,51.507,-0.128"))
        assertEquals(listOf("PT", "GB"), parsed.map { it.country })
        assertEquals(Place(38.722, -9.139), parsed[0].place)
    }

    @Test
    fun `stays near a town and on the map`() {
        val picker = PlacePicker(seeds, Random(1))
        repeat(1000) {
            val place = picker.next()
            assertTrue(place.lng in -180.0..180.0)
            assertTrue(seeds.any { distanceKm(it.place, place) < 30 })
        }
    }

    @Test
    fun `every country comes up`() {
        val picker = PlacePicker(seeds, Random(2))
        val picks = List(300) { picker.next() }
        seeds.forEach { seed -> assertTrue(picks.any { distanceKm(it, seed.place) < 30 }) }
    }
}
