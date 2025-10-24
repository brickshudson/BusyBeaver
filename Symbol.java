
public class Symbol
{
    public static final byte ZERO = 0;
    public static final byte UNDEFINED = -1;

    public static byte charToSymbol(char charIn)
    {
        if(charIn == '-')
            return charToSymbol('1');
        else if(charIn >= '0' && charIn <= '9')
            return (byte)(charIn - '0');
        else
            throw new IllegalArgumentException("Invalid symbol " + charIn + " recieved when parsing beaver. Valid symbols are the digits 1-9.");
    }

    public static char symbolToChar(byte symbolIn)
    {
        if(symbolIn == UNDEFINED)
            return '-';
        else
            return (char)(symbolIn + '0');
    }
}
