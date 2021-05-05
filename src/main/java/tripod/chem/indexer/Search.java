package tripod.chem.indexer;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.concurrent.*;

import org.apache.lucene.search.Filter;
import org.apache.lucene.search.NumericRangeFilter;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.FieldCacheTermsFilter;

import chemaxon.formats.MolImporter;
import static tripod.chem.indexer.StructureIndexer.*;

public class Search {
    static final Logger logger = Logger.getLogger(Search.class.getName());

    File index;
    List<String> queries = new ArrayList<String>();
    List<Filter> filters = new ArrayList<Filter>();
    String search = "text";
    double threshold = 0.8;
    boolean list;
    String format = "smiles";
    Set<String> fields = new TreeSet<String>();
    
    public Search (String[] argv) {
        for (int i = 0; i < argv.length; ++i) {
            if (argv[i].charAt(0) == '-') {
                switch (argv[i].charAt(1)) {
                case 'h':
                    usage (System.err);
                    
                case 'F': {
                    String arg;
                    if (argv[i].length() > 2)
                        arg = argv[i].substring(2);
                    else
                        arg = argv[++i];
                    
                    int pos = arg.indexOf('=');
                    if (pos > 0) {
                        String field = arg.substring(0, pos);
                        String value = arg.substring(pos+1);

                        Filter filter = parseFilter (field, value);
                        if (filter != null) {
                            fields.add(field);
                            filters.add(filter);
                        }
                        else {
                            logger.warning("Unable to parse filter: "
                                           +field+"="+value);
                        }
                    }
                    else {
                        logger.warning("Invalid argument for -F option; "
                                       +"argument must be of the form "
                                       +"KEY=VALUE!");
                    }
                    break;
                }
                case 's': {
                    String arg;
                    if (argv[i].length() > 2)
                        arg = argv[i].substring(2);
                    else
                        arg = argv[++i];
                    if (arg.startsWith("sim") || arg.startsWith("tan")) {
                        search = "similarity";
                    }
                    else if (arg.startsWith("sub")) {
                        search = "substructure";
                    }
                    else if (arg.equalsIgnoreCase("text"))
                        search = "text";
                    else {
                        logger.warning("Unknown value \""+arg
                                       +"\" for -s option; must be one of "
                                       +"{similarity, substructure}");
                    }
                    break;
                }
                case 't': {
                    String arg;
                    if (argv[i].length() > 2)
                        arg = argv[i].substring(2);
                    else
                        arg = argv[++i];
                    try {
                        threshold = Double.parseDouble(arg);
                        if (threshold < 0. || threshold > 1.) {
                            logger.warning("Threshold out of range: [0,1]");
                        }
                    }
                    catch (NumberFormatException ex) {
                        logger.warning("Bogus threshold value: "+arg);
                    }
                    break;
                }
                case 'L':
                    list = true;
                    break;
                    
                case 'f':
                    if (argv[i].length() > 2)
                        format = argv[i].substring(2);
                    else
                        format = argv[++i];
                    break;
                    
                default:
                    logger.warning("Unknown option: "+argv[i]);
                }
            }
            else if (index == null) {
                index = new File (argv[i]);
                if (!index.exists()) {
                    logger.warning(argv[i]+": doesn't exist!");
                    usage ();
                }
                else if (!index.isDirectory()) {
                    logger.warning(argv[i]+": not a valid index directory!");
                    usage ();
                }
            }
            else {
                queries.add(argv[i]);
            }
        }

        if (index == null)
            usage ();
        
    }

    static final String NUMREG = "(-?[0-9]*\\.?[0-9]*)";
    static Filter parseFilter (String field, String value) {
        Filter f = null;        
        if (value.charAt(0) == '=') {
            value = value.substring(1);
            if (value.indexOf('.') >= 0) {
                try {
                    double val = Double.parseDouble(value);
                    f = NumericRangeFilter.newDoubleRange
                        (field, val, val, true, true);
                }
                catch (NumberFormatException ex) {
                    logger.warning("Invalid double value: "+value);
                    System.exit(1);
                }
            }
            else {
                try {
                    long val = Long.parseLong(value);
                    f = NumericRangeFilter.newLongRange
                        (field, val, val, true, true);
                }
                catch (NumberFormatException ex) {
                    logger.warning("Invalid long value: "+value);
                    System.exit(1);
                }
            }
            return f;
        }
        
        Pattern p = Pattern.compile(NUMREG+":"+NUMREG); // range
        Matcher m = p.matcher(value);
        if (m.find()) {
            String g1 = m.group(1), g2 = m.group(2);
            if ((g1.length() > 0 && g1.indexOf('.') < 0)
                || (g2.length() > 0 && g2.indexOf('.') < 0)) {
                Long lower = null;
                try {
                    lower = Long.parseLong(g1);
                }
                catch (NumberFormatException ex) {}
                
                Long upper = null;
                try {
                    upper = Long.parseLong(g2);
                }
                catch (NumberFormatException ex) {}
                
                if (lower != null && upper != null && lower > upper) {
                    logger.warning("Invalid range ["+lower+","+upper+"]");
                    System.exit(1);
                }
                f = NumericRangeFilter.newLongRange
                    (field, lower, upper, true, true);
                //logger.info(field+"=["+lower+","+upper+"]");
            }
            else {
                Double lower = null;
                try {
                    lower = Double.parseDouble(g1);
                }
                catch (NumberFormatException ex) {}
                
                Double upper = null;
                try {
                    upper = Double.parseDouble(g2);
                }
                catch (NumberFormatException ex) {}
                if (lower != null && upper != null && lower > upper) {
                    logger.warning("Invalid range ["+lower+","+upper+"]");
                    System.exit(1);
                }
                f = NumericRangeFilter.newDoubleRange
                    (field, lower, upper, true, true);
                //logger.info(field+"=["+lower+","+upper+"]");
            }
        }
        else {
            // term query
            f = new FieldCacheTermsFilter (field, value);
        }
        //logger.info("Filter="+f);
        
        return f;
    }

    int process (PrintStream ps, ResultEnumeration result) throws Exception {
        int count = 0;
        while (result.hasMoreElements()) {
            Result r = result.nextElement();
            if (count == 0 && format.startsWith("smi")) {
                ps.print("#STRUCTURE\tID\tSOURCE\tSIMILARITY");
                for (String f : fields) {
                    ps.print("\t"+f);
                }
                ps.println();
            }
            
            if (format.startsWith("smi")) {
                ps.print(r.getMol().toFormat(format));
                ps.print("\t"+r.getId());
                ps.print("\t"+r.getSource());
                ps.print("\t"+String.format("%1$.3f", r.getSimilarity()));
                for (String f : fields) {
                    if (f.equals(FIELD_SOURCE))
                        ps.print("\t"+r.getSource());
                    else if (f.equals(FIELD_MOLWT))
                        ps.print("\t"+r.getMol().getMass());
                    else if (f.equals(FIELD_NATOMS))
                        ps.print("\t"+r.getMol().getAtomCount());
                    else if (f.equals(FIELD_NBONDS))
                        ps.print("\t"+r.getMol().getBondCount());
                    else {
                        String v = r.getMol().getProperty(f);
                        ps.print("\t");                 
                        if (v != null)
                            ps.print(v);
                    }
                }
                ps.println();
            }
            else {
                ps.print(r.getMol().toFormat(format));
            }
            /*
            System.out.println
                ("++++ "+r.getId()+" sim="
                 +r.getSimilarity()+" mwt="+r.getMol().getMass()
                 +" atoms="+r.getMol().getAtomCount()
                 +" bonds="+r.getMol().getBondCount());
            */
            ++count;
        }
        return count;
    }

    void text (PrintStream ps, StructureIndexer indexer, String q)
        throws Exception {
        long start = System.currentTimeMillis();
        int count = process (ps, indexer.search
                             (q, 0, filters.toArray(new Filter[0])));
        double ellapsed = (System.currentTimeMillis()-start)*1e-3;
        logger.info(q+": "+count+" matches found in "
                    +String.format("%1$.2fs", ellapsed));

    }
    
    void similarity (PrintStream ps, StructureIndexer indexer, String q)
        throws Exception {
        long start = System.currentTimeMillis();
        int count = process (ps, indexer.similarity
                             (q, threshold, filters.toArray(new Filter[0])));
        double ellapsed = (System.currentTimeMillis()-start)*1e-3;
        logger.info(q+": "+count+" matches found in "
                    +String.format("%1$.2fs", ellapsed));
    }

    void substructure (PrintStream ps, StructureIndexer indexer, String q)
        throws Exception {
        long start = System.currentTimeMillis();
        int count = process (ps, indexer.substructure
                             (q, 0, 3, filters.toArray(new Filter[0])));
        double ellapsed = (System.currentTimeMillis()-start)*1e-3;
        logger.info(q+": "+count+" matches found in "
                    +String.format("%1$.2fs", ellapsed));
    }

    public void exec () throws Exception {
        exec (System.out);
    }
    
    public void exec (PrintStream ps) throws Exception {
        StructureIndexer indexer = StructureIndexer.openReadOnly(index);
        try {
            if (list) {
                String[] fields = indexer.getFields();
                for (String f : fields) {
                    ps.println("  \""+f+"\"");
                }
                ps.println
                    ("## Index contains "+fields.length+" fields!");
            }
            else if (queries.isEmpty() && filters.isEmpty()) {
                logger.warning("Neither queries nor filters are specified!");
                usage ();
            }

            if (queries.isEmpty()) {
                if (!filters.isEmpty()) {
                    long start = System.currentTimeMillis();
                    int count = process (ps, indexer.search
                                         (filters.toArray(new Filter[0])));
                    double ellapsed = (System.currentTimeMillis()-start)*1e-3;
                    //Thread.currentThread().sleep(5000);
                    logger.info(count+" matches found in "
                                +String.format("%1$.2fs", ellapsed));
                }
            }
            else {
                for (String q : queries) {
                    if (search.equalsIgnoreCase("text"))
                        text (ps, indexer, q);
                    else if (search.equalsIgnoreCase("similarity"))
                        similarity (ps, indexer, q);
                    else
                        substructure (ps, indexer, q);
                }
            }
        }
        finally {
            indexer.shutdown();
        }
    }

    static void usage () {
        usage (System.err);
    }
    
    static void usage (PrintStream ps) {
        ps.println("Usage: Search [OPTIONS] INDEX SMILES/SMARTS...");
        ps.println("where OPTIONS can be one or more of the "
                   +"following:");
        ps.println("-h print this message");
        ps.println("-s {sim(ilarity)|sub(structure)|text}  search type "
                   +"(default: text)");
        ps.println("-t CUTOFF  specify Tanimoto cutoff (default: 0.8) for "
                   +"similarity search");
        ps.println("-F FIELD=VALUE  filter based on the provided field-value "
                   +"pair. See -L option");
        ps.println("   for a list of valid FIELDs. If VALUE is numeric then "
                   +"an appropriate range");
        ps.println("   must be specified in the format LOW:HIGH, e.g., "
                   +"-F_molwt=450:550 specifies");
        ps.println("   that the molecular weight must be between 450 "
                   +"and 550 inclusive. Either LOW");
        ps.println("   or HIGH might be ommitted. To search for exact numeric "
                   +"value, use == instead;");
        ps.println("   e.g., -F_natoms==30. Here is the list of builtin fields:");
        ps.println("    {"+FIELD_POPCNT+", "+FIELD_MOLWT+", "+FIELD_NATOMS
                   +", "+FIELD_NBONDS+", "+FIELD_NAME+"}");
        ps.println("-L list available filter FIELDs");
        ps.println("-f {smiles|mol|sdf}  specify output format (default: smiles)");
        
        System.exit(1);
    }
    
    public static void main (String[] argv) throws Exception {
        Search search = new Search (argv);
        search.exec();
    }
}
