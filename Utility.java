import java.math.BigInteger;

public class Utility
{
                  //              0                5                  10                  15                  20                   25                       30                       35                       40                       45
    public static int[] primes = {2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97, 101, 103, 107, 109, 113, 127, 131, 137, 139, 149, 151, 157, 163, 167, 173, 179, 181, 191, 193, 197, 199};

    public static boolean isInt(String s)
    {
        try
        {
            Integer.parseInt(s);
            return true;
        }
        catch(NumberFormatException e)
        {
            return false;
        }
    }

    public static BigInteger TheoreticalMaxSteps(int states, int symbols, int tapeLength)
    {
        BigInteger tapePermutations = BigInteger.valueOf(symbols).pow(tapeLength);
        BigInteger statesPerLocation = BigInteger.valueOf(states);
        BigInteger headLocations = BigInteger.valueOf(tapeLength);

        // Max steps for BB(T, S) on tape length n is `n * T * S^n`
        // See https://discord.com/channels/960643023006490684/1362008236118511758/1424389840111407176
        return tapePermutations.multiply(statesPerLocation).multiply(headLocations);
    }
}
