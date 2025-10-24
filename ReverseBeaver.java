import java.util.List;
import java.util.ArrayList;

public class ReverseBeaver
{
    public Beaver forward;
    public ReverseState[] states;
    public int exitState;
    public byte exitSymbol;

    public class ReverseState
    {
        public record SourceState(int state, byte symbol) { }
        
        public int state;
        public List<List<SourceState>> leftTransitions;
        public List<List<SourceState>> rightTransitions;

        ReverseState(int s)
        {
            state = s;

            leftTransitions = new ArrayList<>();
            for(int i = 0; i < forward.symbolCount; ++i)
                leftTransitions.add(new ArrayList<>());

            rightTransitions = new ArrayList<>();
            for(int i = 0; i < forward.symbolCount; ++i)
                rightTransitions.add(new ArrayList<>());
        }
    }

    private void addStateTransition(int sourceState, byte sourceSymbol, byte symbol, boolean dir, int targetState)
    {
        if(targetState == State.HALTED)
            return;

        // Record the state + symbol combination that leads to targetState
        ReverseState.SourceState source = new ReverseState.SourceState(sourceState, sourceSymbol);

        if(dir != Tape.LEFT) // Flip the directions to keep tape oriented
            states[targetState].leftTransitions.get(symbol).add(source);
        else
            states[targetState].rightTransitions.get(symbol).add(source);
    }

    ReverseBeaver(String b)
    {
        forward = new Beaver(b);

        // Find the beaver's Halt transition
        for(State state : forward.states)
            for(byte symbol = 0; symbol < state.targetState.length; ++symbol)
                if(state.targetState[symbol] == State.HALTED)
                {
                    exitState = state.state;
                    exitSymbol = symbol;
                }

        // Initialize the reversed states
        states = new ReverseState[forward.states.length];
        for(int i = 0; i < states.length; ++i)
            states[i] = new ReverseState(i);

        // Convert the beaver by inverting the transitions
        for(int i = 0; i < states.length; ++i)
        {
            State curr = forward.states[i];

            for(byte symbol = 0; symbol < forward.symbolCount; ++symbol)
                addStateTransition(i, symbol, curr.symbol[symbol], curr.dir[symbol], curr.targetState[symbol]);
        }
        
        if(Beaver.logging)
            System.out.println("Reversed beaver");
    }
}
