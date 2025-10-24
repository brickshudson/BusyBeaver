
public class State
{
    public static final int HALTED = -1;

    public static int charToState(char charIn)
    {
        if(charIn == '-' || charIn == 'Z')
            return State.HALTED;
        else if(charIn >= 'A' && charIn < 'Z')
            return charIn - 'A';
        else
            throw new IllegalArgumentException("Invalid state character " + charIn + " recieved when parsing beaver. Valid states are 'A'-'Y' and '-' or 'Z' for halted.");
    }

    public static char stateToChar(int stateIn)
    {
        if(stateIn == State.HALTED)
            return '-';
        else
            return (char)(stateIn + 'A');
    }

    public int state;
    public byte[] symbol;
    public boolean[] dir;
    public int[] targetState;

    State(int s, String stateChanges, int symbolCount) // Gets state info (eg. "1RB1LA")
    {
        symbol = new byte[symbolCount];
        dir = new boolean[symbolCount];
        targetState = new int[symbolCount];
        
        state = s;

        int strIndex = 0;
        for(int i = 0; i < symbolCount; ++i)
        {
            // Split and parse state changes for encountering each symbol (eg. "1RB")
            symbol[i] = Symbol.charToSymbol(stateChanges.charAt(strIndex++));
            dir[i] = Tape.charToDirection(stateChanges.charAt(strIndex++));
            targetState[i] = charToState(stateChanges.charAt(strIndex++));
        }
    }
}
