import java.io.FileNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;

public class BBReverseSolver
{
    private static int backtrackTestRate = 10;
    private static boolean useTrie = true;

    private ReverseBeaver beaver;
    private int maxDepth;
    private ArrayDeque<Integer> stateStack;
    private Tape tape;

    // Statistics
    private int resultCount = 0;
    private Trie possibleResults;
    private Duration timeTaken = null;

    public BBReverseSolver(ReverseBeaver rb, int maxDepth_)
    {
        tape = new ReverseTape();
        tape.writeHead(Symbol.charToSymbol('-'));
        stateStack = new ArrayDeque<>(maxDepth);
        possibleResults = new Trie();
        maxDepth = maxDepth_;
        beaver = rb;
    }

    private boolean testPreviousStates(boolean direction, int state)
    {
        tape.move(direction);
        byte currSymbol = tape.readHead();

        // Get the list of transitions for the appropriate direction
        var transitionLists = beaver.states[state].rightTransitions;
        if(direction == Tape.LEFT)
            transitionLists = beaver.states[state].leftTransitions;

        // If no symbol is required, test all symbols
        if(currSymbol == Symbol.UNDEFINED)
        {
            for(int s = 0; s < beaver.forward.symbolCount; ++s)
                for(ReverseBeaver.ReverseState.SourceState prevState : transitionLists.get(s))
                    if(solveRecursive(prevState.state(), prevState.symbol()))
                        return true;
        }
        else // Otherwise, only test the required symbol
        {
            for(ReverseBeaver.ReverseState.SourceState prevState : transitionLists.get(currSymbol))
                if(solveRecursive(prevState.state(), prevState.symbol()))
                    return true;
        }

        tape.undoMove(direction);

        return false;
    }

    private boolean inImpossibleState()
    {
        // I'm not smart enough to know indicators for impossible states
        return false;
    }

    private boolean solveRecursive(int state, byte symbol)
    {
        Instant startTime = null;
        if(stateStack.size() == 0)
            startTime = Instant.now();

        // Occasionally, check if the rb is in an impossible state
        if(stateStack.size() % backtrackTestRate == 0)
        {
            if(inImpossibleState())
                return false;
        }

        // Update tape with current state
        stateStack.push(state);
        byte oldSymbol = tape.readHead();
        tape.writeHead(symbol);

        // Check if we have found the start of the beaver
        if(state == beaver.forward.startState && tape.isCleanTape())
        {
            if(useTrie)
                possibleResults.addResult(stateStack);

            System.out.println("Start found at a depth of " + stateStack.size() + " steps with a tape width of " + tape.definedCount + " after " + resultCount + " max depth passes.");

            ++resultCount;
            return true;
        }

        // If we still can run more, continue
        if(stateStack.size() < maxDepth - 1)
        {
            // Attempt all previous moves to the left
            if(testPreviousStates(Tape.LEFT, state))    // Recursion
                return true;

            // Attempt all previous moves to the right
            if(testPreviousStates(Tape.RIGHT, state))   // Recursion
                return true;
        }
        else // If we ran out of depth, add result
        {
            if(useTrie)
                possibleResults.addResult(stateStack);

            ++resultCount;
        }

        // Reset the changes this state made to the tape
        tape.writeHead(oldSymbol);
        stateStack.pop();

        if(stateStack.size() == 0)
            timeTaken = Duration.between(startTime, Instant.now());

        return false;
    }

    private void outputResults()
    {
        Instant printStart = Instant.now();

        if(useTrie)
            System.out.println("Result Trie: " + possibleResults.toString());

        Instant printStop = Instant.now();

        System.out.println("Time taken: " + timeTaken + " seconds)");

        if(useTrie)
            System.out.println("Time taken to print: " + Duration.between(printStart, printStop) + " seconds)");
    }

    public static void main(String[] args) throws FileNotFoundException
    {
        String beaver;
        int maxDepth;
        
        // Get command line arguments if applicable
        try
        {
            beaver = args.length > 1 ? args[1] :  "1RB1LB_1LA1RZ";
            maxDepth = args.length > 2 ? Integer.parseInt(args[2]) : 30;
            useTrie = args.length > 3 ? Boolean.parseBoolean(args[3]) : true;
        }
        catch(Exception e)
        {
            System.out.println("Invalid arguments. Use the following command:");
            System.out.println("java BBReverseSolver <TM> <Search depth> [<'false' to disable trie generation>]");
            System.out.println("Example: java ESRecursive 1RB1LB_1LA1RZ 20");
            System.out.println("Solver does not work for TNF TMs with multiple halt transitions.");
            System.out.println("Disabling trie generation is recommended for large search depths, as printing can take several minutes.");

            return;
        }

        // Initialize the reversed beaver and solver
        ReverseBeaver rb = new ReverseBeaver(beaver);
        BBReverseSolver solver = new BBReverseSolver(rb, maxDepth);

        if(useTrie)
            Trie.MAX_STATES = rb.forward.states.length;

        // Parse the state space from the halt state
        boolean isSolved = solver.solveRecursive(rb.exitState, rb.exitSymbol);

        if(!isSolved)
            solver.outputResults();
    }
}