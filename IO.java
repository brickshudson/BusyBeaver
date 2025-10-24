import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class IO
{
    public static String dataPath = "./data/";
    public static List<String> GetBeaverList(String fileName) throws FileNotFoundException
    {
        ArrayList<String> beaverList = new ArrayList<>();
        Scanner s = new Scanner(new File(dataPath + fileName));

        while(s.hasNextLine())
            beaverList.add(s.nextLine());

        s.close();
        return beaverList;
    }
    
    public static void SaveTransitionList(EnumerateSubtapes subtapes) throws IOException
    {
        String fileName = subtapes.beaver.beaverString + "_transitions" + subtapes.subtapeLength + ".txt";
        PrintWriter pw = new PrintWriter(new File(fileName));

        // For each transition, generate a line with <source tape> <steps taken> <result tape>
        for(EnumerateSubtapes.SubtapeTransition t : subtapes.transitionList)
        {
            String sourceTape;
            if(t.source().headDirection == Tape.LEFT)
                sourceTape = State.stateToChar(t.state()) + ">" + t.source().toString();
            else
                sourceTape = t.source().toString() + "<" + State.stateToChar(t.state());

            String resultTape;
            if(t.result().halted)
                resultTape = "HALT";
            else if(t.result().headDirection == Tape.LEFT)
                resultTape = "<" + State.stateToChar(t.nextState()) + t.result().toString();
            else
                resultTape = t.result().toString() + State.stateToChar(t.nextState()) + ">";

            pw.println(sourceTape + " " + t.result().steps + " " + resultTape);
        }

        pw.close();
    }

    public static void systemOutToFile(String outputFileName) throws FileNotFoundException
    {
        PrintStream ps = new PrintStream(new File(dataPath + outputFileName));
        System.setOut(ps);
    }
}
