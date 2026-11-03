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
        // matériel pompier (ajouté à la fin : les UV des cases existantes ne bougent pas)
        cell("visor", 0x9ED6EE); cell("brass", 0xC9A227); cell("steel", 0xD5DAE0); cell("rubber", 0x2A2C31);
        cell("yellow_dk", 0xC79A00); cell("chrome", 0xEEF2F6);
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
        String axis = "y"; float ox = 8, oy = 8, oz = 8;
        Box(String name, float x1, float y1, float z1, float x2, float y2, float z2) {
            this.name = name; this.from = new float[]{x1, y1, z1}; this.to = new float[]{x2, y2, z2};
        }
        Box all(String c) { java.util.Arrays.fill(face, c); return this; }
        Box side(String f, String c) { for (int i = 0; i < 6; i++) if (FACES[i].equals(f)) face[i] = c; return this; }
        Box sides(String c) { for (String f : new String[]{"north", "east", "south", "west"}) side(f, c); return this; }
        Box up(String c) { return side("up", c); }
        Box down(String c) { return side("down", c); }
        Box rotY(int a) { rotY = a; return this; }
        /** Rotation sur un axe quelconque (Minecraft : -45 à 45 par pas de 22,5). */
        Box rot(String ax, int a, float x, float y, float z) { axis = ax; rotY = a; ox = x; oy = y; oz = z; return this; }
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
            if (b.rotY != 0) sb.append(", \"rotation\": {\"angle\": ").append(b.rotY).append(", \"axis\": \"").append(b.axis)
                    .append("\", \"origin\": [").append(f(b.ox)).append(", ").append(f(b.oy)).append(", ").append(f(b.oz)).append("]}");
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
        b.add(new Box("lisere", 2.3f, 0.8f, 7.4f, 13.7f, 15.2f, 8.6f).all("silver"));
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

    // ---------------------------------------------------------------- matériel des pompiers
    static final File BLOCK_MODELS = new File("src/main/resources/assets/minenorthsecours/models/block");

    static void writeBlock(String name, String json) throws Exception {
        BLOCK_MODELS.mkdirs();
        Files.writeString(new File(BLOCK_MODELS, name + ".json").toPath(), json, StandardCharsets.UTF_8);
    }

    static Map<String, String> atlasOnly() {
        Map<String, String> t = new LinkedHashMap<>();
        t.put("0", NS + "secours_atlas"); t.put("particle", NS + "secours_atlas");
        return t;
    }

    /** Outil long tenu comme une hache vanilla : modèle vertical incliné à 45 degrés (comme les sprites d'outils). */
    static List<Box> diagonal(List<Box> b) { for (Box x : b) x.rot("z", -45, 8, 8, 8); return b; }

    /** Affichage d'outil tenu en main (valeurs de item/handheld), avec un léger relief dans l'inventaire. */
    static String handheld() {
        return "{\n"
            + "    \"gui\": {\"rotation\": [0, -30, 0], \"translation\": [0, 0, 0], \"scale\": [0.95, 0.95, 0.95]},\n"
            + "    \"ground\": {\"rotation\": [0, 0, 0], \"translation\": [0, 2, 0], \"scale\": [0.5, 0.5, 0.5]},\n"
            + "    \"fixed\": {\"rotation\": [0, 180, 0], \"translation\": [0, 0, 0], \"scale\": [1, 1, 1]},\n"
            + "    \"thirdperson_righthand\": {\"rotation\": [0, -90, 55], \"translation\": [0, 4, 0.5], \"scale\": [0.85, 0.85, 0.85]},\n"
            + "    \"thirdperson_lefthand\": {\"rotation\": [0, 90, -55], \"translation\": [0, 4, 0.5], \"scale\": [0.85, 0.85, 0.85]},\n"
            + "    \"firstperson_righthand\": {\"rotation\": [0, -90, 25], \"translation\": [1.13, 3.2, 1.13], \"scale\": [0.68, 0.68, 0.68]},\n"
            + "    \"firstperson_lefthand\": {\"rotation\": [0, 90, -25], \"translation\": [1.13, 3.2, 1.13], \"scale\": [0.68, 0.68, 0.68]}\n  }";
    }

    static void hache() throws Exception {
        List<Box> b = new ArrayList<>();
        b.add(new Box("manche", 7.3f, 1, 7.3f, 8.7f, 13, 8.7f).all("yellow").up("yellow_dk").down("yellow_dk"));
        b.add(new Box("poignee", 7, 0.6f, 7, 9, 5, 9).all("rubber"));
        b.add(new Box("pommeau", 6.8f, 0, 6.8f, 9.2f, 0.8f, 9.2f).all("black"));
        b.add(new Box("bague1", 7.15f, 7, 7.15f, 8.85f, 7.6f, 8.85f).all("black"));
        b.add(new Box("bague2", 7.15f, 9, 7.15f, 8.85f, 9.6f, 8.85f).all("black"));
        b.add(new Box("tete", 6.4f, 12, 6.9f, 9.6f, 15.6f, 9.1f).all("red").up("red_hi").down("red_dk"));
        b.add(new Box("lame", 9.6f, 11.4f, 7.3f, 12.6f, 15.8f, 8.7f).all("red").up("red_hi").down("red_dk"));
        b.add(new Box("lame_bout", 12.6f, 10.8f, 7.6f, 13.8f, 16.3f, 8.4f).all("steel").side("east", "chrome"));
        b.add(new Box("pic", 3.4f, 13, 7.5f, 6.4f, 14.6f, 8.5f).all("red").up("red_hi"));
        b.add(new Box("pic_pointe", 2.2f, 13.3f, 7.7f, 3.4f, 14.3f, 8.3f).all("steel"));
        b.add(new Box("rivet_s", 7.5f, 13.2f, 9.1f, 8.5f, 14.2f, 9.3f).all("silver"));
        b.add(new Box("rivet_n", 7.5f, 13.2f, 6.7f, 8.5f, 14.2f, 6.9f).all("silver"));
        write("hache_pompier", model(diagonal(b), 0, atlasOnly(), handheld(), ""));
    }

    static void halligan() throws Exception {
        List<Box> b = new ArrayList<>();
        b.add(new Box("barre", 7.35f, 1.6f, 7.35f, 8.65f, 13.4f, 8.65f).all("steel").up("chrome"));
        b.add(new Box("grip", 7.15f, 5, 7.15f, 8.85f, 9, 8.85f).all("gray_dk"));
        b.add(new Box("bande_rouge", 7.2f, 10, 7.2f, 8.8f, 10.8f, 8.8f).all("red"));
        b.add(new Box("bande_jaune", 7.2f, 10.8f, 7.2f, 8.8f, 11.6f, 8.8f).all("yellow"));
        b.add(new Box("collier", 6.9f, 13, 6.9f, 9.1f, 15.4f, 9.1f).all("gray").up("silver"));
        b.add(new Box("herminette", 9.1f, 13.6f, 6.6f, 12.4f, 14.6f, 9.4f).all("steel").up("chrome"));
        b.add(new Box("herminette_bout", 12.4f, 13.4f, 6.8f, 13.4f, 14.2f, 9.2f).all("chrome"));
        b.add(new Box("pic", 3.6f, 14, 7.6f, 6.9f, 15, 8.4f).all("steel"));
        b.add(new Box("pic_pointe", 2.4f, 14.2f, 7.8f, 3.6f, 14.8f, 8.2f).all("chrome"));
        b.add(new Box("fourche_base", 7, 1, 7, 9, 2, 9).all("gray"));
        b.add(new Box("dent_g", 6.4f, -1.2f, 7.4f, 7.5f, 1, 8.6f).all("steel").down("chrome"));
        b.add(new Box("dent_d", 8.5f, -1.2f, 7.4f, 9.6f, 1, 8.6f).all("steel").down("chrome"));
        write("halligan", model(diagonal(b), 0, atlasOnly(), handheld(), ""));
    }

    static void lance() throws Exception {
        List<Box> b = new ArrayList<>();
        b.add(new Box("raccord", 6.2f, 0, 6.2f, 9.8f, 2, 9.8f).all("brass").up("yellow").down("yellow_dk"));
        b.add(new Box("raccord_ergot_e", 9.8f, 0.4f, 7.4f, 10.4f, 1.6f, 8.6f).all("brass"));
        b.add(new Box("raccord_ergot_o", 5.6f, 0.4f, 7.4f, 6.2f, 1.6f, 8.6f).all("brass"));
        b.add(new Box("corps", 6.8f, 2, 6.8f, 9.2f, 10, 9.2f).all("silver").up("steel"));
        b.add(new Box("corps_arrondi", 6.5f, 2.5f, 7.2f, 9.5f, 9.5f, 8.8f).all("silver"));
        b.add(new Box("crosse", 4, 3.5f, 7.2f, 6.5f, 7.5f, 8.8f).all("black").side("west", "rubber"));
        b.add(new Box("crosse_bas", 3.6f, 3, 7.3f, 5.2f, 4.2f, 8.7f).all("rubber"));
        b.add(new Box("levier", 9.5f, 7.2f, 7.4f, 11.6f, 8.2f, 8.6f).all("red").up("red_hi"));
        b.add(new Box("levier_bras", 10.6f, 4, 7.4f, 11.6f, 7.2f, 8.6f).all("red"));
        b.add(new Box("reglage", 6.6f, 9.6f, 6.6f, 9.4f, 10.8f, 9.4f).all("gray_dk"));
        b.add(new Box("pare_chocs", 6.3f, 10.8f, 6.3f, 9.7f, 13, 9.7f).all("rubber").up("black"));
        b.add(new Box("pare_chocs_bande", 6.25f, 11.6f, 6.25f, 9.75f, 12.2f, 9.75f).all("red"));
        b.add(new Box("embout", 7.1f, 13, 7.1f, 8.9f, 14, 8.9f).all("chrome"));
        b.add(new Box("orifice", 7.6f, 14, 7.6f, 8.4f, 14.05f, 8.4f).all("black"));
        write("lance_incendie", model(diagonal(b), 0, atlasOnly(), handheld(), ""));
    }

    /** Écran de la caméra thermique : image en fausses couleurs (fond violet, foyer blanc-jaune au centre). */
    static void cameraScreen() throws Exception {
        BufferedImage im = new BufferedImage(40, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g2(im);
        fill(g, 0x1E2024, 0, 0, 40, 32);
        for (int y = 3; y < 25; y++) for (int x = 3; x < 37; x++) {
            double dx = (x - 20) / 9.0, dy = (y - 17) / 8.0;
            double heat = Math.max(0, 1.2 - Math.sqrt(dx * dx + dy * dy * 0.7)) + (y > 18 ? 0.15 : 0);
            int c = heat > 1.0 ? 0xFFF6C0 : heat > 0.75 ? 0xFFC83A : heat > 0.5 ? 0xFF6A10 : heat > 0.25 ? 0xC0205A : 0x2A0E5A;
            im.setRGB(x, y, 0xFF000000 | c);
        }
        fill(g, 0x20AAEB, 3, 26, 34, 1);
        fill(g, 0xCFE3FF, 4, 28, 10, 2);
        fill(g, 0xFF6A10, 28, 28, 8, 2);
        fill(g, 0xF4F4F4, 19, 10, 2, 1);
        fill(g, 0xF4F4F4, 19, 12, 2, 1);
        g.dispose();
        save(im, "camera_ecran.png");
    }

    static void camera() throws Exception {
        List<Box> b = new ArrayList<>();
        b.add(new Box("corps", 3.5f, 4, 5.5f, 12.5f, 12, 10.5f).all("yellow").side("south", "#1").down("yellow_dk"));
        b.add(new Box("corps_arrondi", 3, 4.5f, 6, 13, 11.5f, 10).all("yellow_dk"));
        b.add(new Box("dessus_arrondi", 4, 12, 6, 12, 12.5f, 10).all("yellow"));
        for (int i = 0; i < 4; i++) {
            float x = i % 2 == 0 ? 2.8f : 12;
            float y = i < 2 ? 3.6f : 10.6f;
            b.add(new Box("coin" + i, x, y, 5.3f, x + 1.2f, y + 1.8f, 10.7f).all("rubber"));
        }
        b.add(new Box("objectif", 6.2f, 7, 4.2f, 9.8f, 10.6f, 5.5f).all("rubber"));
        b.add(new Box("objectif_rond", 6.7f, 6.5f, 4.2f, 9.3f, 11.1f, 5.5f).all("rubber"));
        b.add(new Box("lentille", 6.9f, 7.5f, 3.9f, 9.1f, 10.1f, 4.2f).all("navy").side("north", "visor"));
        b.add(new Box("poignee", 6.3f, 0, 6.2f, 9.7f, 4, 9.6f).all("rubber").down("black"));
        b.add(new Box("gachette", 7.2f, 2.2f, 5.6f, 8.8f, 3.6f, 6.2f).all("red"));
        b.add(new Box("bouton", 6.9f, 12.5f, 7.4f, 9.1f, 13, 8.6f).all("red").up("red_hi"));
        b.add(new Box("dragonne", 12.8f, 1, 7.6f, 13.4f, 5, 8.4f).all("black"));
        Map<String, String> t = new LinkedHashMap<>();
        t.put("0", NS + "secours_atlas"); t.put("1", NS + "camera_ecran"); t.put("particle", NS + "secours_atlas");
        write("camera_thermique", model(b, 0.5f, t, display(1.1f, 0.62f, 0.6f), ""));
    }

    /** Face de la pochette de couverture anti-feu : flamme barrée, bande blanche. */
    static void blanketFace() throws Exception {
        BufferedImage im = new BufferedImage(48, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g2(im);
        fill(g, 0x8E1B1B, 0, 0, 48, 40);
        fill(g, 0xC62828, 2, 2, 44, 36);
        fill(g, 0xE24A4A, 2, 2, 44, 1);
        fill(g, 0xF4F4F4, 2, 25, 44, 8);
        for (int x = 5; x < 43; x += 4) fill(g, 0x8A9099, x, 28, 2, 2);
        g.setColor(new Color(0xF5C400));
        g.fillPolygon(new int[]{24, 18, 20, 16, 24, 32, 28, 30}, new int[]{4, 12, 13, 20, 22, 20, 13, 12}, 8);
        g.setColor(new Color(0xF28C28));
        g.fillPolygon(new int[]{24, 21, 24, 27}, new int[]{11, 18, 21, 18}, 4);
        g.setColor(new Color(0xF4F4F4));
        g.setStroke(new BasicStroke(2));
        g.drawLine(14, 5, 34, 22);
        g.dispose();
        save(im, "couverture_face.png");
    }

    static void couverture() throws Exception {
        List<Box> b = new ArrayList<>();
        b.add(new Box("pochette", 2.5f, 1, 5.5f, 13.5f, 12, 10.5f).sides("red").side("north", "#1").side("south", "#1").up("red_hi").down("red_dk"));
        b.add(new Box("bord_g", 2, 1.5f, 6, 2.5f, 11.5f, 10).all("red_dk"));
        b.add(new Box("bord_d", 13.5f, 1.5f, 6, 14, 11.5f, 10).all("red_dk"));
        b.add(new Box("dessus", 3, 12, 6, 13, 12.5f, 10).all("red_hi"));
        b.add(new Box("couture", 2.4f, 11.2f, 5.4f, 13.6f, 11.6f, 10.6f).all("white"));
        b.add(new Box("tirette_g", 4, -1.5f, 7.4f, 6, 1, 8.6f).all("black"));
        b.add(new Box("tirette_d", 10, -1.5f, 7.4f, 12, 1, 8.6f).all("black"));
        b.add(new Box("tirette_g_bout", 3.8f, -2.2f, 7.2f, 6.2f, -1.5f, 8.8f).all("rubber"));
        b.add(new Box("tirette_d_bout", 9.8f, -2.2f, 7.2f, 12.2f, -1.5f, 8.8f).all("rubber"));
        b.add(new Box("attache", 6.5f, 12.5f, 7.4f, 9.5f, 14.5f, 8.6f).all("gray_dk"));
        b.add(new Box("anneau", 7.2f, 14.5f, 7.6f, 8.8f, 15.5f, 8.4f).all("silver"));
        Map<String, String> t = new LinkedHashMap<>();
        t.put("0", NS + "secours_atlas"); t.put("1", NS + "couverture_face"); t.put("particle", NS + "secours_atlas");
        write("couverture_antifeu", model(b, 1.5f, t, display(1.1f, 0.62f, 0.6f), ""));
    }

    /** Masque ARI : visage côté nord, à la taille de la tête (agrandi de 25 % le 02/11, voir plus bas) (la tête occupe 1,6 à 14,4 dans l'affichage « head », comme une citrouille). */
    static void masque() throws Exception {
        List<Box> b = new ArrayList<>();
        b.add(new Box("jupe", 3.2f, 3.4f, 1.0f, 12.8f, 12.9f, 1.6f).all("rubber"));
        b.add(new Box("jupe_arrondi", 3.8f, 2.8f, 1.1f, 12.2f, 13.4f, 1.6f).all("rubber"));
        b.add(new Box("visiere", 4.2f, 6.4f, 0.6f, 11.8f, 12.4f, 1.0f).all("visor").side("north", "cyan"));
        b.add(new Box("visiere_arrondi", 4.8f, 5.9f, 0.6f, 11.2f, 12.9f, 1.0f).all("visor"));
        b.add(new Box("cerclage", 3.9f, 6.1f, 0.8f, 12.1f, 6.5f, 1.05f).all("black"));
        b.add(new Box("soupape", 6.3f, 3.1f, -0.6f, 9.7f, 6.2f, 1.0f).all("gray_dk").up("gray"));
        b.add(new Box("soupape_arrondi", 6.8f, 2.6f, -0.4f, 9.2f, 6.6f, 1.0f).all("gray_dk"));
        b.add(new Box("soupape_capot", 6.9f, 3.6f, -1.0f, 9.1f, 5.7f, -0.6f).all("black"));
        b.add(new Box("bouton", 7.6f, 6.4f, -0.3f, 8.4f, 7.0f, 0.5f).all("red"));
        b.add(new Box("expiration_g", 4.4f, 3.9f, 0.7f, 6.0f, 5.4f, 1.1f).all("gray"));
        b.add(new Box("expiration_d", 10.0f, 3.9f, 0.7f, 11.6f, 5.4f, 1.1f).all("gray"));
        b.add(new Box("tuyau", 7.3f, 0.2f, -0.3f, 8.7f, 2.8f, 1.0f).all("black"));
        b.add(new Box("tuyau_raccord", 7.0f, -0.6f, -0.5f, 9.0f, 0.2f, 1.2f).all("silver"));
        // harnais : sangles autour de la tête
        b.add(new Box("sangle_haut_g", 1.2f, 9.0f, 1.6f, 1.6f, 10.2f, 14.4f).all("black"));
        b.add(new Box("sangle_haut_d", 14.4f, 9.0f, 1.6f, 14.8f, 10.2f, 14.4f).all("black"));
        b.add(new Box("sangle_bas_g", 1.2f, 4.4f, 1.6f, 1.6f, 5.4f, 14.4f).all("black"));
        b.add(new Box("sangle_bas_d", 14.4f, 4.4f, 1.6f, 14.8f, 5.4f, 14.4f).all("black"));
        b.add(new Box("sangle_nuque_haut", 1.2f, 9.0f, 14.4f, 14.8f, 10.2f, 14.8f).all("black"));
        b.add(new Box("sangle_nuque_bas", 1.2f, 4.4f, 14.4f, 14.8f, 5.4f, 14.8f).all("black"));
        b.add(new Box("sangle_crane", 7.3f, 14.4f, 1.6f, 8.7f, 14.8f, 14.4f).all("black"));
        b.add(new Box("boucle_g", 1.0f, 8.8f, 6.0f, 1.3f, 10.4f, 7.4f).all("silver"));
        b.add(new Box("boucle_d", 14.7f, 8.8f, 6.0f, 15.0f, 10.4f, 7.4f).all("silver"));
        String disp = "{\n"
            + "    \"gui\": {\"rotation\": [10, 155, 0], \"translation\": [0, 0, 0], \"scale\": [0.95, 0.95, 0.95]},\n"
            + "    \"ground\": {\"rotation\": [0, 0, 0], \"translation\": [0, 2, 0], \"scale\": [0.5, 0.5, 0.5]},\n"
            + "    \"fixed\": {\"rotation\": [0, 0, 0], \"translation\": [0, 0, 0], \"scale\": [0.9, 0.9, 0.9]},\n"
            + "    \"head\": {\"rotation\": [0, 0, 0], \"translation\": [0, 0, 0], \"scale\": [1, 1, 1]},\n"
            + "    \"thirdperson_righthand\": {\"rotation\": [75, 180, 0], \"translation\": [0, 2.5, 0], \"scale\": [0.45, 0.45, 0.45]},\n"
            + "    \"thirdperson_lefthand\": {\"rotation\": [75, 180, 0], \"translation\": [0, 2.5, 0], \"scale\": [0.45, 0.45, 0.45]},\n"
            + "    \"firstperson_righthand\": {\"rotation\": [0, 135, 0], \"translation\": [0, 1, 0], \"scale\": [0.45, 0.45, 0.45]},\n"
            + "    \"firstperson_lefthand\": {\"rotation\": [0, 225, 0], \"translation\": [0, 1, 0], \"scale\": [0.45, 0.45, 0.45]}\n  }";
        // 02/11 : masque trop petit sur le visage -> agrandi de 25 % autour du centre du visage (x, y) et vers l'avant à partir de la peau (z = 1,6),
        // puis descendu de 0,6 (le bas de la jupe au menton, le haut sous le sommet du crâne) ; les sangles et boucles, déjà au ras des côtés de la
        // tête, ne sont recalées qu'en hauteur.
        final float k = 1.25f;
        for (Box x : b) {
            if (x.name.equals("sangle_crane")) continue;
            boolean side = x.name.startsWith("sangle_") || x.name.startsWith("boucle_");
            for (float[] p : new float[][]{x.from, x.to}) {
                p[1] = 8 + (p[1] - 8) * k;
                if (side) continue;
                p[1] -= 0.6f;
                p[0] = 8 + (p[0] - 8) * k;
                p[2] = 1.6f - (1.6f - p[2]) * k;
            }
        }
        write("masque_ari", model(b, 0, atlasOnly(), disp, ""));
    }

    /**
     * Bouteille d'air de l'ARI sur son dossard (02/11), portée dans le dos (emplacement Dos du mod Vêtements). Le mod Vêtements la dessine avec
     * l'affichage « head » du modèle, centré sur le torse et à l'échelle 0,625 : 1 unité = 0,625 px, centre du torse en (8, 8, 8), dos du
     * vêtement en z = 11,7, devant en z = 4,3, épaules en y = 17,6, taille en y = -1,6, côtés du torse en x = 1,6 et 14,4.
     */
    static void bouteille() throws Exception {
        List<Box> b = new ArrayList<>();
        // dossard et sangles dorsales
        b.add(new Box("dossard", 4.2f, -0.6f, 11.7f, 11.8f, 16.8f, 12.5f).all("black").side("south", "rubber"));
        b.add(new Box("dossard_haut", 5.0f, 16.8f, 11.7f, 11.0f, 17.6f, 12.3f).all("black"));
        // bouteille composite (prisme octogonal), robinet en bas, comme sur un vrai ARI
        float cz = 16.0f;
        b.add(new Box("bouteille_a", 4.6f, 1.4f, 12.5f, 11.4f, 15.2f, 19.5f).all("gray_dk").up("gray").rot("y", 45, 8, 8, cz));
        b.add(new Box("bouteille_b", 4.6f, 1.4f, 12.5f, 11.4f, 15.2f, 19.5f).all("gray_dk").up("gray"));
        b.add(new Box("ogive_haut", 5.6f, 15.2f, 13.5f, 10.4f, 16.2f, 18.5f).all("gray_dk").up("gray"));
        b.add(new Box("ogive_bas", 5.6f, 0.4f, 13.5f, 10.4f, 1.4f, 18.5f).all("gray_dk"));
        b.add(new Box("etiquette_a", 4.55f, 9.2f, 12.45f, 11.45f, 11.6f, 19.55f).all("yellow").rot("y", 45, 8, 8, cz));
        b.add(new Box("etiquette_b", 4.55f, 9.2f, 12.45f, 11.45f, 11.6f, 19.55f).all("yellow"));
        b.add(new Box("lisere", 4.55f, 3.0f, 12.45f, 11.45f, 3.5f, 19.55f).all("white"));
        b.add(new Box("goulot", 7.2f, -0.6f, cz - 0.8f, 8.8f, 0.4f, cz + 0.8f).all("chrome"));
        b.add(new Box("robinet", 6.8f, -1.4f, cz - 1.2f, 9.2f, -0.6f, cz + 1.2f).all("silver"));
        b.add(new Box("volant", 6.0f, -2.2f, cz - 2.0f, 10.0f, -1.4f, cz + 2.0f).all("black").down("rubber"));
        b.add(new Box("detendeur", 8.8f, -1.2f, cz - 0.6f, 11.2f, 0.2f, cz + 0.6f).all("chrome"));
        // sangles de maintien de la bouteille
        b.add(new Box("collier_haut", 4.5f, 12.6f, 12.4f, 11.5f, 13.4f, 19.6f).all("black"));
        b.add(new Box("collier_bas", 4.5f, 4.4f, 12.4f, 11.5f, 5.2f, 19.6f).all("black"));
        b.add(new Box("boucle_collier", 7.3f, 4.3f, 19.6f, 8.7f, 5.3f, 19.9f).all("silver"));
        // bretelles : dos -> par-dessus les épaules -> devant
        for (float x : new float[]{4.6f, 9.8f}) {
            b.add(new Box("bretelle_epaule", x, 17.6f, 4.0f, x + 1.6f, 18.3f, 12.3f).all("black").up("rubber"));
            b.add(new Box("bretelle_devant", x, 6.0f, 3.7f, x + 1.6f, 17.6f, 4.3f).all("black").side("north", "rubber"));
            b.add(new Box("bande_reflechissante", x, 11.0f, 3.65f, x + 1.6f, 12.0f, 3.7f).all("yellow"));
        }
        // ceinture ventrale
        b.add(new Box("ceinture_devant", 1.2f, -1.6f, 3.7f, 14.8f, 0.2f, 4.3f).all("black"));
        b.add(new Box("ceinture_g", 1.0f, -1.6f, 3.7f, 1.6f, 0.2f, 11.9f).all("black"));
        b.add(new Box("ceinture_d", 14.4f, -1.6f, 3.7f, 15.0f, 0.2f, 11.9f).all("black"));
        b.add(new Box("boucle", 7.0f, -1.8f, 3.4f, 9.0f, 0.4f, 3.7f).all("silver"));
        // manomètre sur la bretelle gauche, flexible qui passe sur l'épaule
        b.add(new Box("manometre", 3.9f, 8.0f, 3.1f, 6.9f, 11.0f, 3.7f).all("black"));
        b.add(new Box("manometre_cadran", 4.3f, 8.4f, 3.05f, 6.5f, 10.6f, 3.1f).all("white"));
        b.add(new Box("manometre_aiguille", 5.3f, 9.4f, 3.0f, 6.2f, 9.7f, 3.05f).all("red"));
        b.add(new Box("flexible_devant", 4.0f, 11.0f, 3.5f, 4.6f, 17.4f, 4.0f).all("rubber"));
        b.add(new Box("flexible_epaule", 4.0f, 17.4f, 3.9f, 4.6f, 18.0f, 12.0f).all("rubber"));
        String disp = "{\n"
            + "    \"gui\": {\"rotation\": [15, -30, 0], \"translation\": [0, 0, 0], \"scale\": [0.62, 0.62, 0.62]},\n"
            + "    \"ground\": {\"rotation\": [0, 0, 0], \"translation\": [0, 2, -2], \"scale\": [0.45, 0.45, 0.45]},\n"
            + "    \"fixed\": {\"rotation\": [0, 0, 0], \"translation\": [0, 0, -2], \"scale\": [0.6, 0.6, 0.6]},\n"
            + "    \"head\": {\"rotation\": [0, 0, 0], \"translation\": [0, 0, 0], \"scale\": [1, 1, 1]},\n"
            + "    \"thirdperson_righthand\": {\"rotation\": [0, 180, 0], \"translation\": [0, 0, -3], \"scale\": [0.4, 0.4, 0.4]},\n"
            + "    \"thirdperson_lefthand\": {\"rotation\": [0, 180, 0], \"translation\": [0, 0, -3], \"scale\": [0.4, 0.4, 0.4]},\n"
            + "    \"firstperson_righthand\": {\"rotation\": [0, 160, 0], \"translation\": [0, 0, -2], \"scale\": [0.4, 0.4, 0.4]},\n"
            + "    \"firstperson_lefthand\": {\"rotation\": [0, 200, 0], \"translation\": [0, 0, -2], \"scale\": [0.4, 0.4, 0.4]}\n  }";
        write("bouteille_ari", model(b, 0, atlasOnly(), disp, ""));
    }

    /** Bouche d'incendie (bloc) : poteau rouge arrondi (prismes octogonaux), chapeau, trois sorties à bouchon laiton, écrou de manœuvre. */
    static void boucheIncendie() throws Exception {
        List<Box> b = new ArrayList<>();
        b.add(new Box("socle_a", 4, 0, 4, 12, 1.5f, 12).all("gray").up("silver"));
        b.add(new Box("socle_b", 4, 0, 4, 12, 1.5f, 12).all("gray").up("silver").rotY(45));
        b.add(new Box("fut_a", 5.2f, 1.5f, 5.2f, 10.8f, 11, 10.8f).all("red").down("red_dk"));
        b.add(new Box("fut_b", 5.2f, 1.5f, 5.2f, 10.8f, 11, 10.8f).all("red").down("red_dk").rotY(45));
        b.add(new Box("bague_a", 4.8f, 9.5f, 4.8f, 11.2f, 10.5f, 11.2f).all("red_dk"));
        b.add(new Box("bague_b", 4.8f, 9.5f, 4.8f, 11.2f, 10.5f, 11.2f).all("red_dk").rotY(45));
        b.add(new Box("chapeau_a", 5.6f, 11, 5.6f, 10.4f, 13, 10.4f).all("red_hi"));
        b.add(new Box("chapeau_b", 5.6f, 11, 5.6f, 10.4f, 13, 10.4f).all("red_hi").rotY(45));
        b.add(new Box("dome", 6.4f, 13, 6.4f, 9.6f, 14, 9.6f).all("red_hi"));
        b.add(new Box("ecrou", 7.2f, 14, 7.2f, 8.8f, 15.2f, 8.8f).all("brass").up("yellow"));
        b.add(new Box("sortie_o", 2.8f, 6, 6.9f, 5.2f, 8.2f, 9.1f).all("red"));
        b.add(new Box("sortie_o_bouchon", 2.2f, 5.8f, 6.7f, 2.8f, 8.4f, 9.3f).all("brass"));
        b.add(new Box("sortie_e", 10.8f, 6, 6.9f, 13.2f, 8.2f, 9.1f).all("red"));
        b.add(new Box("sortie_e_bouchon", 13.2f, 5.8f, 6.7f, 13.8f, 8.4f, 9.3f).all("brass"));
        b.add(new Box("sortie_n", 6.6f, 5.4f, 3, 9.4f, 8.6f, 5.2f).all("red"));
        b.add(new Box("sortie_n_bouchon", 6.4f, 5.2f, 2.3f, 9.6f, 8.8f, 3).all("brass").side("north", "yellow"));
        b.add(new Box("bande_reflechissante_a", 5.15f, 3, 5.15f, 10.85f, 3.8f, 10.85f).all("white"));
        b.add(new Box("bande_reflechissante_b", 5.15f, 3, 5.15f, 10.85f, 3.8f, 10.85f).all("white").rotY(45));
        String disp = "{\n"
            + "    \"gui\": {\"rotation\": [30, 225, 0], \"translation\": [0, 0, 0], \"scale\": [0.8, 0.8, 0.8]},\n"
            + "    \"ground\": {\"rotation\": [0, 0, 0], \"translation\": [0, 3, 0], \"scale\": [0.4, 0.4, 0.4]},\n"
            + "    \"fixed\": {\"rotation\": [0, 0, 0], \"translation\": [0, 0, 0], \"scale\": [0.6, 0.6, 0.6]},\n"
            + "    \"thirdperson_righthand\": {\"rotation\": [75, 45, 0], \"translation\": [0, 2.5, 0], \"scale\": [0.45, 0.45, 0.45]},\n"
            + "    \"thirdperson_lefthand\": {\"rotation\": [75, 45, 0], \"translation\": [0, 2.5, 0], \"scale\": [0.45, 0.45, 0.45]},\n"
            + "    \"firstperson_righthand\": {\"rotation\": [0, 45, 0], \"translation\": [0, 0, 0], \"scale\": [0.45, 0.45, 0.45]},\n"
            + "    \"firstperson_lefthand\": {\"rotation\": [0, 225, 0], \"translation\": [0, 0, 0], \"scale\": [0.45, 0.45, 0.45]}\n  }";
        writeBlock("bouche_incendie", model(b, 0, atlasOnly(), disp, ""));
        write("bouche_incendie", "{\n  \"parent\": \"minenorthsecours:block/bouche_incendie\"\n}\n");
    }

    /**
     * Texture du tuyau dessiné dans le monde (HoseRenderer) : 16 x 16, tissu rouge tissé avec deux liserés clairs sur v 0 à 0,72,
     * laiton des raccords sur v 0,8 à 1. u fait le tour du tube.
     */
    static void hoseTexture() throws Exception {
        BufferedImage im = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(11);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int c;
            if (y >= 12) c = (x + y) % 4 == 0 ? 0xE0B83A : y == 12 ? 0x8E6E14 : 0xC9A227;
            else if (y == 11) c = 0x5C1010;
            else if (x == 3 || x == 11) c = 0xE8D8B0;                       // liserés le long du tuyau
            else c = ((x + y) & 1) == 0 ? 0xB71F1F : 0xC62828;              // trame du tissu
            int n = r.nextInt(7) - 3;
            c = (clamp(((c >> 16) & 255) + n) << 16) | (clamp(((c >> 8) & 255) + n) << 8) | clamp((c & 255) + n);
            im.setRGB(x, y, 0xFF000000 | c);
        }
        File dir = new File("src/main/resources/assets/minenorthsecours/textures/entity");
        dir.mkdirs();
        ImageIO.write(im, "png", new File(dir, "tuyau.png"));
    }

    /** Rouleau de tuyau : galette octogonale (tuyau enroulé à plat), spires visibles, raccords laiton qui dépassent, sangle. */
    static void rouleau() throws Exception {
        List<Box> b = new ArrayList<>();
        b.add(new Box("galette_a", 2, 0, 2, 14, 5, 14).all("red").up("red_hi").down("red_dk"));
        b.add(new Box("galette_b", 2, 0, 2, 14, 5, 14).all("red").up("red_hi").down("red_dk").rotY(45));
        b.add(new Box("spire_a", 2.1f, 1.6f, 2.1f, 13.9f, 1.9f, 13.9f).all("red_dk"));
        b.add(new Box("spire_b", 2.1f, 1.6f, 2.1f, 13.9f, 1.9f, 13.9f).all("red_dk").rotY(45));
        b.add(new Box("spire_c", 2.1f, 3.3f, 2.1f, 13.9f, 3.6f, 13.9f).all("red_dk"));
        b.add(new Box("spire_d", 2.1f, 3.3f, 2.1f, 13.9f, 3.6f, 13.9f).all("red_dk").rotY(45));
        b.add(new Box("lisere_a", 4.5f, 5, 4.5f, 11.5f, 5.1f, 11.5f).all("beige"));
        b.add(new Box("lisere_b", 4.5f, 5, 4.5f, 11.5f, 5.1f, 11.5f).all("beige").rotY(45));
        b.add(new Box("coeur_a", 6, 5, 6, 10, 5.2f, 10).all("red_dk"));
        b.add(new Box("coeur_b", 6.8f, 5.2f, 6.8f, 9.2f, 5.25f, 9.2f).all("black"));
        b.add(new Box("raccord_1", 12.5f, 0.6f, 6.6f, 15.6f, 4.4f, 9.4f).all("brass").up("yellow"));
        b.add(new Box("raccord_1_ergot", 15.6f, 1.6f, 7.4f, 16, 3.4f, 8.6f).all("brass"));
        b.add(new Box("raccord_2", 6.6f, 0.6f, 12.5f, 9.4f, 4.4f, 15.6f).all("brass").up("yellow"));
        b.add(new Box("raccord_2_ergot", 7.4f, 1.6f, 15.6f, 8.6f, 3.4f, 16).all("brass"));
        b.add(new Box("sangle", 7.2f, -0.1f, 1.6f, 8.8f, 5.3f, 14.4f).all("black").up("rubber"));
        Map<String, String> t = atlasOnly();
        write("rouleau_tuyau", model(b, 4, t, display(1.05f, 0.62f, 0.6f), ""));
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
        hache();
        halligan();
        lance();
        cameraScreen();
        camera();
        blanketFace();
        couverture();
        masque();
        bouteille();
        boucheIncendie();
        hoseTexture();
        rouleau();
        for (String old : new String[]{"bandage.png", "trousse_soins.png", "defibrillateur.png", "tablette_secours.png"}) new File(TEX, old).delete();
        System.out.println("Textures et modèles générés.");
    }
}
