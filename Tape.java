
public class Tape
{
    public static final boolean LEFT = true;
    public static final boolean RIGHT = !LEFT;

    public static boolean charToDirection(char charIn)
    {
        if(charIn == 'L')
            return LEFT;
        else if(charIn == 'R' || charIn == '-')
            return RIGHT;
        else
            throw new IllegalArgumentException("Tape recieved an invalid direction: " + charIn);
    }

    public static char directionToChar(boolean direction)
    {
        if(direction == LEFT)
            return 'L';
        else
            return 'R';
    }
    
    protected class Node
    {
        Node right;
        Node left;
        byte item = defaultSymbol;

        Node()
        {
            this.right = null;
            this.left = null;

            if(defaultSymbol != Symbol.UNDEFINED)
                ++definedCount;
            if(defaultSymbol == Symbol.ZERO)
                ++symbolZeroCount;
        }

        Node(Tape.Node l, Tape.Node r)
        {
            this.right = r;
            this.left = l;

            if(defaultSymbol != Symbol.UNDEFINED)
                ++definedCount;
            if(defaultSymbol == Symbol.ZERO)
                ++symbolZeroCount;
        }
    }

    protected Node head;
    protected Node leftFrontier;
    protected Node rightFrontier;

    // Statistics for tape length and sigma
    public int symbolZeroCount = 0;
    public int definedCount = 0;

    // Default symbol allows for alternative tapes, including reverse tapes
    protected byte defaultSymbol = Symbol.ZERO;

    Tape()
    {
        leftFrontier = rightFrontier = head = new Node();
    }

    public boolean isCleanTape()
    {
        // A tape is considered clean when sigma == 0 (there are no defined non-zero symbols on the tape)
        return symbolZeroCount == definedCount;
    }

    public int sigma()
    {
        // Sigma is the number of defined nonzero symbols on the tape
        return definedCount - symbolZeroCount;
    }

    public byte writeHead(byte val)
    {
        // Calculate stats changes
        if(val == Symbol.ZERO)
            ++symbolZeroCount;
        else if(val == Symbol.UNDEFINED)
            --definedCount;

        if(head.item == Symbol.ZERO)
            --symbolZeroCount;
        else if(head.item == Symbol.UNDEFINED)
            ++definedCount;

        // Write to the head
        head.item = val;

        return head.item;
    }

    public byte readHead()
    {
        return head.item;
    }

    public byte move(boolean direction)
    {
        if(direction == LEFT)
            return moveLeft();
        else
            return moveRight();
    }

    public byte undoMove(boolean direction)
    {
        return move(!direction);
    }

    public byte peek(boolean direction)
    {
        if(direction == LEFT)
            return peekLeft();
        else
            return peekRight();
    }

    public byte moveLeft()
    {
        // If the node hasn't been created, create it
        if(head.left == null)
            leftFrontier = head.left = new Node(null, head);
        
        head = head.left;

        return head.item;
    }

    public byte moveRight()
    {
        // If the node hasn't been created, create it
        if(head.right == null)
            rightFrontier = head.right = new Node(head, null);
        
        head = head.right;

        return head.item;
    }

    public byte peekLeft()
    {
        if(head.left == null)
            return defaultSymbol;

        return head.left.item;
    }

    public byte peekRight()
    {
        if(head.right == null)
            return defaultSymbol;

        return head.right.item;
    }

    // Does not currently handle patterns of symbols
    public static String compressString(String s)
    {
        StringBuilder outString = new StringBuilder();

        // Initialize the group to match the first cell
        char currentChar = s.charAt(0);
        int charCount = 0;

        for(char c : s.toCharArray())
        {
            // The end of a group is found
            if(c != currentChar)
            {
                // Output the group
                if(charCount < 4)
                    outString.append(("" + currentChar).repeat(charCount) + (charCount > 1 ? " " : ""));
                else
                {
                    outString.append(("" + currentChar));
                    outString.append("^" + charCount + " ");
                }
                
                // Start the next group
                currentChar = c;
                charCount = 1;
            }
            else // Combine current symbol into the group
                ++charCount;
        }

        // Output the final group
        outString.append((char)currentChar);
        if(charCount > 1)
            outString.append("^" + charCount);

        return outString.toString();
    }

    public String toString()
    {
        StringBuilder outString = new StringBuilder();
        Node temp = leftFrontier;
        
        // Append each symbol
        while(temp != null)
        {
            if(temp == head)
                outString.append('[');

            outString.append(Symbol.symbolToChar(temp.item));

            if(temp == head)
                outString.append(']');
            
            temp = temp.right;
        }

        // Trim residual undefined cells from result
        //   Only works if undefined states are whitespace
        return outString.toString().trim();
    }
}
