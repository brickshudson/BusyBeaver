import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;

public class Beaver
{
    public static boolean logging = true;

    // Beaver internals
    public String beaverString;
    public byte symbolCount;
    public State[] states;

    // Editable start state for alternate runs
    public int startState = State.charToState('A');

    Beaver(String beaver)
    {
        beaverString = beaver;

        // Clean additional data from the string
        beaver = beaver.split("[^0-9A-Z_-]")[0];

        // Split the string into its states and identify the number of symbols being used
        String[] stateList = beaver.split("_");
        symbolCount = (byte)(stateList[0].length()/3);

        // Initialize the state array
        states = new State[stateList.length];
        for(int i = 0; i < states.length; ++i)
            states[i] = new State(i, stateList[i], symbolCount);
        
        // If no state identified a halting transition in the state constructor, this is not a valid beaver
        boolean beaverHalts = false;

        for(State state : states)
            for(int transitionState : state.targetState)
                if(transitionState == State.HALTED)
                    beaverHalts = true;

        if(!beaverHalts)
            throw new IllegalArgumentException("No halting transition found. Valid characters for halt state are '-' and 'Z'.");

        if(logging)
            System.out.println("Built size " + states.length + " beaver " + beaver);
    }

    public void run(BigInteger maxStepCount)
    {
        Instant start = Instant.now();

        // Initialize tape
        int currState = startState;
        Tape tape = new Tape();
        BigInteger stepCount = BigInteger.ZERO;

        // Run machine until halted or over the user-defined step count
        while(currState != State.HALTED && (maxStepCount.equals(BigInteger.ZERO) || stepCount.compareTo(maxStepCount) < 0))
        {
            // Read tape
            byte currSymbol = tape.readHead();
            stepCount = stepCount.add(BigInteger.ONE);

            // Execute Transition
            tape.writeHead(states[currState].symbol[currSymbol]);
            tape.move(states[currState].dir[currSymbol]);
            currState = states[currState].targetState[currSymbol];
        }

        Instant end = Instant.now();

        if(logging)
        {
            // Output results
            System.out.println((currState == State.HALTED ? "Halted: " : "Not halted: ") + stepCount + " steps taken, " + tape.sigma() + " nonzero cells and " + tape.definedCount + " cells on tape.");
            System.out.println(Duration.between(start, end));
        }
    }

    public static void main(String[] args)
    {
        String beaver = args.length > 0 ? args[0] : "1RB1LB_1LA1RZ";
        Beaver b = new Beaver(beaver);
        
        BigInteger maxStepsToRun = BigInteger.ZERO;
        if(args.length > 2)
            maxStepsToRun = new BigInteger(args[2]);

        b.run(maxStepsToRun);
    }
}
