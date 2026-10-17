import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Génère les textures et les modèles 3D des objets de santé (médicament, vitamines, vaccin), espace de noms historique « minenorthvie ».
 * Usage depuis la racine du projet : java tools/GenSante.java
 * (séparé de GenAssets pour ne pas toucher aux modèles défibrillateur / trousse / bandage / tablette).
 */
public class GenSante {
    static final File TEX = new File("src/main/resources/assets/minenorthvie/textures/item");
    static final File MODELS = new File("src/main/resources/assets/minenorthvie/models/item");
    static final String NS = "minenorthvie:item/";

    static final Map<String, Integer> CELL_COLOR = new LinkedHashMap<>();
    static final List<String> CELLS = new ArrayList<>();

    static void cell(String name, int rgb) { CELLS.add(name); CELL_COLOR.put(name, rgb); }

    static {
        cell("amber", 0xC8741A); cell("amber_hi", 0xE39A3A); cell("amber_dk", 0x8A4C0F);
        cell("white", 0xF4F4F4); cell("offwhite", 0xDADFE6); cell("silver", 0xB8BEC8);
        cell("gray", 0x8A9099); cell("gray_dk", 0x4A4F57); cell("black", 0x1E2024);
        cell("red", 0xC62828); cell("red_hi", 0xE24A4A); cell("orange", 0xF28C28);
        cell("orange_hi", 0xFFB347); cell("yellow", 0xF5C400); cell("green", 0x2EC46A);
        cell("blue", 0x2B7DE9); cell("blue_dk", 0x1B4FA0); cell("cyan", 0x20AAEB);
        cell("glass", 0xCDEFF7); cell("pink", 0xF48FB1);
    }

    static float[] uv(String cell) {
        int i = CELLS.indexOf(cell);
        if (i < 0) throw new IllegalArgumentException("case inconnue : " + cell);
        int col = i % 8, row = i / 8;
        return new float[]{col * 2 + 0.25f, row * 2 + 0.25f, col * 2 + 1.75f, row * 2 + 1.75f};
    }

    static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    static void atlas() throws Exception {
        BufferedImage im = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(11);
        for (int i = 0; i < CELLS.size(); i++) {
            int base = CELL_COLOR.get(CELLS.get(i));
            for (int x = 0; x < 8; x++) for (int y = 0; y < 8; y++) {
                int n = r.nextInt(7) - 3;
                int rr = clamp(((base >> 16) & 255) + n), gg = clamp(((base >> 8) & 255) + n), bb = clamp((base & 255) + n);
                im.setRGB((i % 8) * 8 + x, (i / 8) * 8 + y, 0xFF000000 | (rr << 16) | (gg << 8) | bb);
            }
        }
        save(im, "sante_atlas.png");
    }

    static void save(BufferedImage im, String name) throws Exception {
        TEX.mkdirs();
        ImageIO.write(im, "png", new File(TEX, name));
    }

    static Graphics2D g2(BufferedImage im) {
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setStroke(new BasicStroke(1));
        return g;
    }

    static void fill(Graphics2D g, int rgb, int x, int y, int w, int h) { g.setColor(new Color(rgb)); g.fillRect(x, y, w, h); }

    // ---------------------------------------------------------------- étiquettes (32 x 24 px)
    static void labelMedicament() throws Exception {
        BufferedImage im = new BufferedImage(32, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g2(im);
        fill(g, 0xF4F4F4, 0, 0, 32, 24);
        fill(g, 0xC62828, 0, 0, 32, 5);                      // bandeau rouge
        fill(g, 0xC62828, 0, 22, 32, 2);
        fill(g, 0xF4F4F4, 3, 1, 9, 3);                        // nom
        fill(g, 0xF4F4F4, 14, 2, 14, 1);
        // croix de soin
        fill(g, 0x8E1B1B, 12, 8, 8, 12);
        fill(g, 0x8E1B1B, 8, 12, 16, 4);
        fill(g, 0xC62828, 13, 7, 6, 12);
        fill(g, 0xC62828, 9, 11, 14, 4);
        // lignes de texte
        fill(g, 0x8A9099, 3, 8, 5, 1); fill(g, 0x8A9099, 3, 11, 4, 1); fill(g, 0x8A9099, 3, 14, 5, 1);
        fill(g, 0x8A9099, 25, 8, 4, 1); fill(g, 0x8A9099, 25, 11, 3, 1); fill(g, 0x8A9099, 25, 14, 4, 1);
        g.dispose();
        save(im, "medicament_etiquette.png");
    }

    static void labelVitamines() throws Exception {
        BufferedImage im = new BufferedImage(32, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g2(im);
        fill(g, 0xFFF3C4, 0, 0, 32, 24);
        fill(g, 0xF28C28, 0, 0, 32, 4);
        fill(g, 0xF28C28, 0, 21, 32, 3);
        fill(g, 0xFFF3C4, 4, 1, 12, 2);
        // soleil / agrume
        fill(g, 0xE0780F, 10, 6, 12, 12);
        fill(g, 0xF5C400, 11, 7, 10, 10);
        fill(g, 0xFFE066, 12, 8, 4, 3);
        fill(g, 0x2EC46A, 20, 5, 4, 3);                       // feuille
        fill(g, 0x2EC46A, 22, 4, 3, 2);
        fill(g, 0xE0780F, 15, 11, 2, 4);                      // quartiers
        fill(g, 0xE0780F, 13, 13, 6, 1);
        fill(g, 0x8A9099, 3, 9, 5, 1); fill(g, 0x8A9099, 3, 12, 4, 1);
        fill(g, 0x8A9099, 25, 9, 4, 1); fill(g, 0x8A9099, 25, 12, 3, 1);
        g.dispose();
        save(im, "vitamines_etiquette.png");
    }

    static void labelVaccin() throws Exception {
        BufferedImage im = new BufferedImage(32, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g2(im);
        fill(g, 0xF4F4F4, 0, 0, 32, 24);
        fill(g, 0x2B7DE9, 0, 0, 32, 5);
        fill(g, 0x2B7DE9, 0, 22, 32, 2);
        fill(g, 0xF4F4F4, 3, 1, 11, 3);
        fill(g, 0xF4F4F4, 16, 2, 12, 1);
        // seringue en diagonale
        g.setColor(new Color(0x1B4FA0));
        g.drawLine(8, 18, 24, 8); g.drawLine(8, 19, 24, 9); g.drawLine(9, 19, 25, 9);
        fill(g, 0x20AAEB, 13, 12, 6, 3);
        fill(g, 0x1B4FA0, 25, 6, 3, 5);
        fill(g, 0x8A9099, 4, 7, 4, 1); fill(g, 0x8A9099, 4, 10, 3, 1);
        fill(g, 0x8A9099, 24, 17, 4, 1); fill(g, 0x8A9099, 24, 19, 3, 1);
        g.dispose();
        save(im, "vaccin_etiquette.png");
    }

    // ---------------------------------------------------------------- constructeur de modèles
    static final String[] FACES = {"north", "east", "south", "west", "up", "down"};

    static class Box {
        final String name; final float[] from, to; final String[] face = new String[6];
        Box(String name, float x1, float y1, float z1, float x2, float y2, float z2) {
            this.name = name; this.from = new float[]{x1, y1, z1}; this.to = new float[]{x2, y2, z2};
        }
        Box all(String c) { java.util.Arrays.fill(face, c); return this; }
        Box side(String f, String c) { for (int i = 0; i < 6; i++) if (FACES[i].equals(f)) face[i] = c; return this; }
        Box sides(String c) { for (String f : new String[]{"north", "east", "south", "west"}) side(f, c); return this; }
        Box up(String c) { return side("up", c); }
        Box down(String c) { return side("down", c); }
    }

    static String f(float v) {
        return v == Math.rint(v) ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "");
    }

    static String model(List<Box> boxes, float dx, float dy, String label) {
        StringBuilder sb = new StringBuilder("{\n  \"textures\": {\n");
        sb.append("    \"0\": \"").append(NS).append("sante_atlas\",\n");
        if (label != null) sb.append("    \"1\": \"").append(NS).append(label).append("\",\n");
        sb.append("    \"particle\": \"").append(NS).append("sante_atlas\"\n  },\n  \"elements\": [\n");
        for (int i = 0; i < boxes.size(); i++) {
            Box b = boxes.get(i);
            sb.append("    {\"name\": \"").append(b.name).append("\", \"from\": [")
              .append(f(b.from[0] + dx)).append(", ").append(f(b.from[1] + dy)).append(", ").append(f(b.from[2])).append("], \"to\": [")
              .append(f(b.to[0] + dx)).append(", ").append(f(b.to[1] + dy)).append(", ").append(f(b.to[2])).append("],\n     \"faces\": {");
            for (int k = 0; k < 6; k++) {
                String c = b.face[k];
                if (c == null) c = "gray_dk";
                sb.append(k == 0 ? "" : ", ").append("\"").append(FACES[k]).append("\": ");
                if (c.startsWith("#")) sb.append("{\"uv\": [0, 0, 16, 16], \"texture\": \"").append(c).append("\"}");
                else { float[] u = uv(c); sb.append("{\"uv\": [").append(f(u[0])).append(", ").append(f(u[1])).append(", ").append(f(u[2])).append(", ").append(f(u[3])).append("], \"texture\": \"#0\"}"); }
            }
            sb.append("}}").append(i + 1 < boxes.size() ? ",\n" : "\n");
        }
        sb.append("  ],\n  \"display\": ").append(display()).append("\n}\n");
        return sb.toString();
    }

    static String display() {
        return "{\n"
            + "    \"gui\": {\"rotation\": [30, 225, 0], \"translation\": [0, 0, 0], \"scale\": [1.2, 1.2, 1.2]},\n"
            + "    \"ground\": {\"rotation\": [0, 0, 0], \"translation\": [0, 3, 0], \"scale\": [0.65, 0.65, 0.65]},\n"
            + "    \"fixed\": {\"rotation\": [0, 180, 0], \"translation\": [0, 0, 0], \"scale\": [1, 1, 1]},\n"
            + "    \"head\": {\"rotation\": [0, 180, 0], \"translation\": [0, 13, 7], \"scale\": [1, 1, 1]},\n"
            + "    \"thirdperson_righthand\": {\"rotation\": [75, 45, 0], \"translation\": [0, 2.5, 0], \"scale\": [0.7, 0.7, 0.7]},\n"
            + "    \"thirdperson_lefthand\": {\"rotation\": [75, 45, 0], \"translation\": [0, 2.5, 0], \"scale\": [0.7, 0.7, 0.7]},\n"
            + "    \"firstperson_righthand\": {\"rotation\": [0, 45, 0], \"translation\": [0, 1, 0], \"scale\": [0.75, 0.75, 0.75]},\n"
            + "    \"firstperson_lefthand\": {\"rotation\": [0, 225, 0], \"translation\": [0, 1, 0], \"scale\": [0.75, 0.75, 0.75]}\n  }";
    }

    static void write(String name, String json) throws Exception {
        MODELS.mkdirs();
        Files.writeString(new File(MODELS, name + ".json").toPath(), json, StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- les 3 modèles
    /** Flacon de comprimés ambré à bouchon blanc, avec quelques comprimés posés devant. */
    static void medicament() throws Exception {
        List<Box> b = new ArrayList<>();
        // corps à pans coupés : deux boîtes croisées
        b.add(new Box("corps_a", 4.5f, 0, 5.5f, 11.5f, 8, 10.5f).all("amber").up("amber_hi").down("amber_dk"));
        b.add(new Box("corps_b", 5.5f, 0, 4.5f, 10.5f, 8, 11.5f).all("amber").up("amber_hi").down("amber_dk"));
        b.add(new Box("epaule", 5.2f, 8, 5.2f, 10.8f, 8.8f, 10.8f).all("amber_dk").up("amber"));
        b.add(new Box("col", 6, 8.8f, 6, 10, 9.6f, 10).all("amber_dk"));
        // étiquette (nord et sud)
        b.add(new Box("etiquette_n", 5.1f, 1.2f, 4.4f, 10.9f, 6.9f, 4.5f).all("white").side("north", "#1"));
        b.add(new Box("etiquette_s", 5.1f, 1.2f, 11.5f, 10.9f, 6.9f, 11.6f).all("white").side("south", "#1"));
        // bouchon de sécurité blanc, avec bague crantée
        b.add(new Box("bague", 5.3f, 9.6f, 5.3f, 10.7f, 10.4f, 10.7f).all("offwhite"));
        b.add(new Box("bouchon", 5.5f, 10.4f, 5.5f, 10.5f, 12.2f, 10.5f).all("white").up("offwhite"));
        b.add(new Box("bouchon_creux", 6.8f, 12.2f, 6.8f, 9.2f, 12.4f, 9.2f).all("silver"));
        // comprimés posés devant le flacon
        b.add(new Box("cp1_a", 11.8f, 0, 6, 12.9f, 1.1f, 8).all("red").up("red_hi"));
        b.add(new Box("cp1_b", 12.9f, 0, 6, 14, 1.1f, 8).all("white"));
        b.add(new Box("cp2_a", 12, 0, 9, 14, 1.1f, 10.1f).all("red").up("red_hi"));
        b.add(new Box("cp2_b", 12, 0, 10.1f, 14, 1.1f, 11.2f).all("white"));
        b.add(new Box("cp3", 2, 0, 6.5f, 3.5f, 0.9f, 8).all("white").up("offwhite"));
        write("medicament", model(b, -1, 2, "medicament_etiquette"));
    }

    /** Pot de vitamines orange à couvercle jaune, avec des gélules colorées. */
    static void vitamines() throws Exception {
        List<Box> b = new ArrayList<>();
        b.add(new Box("pot_a", 4, 0, 5, 12, 7.5f, 11).all("orange").up("orange_hi").down("amber_dk"));
        b.add(new Box("pot_b", 5, 0, 4, 11, 7.5f, 12).all("orange").up("orange_hi").down("amber_dk"));
        b.add(new Box("etiquette_n", 4.8f, 1, 3.9f, 11.2f, 6.5f, 4).all("white").side("north", "#1"));
        b.add(new Box("etiquette_s", 4.8f, 1, 12, 11.2f, 6.5f, 12.1f).all("white").side("south", "#1"));
        // couvercle jaune à rebord
        b.add(new Box("couvercle_bas", 4.3f, 7.5f, 4.3f, 11.7f, 8.4f, 11.7f).all("yellow").down("orange"));
        b.add(new Box("couvercle", 4.6f, 8.4f, 4.6f, 11.4f, 9.6f, 11.4f).all("yellow").up("yellow"));
        b.add(new Box("couvercle_haut", 5.2f, 9.6f, 5.2f, 10.8f, 9.8f, 10.8f).all("orange_hi"));
        // gélules posées
        b.add(new Box("gel1_a", 12.3f, 0, 5.5f, 14.3f, 1.1f, 6.6f).all("yellow"));
        b.add(new Box("gel1_b", 12.3f, 0, 6.6f, 14.3f, 1.1f, 7.7f).all("orange"));
        b.add(new Box("gel2_a", 12.6f, 0, 8.6f, 14.6f, 1.1f, 9.7f).all("green"));
        b.add(new Box("gel2_b", 12.6f, 0, 9.7f, 14.6f, 1.1f, 10.8f).all("white"));
        b.add(new Box("gel3_a", 1.4f, 0, 7, 3.4f, 1.1f, 8.1f).all("pink"));
        b.add(new Box("gel3_b", 1.4f, 0, 8.1f, 3.4f, 1.1f, 9.2f).all("white"));
        write("vitamines", model(b, -0.3f, 3, "vitamines_etiquette"));
    }

    /** Flacon de vaccin (verre, capsule bleue) et seringue posée devant. */
    static void vaccin() throws Exception {
        List<Box> b = new ArrayList<>();
        // fiole : liquide en bas, épaule en verre clair
        b.add(new Box("fiole_a", 2.8f, 0, 5.8f, 7.8f, 4.6f, 10.2f).all("cyan").up("glass").down("gray"));
        b.add(new Box("fiole_b", 3.3f, 0, 5.3f, 7.3f, 4.6f, 10.7f).all("cyan").up("glass").down("gray"));
        b.add(new Box("verre_a", 2.8f, 4.6f, 5.8f, 7.8f, 6.8f, 10.2f).all("glass"));
        b.add(new Box("verre_b", 3.3f, 4.6f, 5.3f, 7.3f, 6.8f, 10.7f).all("glass"));
        b.add(new Box("etiquette_n", 3.2f, 0.8f, 5.2f, 7.4f, 4.3f, 5.3f).all("white").side("north", "#1"));
        b.add(new Box("etiquette_s", 3.2f, 0.8f, 10.7f, 7.4f, 4.3f, 10.8f).all("white").side("south", "#1"));
        b.add(new Box("col", 3.9f, 6.8f, 6.4f, 6.7f, 7.5f, 9.6f).all("glass"));
        b.add(new Box("capsule", 3.6f, 7.5f, 6.1f, 7f, 8.9f, 9.9f).all("blue").up("blue_dk").down("silver"));
        b.add(new Box("bouchon", 4.4f, 8.9f, 6.9f, 6.2f, 9.3f, 9.1f).all("black"));
        // seringue couchée devant
        b.add(new Box("corps", 8.4f, 0, 11.4f, 13.6f, 2, 13.4f).all("offwhite").up("cyan").side("north", "glass").side("south", "glass"));
        b.add(new Box("graduations", 8.4f, 2, 12.1f, 13.6f, 2.15f, 12.7f).all("gray_dk"));
        b.add(new Box("collerette", 13.6f, 0, 10.6f, 14.2f, 2.6f, 14.2f).all("silver"));
        b.add(new Box("piston", 14.2f, 0.5f, 11.9f, 16.4f, 1.5f, 12.9f).all("white"));
        b.add(new Box("poussoir", 16.4f, 0, 11.1f, 17, 2, 13.7f).all("blue").up("cyan"));
        b.add(new Box("embout", 7.6f, 0.4f, 11.9f, 8.4f, 1.6f, 12.9f).all("silver"));
        b.add(new Box("aiguille", 4.6f, 0.9f, 12.2f, 7.6f, 1.1f, 12.6f).all("silver"));
        write("vaccin", model(b, -2, 3.2f, "vaccin_etiquette"));
    }

    public static void main(String[] a) throws Exception {
        atlas();
        labelMedicament();
        labelVitamines();
        labelVaccin();
        medicament();
        vitamines();
        vaccin();
        System.out.println("Textures et modèles de santé générés.");
    }
}
