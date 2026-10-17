package fr.minenorth.secours.vie;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Module Santé / mode de vie (ex-mod minenorth-vie, fusionné dans secours). Forme (sport), alimentation, repos et toxines donnent une immunité ;
 * la forme et l'alimentation donnent des bonus (course plus rapide et plus longue, un peu plus de vie) ; une mauvaise hygiène de vie expose aux
 * maladies. Tout est calculé côté serveur : le client n'affiche que ce que le serveur lui envoie.
 * <p>
 * NAMESPACE : les objets, blocs et l'entité du module restent enregistrés sous « minenorthvie » (et non « minenorthsecours ») pour que les
 * objets en inventaire, les machines posées et les données joueurs de l'ancien mod minenorth-vie soient retrouvés tels quels.
 */
public final class MineNorthVie {
    private MineNorthVie() {}

    /** Espace de noms historique des identifiants (minenorthvie:medicament, minenorthvie:tapis_de_course…). */
    public static final String NAMESPACE = "minenorthvie";

    public static void init(IEventBus modBus) {
        ModItems.register(modBus);
        VieNet.register();
        MinecraftForge.EVENT_BUS.register(new VieEvents());
        MinecraftForge.EVENT_BUS.register(new VieWipe());
        // Config du serveur dédié seulement, dans config/ (le client ne crée aucun fichier).
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, VieConfig.SPEC, "minenorth_vie.toml");
        }
    }
}
