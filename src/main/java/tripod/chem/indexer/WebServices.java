package tripod.chem.indexer;

import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
@RestController
public class WebServices {
    @GetMapping("/")
    public String usage() {
        return "<h1>Structure Search</h1>" +
                "<h2>Usage</h2>" +
                "<div>URL: '/search'</div>" +
                "<div>" +
                "Arguments<ul>" +
                "<li>structure (required) - the URI encoded SMILES string of the compound</li>" +
                "<li>type (optional, default = 'sim') - the type of search, either {sim}ilarity or {sub}structure</li>" +
                "<li>t (optional, default = 0.8 for similarity search) - the cutoff for compound similarity</li>" +
                "<li>format (optional, default = 'text') - either 'json' to get a json string, or anything else to get a tab delimited table</li>" +
                "</ul>" +
                "</div>" +
                "<h1>Image Renderer</h1>" +
                "<h2>Usage</h2>" +
                "<div>URL: '/render'</div>" +
                "<div>" +
                "Arguments<ul>" +
                "<li>structure (required) - the URI encoded SMILES string of the compound</li>" +
                "<li>size (optional, default = '400') - the size of the image</li>" +
                "</ul>" +
                "</div>";
    }

    @GetMapping("/search")
    public Object findSimilarStructures(@RequestParam Map<String, String> queryParams) throws Exception {
        String structure = queryParams.get("structure");
        if (structure == null) {
            return null;
        }
        String method = coalesce(queryParams.get("type"), "sim");
        String cutoff = coalesce(queryParams.get("t"), "0.8");
        String format = coalesce(queryParams.get("format"), "text");

        Search s = new Search(new String[]{"idx", "-t", cutoff, "-s", method, structure});

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final String utf8 = StandardCharsets.UTF_8.name();
        try (PrintStream ps = new PrintStream(baos, true, utf8)) {
            s.exec(ps);
        }
        String data = baos.toString(utf8);

        if(format.equals("json")) {
            String[] lines = data.split("\n");
            String[] fields = cleanFields(lines[0].split("\t"));
            ArrayList compounds = new ArrayList<HashMap<String,String>>();
            for(Integer i = 1 ; i < lines.length ; i++){
                HashMap<String, String> c = new HashMap<String, String>();
                compounds.add(c);
                String[] values = lines[i].split("\t");
                for(Integer j = 0 ; j < fields.length ; j++){
                    c.put(fields[j], values[j]);
                }
            }
            return ResponseEntity.ok(compounds);
        }
        return "<pre>" + data + "</pre>";
    }

    @RequestMapping(value = "/render", method = RequestMethod.GET)
    public void renderStructure(HttpServletResponse response, @RequestParam Map<String, String> queryParams) throws IOException {
        String structure = queryParams.get("structure");
        if (structure == null) {
            return;
        }
        String size = coalesce(queryParams.get("size"), "400");
        Molecule m = MolImporter.importMol(structure);

        response.setContentType(MediaType.IMAGE_PNG_VALUE);
        MolExporter exporter = new MolExporter(response.getOutputStream(),
                "png:w" + size + ",h" + size);
        exporter.write(m);
        exporter.close();
    }

    public static void main (String[] argv) {
        SpringApplication.run(WebServices.class, argv);
    }

    private static <T> T coalesce(T... items) {
        for (T i : items) if (i != null) return i;
        return null;
    }

    private static String cleanField(String name) {
        return name.replace("#", "").toLowerCase();
    }
    private static String[] cleanFields(String[] names) {
        String[] out = new String[names.length];
        for(Integer i = 0 ; i < names.length ; i++){
            out[i] = cleanField(names[i]);
        }
        return out;
    }
}
