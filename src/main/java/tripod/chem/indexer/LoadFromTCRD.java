package tripod.chem.indexer;

import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;

import java.io.IOException;
import java.sql.*;

public class LoadFromTCRD {

    public LoadFromTCRD() {
    }

    public Integer fetchLigandsFromTCRD(StructureIndexer indexer) throws ClassNotFoundException {

        String url = "jdbc:mysql://tcrd.ncats.io/tcrd6124?useSSL=false";
        String user = "tcrd";
        String password = "";
        Integer count = 0;

        String query = "SELECT identifier, smiles from ncats_ligands where smiles is not null";

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
    }



}
