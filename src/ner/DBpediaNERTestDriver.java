
package ner;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;


/** a simple driver to test the functionality of <code>DBpediaNER.class</code>
 *
 * @author David Lindberg
 */
public class DBpediaNERTestDriver
{
    private static final String endpoint = "http://dbpedia.org/sparql";
    private static final String graphIRI = "http://dbpedia.org";

    public static void main(String[] args)
    {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        DBpediaNER dbpediaNER = new DBpediaNER(endpoint, graphIRI);
//
        System.out.println("Enter a sentence at the prompt (>)");
        while (true)
        {
            System.out.print("> ");
            try
            {
                String input = reader.readLine();
                if (!input.equals(""))
                {
                    System.out.println("Retrieving annotations for \"" + input + "\"...");
                    String[][] typeTable = dbpediaNER.getAnnotationTable(input);

                    if (typeTable == null) //no base noun phrases were found
                        System.out.println("No base noun phrases were found.");
                    else
                    {
                        for (int phrase = 0; phrase < typeTable.length; phrase++)
                        {
                            System.out.println("\""+typeTable[phrase][0]+"\" has type "
                                                        + typeTable[phrase][1]);
                        }
                    }
                }
            }
            catch (IOException e)
            {
                ; //ignore and wait for another input line
            }
        }
    }
}
