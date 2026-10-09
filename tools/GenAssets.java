import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;

/** Génère les textures des items de soin. Usage : java tools/GenAssets.java (depuis la racine du projet). */
public class GenAssets {
    static final File OUT = new File("src/main/resources/assets/minenorthsecours/textures/item");

    static void rect(BufferedImage im, int x, int y, int w, int h, int rgb) {
        for (int i = x; i < x + w; i++) for (int j = y; j < y + h; j++) im.setRGB(i, j, new Color(rgb, true).getRGB());
    }

    static void save(BufferedImage im, String name) throws Exception {
        OUT.mkdirs();
        ImageIO.write(im, "png", new File(OUT, name));
    }

    public static void main(String[] a) throws Exception {
        // Bandage : rouleau blanc incliné avec une croix rouge.
        BufferedImage b = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        rect(b, 3, 5, 10, 6, 0xFFEFEFEF);
        rect(b, 3, 5, 10, 1, 0xFFFFFFFF);
        rect(b, 3, 10, 10, 1, 0xFFB8B8B8);
        rect(b, 3, 5, 1, 6, 0xFFB8B8B8);
        rect(b, 7, 6, 2, 4, 0xFFD62828);
        rect(b, 6, 7, 4, 2, 0xFFD62828);
        save(b, "bandage.png");

        // Trousse : boîte rouge, poignée, croix blanche.
        BufferedImage t = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        rect(t, 2, 5, 12, 9, 0xFFC62828);
        rect(t, 2, 5, 12, 1, 0xFFE55353);
        rect(t, 2, 13, 12, 1, 0xFF8E1B1B);
        rect(t, 6, 3, 4, 2, 0xFF5C5C5C);
        rect(t, 7, 6, 2, 6, 0xFFFFFFFF);
        rect(t, 5, 8, 6, 2, 0xFFFFFFFF);
        save(t, "trousse_soins.png");

        // Défibrillateur : texture 32x32 dépliée pour le modèle en cubes.
        BufferedImage d = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        rect(d, 0, 0, 32, 32, 0xFF4A4F57);          // gris de base
        rect(d, 0, 0, 16, 10, 0xFFE8E8E8);          // boîtier clair
        rect(d, 0, 0, 16, 1, 0xFFFFFFFF);
        rect(d, 16, 0, 8, 8, 0xFF0B2A12);           // écran sombre
        rect(d, 17, 3, 6, 1, 0xFF3CFF6B);           // ligne ECG
        rect(d, 19, 2, 1, 1, 0xFF3CFF6B);
        rect(d, 20, 4, 1, 1, 0xFF3CFF6B);
        rect(d, 24, 0, 8, 8, 0xFFFFC400);           // palette jaune
        rect(d, 0, 12, 8, 8, 0xFFD62828);           // bouton choc rouge
        rect(d, 8, 12, 8, 8, 0xFF2B2E33);           // palette / câble sombre
        rect(d, 16, 12, 16, 8, 0xFFC62828);         // croix du boîtier
        rect(d, 22, 13, 4, 6, 0xFFFFFFFF);
        rect(d, 20, 15, 8, 2, 0xFFFFFFFF);
        save(d, "defibrillateur.png");
    }
}
