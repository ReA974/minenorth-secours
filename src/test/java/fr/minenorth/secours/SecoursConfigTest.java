package fr.minenorth.secours;

import com.google.gson.Gson;
import fr.minenorth.secours.config.SecoursConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecoursConfigTest {
    private static final Gson GSON = new Gson();

    @Test
    void oldConfigWithItemFieldStillLoads() {
        SecoursConfig c = GSON.fromJson("{\"bandage\":{\"item\":\"minecraft:paper\",\"secondes\":4,\"consomme\":true,\"reserve_secours\":false}}", SecoursConfig.class);
        assertEquals(4, c.bandage.secondes);
    }

    @Test
    void newOptionsHaveDefaults() {
        SecoursConfig c = GSON.fromJson("{}", SecoursConfig.class);
        assertEquals(12, c.defib_battements);
        assertTrue(c.coma_mortel);
        assertEquals(0.7, c.defib_precision_min, 1e-9);
    }
}
