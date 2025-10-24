import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.Iterator;

public class Trie
{
    public static int MAX_STATES = 6;

    private static class Node 
    {
        int state;
        Node[] children = new Node[MAX_STATES];
        int childCount = 0;

        Node(int s)
        {
            state = s;
        }

        Node addChild(int state)
        {
            // Check if the state already exists
            for(int i = 0; i < childCount; ++ i)
                if(children[i].state == state)
                    return children[i];

            // If not, create a new child with this state
            Node newChild = new Node(state);
            children[childCount++] = newChild;

            return newChild;
        }

        @Override
        public boolean equals(Object obj)
        {
            if(!(obj instanceof Node))
                return false;
            Node other = (Node)obj;

            // Check if internal variables match
            if(state != other.state || childCount != other.childCount)
                return false;

            // Check all children are equal
            boolean childrenEqual = true;
            for(int i = 0; i < childCount; ++i)
            {
                int childState = children[i].state;

                int j = 0;
                // Find a child with the matching state and compare them
                while(j < other.childCount)
                {
                    if(childState == other.children[j].state)
                    {
                        childrenEqual &= children[i].equals(other.children[j]);
                        break;
                    }

                    ++j;
                }

                // No matching child was found
                if(j == other.childCount)
                    return false;
            }

            return childrenEqual;
        }

        @Override
        public int hashCode()
        {
            int hash = 17 + state;

            hash = hash << 5 + childCount;

            for(Node n : children)
                hash = 31 * hash + n.hashCode();

            return hash;
        }
    }

    Node baseNode = new Node(-1);
    int resultCount = 0;

    public Trie() {}

    public Trie(String trieString) throws IOException, NumberFormatException
    {
        String[] parts = trieString.split("|");
        StringReader trieStream = new StringReader(parts[1]);

        resultCount = Integer.parseInt(parts[0]);
        baseNode = parseStream(trieStream);
    }

    public void addResult(ArrayDeque<Integer> stateStack)
    {
        Iterator<Integer> stackIt = stateStack.descendingIterator();
        Node curr = baseNode;
        ++resultCount;

        while(stackIt.hasNext())
            curr = curr.addChild(stackIt.next());
    }

    private void buildString(Trie.Node node, StringBuilder outString)
    {
        outString.append(State.stateToChar(node.state));

        if(node.childCount > 0)
        {
            outString.append(node.childCount);
            for(int i = 0; i < node.childCount; ++ i)
                buildString(node.children[i], outString);
        }
    }

    private Node parseStream(StringReader inString) throws IOException
    {
        Node currNode = new Node(State.charToState((char)inString.read()));

        // Read the number of children (reset to this point in the stream if not a number)
        inString.mark(1);
        int numChildren = inString.read() - '0';
        
        if(numChildren > 9)
        {
            // This node has no children
            inString.reset();
            return currNode;
        }

        currNode.childCount = numChildren;

        // Create the child nodes and parse them
        for(int i = 0; i < currNode.childCount; ++i)
            currNode.children[i] = parseStream(inString);

        return currNode;
    }

    @Override
    public String toString() {
        StringBuilder outString = new StringBuilder();

        // Add internal variables
        outString.append(resultCount);
        outString.append("|");

        // Add the trie
        buildString(baseNode, outString);

        return outString.toString();
    }

    @Override
    public boolean equals(Object obj)
    {
        if(obj == this)
            return true;
        if(!(obj instanceof Trie))
            return false;

        // Check if internal variables match
        if(resultCount != ((Trie)obj).resultCount)
            return false;

        // Check if the tries match
        return baseNode.equals(((Trie)obj).baseNode);
    }
}
