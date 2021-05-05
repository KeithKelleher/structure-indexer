package tripod.chem.indexer;

import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Map;

@SpringBootApplication
@RestController
public class LoadFromTCRD {

    public LoadFromTCRD() {
    }

    public Integer fetchLigandsFromTCRD(StructureIndexer indexer) throws ClassNotFoundException {

        String url = "jdbc:mysql://tcrd.ncats.io/tcrd6110?useSSL=false";
        String user = "tcrd";
        String password = "";
        Integer count = 0;

        String query = "SELECT id, smiles from ncats_ligands where smiles is not null";

        Class.forName("com.mysql.jdbc.Driver");
        try (Connection con = DriverManager.getConnection(url, user, password);
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(query)) {

            while (rs.next()) {
                String id = rs.getString(1);
                String smiles = rs.getString(2);
                Molecule m = MolImporter.importMol(smiles);
                indexer.add("TCRD", id, m);
                count++;
            }

        } catch (SQLException | IOException ex) {
            System.out.println(ex);
        }
        return count;
    }

    public static void main(String[] args) {
        SpringApplication.run(LoadFromTCRD.class, args);
    }

    @GetMapping("/")
    public String deployCheck(){
        return "<h1>NCATS Structure Search</h1>" +
                "<h2>Usage</h2>" +
                "<div>URL: '/search'</div>" +
                "<div>" +
                "Arguments<ul>" +
                "<li>smiles (required) - the URI encoded SMILES string of the compound</li>" +
                "<li>type (optional, default = 'sim') - the type of search, either {sim}ilarity or {sub}structure</li>" +
                "<li>t (optional, default = 0.8 for similarity search) - the cutoff for compound similarity</li>" +
                "</ul>" +
                "</div>";
    }


    @GetMapping("/search")
    public String findSimilarStructures(@RequestParam Map<String, String> queryParams) throws Exception {
        String smiles = queryParams.get("smiles");
        if(smiles == null) {
            return null;
        }
        String method = coalesce(queryParams.get("type"), "sim");
        String cutoff = coalesce(queryParams.get("t"), "0.8");
        Search s = new Search(new String[]{"idx", "-t", cutoff, "-s", method, smiles});

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final String utf8 = StandardCharsets.UTF_8.name();
        try (PrintStream ps = new PrintStream(baos, true, utf8)) {
            s.exec(ps);
        }
        String data = baos.toString(utf8);

        return "<pre>" + data + "</pre>";
    }

    private static <T> T coalesce(T ...items) {
        for(T i : items) if(i != null) return i;
        return null;
    }

}
