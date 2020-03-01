package name.nepavel.pickapic

import kotlin.math.pow

object GFG {
    // Function to calculate the Probability
    private fun probability(
        rating1: Float,
        rating2: Float
    ): Float {
        return 1.0f * 1.0f / (1 + 1.0f *
                10.0.pow(
                    1.0f *
                            (rating1 - rating2) / 400.toDouble()
                ).toFloat())
    }

    fun eloRating(
        winner: Float,
        loser: Float,
        K: Int
    ): Pair<Double, Double> {
        // To calculate the Winning
// Probability of Player B
        val pb = probability(winner, loser)
        // To calculate the Winning
// Probability of Player A
        val pa = probability(loser, winner)
        val ra = winner + K * (1 - pa)
        val rb = loser + K * (0 - pb)
        return Pair(ra.toDouble(), rb.toDouble())
    }
}