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
 * Génère les textures et les modèles 3D des items (défibrillateur, trousse, bandage, tablette).
 * Usage depuis la racine du projet : java tools/GenAssets.java
 * Les UV d'un modèle d'item Minecraft vont de 0 à 16 quelle que soit la taille de la texture.
 */
public class GenAssets {
    static final File TEX = new File("src/main/resources/assets/minenorthsecours/textures/item");
    static final File MODELS = new File("src/main/resources/assets/minenorthsecours/models/item");
    static final String NS = "minenorthsecours:item/";

    // ---------------------------------------------------------------- atlas de couleurs (cases de 8x8 px sur 64x64)
    static final Map<String, Integer> CELL_COLOR = new LinkedHashMap<>();
    static final List<String> CELLS = new ArrayList<>();

    static void cell(String name, int rgb) { CELLS.add(name); CELL_COLOR.put(name, rgb); }

    static {
        cell("red", 0xC62828); cell("red_hi", 0xE24A4A); cell("red_dk", 0x8E1B1B);
        cell("white", 0xF4F4F4); cell("offwhite", 0xDADFE6); cell("silver", 0xB8BEC8);
        cell("gray", 0x8A9099); cell("gray_dk", 0x4A4F57); cell("black", 0x1E2024);
        cell("yellow", 0xF5C400); cell("green", 0x2EC46A); cell("blue", 0x2B7DE9);
        cell("orange", 0xF28C28); cell("beige", 0xE8D8B0); cell("navy", 0x161048); cell("cyan", 0x20AAEB);
    }

    static float[] uv(String cell) {
        int i = CELLS.indexOf(cell);
        if (i < 0) throw new IllegalArgumentException("case inconnue : " + cell);
        int col = i % 8, row = i / 8;
        // 64 px = 16 unités UV : une case de 8 px = 2 unités, resserrée de 0,25 pour éviter les débordements.
        return new float[]{col * 2 + 0.25f, row * 2 + 0.25f, col * 2 + 1.75f, row * 2 + 1.75f};
    }

    static void atlas() throws Exception {
        BufferedImage im = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(7);
        for (int i = 0; i < CELLS.size(); i++) {
            int base = CELL_COLOR.get(CELLS.get(i));
            for (int x = 0; x < 8; x++) for (int y = 0; y < 8; y++) {
                int n = r.nextInt(9) - 4;
                int rr = clamp(((base >> 16) & 255) + n), gg = clamp(((base >> 8) & 255) + n), bb = clamp((base & 255) + n);
                im.setRGB((i % 8) * 8 + x, (i / 8) * 8 + y, 0xFF000000 | (rr << 16) | (gg << 8) | bb);
            }
        }
        save(im, "secours_atlas.png");
    }

    static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

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

    // ---------------------------------------------------------------- textures de faces
    /** Face avant du défibrillateur (12 x 10 unités, 4 px par unité). */
    static void defibFace() throws Exception {
        BufferedImage im = new BufferedImage(48, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g2(im);
        fill(g, 0x9B1C1C, 0, 0, 48, 40);
        fill(g, 0xD13535, 2, 2, 44, 36);
        fill(g, 0xE24A4A, 2, 2, 44, 1);
        // écran : cadre noir, fond vert sombre, tracé ECG, ligne de données
        fill(g, 0x1E2024, 3, 5, 24, 17);
        fill(g, 0x0B2A1A, 4, 6, 22, 15);
        g.setColor(new Color(0x3CFF6B));
        int[][] ecg = {{5, 15}, {10, 15}, {12, 15}, {13, 10}, {15, 19}, {17, 15}, {20, 15}, {21, 13}, {22, 15}, {25, 15}};
        for (int i = 0; i + 1 < ecg.length; i++) g.drawLine(ecg[i][0], ecg[i][1], ecg[i + 1][0], ecg[i + 1][1]);
        fill(g, 0x2EC46A, 5, 7, 8, 2);
        fill(g, 0x6CFF9A, 15, 7, 3, 2);
        // haut-parleur
        fill(g, 0x8E1B1B, 13, 24, 14, 13);
        for (int x = 15; x < 26; x += 3) for (int y = 26; y < 36; y += 3) fill(g, 0x3A0C0C, x, y, 2, 2);
        // badge croix
        fill(g, 0xF4F4F4, 4, 26, 8, 8);
        fill(g, 0xC62828, 7, 27, 2, 6);
        fill(g, 0xC62828, 5, 29, 6, 2);
        // bouton de choc : couronne sombre sous le relief
        fill(g, 0x5C1010, 27, 18, 13, 13);
        fill(g, 0x8E1B1B, 28, 19, 11, 11);
        // éclair jaune
        g.setColor(new Color(0xF5C400));
        g.fillPolygon(new int[]{35, 31, 34, 32, 38, 35, 37}, new int[]{3, 9, 9, 15, 8, 8, 3}, 7);
        // voyant d'état
        fill(g, 0x5C1010, 39, 7, 7, 6);
        fill(g, 0x2EC46A, 41, 8, 3, 3);
        g.dispose();
        save(im, "defibrillateur_face.png");
    }

    /** Face de la trousse (12 x 8,5 unités). */
    static void kitFace() throws Exception {
        BufferedImage im = new BufferedImage(48, 34, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g2(im);
        fill(g, 0x8E1B1B, 0, 0, 48, 34);
        fill(g, 0xC62828, 2, 2, 44, 30);
        fill(g, 0xE24A4A, 2, 2, 44, 1);
        // croix blanche avec ombre portée
        fill(g, 0x8E1B1B, 21, 7, 8, 24);
        fill(g, 0x8E1B1B, 13, 15, 24, 8);
        fill(g, 0xF4F4F4, 20, 6, 8, 24);
        fill(g, 0xF4F4F4, 12, 14, 24, 8);
        fill(g, 0xDADFE6, 20, 6, 8, 1);
        fill(g, 0xDADFE6, 12, 14, 1, 8);
        // rivets
        for (int[] p : new int[][]{{4, 4}, {42, 4}, {4, 28}, {42, 28}}) fill(g, 0xB8BEC8, p[0], p[1], 2, 2);
        g.dispose();
        save(im, "trousse_face.png");
    }

    /** Écran de la tablette (11 x 14 unités). */
    static void tabletScreen() throws Exception {
        BufferedImage im = new BufferedImage(44, 56, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g2(im);
        fill(g, 0x1E2024, 0, 0, 44, 56);                 // cadre
        fill(g, 0x161048, 3, 3, 38, 46);                 // écran
        fill(g, 0x20AAEB, 3, 3, 38, 7);                  // bandeau
        fill(g, 0xF4F4F4, 5, 4, 5, 5);                   // logo croix
        fill(g, 0xC62828, 7, 5, 1, 3);
        fill(g, 0xC62828, 6, 6, 3, 1);
        fill(g, 0xCFE3FF, 13, 5, 16, 2);
        fill(g, 0x8FA8E0, 13, 8, 10, 1);
        // cartes d'alerte
        int[] accents = {0xC62828, 0xF5C400, 0xC83CF0};
        for (int i = 0; i < 3; i++) {
            int y = 12 + i * 10;
            fill(g, 0x0E0A34, 5, y, 34, 8);
            fill(g, accents[i], 5, y, 2, 8);
            fill(g, 0xCFE3FF, 9, y + 2, 18, 1);
            fill(g, 0x8FA8E0, 9, y + 5, 12, 1);
            fill(g, accents[i], 31, y + 2, 6, 4);
        }
        // bouton J'Y VAIS
        fill(g, 0x1E9E52, 6, 42, 32, 5);
        fill(g, 0x6CFF9A, 18, 44, 8, 1);
        // bouton d'accueil et caméra
        fill(g, 0x4A4F57, 18, 51, 8, 3);
        fill(g, 0x2B7DE9, 20, 1, 4, 1);
        g.dispose();
        save(im, "tablette_ecran.png");
    }

    // ---------------------------------------------------------------- constructeur de modèles
    static final String[] FACES = {"north", "east", "south", "west", "up", "down"};

    static class Box {
        final String name; final float[] from, to; final String[] face = new String[6];
        int rotY = 0;
        Box(String name, float x1, float y1, float z1, float x2, float y2, float z2) {
            this.name = name; this.from = new float[]{x1, y1, z1}; this.to = new float[]{x2, y2, z2};
        }
        Box all(String c) { java.util.Arrays.fill(face, c); return this; }
        Box side(String f, String c) { for (int i = 0; i < 6; i++) if (FACES[i].equals(f)) face[i] = c; return this; }
        Box sides(String c) { for (String f : new String[]{"north", "east", "south", "west"}) side(f, c); return this; }
        Box up(String c) { return side("up", c); }
        Box down(String c) { return side("down", c); }
        Box rotY(int a) { rotY = a; return this; }
    }

    static String f(float v) {
        return v == Math.rint(v) ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "");
    }

    /** custom : textures de faces personnalisées, nom de case -> variable de texture (ex. "#1"). */
    static String model(List<Box> boxes, float yShift, Map<String, String> textures, String displayJson, String customPrefix) {
        StringBuilder sb = new StringBuilder("{\n  \"textures\": {\n");
        int n = 0;
        for (Map.Entry<String, String> t : textures.entrySet()) {
            sb.append("    \"").append(t.getKey()).append("\": \"").append(t.getValue()).append("\"").append(++n < textures.size() ? ",\n" : "\n");
        }
        sb.append("  },\n  \"elements\": [\n");
        for (int i = 0; i < boxes.size(); i++) {
            Box b = boxes.get(i);
            sb.append("    {\"name\": \"").append(b.name).append("\", \"from\": [")
              .append(f(b.from[0])).append(", ").append(f(b.from[1] + yShift)).append(", ").append(f(b.from[2])).append("], \"to\": [")
              .append(f(b.to[0])).append(", ").append(f(b.to[1] + yShift)).append(", ").append(f(b.to[2])).append("]");
            if (b.rotY != 0) sb.append(", \"rotation\": {\"angle\": ").append(b.rotY).append(", \"axis\": \"y\", \"origin\": [8, 8, 8]}");
            sb.append(",\n     \"faces\": {");
            for (int k = 0; k < 6; k++) {
                String c = b.face[k];
                if (c == null) c = "gray_dk";
                sb.append(k == 0 ? "" : ", ").append("\"").append(FACES[k]).append("\": ");
                if (c.startsWith("#")) sb.append("{\"uv\": [0, 0, 16, 16], \"texture\": \"").append(c).append("\"}");
                else { float[] u = uv(c); sb.append("{\"uv\": [").append(f(u[0])).append(", ").append(f(u[1])).append(", ").append(f(u[2])).append(", ").append(f(u[3])).append("], \"texture\": \"#0\"}"); }
            }
            sb.append("}}").append(i + 1 < boxes.size() ? ",\n" : "\n");
        }
        sb.append("  ],\n  \"display\": ").append(displayJson).append("\n}\n");
        return sb.toString();
    }

    static String display(float gui, float hand, float ground) { return display(gui, hand, ground, 45, 225, hand); }

    /** fpRight / fpLeft : rotation Y en première personne ; fpScale : taille en première personne. */
    static String display(float gui, float hand, float ground, int fpRight, int fpLeft, float fpScale) {
        return "{\n"
            + "    \"gui\": {\"rotation\": [30, 225, 0], \"translation\": [0, 0, 0], \"scale\": [" + f(gui) + ", " + f(gui) + ", " + f(gui) + "]},\n"
            + "    \"ground\": {\"rotation\": [0, 0, 0], \"translation\": [0, 3, 0], \"scale\": [" + f(ground) + ", " + f(ground) + ", " + f(ground) + "]},\n"
            + "    \"fixed\": {\"rotation\": [0, 180, 0], \"translation\": [0, 0, 0], \"scale\": [0.9, 0.9, 0.9]},\n"
            + "    \"head\": {\"rotation\": [0, 180, 0], \"translation\": [0, 13, 7], \"scale\": [1, 1, 1]},\n"
            + "    \"thirdperson_righthand\": {\"rotation\": [75, 45, 0], \"translation\": [0, 2.5, 0], \"scale\": [" + f(hand) + ", " + f(hand) + ", " + f(hand) + "]},\n"
            + "    \"thirdperson_lefthand\": {\"rotation\": [75, 45, 0], \"translation\": [0, 2.5, 0], \"scale\": [" + f(hand) + ", " + f(hand) + ", " + f(hand) + "]},\n"
            + "    \"firstperson_righthand\": {\"rotation\": [0, " + fpRight + ", 0], \"translation\": [0, 1, 0], \"scale\": [" + f(fpScale) + ", " + f(fpScale) + ", " + f(fpScale) + "]},\n"
            + "    \"firstperson_lefthand\": {\"rotation\": [0, " + fpLeft + ", 0], \"translation\": [0, 1, 0], \"scale\": [" + f(fpScale) + ", " + f(fpScale) + ", " + f(fpScale) + "]}\n  }";
    }

    static void write(String name, String json) throws Exception {
        MODELS.mkdirs();
        Files.writeString(new File(MODELS, name + ".json").toPath(), json, StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- les 4 modèles
    static void defibrillateur() throws Exception {
        List<Box> b = new ArrayList<>();
        // boîtier : face avant et arrière avec la texture dessinée (les deux côtés se lisent en main comme dans l'inventaire)
        b.add(new Box("boitier", 2, 1, 5.5f, 14, 11, 10.5f).sides("red").side("north", "#1").side("south", "#1").up("red_hi").down("red_dk"));
        b.add(new Box("bride", 1.5f, 10, 5, 14.5f, 11.8f, 11).all("gray_dk").up("silver"));
        b.add(new Box("pied_g", 2.5f, 0, 6, 5, 1, 10).all("black"));
        b.add(new Box("pied_d", 11, 0, 6, 13.5f, 1, 10).all("black"));
        // poignée de transport
        b.add(new Box("poignee_g", 4, 11.8f, 7, 5.5f, 14.5f, 9).all("gray_dk"));
        b.add(new Box("poignee_d", 10.5f, 11.8f, 7, 12, 14.5f, 9).all("gray_dk"));
        b.add(new Box("poignee_barre", 4, 14.5f, 6.8f, 12, 15.8f, 9.2f).all("black").up("gray"));
        // loquets latéraux
        b.add(new Box("loquet_g", 1.4f, 5, 6.5f, 2, 8.5f, 9.5f).all("silver"));
        b.add(new Box("loquet_d", 14, 5, 6.5f, 14.6f, 8.5f, 9.5f).all("silver"));
        // électrodes (patchs jaunes) clipsées sur les côtés, reliées par un câble
        b.add(new Box("patch_g", 0, 2.5f, 6, 1.4f, 9.5f, 10).all("yellow").side("west", "orange"));
        b.add(new Box("patch_d", 14.6f, 2.5f, 6, 16, 9.5f, 10).all("yellow").side("east", "orange"));
        b.add(new Box("cable_g", 1.4f, 3.5f, 7.5f, 2, 4.5f, 8.5f).all("black"));
        b.add(new Box("cable_d", 14, 3.5f, 7.5f, 14.6f, 4.5f, 8.5f).all("black"));
        // bouton de choc et voyant en relief (côté sud ; symétrique côté nord)
        b.add(new Box("choc_s", 9, 3.5f, 10.5f, 12, 6.5f, 11.1f).all("red_hi").up("red").side("south", "red_hi"));
        b.add(new Box("choc_n", 4, 3.5f, 4.9f, 7, 6.5f, 5.5f).all("red_hi").up("red").side("north", "red_hi"));
        b.add(new Box("voyant_s", 12, 8, 10.5f, 13.5f, 9, 10.8f).all("green"));
        b.add(new Box("voyant_n", 2.5f, 8, 5.2f, 4, 9, 5.5f).all("green"));
        // antenne de signal
        b.add(new Box("antenne", 12, 11.8f, 9, 13, 14, 9.8f).all("gray_dk"));
        b.add(new Box("antenne_bout", 12, 14, 9, 13, 14.6f, 9.8f).all("red"));
        Map<String, String> t = new LinkedHashMap<>();
        t.put("0", NS + "secours_atlas"); t.put("1", NS + "defibrillateur_face"); t.put("particle", NS + "secours_atlas");
        write("defibrillateur", model(b, 0, t, display(1.15f, 0.62f, 0.6f), ""));
    }

    static void trousse() throws Exception {
        List<Box> b = new ArrayList<>();
        b.add(new Box("caisse", 2, 0.5f, 5, 14, 9, 11).sides("red").side("north", "#1").side("south", "#1").up("red_hi").down("red_dk"));
        b.add(new Box("couvercle", 1.8f, 9, 4.8f, 14.2f, 10.6f, 11.2f).all("red_hi").down("red_dk"));
        b.add(new Box("joint", 1.9f, 8.6f, 4.9f, 14.1f, 9, 11.1f).all("red_dk"));
        // coins renforcés
        for (int i = 0; i < 4; i++) {
            float x = i % 2 == 0 ? 1.7f : 13;
            float z = i < 2 ? 4.7f : 10.2f;
            b.add(new Box("coin" + i, x, 0.3f, z, x + 1.3f, 10.8f, z + 1.1f).all("gray").up("silver"));
        }
        // poignée
        b.add(new Box("poignee_g", 5.5f, 10.6f, 7.3f, 6.5f, 12.4f, 8.7f).all("gray_dk"));
        b.add(new Box("poignee_d", 9.5f, 10.6f, 7.3f, 10.5f, 12.4f, 8.7f).all("gray_dk"));
        b.add(new Box("poignee_barre", 5.5f, 12.4f, 7.1f, 10.5f, 13.4f, 8.9f).all("black").up("gray"));
        // loquets
        b.add(new Box("loquet_s1", 4.3f, 7.5f, 11, 5.5f, 10, 11.4f).all("silver"));
        b.add(new Box("loquet_s2", 10.5f, 7.5f, 11, 11.7f, 10, 11.4f).all("silver"));
        b.add(new Box("loquet_n1", 4.3f, 7.5f, 4.6f, 5.5f, 10, 5).all("silver"));
        b.add(new Box("loquet_n2", 10.5f, 7.5f, 4.6f, 11.7f, 10, 5).all("silver"));
        // bandage qui dépasse sur le côté
        b.add(new Box("rouleau", 14.2f, 2.5f, 6.5f, 15.6f, 6.5f, 9.5f).all("offwhite").side("east", "white"));
        b.add(new Box("rouleau_croix", 14.2f, 4, 6.5f, 15.7f, 5, 9.5f).all("red"));
        Map<String, String> t = new LinkedHashMap<>();
        t.put("0", NS + "secours_atlas"); t.put("1", NS + "trousse_face"); t.put("particle", NS + "secours_atlas");
        write("trousse_soins", model(b, 1.2f, t, display(1.15f, 0.62f, 0.6f), ""));
    }

    static void bandage() throws Exception {
        List<Box> b = new ArrayList<>();
        // rouleau octogonal : deux prismes dont un tourné de 45 degrés
        b.add(new Box("rouleau_a", 4.5f, 0.5f, 4.5f, 11.5f, 7.5f, 11.5f).all("offwhite").up("white").down("silver"));
        b.add(new Box("rouleau_b", 4.5f, 0.5f, 4.5f, 11.5f, 7.5f, 11.5f).all("offwhite").up("white").down("silver").rotY(45));
        b.add(new Box("bande_a", 4.4f, 3, 4.4f, 11.6f, 4.4f, 11.6f).all("red"));
        b.add(new Box("bande_b", 4.4f, 3, 4.4f, 11.6f, 4.4f, 11.6f).all("red").rotY(45));
        // âme en carton au centre du dessus
        b.add(new Box("ame", 6.8f, 7.5f, 6.8f, 9.2f, 7.7f, 9.2f).all("beige"));
        b.add(new Box("ame_trou", 7.3f, 7.7f, 7.3f, 8.7f, 7.8f, 8.7f).all("gray_dk"));
        // bande qui se déroule vers l'avant, avec son sparadrap
        b.add(new Box("pan", 5.5f, 0.5f, 11.5f, 10.5f, 0.9f, 15).all("offwhite").up("white"));
        b.add(new Box("pan_bord", 5.5f, 0.9f, 14.5f, 10.5f, 1.1f, 15).all("silver"));
        b.add(new Box("epingle", 7, 1.1f, 12.8f, 9, 1.8f, 13.4f).all("silver"));
        // croix rouge sur le côté
        b.add(new Box("croix_v", 7.3f, 4.6f, 11.55f, 8.7f, 7, 11.7f).all("red"));
        b.add(new Box("croix_h", 6.1f, 5.4f, 11.55f, 9.9f, 6.2f, 11.7f).all("red"));
        Map<String, String> t = new LinkedHashMap<>();
        t.put("0", NS + "secours_atlas"); t.put("particle", NS + "secours_atlas");
        write("bandage", model(b, 3.5f, t, display(1.25f, 0.7f, 0.65f), ""));
    }

    static void tablette() throws Exception {
        List<Box> b = new ArrayList<>();
        b.add(new Box("corps", 2.5f, 1, 7.2f, 13.5f, 15, 8.8f).all("black").side("north", "#1").side("south", "#1"));
        b.add(new Box("liseré", 2.3f, 0.8f, 7.4f, 13.7f, 15.2f, 8.6f).all("silver"));
        // coque de protection orange aux quatre coins
        for (int i = 0; i < 4; i++) {
            float x = i % 2 == 0 ? 2.1f : 12;
            float y = i < 2 ? 0.6f : 12.9f;
            b.add(new Box("coin" + i, x, y, 7, x + 1.9f, y + 2.5f, 9).all("orange").side("north", "red").side("south", "red"));
        }
        // caméra
        b.add(new Box("camera_s", 7.5f, 13.7f, 8.8f, 8.5f, 14.5f, 9.1f).all("black"));
        b.add(new Box("camera_n", 7.5f, 13.7f, 6.9f, 8.5f, 14.5f, 7.2f).all("black"));
        // gyrophare : base grise, feu rouge et feu bleu
        b.add(new Box("gyro_base", 3, 15, 7.4f, 13, 15.5f, 8.6f).all("gray_dk"));
        b.add(new Box("gyro_rouge", 3.5f, 15.5f, 7.6f, 7, 16.8f, 8.4f).all("red").up("red_hi"));
        b.add(new Box("gyro_bleu", 9, 15.5f, 7.6f, 12.5f, 16.8f, 8.4f).all("blue").up("cyan"));
        // antenne et pied d'appui
        b.add(new Box("antenne", 12.6f, 15, 7.8f, 13.1f, 17.2f, 8.2f).all("gray_dk"));
        b.add(new Box("pied", 5, 0, 7.4f, 11, 1, 8.6f).all("gray_dk"));
        Map<String, String> t = new LinkedHashMap<>();
        t.put("0", NS + "secours_atlas"); t.put("1", NS + "tablette_ecran"); t.put("particle", NS + "secours_atlas");
        write("tablette_secours", model(b, -0.6f, t, display(0.95f, 0.6f, 0.55f, -30, -30, 0.38f), ""));
    }

    public static void main(String[] a) throws Exception {
        atlas();
        defibFace();
        kitFace();
        tabletScreen();
        defibrillateur();
        trousse();
        bandage();
        tablette();
        for (String old : new String[]{"bandage.png", "trousse_soins.png", "defibrillateur.png", "tablette_secours.png"}) new File(TEX, old).delete();
        System.out.println("Textures et modèles générés.");
    }
}
