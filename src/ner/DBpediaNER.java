
package ner;

import java.util.ArrayList;

import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import com.hp.hpl.jena.query.*;

/** a class that uses DBpedia to attempt to identify named entities in a sentence
 *
 * @author David Lindberg
 */
public class DBpediaNER
{
    private static final String RDFS_PREFIX="http://www.w3.org/2000/01/rdf-schema#";
    private static final String OWL_PREFIX="http://www.w3.org/2002/07/owl#";

    private LexicalizedParser parser;
    private String endpoint;
    private String graph;

    /**
     * Creates a new instance of <code>DBpediaNER</code> that will query
     * the supplied graph IRI using the specified SPARQL endpoint.
     *
     * @param sparqlEndpoint    the address of a SPARQL endpoint
     * @param graphIRI          the graph to be queried
     */
    public DBpediaNER(String sparqlEndpoint, String graphIRI)
    {
        endpoint = sparqlEndpoint;
        graph = graphIRI;
        parser = LexicalizedParser.loadModel("edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz");
    }

    /**
     * Returns a 2D array in which column 0 contains candidate substrings of the
     * input <code>sentence</code>, and column 1 contains a corresponding DBpedia concept type.
     * Not all candidate substrings are guaranteed to be mapped to a DBpedia type,
     * and any unmatched candidates in column 0 will have a null type in column 1.
     *
     * @param sentence  the sentence in which to look for named entities
     * @return a 2D array of [substring, type] pairs
     */
    public String[][] getAnnotationTable(String sentence)
    {
        String[] baseNPs = extractBaseNPs(sentence);
        if (baseNPs == null)
            return null;

        String[] annotations = getTypeAnnotations(baseNPs);
        String[][] annotationTable = new String[baseNPs.length][2];

        for (int i = 0; i < baseNPs.length; i++)
        {
            annotationTable[i][0] = baseNPs[i];
            annotationTable[i][1] = annotations[i];
        }

        return annotationTable;
    }

    /**
     * Returns an array of named entity types associated with the input array of
     * words/phrases. Phrases that couldn't be mapped to a DBpedia type correspond
     * to a null value in the returned array.
     *
     * @param phrases   an array of candidate words or phrases
     * @return          an array containing a type for each candidate phrase
     */
    private String[] getTypeAnnotations(String[] phrases)
    {
        String[] types = new String[phrases.length];
        String[] vars = {"type"};


        for (int i = 0; i < types.length; i++)
        {
            // apostrophes in the phrase will cause problems
            phrases[i] = phrases[i].replace("'","\\'");

            String query = "SELECT ?type FROM <"+graph+"> WHERE "
                    +  "{ ?entity <"+RDFS_PREFIX+"label> ?label ."
                    +  " ?entity a ?type ."
                    +  " ?type <"+RDFS_PREFIX+"subClassOf> ?superType ."
                    +  " ?superType a <"+OWL_PREFIX+"Class> ."
                    +  " FILTER (<bif:contains>(?label, \"\'"+phrases[i]+"\'\"))}"
                    +  " ORDER BY strlen(?label) "
                    +  " LIMIT 1";

            // type will be element 0 of the array returned by getQueryResult()
            types[i] = getQueryResult(query,vars)[0];
        }
        return types;
    }

    /**
     * Executes a given <code>query</code> and returns the values corresponding to the query
     * variables stored in the <code>vars</code> array.
     *
     * @param query a SPARQL query to execute
     * @param vars  an array of variables whose values to return
     * @return      an array of values corresponding to the query variables
     */
    private String[] getQueryResult(String query, String[] vars)
    {
        String[] values = new String[vars.length];
        Query q = QueryFactory.create(query);
        QueryExecution exec = QueryExecutionFactory.sparqlService(endpoint, q);

        try
        {
            ResultSet result = exec.execSelect();
            while (result.hasNext())
            {
                QuerySolution soln = result.nextSolution();
                for (int i = 0; i < values.length; i++)
                    values[i] = soln.get(vars[i]).toString();
            }
        }

        finally
        {
            exec.close();
        }
        return values;
    }

    /**
     * Returns substrings from the 'base' noun phrases (NPs) in the input <code>sentence</code>.
     * We define a base NP as an NP not dominated by another NP in the parse tree.
     * We also exclude NPs that contain only a pronoun (PRP or PRP$) or determiner
     * (DT).
     *
     * @param sentence  the sentence from which to extract base NPs
     * @return          an array of substrings corresponding the the base NPs
     */
    private String[] extractBaseNPs(String sentence)
    {
        final String baseNPTregexPattern = "NP=np !<: /DT|PRP.?/ !> NP";

        ArrayList<String> baseNPs = new ArrayList<String>();
        StringBuilder npString = new StringBuilder();

        Tree sentenceParseTree = parser.apply(sentence);
        TregexMatcher baseNPMatcher = matchPattern(sentenceParseTree, baseNPTregexPattern);

        while (baseNPMatcher.find())
        {
            Tree np = baseNPMatcher.getNode("np");

            for (Tree t : np.getLeaves())
            {
                npString.append(t.value()).append(" ");
            }

            // the base NP pattern might match the same NP more than once
            if (!baseNPs.contains(npString.toString()))
            {
                baseNPs.add(npString.toString());
            }

            npString.setLength(0);
        }

        if (!baseNPs.isEmpty())
            return baseNPs.toArray(new String[baseNPs.size()]);
        else
            return null;
    }

    /**
     * Returns a Stanford TregexMatcher containing the subtrees found by matching
     * the given pattern (given in Stanford Tregex syntax) against the given
     * parse tree <code>t</code>.
     *
     * @param t             the parse tree to be processed
     * @param tree_pattern  the pattern we're looking for (specified in Tregex syntax)
     * @return              a TregexMatcher for the given pattern
     */
    private static TregexMatcher matchPattern(Tree t, String tree_pattern)
    {
        TregexPattern pattern = TregexPattern.compile(tree_pattern);
        return pattern.matcher(t);
    }

}
