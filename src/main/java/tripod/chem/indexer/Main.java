package tripod.chem.indexer;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.concurrent.*;

import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;

public class Main {
    static final Logger logger = Logger.getLogger(Main.class.getName());

    File index;
    String idField;
    boolean listSource;
    boolean loadFromTCRD;
    List<File> files = new ArrayList<File>();

    public Main (String[] argv) throws IOException {
        for (int i = 0; i < argv.length; ++i) {
            if (argv[i].charAt(0) == '-') {
                switch (argv[i].charAt(1)) {
                case 'h':
                    usage (System.err);

                case 'i':
                    if (argv[i].length() > 2)
                        idField = argv[i].substring(2);
                    else
                        idField = argv[++i];
                    logger.info("Id Field: \""+idField+"\"");
                    break;

                case 'l':
                    listSource = true;
                    break;

                case 't':
                    loadFromTCRD = true;
                    break;

                default:
                    logger.warning("Unknown option: "+argv[i]);
                }
            }
            else {
                File f = new File (argv[i]);
                if (index == null) {
                    if (!f.exists())
                        f.mkdirs();
                    index = f;
                }
                else if (f.exists()) {
                    files.add(f);
                }
                else {
                    logger.warning(argv[i]+": Invalid file!");
                }
            }
        }

        if (index == null) {
            logger.warning("No INDEX directory specified!");
            usage ();
        }
    }

    public void exec () throws Exception {
        StructureIndexer indexer = StructureIndexer.open(index);
        try {
            if (listSource) {
                Map<String, Integer> sources = indexer.getSources();
                logger.info(sources.size()+" source(s) indexed:");
                for (Map.Entry<String, Integer> me : sources.entrySet()) {
                    System.out.println(me.getKey()+"\t"+me.getValue());
                }
                System.out.println("** Total: "+indexer.size());
            }

            if (!files.isEmpty() || loadFromTCRD)
                logger.info("Adding structures to index " + index + "...");

            long start = System.currentTimeMillis(), total = 0;
            for (File f : files) {
                MolImporter mi = new MolImporter (new FileInputStream (f));
                int count = 0;
                for (Molecule m = new Molecule (); mi.read(m); ++count) {
                    String id = null;
                    if (idField != null) {
                        id = m.getProperty(idField);
                        if (id == null) {
                            /*
                            logger.warning
                                ("Can't retrieve id from field \""
                                 +idField+"\"");
                            */
                            id = m.getName();
                        }
                    }
                    else {
                        id = String.format("%1$010d", count+1);
                    }
                    String source = f.getName();
                    int pos = source.lastIndexOf('.');
                    if (pos > 0) {
                        source = source.substring(0, pos);
                    }
                    indexer.add(source, id, m);
                }
                logger.info(f.getName()+": "+count+"/"+indexer.size());
                total += count;
                mi.close();
            }

            if(loadFromTCRD){
                System.out.println("loading ligands from TCRD");
                LoadFromTCRD loader = new LoadFromTCRD();
                total += loader.fetchLigandsFromTCRD(indexer);
            }

            if (total > 0) {
                logger.info("Indexing time "+String.format
                            ("%1$.2fs",1e-3*(System.currentTimeMillis()-start))
                            +" for "+total+" structures!");
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
        ps.println("Usage: Main [OPTIONS] INDEX FILES...");
        ps.println("where INDEX is the index directory and OPTIONS can");
        ps.println("be one or more of the following:");
        ps.println("-h print this message");
        ps.println("-i FIELD  specify the field name to extract ID; if "
                   +"not specified,");
        ps.println("   an autoincrement value is used");
        ps.println("-l print all the source filenames that have been indexed");
        System.exit(1);
    }

    public static void main (String[] argv) throws Exception {
        Main m = new Main (argv);
        m.exec();
    }
}
