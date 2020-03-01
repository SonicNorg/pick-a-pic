package name.nepavel.pickapic;

import kotlin.Pair;

public class GFG {

    // Function to calculate the Probability
    static float Probability(float rating1,
                             float rating2)
    {
        return 1.0f * 1.0f / (1 + 1.0f *
                (float)(Math.pow(10, 1.0f *
                        (rating1 - rating2) / 400)));
    }

    // Function to calculate Elo rating
    // K is a constant.
    // d determines whether Player A wins
    // or Player B.
    public static Pair<Double, Double> EloRating(float Ra, float Rb,
                                 int K, boolean aWins)
    {

        // To calculate the Winning
        // Probability of Player B
        float Pb = Probability(Ra, Rb);

        // To calculate the Winning
        // Probability of Player A
        float Pa = Probability(Rb, Ra);

        // Case -1 When Player A wins
        // Updating the Elo Ratings
        if (aWins) {
            Ra = Ra + K * (1 - Pa);
            Rb = Rb + K * (0 - Pb);
        }

        // Case -2 When Player B wins
        // Updating the Elo Ratings
        else {
            Ra = Ra + K * (0 - Pa);
            Rb = Rb + K * (1 - Pb);
        }

         return new Pair<>(Math.round(
                Ra * 1000000.0) / 1000000.0,
                Math.round(Rb * 1000000.0) / 1000000.0);
    }

    //driver code
    public static void main (String[] args)
    {

        // Ra and Rb are current ELO ratings
        float Ra = 1200, Rb = 1000;

        int K = 30;
        boolean d = true;

        EloRating(Ra, Rb, K, d);
    }
}
