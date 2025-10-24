import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;

public class EnumerateSubtapes
{
    public record SubtapeTransition(Subtape source, Subtape result, int state, int nextState) { }
    public static int loggingLevel = 2;

    public Beaver beaver;
    public Map<Boolean, Set<Integer>> statesFromDirection;
    public BigInteger maxPossibleSteps;
    public int subtapeLength;
    public int haltCount = 0;
    public int loopCount = 0;
    
    public Set<Subtape> possibleSubtapes = new HashSet<Subtape>();
    public ArrayList<SubtapeTransition> transitionList = new ArrayList<>();
    private ArrayDeque<Subtape> unexploredSubtapes = new ArrayDeque<>();

    public class Subtape
    {
        public int tapeLength;
        public byte[] tape;
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
            tape = new byte[tapeLength];
            headDirection = head;
        }

        Subtape(boolean head, int length)
        {
            // Create a custom length tape for special conditions
            tapeLength = length;
            tape = new byte[tapeLength];
            headDirection = head;
        }

        Subtape(Subtape s)
        {
            tapeLength = s.tapeLength;
            headDirection = s.headDirection;
            halted = s.halted;
            looping = s.looping;

            tape = new byte[tapeLength];
            for(int i = 0; i < tapeLength; ++i)
                tape[i] = s.tape[i];

            bigHash = s.bigHash;
            cachedHashcode = s.cachedHashcode;

            // Steps are ignored, as they were from the previous subtape transition
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
                    // If the max steps is exceeded, the TM is looping on the subtape
                    looping = true;
                    return state;
                }
                
                // Read the tape
                byte currSymbol = tape[headIndex];
                steps = steps.add(BigInteger.ONE);

                // Execute the transition
                tape[headIndex] = beaver.states[state].symbol[currSymbol];

                if(beaver.states[state].dir[currSymbol] == Tape.LEFT)
                    --headIndex;
                else
                    ++headIndex;

                state = beaver.states[state].targetState[currSymbol];
            }

            halted = true;
            return state;
        }

        @Override
        public String toString()
        {
            StringBuilder s = new StringBuilder();

            for(int i = 0; i < tape.length; ++i)
                s.append(tape[i]);

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
            int bitsPerSymbol = Integer.highestOneBit(beaver.symbolCount - 1);
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

    public EnumerateSubtapes(Beaver b, int length)
    {
        beaver = b;
        subtapeLength = length;

        maxPossibleSteps = Utility.TheoreticalMaxSteps(b.states.length, b.symbolCount, subtapeLength);

        // Make a set of all states that can access a subtape from the each side
        statesFromDirection = new HashMap<>();
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

        generateSubtapes();
    }

    private void generateSubtapes()
    {
        // Add the starting two empty tape possibilities
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
            for(int state : statesFromDirection.get(source.headDirection))
            {
                // Generate new subtape from the previous one
                Subtape newTape = new Subtape(source);
                int nextState = newTape.run(state);

                // If the new subtape is not in the set, add it
                addSubtape(newTape, state, source, nextState);
            }
        }
    }

    private void addSubtape(Subtape newTape, int state, Subtape sourceTape, int nextState)
    {
        // Do not add looping tapes to preserve memory
        if(newTape.looping)
        {
            ++loopCount;
            return;
        }

        // Document the state and original tape used to reach the new subtape
        transitionList.add(new SubtapeTransition(sourceTape, newTape, state, nextState));

        if(newTape.halted)
        {
            ++haltCount;
            return;
        }

        // Add the subtape, if it doesn't already exist, add it to the unexplored list
        if(possibleSubtapes.add(newTape))
            unexploredSubtapes.add(newTape);
    }

    private void evaluateTransitionList()
    {
        record SubtapePath(Subtape source, Subtape result) { }

        int[] transitionsPerState = new int[beaver.states.length];
        Set<SubtapePath> pathSet = new HashSet<SubtapePath>();
        int duplicateCount = 0;
        int leftHead = 0;
        int rightHead = 0;

        // Create a set of all unique source -> result subtape paths
        for(int i = 0; i < transitionList.size(); ++i)
        {
            SubtapeTransition transition = transitionList.get(i);

            ++transitionsPerState[transition.state];
            if(!pathSet.add(new SubtapePath(transition.source, transition.result)))
                ++duplicateCount;
        }

        // Record the direction of the head relative to each subtape found
        for(Subtape tape : possibleSubtapes)
        {
            if(tape.headDirection == Tape.LEFT)
                ++leftHead;
            else
                ++rightHead;
        }

        // Find unusual ratios of head direction relative to the subtape
        if(loggingLevel > 2 && Math.max(leftHead, rightHead) / Math.min(leftHead, rightHead) > 2)
            System.out.println("There are " + leftHead + " S>T tapes and " + rightHead + " T<S tapes. Check " + beaver.beaverString);

        if(loggingLevel > 1)
        {
            // Generic stats about the TM with this subtape size
            System.out.print("Total transitions: " + transitionList.size() + " Duplicate transitions: " + duplicateCount + " Transitions per state: " + Arrays.toString(transitionsPerState));
            System.out.println(" Halted transitions: " + haltCount + " Looped transitions: " + loopCount + " (not counted in other stats)");
        }
    }


    public static void main(String[] args) throws FileNotFoundException, IOException
    {
        String beaverListFile = "BBList.txt";
        String outputFileName = null;
        boolean saveTransitionListToFile = false;
        loggingLevel = 2;

        int maxSubtapesPerLength = 0;
        int[] lengthArray = Utility.primes;
        int defaultMinLengthIndex = 7;
        int defaultLengthCount = 1;

        // Get command line arguments if applicable
        // This is bad code, please ignore it
        try
        {
            int argsIndex = 1;

            // Get logging level
            if(args.length > argsIndex && Utility.isInt(args[argsIndex]))
                loggingLevel = Integer.parseInt(args[argsIndex++]);

            // Get beaver list
            beaverListFile = args.length > argsIndex ? args[argsIndex++] : beaverListFile;

            // Get length array variables
            int[] lengthsArgs = {0, 0, 0};
            int i = 0;
            while(i < 3)
            {
                if(args.length <= argsIndex || !Utility.isInt(args[argsIndex]))
                    break;

                lengthsArgs[i++] = Integer.parseInt(args[argsIndex++]);
            }

            // Generate or trim the length array
            switch(i)
            {
                case 0:
                    lengthArray = Arrays.copyOfRange(lengthArray, Math.min(defaultMinLengthIndex, lengthArray.length - 1), Math.min(defaultMinLengthIndex + defaultLengthCount, lengthArray.length));
                  break;
                case 1:
                    lengthArray = new int[]{lengthsArgs[0]};
                  break;
                case 2:
                    lengthArray = Arrays.copyOfRange(lengthArray, Math.min(lengthsArgs[0], lengthArray.length - 1), Math.min(lengthsArgs[0] + lengthsArgs[1], lengthArray.length));
                  break;
                case 3:
                    lengthArray = IntStream.iterate(lengthsArgs[0], length-> length <= lengthsArgs[1], length-> length + lengthsArgs[2]).toArray();
                  break;
            }

            // Get the filename for the CSV output
            if(args.length > argsIndex && !Utility.isInt(args[argsIndex]))
                outputFileName = args[argsIndex++];

            // Get the cap on how many subtapes per length before the rest of the lengths are skipped
            if(args.length > argsIndex && Utility.isInt(args[argsIndex]))
                maxSubtapesPerLength = Integer.parseInt(args[argsIndex++]);
        }
        catch(Exception e)
        {
            System.out.println("Invalid arguments. The following are proper command line prompts:");
            System.out.println("To use default values: java EnumerateSubtapes [<logging level, 0-2>] <TM List File> [<Output CSV Filename> [<Max Subtapes To Enumerate>]]");
            System.out.println("To run with a single length: java EnumerateSubtapes [<logging level, 0-2>] <TM List File> <Subtape Lengths> [<Output CSV Filename> [<Max Subtapes To Enumerate>]]");
            System.out.println("To run with a range of prime lengths (up to prime #45): java EnumerateSubtapes [<logging level, 0-2>] <TM List File> <Index of First Prime> <Index of Last Prime> [<Output CSV Filename> [<Max Subtapes To Enumerate>]]");
            System.out.println("To run a range of every nth length: java EnumerateSubtapes [<logging level, 0-2>] <TM List File> <Smallest Length> <Largest Length> <n> [<Output CSV Filename> [<Max Subtapes To Enumerate>]]");
            System.out.println();
            System.out.println("All filenames will be automatically be prepended with the data folder, currently \"" + IO.dataPath + "\" so only use the name of the file in the command (eg. BBList.txt)");
            return;
        }

        if(loggingLevel < 2)
            Beaver.logging = false;

        // Set up to output for usage in spreadsheets
        boolean outputForSpreadsheet = false;
        if(outputFileName != null)
        {
            outputForSpreadsheet = true;
            Beaver.logging = false;
            IO.systemOutToFile(outputFileName);
            saveTransitionListToFile = false;
        }

        List<String> beavers = IO.GetBeaverList(beaverListFile);
        for(String beaver : beavers)
        {
            Beaver b = new Beaver(beaver);
            
            if(outputForSpreadsheet)
                System.out.print(beaver);

            // Enumerate all subtapes generated by beaver b for each specified tape length
            for(int tapeLength : lengthArray)
            {
                EnumerateSubtapes es = new EnumerateSubtapes(b, tapeLength);
                int subtapesFound = es.possibleSubtapes.size();

                if(saveTransitionListToFile)
                    IO.SaveTransitionList(es);

                // Output results for this tape length
                if(outputForSpreadsheet)
                    System.out.print(", " + subtapesFound);
                else
                {
                    // Check for odd transition ratios
                    es.evaluateTransitionList();

                    if(loggingLevel > 1)
                    {
                        // The maximum number of BB(x, S) tape permutations on a tape of length n is `2 * S^n` (the 2 accounts for where the TM head is relative to the subtape) 
                        BigInteger tapePermuations = BigInteger.valueOf(b.symbolCount).pow(tapeLength).multiply(BigInteger.TWO);

                        System.out.print("" + subtapesFound + " total subtapes were found. Tapes halted " + es.haltCount + " times, and " + es.loopCount + " tapes looped. ");
                        System.out.println("" + ((subtapesFound) * 100.0 / (tapePermuations.longValue())) + "% of the possible " + tapePermuations + " size " + tapeLength + " tapes.");
                    }
                }

                // If too many subtapes are being produced at this size, don't check longer subtapes
                if(maxSubtapesPerLength != 0 && subtapesFound > maxSubtapesPerLength)
                    break;
            }
            
            if(outputForSpreadsheet)
                System.out.println();
        }
    }
}
