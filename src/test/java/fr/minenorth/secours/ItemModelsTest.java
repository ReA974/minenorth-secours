package fr.minenorth.secours;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Vérifie les modèles 3D des items : UV dans 0..16 (comme Minecraft les lit), coordonnées dans 0..16, textures existantes. */
class ItemModelsTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/minenorthsecours");
    private static final List<String> MODELS = List.of("defibrillateur", "trousse_soins", "bandage", "tablette_secours");

    private static JsonObject load(String name) throws IOException {
        try (Reader r = Files.newBufferedReader(ASSETS.resolve("models/item/" + name + ".json"), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }

    @Test
    void everyModelIsA3DModelWithElements() throws IOException {
        for (String m : MODELS) {
            JsonObject o = load(m);
            assertTrue(o.has("elements") && o.getAsJsonArray("elements").size() >= 8, m + " : modèle 3D détaillé attendu (au moins 8 éléments)");
            assertTrue(o.has("display"), m + " : display manquant");
        }
    }

    @Test
    void geometryAndUvStayInsideBounds() throws IOException {
        for (String m : MODELS) {
            for (JsonElement el : load(m).getAsJsonArray("elements")) {
                JsonObject e = el.getAsJsonObject();
                for (String k : List.of("from", "to")) {
                    for (JsonElement v : e.getAsJsonArray(k)) {
                        double d = v.getAsDouble();
                        assertTrue(d >= -16 && d <= 32, m + " : coordonnée hors limites " + d);
                    }
                }
                for (Map.Entry<String, JsonElement> face : e.getAsJsonObject("faces").entrySet()) {
                    JsonArray uv = face.getValue().getAsJsonObject().getAsJsonArray("uv");
                    for (JsonElement v : uv) {
                        double d = v.getAsDouble();
                        assertTrue(d >= 0 && d <= 16, m + " : UV hors de 0..16 (" + d + ") sur la face " + face.getKey());
                    }
                }
            }
        }
    }

    @Test
    void everyReferencedTextureExists() throws IOException {
        for (String m : MODELS) {
            for (Map.Entry<String, JsonElement> t : load(m).getAsJsonObject("textures").entrySet()) {
                String ref = t.getValue().getAsString();
                String path = ref.substring(ref.indexOf(':') + 1);
                assertTrue(Files.exists(ASSETS.resolve("textures/" + path + ".png")), m + " : texture absente " + ref);
            }
        }
    }
}
