import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.HashMap;
import java.util.Map;
import java.io.FileNotFoundException;
import java.math.BigInteger;

public class ESRecursive
{
    public record TapeState(int tapeIndex, boolean moveDirection, int nextState, BigInteger steps) { }
    public record TapeTransitions(Map<Integer, TapeState> headLeft, Map<Integer, TapeState> headRight) { }
    public record SubtapeTransition(Subtape source, Subtape result, int state, int nextState) { }
    public static int loggingLevel = 0;

    private Map<Boolean, Set<Integer>> dirToStates;
    public BigInteger maxPossibleSteps;
    public int subtapeLength;
    public int symbolCount;
    public int haltCount = 0;
    public int loopCount = 0;
    
    public ArrayList<TapeTransitions> transitionLookup = new ArrayList<>();
    public Set<Subtape> possibleSubtapes = new HashSet<Subtape>();
    public ArrayList<SubtapeTransition> transitionList = new ArrayList<>();
    private ArrayDeque<Subtape> unexploredSubtapes = new ArrayDeque<>();

    public class Subtape
    {
        public int tapeLength;
        public int[] tape;
        public boolean headDirection;
        public boolean halted = false;
        public boolean looping = false;
        public BigInteger steps = BigInteger.ZERO;

        // BigHash and cached hash code for performance of the equals() function 
        BigInteger bigHash = BigInteger.ZERO;
        int cachedHashcode = 0;

        Subtape(boolean head)
        {
            tapeLength = subtapeLength;
            tape = new int[tapeLength];
            headDirection = head;
        }

        Subtape(int length, boolean head)
        {
            // Create a custom length tape for special conditions
            tapeLength = length;
            tape = new int[tapeLength];
            headDirection = head;
        }

        Subtape(Subtape s)
        {
            tapeLength = s.tapeLength;
            headDirection = s.headDirection;
            halted = s.halted;
            looping = s.looping;

            tape = new int[tapeLength];
            for(int i = 0; i < tapeLength; ++i)
                tape[i] = s.tape[i];

            bigHash = s.bigHash;
            cachedHashcode = s.cachedHashcode;
        }

        public int run(int state)
        {
            if(headDirection == Tape.LEFT)
                return runInternal(0, state);
            else
                return runInternal(tapeLength - 1, state);
        }

        private int runInternal(int headIndex, int state)
        {
            // Reset hash
            bigHash = BigInteger.ZERO;
            cachedHashcode = 0;

            while(state != State.HALTED)
            {
                // If the head leaves the subtape, the run is complete
                if(headIndex < 0)
                {
                    headDirection = Tape.LEFT;
                    return state;
                }
                else if(headIndex >= tapeLength)
                {
                    headDirection = Tape.RIGHT;
                    return state;
                }
                else if(steps.compareTo(maxPossibleSteps) >= 0)
                {
                    looping = true;
                    return state;
                }
                
                // Read and update the tape
                TapeState nextTape = null;
                if(headDirection == Tape.LEFT)
                    nextTape = transitionLookup.get(tape[headIndex]).headLeft.get(state);
                else
                    nextTape = transitionLookup.get(tape[headIndex]).headRight.get(state);

                if(nextTape == null) // TODO: check if this means looping or halted
                    break;

                tape[headIndex] = nextTape.tapeIndex;

                // Move to the next state
                steps = steps.add(nextTape.steps);
                if(nextTape.moveDirection == Tape.LEFT)
                    --headIndex;
                else
                    ++headIndex;
                
                // Inverted, because moving right means entering the next tape from the left and vice versa
                headDirection = !nextTape.moveDirection;
                state = nextTape.nextState;
            }

            halted = true;
            return state;
        }

        @Override
        public String toString()
        {
            StringBuilder s = new StringBuilder();

            for(int i = 0; i < tape.length; ++i)
                s.append("" + tape[i] + " ");

            return s.toString();
        }

        private BigInteger generateBigHash()
        {
            // Record constant-sized variables
            // Steps are not considered
            long info = (17 + tapeLength) << 3;

            if(looping)
                info += 4;

            if(halted)
                info += 2;

            if(headDirection)
                info += 1;

            BigInteger data = BigInteger.valueOf(info);

            // Calculate how many cells can fit in a long
            int bitsPerSymbol = (Integer.BYTES * 8) - Integer.numberOfLeadingZeros(symbolCount - 1);
            int symbolsPerLong = (Long.BYTES * 8) / bitsPerSymbol;

            info = 0;
            // Add the tape to the data
            for(int i = 0; i < tapeLength; ++i)
            {
                // When the long is full, append it to data
                if(i != 0 && i % symbolsPerLong == 0)
                {
                    data = data.shiftLeft((Long.BYTES * 8));
                    data = data.add(BigInteger.valueOf(info));
                    info = 0;
                }

                info = (info << bitsPerSymbol) + tape[i];
            }

            // Add the last segment of the tape to data
            data = data.shiftLeft(64);
            data = data.add(BigInteger.valueOf(info));

            bigHash = data;
            return bigHash;
        }

        @Override
        public boolean equals(Object obj)
        {
            if(this == obj)
                return true;

            if(!(obj instanceof Subtape))
                return false;
            Subtape other = (Subtape)obj;
            
            if((halted && other.halted) || (looping && other.looping))
                return true;

            // Make sure both tapes already have a bigHash
            if(bigHash == BigInteger.ZERO)
                generateBigHash();
            if(other.bigHash == BigInteger.ZERO)
                other.generateBigHash();
            
            // BigInteger handles equals for us! How kind :)
            return bigHash.equals(other.bigHash);
        }

        @Override
        public int hashCode()
        {
            if(halted)
                return 1;
            if(looping)
                return 2;
            
            if(bigHash == BigInteger.ZERO)
                cachedHashcode = generateBigHash().hashCode();

            return cachedHashcode;
        }
    }

    public ESRecursive(Map<Boolean, Set<Integer>> stateDirs, Set<Subtape> subtapes, ArrayList<SubtapeTransition> transitions, int states, int length)
    {
        dirToStates = stateDirs;
        subtapeLength = length;

        // Make a list of all possible tapes (ignoring head direction)
        Set<int[]> tempSubtapeList = new TreeSet<>((a,b) -> Arrays.compare(a,b));
        for(Subtape s : subtapes)
            tempSubtapeList.add(s.tape);
        
        ArrayList<int[]> subtapeList = new ArrayList<>();
        subtapeList.addAll(tempSubtapeList);

        symbolCount = subtapeList.size();
        maxPossibleSteps = Utility.TheoreticalMaxSteps(states, symbolCount, length);

        // Initialize the lookup for transitions
        for(int i = 0; i < subtapeList.size(); ++i)
        {
            Map<Integer, TapeState> left = new HashMap<>();
            Map<Integer, TapeState> right = new HashMap<>();
            transitionLookup.add(new TapeTransitions(left, right));
        }

        // Add every transition to the lookup
        for(SubtapeTransition transition : transitions)
        {
            // Get the index of each tape in the tape list
            int sourceIndex = Collections.binarySearch(subtapeList, transition.source.tape, (a,b) -> Arrays.compare(a,b));
            int resultIndex = Collections.binarySearch(subtapeList, transition.result.tape, (a,b) -> Arrays.compare(a,b));

            // Generate a new transition with the indexes instead of the previous tape values
            TapeState ts = new TapeState(resultIndex, transition.result().headDirection, transition.nextState(), transition.result().steps);
            if(transition.source().headDirection == Tape.LEFT)
                transitionLookup.get(sourceIndex).headLeft.put(transition.state(), ts);
            else
                transitionLookup.get(sourceIndex).headRight.put(transition.state(), ts);
        }

        generateSubtapes();
    }

    private void generateSubtapes()
    {
        // Add the two empty tape possibilities
        Subtape blankSubtapeLeft = new Subtape(Tape.LEFT); // Empty tape, head to the left
        unexploredSubtapes.add(blankSubtapeLeft); 
        possibleSubtapes.add(blankSubtapeLeft);
        
        Subtape blankSubtapeRight = new Subtape(Tape.RIGHT); // Empty tape, head to the left
        unexploredSubtapes.add(blankSubtapeRight); 
        possibleSubtapes.add(blankSubtapeRight);

        // Pop a subtape to evaluate
        while(!unexploredSubtapes.isEmpty())
        {
            Subtape source = unexploredSubtapes.pop();

            // Check the subtape with each possible state
            for(int state : dirToStates.get(source.headDirection))
            {
                // Generate new subtape from the previous
                Subtape newTape = new Subtape(source);
                int resultState = newTape.run(state);

                // If the new subtape is not in the set, add it
                addSubtape(newTape, state, source, resultState);
            }
        }
    }

    private void addSubtape(Subtape newTape, int state, Subtape sourceTape, int resultState)
    {
        // Do not add looping tapes to preserve memory
        if(newTape.looping)
        {
            ++loopCount;
            return;
        }

        // Document the state and original tape used to reach the new subtape
        transitionList.add(new SubtapeTransition(sourceTape, newTape, state, resultState));
        
        if(newTape.halted)
        {
            ++haltCount;
            return;
        }

        // Add the subtape, if it doesn't already exist, add it to the unexplored list
        if(possibleSubtapes.add(newTape))
            unexploredSubtapes.add(newTape);
    }

    private static Map<Boolean, Set<Integer>> GenDirToStates(Beaver beaver)
    {
        // Make a set of all states that can access a subtape from the each side
        Map<Boolean, Set<Integer>> statesFromDirection = new HashMap<>();
        statesFromDirection.put(Tape.LEFT, new HashSet<>());
        statesFromDirection.put(Tape.RIGHT, new HashSet<>());

        // For each transition, add the target state (removing duplicates with set)
        for(State state : beaver.states)
            for(int i = 0; i < beaver.symbolCount; ++i)
            {
                // To enter the tape from the left, you must move to the right (and vice versa) so the boolean is reversed
                if(state.targetState[i] != State.HALTED)
                    statesFromDirection.get(!state.dir[i]).add(state.targetState[i]);
            }

        return statesFromDirection;
    }

    private ESRecursive() {} // Dummy constructor for initial subtape generation
    private static Set<Subtape> GenPossibleSubtapes(Beaver beaver)
    {
        Set<Subtape> tapeSet = new HashSet<>();

        ESRecursive dummyES = new ESRecursive();
        dummyES.symbolCount = 2;

        // Create a subtape for each symbol
        for(int i = 0; i < beaver.symbolCount; ++i)
        {
            Subtape s = dummyES.new Subtape(1, Tape.LEFT);
            s.tape[0] = i;
            tapeSet.add(s);
        }

        return tapeSet;
    }

    private static ArrayList<SubtapeTransition> GenTransitionList(Beaver beaver)
    {
        ArrayList<SubtapeTransition> stateTransitions = new ArrayList<>();
        Subtape[][] tapes = new Subtape[beaver.symbolCount][2];

        // Create dummy subtapes for each symbol
        ESRecursive dummyES = new ESRecursive();
        for(int j = 0; j < 2; ++j)
            for(int i = 0; i < beaver.symbolCount; ++i)
            {
                Subtape s = dummyES.new Subtape(1, Tape.LEFT);
                s.tape[0] = i;
                s.headDirection = (j == 1);
                tapes[i][j] = s;
            }

        // Generate a SubtapeTransition for every transition entering from each side of the tape
        for(int state = 0; state < beaver.states.length; ++state)
            for(int symbol = 0; symbol < beaver.symbolCount; ++symbol)
            {
                stateTransitions.add(new SubtapeTransition(tapes[symbol][0], tapes[beaver.states[state].symbol[symbol]][beaver.states[state].dir[symbol] ? 1 : 0], state, beaver.states[state].targetState[symbol]));
                stateTransitions.add(new SubtapeTransition(tapes[symbol][1], tapes[beaver.states[state].symbol[symbol]][beaver.states[state].dir[symbol] ? 1 : 0], state, beaver.states[state].targetState[symbol]));
            }

        return stateTransitions;
    }

    public static ESRecursive Enumerate(String beaver, int initialSubtape, int subtapeMultiplier, int recursionCount)
    {
        Beaver b = new Beaver(beaver);
        ESRecursive recES = new ESRecursive(GenDirToStates(b), GenPossibleSubtapes(b), GenTransitionList(b), b.states.length, initialSubtape);

        for(int i = 0; i < recursionCount; ++i)
            recES = new ESRecursive(recES.dirToStates, recES.possibleSubtapes, recES.transitionList, b.states.length, subtapeMultiplier);

        return recES;
    }

    public static void main(String[] args) throws FileNotFoundException
    {
        String beaver = "1RB1RF_0LC1RC_1RD1LC_---0RE_1RA1LF_1RA0LE";
        int lengthOfSubtape = 10;
        int subtapeMult = 2;
        int recursiveLayers = 2;
        loggingLevel = 2;

        // Get command line arguments if applicable
        try
        {
            beaver = args.length > 0 ? args[0] : beaver;

            // Get the size of the initial subtape
            if(args.length > 1)
            {
                lengthOfSubtape = Integer.parseInt(args[1]);

                // Get the size of each recursive layer's subtape
                subtapeMult = Integer.parseInt(args[2]);
                
                // Get the number of recursive layers to compute
                recursiveLayers = Integer.parseInt(args[3]);
            }

            // Get logging level (optional)
            if(args.length > 4 && Utility.isInt(args[4]))
                loggingLevel = Integer.parseInt(args[4]);
        }
        catch(Exception e)
        {
            System.out.println("Invalid arguments. Use the following command:");
            System.out.println("java ESRecursive <TM> <Length of initial subtape> <Length of recursive subtapes> <Number of times to recurse> [<logging level, 0-2>]");
            System.out.println("Example: java ESRecursive 1RB1LB_1LA1RZ 10 2 2");
            return;
        }

        if(loggingLevel < 2)
            Beaver.logging = false;

        // Generate subtapes recursively
        ESRecursive recES = Enumerate(beaver, lengthOfSubtape, subtapeMult, recursiveLayers);
        int subtapesFound = recES.possibleSubtapes.size();

        if(loggingLevel > 1)
        {
            // The maximum number of BB(x, S) tape permutations on a tape of length n is `2 * S^n` (the 2 accounts for where the TM head is relative to the subtape) 
            int finalSubtapeLength = lengthOfSubtape * (int)Math.pow(subtapeMult, recursiveLayers);
            BigInteger tapePermuations = BigInteger.valueOf(beaver.split("_")[0].length() / 3).pow(finalSubtapeLength).multiply(BigInteger.TWO);

            System.out.print("" + subtapesFound + " total subtapes were found. Tapes halted " + recES.haltCount + " times, and " + recES.loopCount + " tapes looped. ");
            System.out.println("" + ((subtapesFound) * 100.0 / (tapePermuations.longValue())) + "% of the possible " + tapePermuations + " size " + finalSubtapeLength + " tapes.");
        }
    }
}
