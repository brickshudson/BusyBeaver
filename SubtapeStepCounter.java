import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.util.Scanner;
import java.io.File;
import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;

public class SubtapeStepCounter
{
    public record TapeState(Subtape tape, int head, int state, boolean isPartial) { }

    private Beaver beaver;
    public int subtapeLength;
    public int haltCount = 0;
    public BigInteger loopSteps = BigInteger.ZERO;
    public int finishCount = 0;
    public BigInteger maxPossibleSteps;
    public BigInteger totalSteps = BigInteger.ZERO;
    Stack<TapeState> tapeStack = new Stack<>();

    public BigInteger maxSteps = BigInteger.ZERO;

    public class Subtape
    {
        byte[] tape;
        BigInteger steps = BigInteger.valueOf(0);
        boolean halted = false;
        boolean looping = false;

        Subtape(byte symbol)
        {
            tape = new byte[subtapeLength];
            for(int i = 0; i < subtapeLength; ++i)
                tape[i] = symbol;
        }

        Subtape(Subtape s)
        {
            steps = s.steps;
            tape = new byte[subtapeLength];
            for(int i = 0; i < subtapeLength; ++i)
                tape[i] = s.tape[i];
        }

        TapeState runPartial(int headIndex, int state)
        {
            while(headIndex >= 0 && headIndex < subtapeLength)
            {
                if(state == State.HALTED)
                {
                    halted = true;
                    break;
                }
                else if(steps.compareTo(maxPossibleSteps) >= 0)
                {
                    looping = true;
                    break;
                }
                
                // Read and update the tape
                byte currSymbol = tape[headIndex];
                if(currSymbol == Symbol.UNDEFINED)
                    return new TapeState(this, headIndex, state, true);

                tape[headIndex] = beaver.states[state].symbol[currSymbol];

                // Move to the next state
                steps = steps.add(BigInteger.ONE);
                totalSteps = totalSteps.add(BigInteger.ONE);
                if(beaver.states[state].dir[currSymbol] == Tape.LEFT)
                    --headIndex;
                else
                    ++headIndex;

                state = beaver.states[state].targetState[currSymbol];
            }

            return new TapeState(this, headIndex, state, false);
        }

        boolean sameTapes(byte[] t1, byte[] t2)
        {
            if(t1.length != t2.length)
                return false;

            for(int i = 0; i < t1.length; ++i)
                if(t1[i] != t2[i])
                    return false;

            return true;
        }

        TapeState runPartialWithLoopDetection(int headIndex, int state)
        {
            byte[] detectionTape = tape.clone();
            int detectionState = state;
            int detectionHeadIndex = headIndex;
            int detectionSteps = 0;

            while(headIndex >= 0 && headIndex < subtapeLength)
            {
                if(state == State.HALTED)
                {
                    halted = true;
                    break;
                }
                else if(detectionSteps > 2 && state == detectionState && headIndex == detectionHeadIndex && sameTapes(tape, detectionTape))
                {
                    looping = true;
                    break;
                }
                
                // Read and update the tape
                byte currSymbol = tape[headIndex];
                if(currSymbol == Symbol.UNDEFINED)
                    return new TapeState(this, headIndex, state, true);

                tape[headIndex] = beaver.states[state].symbol[currSymbol];

                // Move to the next state
                ++detectionSteps;
                steps = steps.add(BigInteger.ONE);
                totalSteps = totalSteps.add(BigInteger.ONE);
                if(beaver.states[state].dir[currSymbol] == Tape.LEFT)
                    --headIndex;
                else
                    ++headIndex;

                state = beaver.states[state].targetState[currSymbol];

                // Progress the detection tape every other step
                if(detectionSteps % 2 == 1)
                {
                    // Read and update the tape
                    byte detectionCurrSymbol = detectionTape[detectionHeadIndex];

                    detectionTape[detectionHeadIndex] = beaver.states[detectionState].symbol[detectionCurrSymbol];

                    // Move to the next state
                    if(beaver.states[detectionState].dir[detectionCurrSymbol] == Tape.LEFT)
                        --detectionHeadIndex;
                    else
                        ++detectionHeadIndex;

                    detectionState = beaver.states[detectionState].targetState[detectionCurrSymbol];
                }
            }

            return new TapeState(this, headIndex, state, false);
        }

        @Override
        public String toString()
        {
            StringBuilder s = new StringBuilder();

            for(int i = 0; i < tape.length; ++i)
                s.append(tape[i]);

            return s.toString();
        }
    }

    public SubtapeStepCounter(Beaver b, int length)
    {
        beaver = b;
        subtapeLength = length;

        // Set a maximum to prevent loops
        maxPossibleSteps = Utility.TheoreticalMaxSteps(b.states.length, b.symbolCount, subtapeLength);

        // Create root subtapes
        Subtape leftTape = new Subtape(Symbol.UNDEFINED);
        leftTape.tape[0] = Symbol.ZERO;
        Subtape rightTape = new Subtape(Symbol.UNDEFINED);
        rightTape.tape[subtapeLength - 1] = Symbol.ZERO;

        // Add tapes for special states at start of TM
        Subtape startTape = new Subtape(Symbol.ZERO);
        for(int i = 1; i < subtapeLength - 1; ++i)
            tapeStack.push(new TapeState(new Subtape(startTape), i, beaver.states[0].state, false));

        // Start each root subtape with each possible starting state
        for(int i = 0; i < beaver.states.length; ++i)
        {
            tapeStack.push(new TapeState(new Subtape(leftTape), 0, beaver.states[i].state, true));
            tapeStack.push(new TapeState(new Subtape(rightTape), subtapeLength - 1, beaver.states[i].state, true));
        }
    }

    private void addSubtape(Subtape newTape, BigInteger startSteps)
    {
        // Do not add looping tapes
        if(newTape.looping)
        {
            loopSteps = loopSteps.add(newTape.steps).subtract(startSteps);
            return;
        }

        if(newTape.halted)
            ++haltCount;

        ++finishCount;
        
        // Record the max steps taken within a tape of this size
        if(newTape.steps.compareTo(maxSteps) > 0)
        {
            maxSteps = newTape.steps;
        }
    }

    private void evaluateSubtapes()
    {
        while(!tapeStack.empty())
        {
            // Get the next partial tape
            TapeState ts = tapeStack.peek();
            Subtape s = new Subtape(ts.tape);
            BigInteger currSteps = s.steps;

            // Push the next variant onto the stack (except for special start states)
            if(ts.isPartial && ts.tape.tape[ts.head] < beaver.symbolCount - 1)
                ++ts.tape.tape[ts.head];
            else
                tapeStack.pop();

            // Run the tape until the next unexplored cell
            TapeState newState = s.runPartialWithLoopDetection(ts.head, ts.state);

            // If the tape is incomplete, push the first variant to the stack
            if(newState.isPartial)
            {
                newState.tape.tape[newState.head] = Symbol.ZERO;
                tapeStack.push(newState);
            }
            else // Record the finished tape
                addSubtape(newState.tape, currSteps);

            if(!tapeStack.empty()) tapeStack.peek();
        }
    }

    public record SessionResults(BigInteger maxSteps, String maxBeaver, int ties, BigInteger totalSteps, BigInteger loopSteps) { }
    private static SessionResults calcBeaversMaxSteps(ConcurrentLinkedQueue<String> beavers, int tapeLength)
    {
        int sessionTies = 0;
        BigInteger sessionMaxSteps = BigInteger.ZERO;
        String sessionMaxBeaver = null;
        BigInteger totalSteps = BigInteger.ZERO;
        BigInteger loopSteps = BigInteger.ZERO;
        
        String beaver;
        while((beaver = beavers.poll()) != null)
        {
            Beaver b = new Beaver(beaver);

            // Run the beaver on all possible subtapes
            SubtapeStepCounter es = new SubtapeStepCounter(b, tapeLength);
            es.evaluateSubtapes();

            totalSteps = totalSteps.add(es.totalSteps);
            loopSteps = loopSteps.add(es.loopSteps);

            // Test if the current max is better than the new results
            int comp = es.maxSteps.compareTo(sessionMaxSteps);
            if(comp > 0)
            {
                sessionMaxSteps = es.maxSteps;
                sessionMaxBeaver = beaver;
                sessionTies = 0;
            }
            else if(comp == 0)
                ++sessionTies;
        }

        return new SessionResults(sessionMaxSteps, sessionMaxBeaver, sessionTies, totalSteps, loopSteps);
    }


    private static SessionResults runMultithreaded(ConcurrentLinkedQueue<String> beavers, int tapeLength, int poolSize)
    {
        ExecutorService e = Executors.newFixedThreadPool(poolSize);
        List<Future<SessionResults>> results = new ArrayList<Future<SessionResults>>();

        // Spin up threads
        for(int i = 0; i < poolSize; ++i)
            results.add(e.submit(() -> calcBeaversMaxSteps(beavers, tapeLength)));

        int sessionTies = 0;
        BigInteger sessionMaxSteps = BigInteger.ZERO;
        String sessionMaxBeaver = null;
        BigInteger totalSteps = BigInteger.ZERO;
        BigInteger loopSteps = BigInteger.ZERO;

        // Aggregate stats
        for(Future<SessionResults> result : results)
        {
            try
            {
                SessionResults r = result.get();

                totalSteps = totalSteps.add(r.totalSteps);
                loopSteps = loopSteps.add(r.loopSteps);

                // Test if the current max is better than the new results
                int comp = r.maxSteps.compareTo(sessionMaxSteps);
                if(comp > 0)
                {
                    sessionMaxSteps = r.maxSteps;
                    sessionMaxBeaver = r.maxBeaver;
                    sessionTies = r.ties;
                }
                else if(comp == 0)
                    sessionTies += r.ties + 1;
            }
            catch(Exception error) { }
        }

        e.shutdownNow();

        return new SessionResults(sessionMaxSteps, sessionMaxBeaver, sessionTies, totalSteps, loopSteps);
    }

    private static List<String> GetBeaverList(String filePath, String filter) throws FileNotFoundException
    {
        List<String> beaverList = new ArrayList<>();
        Scanner s = new Scanner(new File(filePath));

        while(s.hasNextLine())
        {
            String b = s.nextLine();
            if(!b.contains(filter))
                beaverList.add(b);
        }

        s.close();
        return beaverList;
    }

    public static void main(String[] args) throws FileNotFoundException
    {
        String beaverListFileDefault = "./BB2x3_verified_enumeration.csv";
        String beaverListFile = args.length > 1 ? args[1] : beaverListFileDefault;
        List<String> beavers = GetBeaverList(beaverListFile, ",nonhalt"); // <- Filter items OUT of the list of TMs

        // Controls
        Beaver.logging = false;
        boolean verbose = false;
        int lengthOfSmallestSubtape = 5;
        int lengthOfLargestSubtape = 10;
        boolean multithreaded = true;
        int minTapeToMultithread = 6;
        int poolSize = 8;

        BigInteger totalSteps = BigInteger.ZERO;
        BigInteger loopSteps = BigInteger.ZERO;
        
        for(int tapeLength = lengthOfSmallestSubtape; tapeLength < lengthOfLargestSubtape + 1; ++tapeLength)
        {
            ConcurrentLinkedQueue<String> b = new ConcurrentLinkedQueue<String>(beavers);
            SessionResults results;

            Instant startTime = Instant.now();

            // Calculate the steps for each beaver
            if(multithreaded && tapeLength > minTapeToMultithread)
                results = runMultithreaded(b, tapeLength, poolSize);
            else
                results = calcBeaversMaxSteps(b, tapeLength);

            Instant endTime = Instant.now();

            // Record stats for calculating how many steps were in loops
            totalSteps = totalSteps.add(results.totalSteps);
            loopSteps = loopSteps.add(results.loopSteps);

            // Use the first beaver to get the max theoretical steps (assumes all tested beavers are the same size)
            BigInteger maxTheoreticalSteps = Utility.TheoreticalMaxSteps(new Beaver(beavers.get(0)).states.length, new Beaver(beavers.get(0)).symbolCount, tapeLength);

            if(verbose)
            {
                // Record max steps taken in this size subtape
                System.out.println("Time Taken:  " + Duration.between(startTime, endTime));
                System.out.print("The max steps in a subtape of size " + tapeLength + " was " + results.maxSteps + ".");
                System.out.println(" Theoretical max for this tape was " + maxTheoreticalSteps + ".");
                System.out.println("This occurred with TM " + results.maxBeaver + " and was tied " + results.ties + " times.");
            }
            else
            {
                System.out.println("" + tapeLength + ": " + results.maxSteps + " / " + maxTheoreticalSteps + "\t" + Duration.between(startTime, endTime));
                System.out.println(results.maxBeaver + " + " + results.ties);
            }
        }

        double percentLoopSteps = loopSteps.multiply(BigInteger.valueOf(1000)).divide(totalSteps).doubleValue() / 10.0;

        System.out.println("A total of " + (totalSteps.divide(BigInteger.valueOf(100000000l)).intValue() / 10.0) + " billion steps were calculated.");
        System.out.println("Approximately " + percentLoopSteps + "% of the steps were performed in loops.");
    }
}
