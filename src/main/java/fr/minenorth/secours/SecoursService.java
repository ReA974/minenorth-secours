package fr.minenorth.secours;

import fr.minenorth.api.BankService;
import fr.minenorth.api.MineNorth;
import fr.minenorth.api.PayResult;
import fr.minenorth.secours.client.ClientState;
import fr.minenorth.secours.compat.Compat;
import fr.minenorth.secours.compat.TaczCompat;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import fr.minenorth.secours.config.SecoursConfig;
import fr.minenorth.secours.data.SecoursData;
import fr.minenorth.secours.data.SecoursData.Injury;
import fr.minenorth.secours.item.CareItem;
import fr.minenorth.secours.item.ModItems;
import fr.minenorth.secours.network.ModNetwork;
import fr.minenorth.secours.network.ModNetwork.ActionPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Blessures et secours.
 *
 * Chute >= 4 blocs : légère. Chute >= 8 blocs : moyenne. Chute qui laisse 4 cœurs ou moins : grave.
 * N'importe quel dégât qui laisserait 2 cœurs ou moins : le joueur tombe inconscient (il ne meurt pas).
 * Dégât d'une arme TACZ : hémorragie + blessure au moins moyenne.
 * Toutes les valeurs sont dans config/minenorth_secours.json.
 */
@Mod.EventBusSubscriber
public final class SecoursService {
    private SecoursService() {}

    private static final UUID SPEED_ID = UUID.fromString("6b0f5a1e-2c5d-4c1e-9a55-0d7f3e2a9b11");
    public static final int BANDAGE = 0, TROUSSE = 1, DEFIB = 2;
    private static final String[] CARE_NAMES = {"Bandage", "Soins", "Réanimation"};

    /** Hauteur de la dernière chute de chaque joueur (remplie juste avant les dégâts de chute). */
    private static final Map<UUID, Float> FALLS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_BLEED = new HashMap<>();
    private static final Set<UUID> TABLETS = new HashSet<>();
    private static final Set<UUID> CLINIC_OPEN = new HashSet<>();
    /** Soin en cours au PNJ. */
    private record Clinic(long endMs, double x, double y, double z, long price) {}
    private static final Map<UUID, Clinic> CLINICS = new HashMap<>();
    /** Soin en cours par un joueur (clé = celui qui soigne). */
    private record Care(UUID target, int type, long startMs, long endMs) {}
    private static final Map<UUID, Care> CARES = new HashMap<>();
    /** Mini-jeu de défibrillation en cours (clé = secouriste). */
    private record DefibSession(UUID target, long seed, int beats, long startMs) {}
    private static final Map<UUID, DefibSession> DEFIBS = new HashMap<>();
    /** Une session ouverte depuis plus longtemps que ça est abandonnée. */
    private static final long DEFIB_TIMEOUT_MS = 60_000L;
    /** Transport : celui qui porte -> le blessé porté. */
    private static final Map<UUID, UUID> CARRY = new HashMap<>();
    /** Blessés que le mod est en train de faire descendre lui-même (sinon un inconscient ne peut pas descendre). */
    private static final Set<UUID> RELEASING = new HashSet<>();

    // ------------------------------------------------------------------ outils
    /** Grade du joueur dans les secours (0 = chef), ou -1. */
    public static int rank(ServerPlayer p) {
        Integer g = SecoursData.get(p.server).staff.get(p.getUUID());
        return g == null ? -1 : Math.max(0, Math.min(2, g));
    }

    public static String display(MinecraftServer s, UUID id) {
        String rp = Compat.rpName(s, id);
        return rp.isBlank() ? SecoursData.get(s).name(id) : rp;
    }

    private static void bar(ServerPlayer p, String text) { p.displayClientMessage(Component.literal(text), true); }
    private static void tell(ServerPlayer p, String text) { p.sendSystemMessage(Component.literal(text)); }
    /** Vrai si le joueur est secouriste ET a pris son service sur la tablette. */
    public static boolean onDuty(ServerPlayer p) {
        return rank(p) >= 0 && SecoursData.get(p.server).onDuty.contains(p.getUUID());
    }
    /** Prend ou quitte le service (tablette ou accueil). Renvoie le nouvel état ; inchangé si le joueur n'est pas secouriste. */
    public static boolean setDuty(ServerPlayer p, boolean on) {
        if (rank(p) < 0) return false;
        SecoursData d = SecoursData.get(p.server);
        boolean was = d.onDuty.contains(p.getUUID());
        if (was == on) return on;
        if (on) d.onDuty.add(p.getUUID()); else d.onDuty.remove(p.getUUID());
        FireService.onDutyChanged(p, on);
        tellSecours(p.server, "§b[Secours] " + display(p.server, p.getUUID()) + (on ? " prend son service." : " quitte son service."));
        resyncComa(p.server);
        if (!on) tell(p, "§eVous avez quitté votre service.");
        return on;
    }
    /** Les alertes ne vont qu'aux secouristes en service. */
    static void tellSecours(MinecraftServer s, String text) {
        for (ServerPlayer p : s.getPlayerList().getPlayers()) if (onDuty(p)) tell(p, text);
    }
    /** Le coma est-il mortel en ce moment ? (config, et aucun secouriste en service si coma_mortel_sans_secours_seulement) */
    private static boolean comaLethal(MinecraftServer s) {
        SecoursConfig cfg = SecoursConfig.get();
        return cfg.coma_mortel && !(cfg.coma_mortel_sans_secours_seulement && secoursOnline(s));
    }

    /** Remet à jour l'écran des inconscients (secours en service ou non) quand la situation change. */
    private static void resyncComa(MinecraftServer s) {
        for (ServerPlayer q : s.getPlayerList().getPlayers()) if (isComa(q)) sync(q);
    }

    /** Touche « se réveiller à l'hôpital » : seulement pour un inconscient, et seulement s'il n'y a aucun secouriste en service. */
    public static void wake(ServerPlayer p) {
        if (!isComa(p)) return;
        if (secoursOnline(p.server)) {
            bar(p, "§cDes secours sont en service : attendez-les ou la fin du délai.");
            sync(p);
            return;
        }
        hospital(p);
    }

    private static boolean secoursOnline(MinecraftServer s) {
        for (ServerPlayer p : s.getPlayerList().getPlayers()) if (onDuty(p)) return true;
        return false;
    }
    static String money(long cents) {
        long a = Math.abs(cents);
        return (a / 100) + (a % 100 == 0 ? "" : "," + String.format("%02d", a % 100)) + " €";
    }
    private static boolean exempt(ServerPlayer p) { return p.isCreative() || p.isSpectator(); }

    /** Dégât causé par une arme à feu TACZ (types de dégâts « tacz:bullet… »). */
    private static boolean isGun(DamageSource source) {
        return source.typeHolder().unwrapKey().map(k -> "tacz".equals(k.location().getNamespace())).orElse(false);
    }

    /** Vrai si la pièce d'armure qui couvre cette zone arrête la balle. Casque = tête, plastron = torse et bras, jambières = jambes. */
    private static boolean protectedZone(ServerPlayer p, int zone) {
        SecoursConfig cfg = SecoursConfig.get();
        if (!cfg.protection_active) return false;
        EquipmentSlot slot = zone == TaczCompat.TETE ? EquipmentSlot.HEAD : zone == TaczCompat.JAMBES ? EquipmentSlot.LEGS : EquipmentSlot.CHEST;
        ItemStack piece = p.getItemBySlot(slot);
        if (piece.isEmpty()) return false;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(piece.getItem());
        if (key != null && cfg.protection_objets.contains(key.toString())) return true;
        if (cfg.protection_seulement_liste) return false;
        return piece.getItem() instanceof ArmorItem armor && armor.getDefense() >= cfg.protection_points_min;
    }

    private static Pose comaPose() {
        try { return Pose.valueOf(SecoursConfig.get().coma_pose.trim().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException e) { return Pose.SLEEPING; }
    }

    /** Vrai si ce joueur est inconscient (fonctionne des deux côtés : serveur = données, client = état reçu). */
    public static boolean isComa(Player player) {
        if (player.level().isClientSide) return ClientState.coma;
        if (!(player instanceof ServerPlayer sp)) return false;
        Injury j = SecoursData.get(sp.server).peek(sp.getUUID());
        return j != null && j.coma;
    }

    // ------------------------------------------------------------------ état et effets
    /** Envoie au joueur son propre état (affichage + pose). */
    public static void sync(ServerPlayer p) {
        Injury j = SecoursData.get(p.server).peek(p.getUUID());
        long now = System.currentTimeMillis();
        Clinic c = CLINICS.get(p.getUUID());
        int care = c == null ? 0 : (int) Math.max(0, (c.endMs() - now + 999) / 1000);
        boolean rescuers = secoursOnline(p.server);
        if (j == null) { ModNetwork.send(p, new ModNetwork.StatePacket(0, 0, false, false, 0, "", care, "", 0, rescuers)); return; }
        ModNetwork.send(p, new ModNetwork.StatePacket(j.level, j.healAt > 0 ? (int) Math.max(0, (j.healAt - now) / 1000) : 0, j.bleeding,
                j.coma, j.coma ? (int) Math.max(0, (j.comaDeadline - now) / 1000) : 0, j.dispatch, care, SecoursConfig.get().coma_rendu_allonge ? "" : comaPose().name(), j.zones, rescuers));
    }

    /** Applique le ralentissement et la pose correspondant à l'état du joueur. Sans effet si rien n'a changé. */
    private static void applyEffects(ServerPlayer p, Injury j) {
        SecoursConfig cfg = SecoursConfig.get();
        boolean coma = j != null && j.coma;
        double slow = j == null ? 0 : coma ? 1.0 : switch (j.level) {
            case SecoursData.LEGERE -> cfg.ralentissement_legere;
            case SecoursData.MOYENNE -> cfg.ralentissement_moyenne;
            case SecoursData.GRAVE -> cfg.ralentissement_grave;
            default -> 0;
        };
        boolean leg = j != null && !coma && TaczCompat.has(j.zones, TaczCompat.JAMBES);
        boolean arm = j != null && !coma && TaczCompat.has(j.zones, TaczCompat.BRAS);
        if (leg) slow = Math.max(slow, cfg.ralentissement_jambe);
        slow = Math.max(0, Math.min(1, slow));
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            AttributeModifier current = speed.getModifier(SPEED_ID);
            double have = current == null ? 0 : -current.getAmount();
            if (Math.abs(have - slow) > 1.0e-6) {
                speed.removeModifier(SPEED_ID);
                if (slow > 0) speed.addTransientModifier(new AttributeModifier(SPEED_ID, "minenorth_blessure", -slow, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
        // Avec le rendu couché du mod, on ne force aucune pose : le joueur garde aussi sa taille normale, donc on peut cliquer dessus.
        Pose want = coma && !cfg.coma_rendu_allonge ? comaPose() : null;
        if (p.getForcedPose() != want) p.setForcedPose(want);
        // Amplificateur négatif : empêche de sauter tant que le joueur est au sol.
        if (coma || leg) p.addEffect(new MobEffectInstance(MobEffects.JUMP, 60, -10, false, false, false));
        // Bras touché : coups plus faibles et gestes plus lents.
        if (arm) {
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 60, 1, false, false, false));
        }
    }

    /** Envoie à tous les joueurs la liste des inconscients (pour les dessiner couchés). */
    private static void broadcastComa(MinecraftServer s) {
        SecoursData d = SecoursData.get(s);
        List<UUID> ids = new ArrayList<>();
        for (ServerPlayer q : s.getPlayerList().getPlayers()) {
            Injury j = d.peek(q.getUUID());
            if (j != null && j.coma) ids.add(q.getUUID());
        }
        ModNetwork.ComaListPacket packet = new ModNetwork.ComaListPacket(ids, SecoursConfig.get().coma_rendu_allonge);
        for (ServerPlayer q : s.getPlayerList().getPlayers()) ModNetwork.send(q, packet);
    }

    private static void refresh(ServerPlayer p) {
        SecoursData d = SecoursData.get(p.server);
        Injury j = d.peek(p.getUUID());
        if (j != null && j.empty()) { d.injuries.remove(p.getUUID()); j = null; }
        d.setDirty();
        applyEffects(p, j);
        sync(p);
        broadcastComa(p.server);
    }

    /** Aggrave la blessure (ne la diminue jamais). */
    private static void raise(ServerPlayer p, Injury j, int level) {
        if (level <= j.level) return;
        SecoursConfig cfg = SecoursConfig.get();
        long now = System.currentTimeMillis();
        j.level = level;
        j.healAt = level == SecoursData.LEGERE ? now + SecoursConfig.ms(cfg.guerison_legere_minutes)
                : level == SecoursData.MOYENNE ? now + SecoursConfig.ms(cfg.guerison_moyenne_minutes) : 0;
        bar(p, level == SecoursData.GRAVE ? "§cBlessure grave : seul un pompier ou le SAMU peut vous soigner."
                : level == SecoursData.MOYENNE ? "§6Blessure moyenne : elle guérira avec le temps." : "§eBlessure légère : elle guérira vite.");
    }

    private static void enterComa(ServerPlayer p, Injury j) {
        SecoursConfig cfg = SecoursConfig.get();
        j.coma = true;
        j.dispatch = "";
        j.comaDeadline = System.currentTimeMillis() + SecoursConfig.ms(cfg.coma_duree_minutes);
        if (j.level < SecoursData.GRAVE) { j.level = SecoursData.GRAVE; j.healAt = 0; }
        // Il reste là où il est (y compris assis dans un véhicule) : un inconscient ne descend pas tout seul.
        CLINICS.remove(p.getUUID());
        SecoursData.get(p.server).note(p.getUUID(), "Tombé inconscient" + (j.zones != 0 ? " (balle : " + TaczCompat.describe(j.zones) + ")" : ""));
        tell(p, "§cVous êtes inconscient. Les secours ont été prévenus.");
        tellSecours(p.server, "§c[Secours] " + display(p.server, p.getUUID()) + " est inconscient en "
                + p.blockPosition().getX() + " " + p.blockPosition().getY() + " " + p.blockPosition().getZ() + ". Ouvrez la tablette pour vous signaler en route.");
    }

    private static void wake(ServerPlayer p, Injury j) {
        j.coma = false; j.dispatch = ""; j.comaDeadline = 0;
        p.setHealth(p.getMaxHealth());
    }

    /** Fin du délai sans secours : réveil à l'hôpital, soigné, avec une facture. */
    private static void hospital(ServerPlayer p) {
        MinecraftServer s = p.server;
        SecoursData d = SecoursData.get(s);
        d.injuries.remove(p.getUUID());
        dismount(p);
        hospitalArrival(p, "Réveil à l'hôpital sans intervention des secours");
    }

    /** Téléporte à l'hôpital (si défini), rend de la vie et prélève la facture. */
    private static void hospitalArrival(ServerPlayer p, String note) {
        MinecraftServer s = p.server;
        SecoursData d = SecoursData.get(s);
        d.note(p.getUUID(), note);
        if (d.hasHospital) {
            ServerLevel level = s.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(d.hospitalDim)));
            if (level != null) p.teleportTo(level, d.hx, d.hy, d.hz, p.getYRot(), p.getXRot());
        }
        p.setHealth(Math.max(p.getHealth(), Math.min(p.getMaxHealth(), 10f)));
        long fee = SecoursConfig.cents(SecoursConfig.get().facture_hopital_euros);
        BankService bank = MineNorth.bank();
        long paid = 0;
        if (fee > 0 && bank.hasAccount(s, p.getUUID())) {
            paid = Math.max(0, Math.min(fee, bank.balance(s, p.getUUID())));
            if (paid > 0 && !bank.debit(s, p.getUUID(), paid, "secours:hopital", false).ok()) paid = 0;
        }
        tell(p, "§eVous vous réveillez à l'hôpital." + (fee > 0 ? " Facture : " + money(fee)
                + (paid >= fee ? " (prélevée sur votre compte)." : paid > 0 ? " (" + money(paid) + " prélevés, solde insuffisant)." : " (non prélevée : pas de solde).") : ""));
        refresh(p);
    }

    // ------------------------------------------------------------------ dégâts
    @SubscribeEvent
    public static void fall(LivingFallEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) FALLS.put(p.getUUID(), e.getDistance());
    }

    /** Un joueur inconscient ne peut plus être frappé. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attacked(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && !comaLethal(p.server) && isComa(p) && !e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(LivingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || exempt(p) || e.getAmount() <= 0) return;
        if (e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return; // /kill, vide : on laisse mourir
        SecoursConfig cfg = SecoursConfig.get();
        SecoursData d = SecoursData.get(p.server);
        Injury j = d.injury(p.getUUID());
        if (j.coma) { if (!comaLethal(p.server)) e.setCanceled(true); return; }

        boolean isFall = e.getSource().is(DamageTypeTags.IS_FALL);
        boolean gun = cfg.hemorragie_par_balle && isGun(e.getSource());
        float comaHp = (float) (cfg.coma_coeurs_restants * 2.0);
        float after = p.getHealth() - e.getAmount();
        int zone = gun ? TaczCompat.zone(p) : TaczCompat.AUCUNE;
        boolean shielded = gun && protectedZone(p, zone);

        if (after <= comaHp) {
            // On ne descend pas sous le seuil : le joueur tombe inconscient au lieu de mourir.
            e.setAmount(Math.max(0f, p.getHealth() - comaHp));
            if (gun && !shielded) { j.bleeding = true; j.zones |= TaczCompat.bit(zone); }
            enterComa(p, j);
        } else {
            if (isFall) {
                float dist = FALLS.getOrDefault(p.getUUID(), 0f);
                int level = after <= cfg.grave_coeurs_restants * 2.0 ? SecoursData.GRAVE
                        : dist >= cfg.chute_moyenne_blocs ? SecoursData.MOYENNE
                        : dist >= cfg.chute_legere_blocs ? SecoursData.LEGERE : SecoursData.AUCUNE;
                raise(p, j, level);
            }
            if (!isFall && !gun) raise(p, j, otherCause(cfg, e.getSource(), e.getAmount(), after));
            if (gun && shielded) {
                // La protection arrête la balle : simple choc, ni hémorragie ni blessure par balle.
                bar(p, "§eVotre protection a arrêté la balle (" + TaczCompat.ZONES[zone] + ").");
                raise(p, j, SecoursData.LEGERE);
            } else if (gun) {
                boolean first = !TaczCompat.has(j.zones, zone);
                if (first) d.note(p.getUUID(), "Blessure par balle : " + TaczCompat.ZONES[zone]);
                j.zones |= TaczCompat.bit(zone);
                j.bleeding = true;
                raise(p, j, SecoursData.MOYENNE);
                if (zone == TaczCompat.TETE && cfg.tete_inconscient_direct) {
                    enterComa(p, j);
                } else if (first) {
                    tell(p, "§cTouché par balle : " + TaczCompat.ZONES[zone] + ". Vous perdez du sang, il faut un bandage."
                            + (zone == TaczCompat.JAMBES ? " Vous ne pouvez plus courir ni sauter."
                            : zone == TaczCompat.BRAS ? " Votre bras est affaibli."
                            : zone == TaczCompat.TORSE ? " L'hémorragie est rapide." : ""));
                }
            }
        }
        FALLS.remove(p.getUUID());
        refresh(p);
    }

    /** Blessure causée par autre chose qu'une chute ou une balle : explosion, accident, feu, noyade, gros coup. */
    private static int otherCause(SecoursConfig cfg, DamageSource source, float amount, float after) {
        boolean crash = source.typeHolder().unwrapKey().map(k -> "mts".equals(k.location().getNamespace())).orElse(false);
        if (cfg.blessures_explosions && (source.is(DamageTypeTags.IS_EXPLOSION) || crash)) {
            if (after <= cfg.grave_coeurs_restants * 2.0) return SecoursData.GRAVE;
            return amount >= cfg.coup_leger_coeurs * 2.0 ? SecoursData.MOYENNE : SecoursData.LEGERE;
        }
        if (source.is(DamageTypeTags.IS_FIRE)) return cfg.blessures_feu && after <= cfg.feu_coeurs_restants * 2.0 ? SecoursData.MOYENNE : SecoursData.AUCUNE;
        if (source.is(DamageTypeTags.IS_DROWNING)) return cfg.blessures_noyade && after <= cfg.noyade_coeurs_restants * 2.0 ? SecoursData.LEGERE : SecoursData.AUCUNE;
        if (!cfg.blessures_coups) return SecoursData.AUCUNE;
        return amount >= cfg.coup_moyen_coeurs * 2.0 ? SecoursData.MOYENNE : amount >= cfg.coup_leger_coeurs * 2.0 ? SecoursData.LEGERE : SecoursData.AUCUNE;
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            Injury dying = SecoursData.get(p.server).peek(p.getUUID());
            if (dying != null && dying.coma && SecoursConfig.get().coma_mortel) {
                HOSPITAL_RESPAWN.add(p.getUUID());
                SecoursData.get(p.server).note(p.getUUID(), "Mort pendant le coma");
                tellSecours(p.server, "§c[Secours] " + display(p.server, p.getUUID()) + " est mort pendant son coma.");
            }
            if (CARRY.containsKey(p.getUUID())) putDown(p);
            SecoursData.get(p.server).injuries.remove(p.getUUID());
            SecoursData.get(p.server).setDirty();
            CLINICS.remove(p.getUUID());
            broadcastComa(p.server);
        }
    }

    /** Joueurs morts pendant leur coma : au prochain respawn ils se réveillent à l'hôpital. */
    private static final Set<UUID> HOSPITAL_RESPAWN = new HashSet<>();

    // ------------------------------------------------------------------ joueur inconscient : aucune action
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interact(PlayerInteractEvent e) {
        if (e.isCancelable() && isComa(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attack(AttackEntityEvent e) {
        if (isComa(e.getEntity())) e.setCanceled(true);
    }

    /** Inconscient : seules les commandes de commandes_autorisees_coma passent (pas de /home, /spawn, /tpa pour fuir). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void command(net.minecraftforge.event.CommandEvent e) {
        if (!(e.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer p) || p.hasPermissions(2) || !isComa(p)) return;
        String input = e.getParseResults().getReader().getString().trim();
        if (input.startsWith("/")) input = input.substring(1);
        String root = input.split(" ", 2)[0].toLowerCase(java.util.Locale.ROOT);
        for (String allowed : SecoursConfig.get().commandes_autorisees_coma) {
            if (allowed != null && root.equals(allowed.trim().toLowerCase(java.util.Locale.ROOT))) return;
        }
        e.setCanceled(true);
        p.displayClientMessage(Component.literal("§cVous êtes inconscient : impossible d'utiliser cette commande."), true);
    }

    // ------------------------------------------------------------------ soins par objets
    private static int careType(ItemStack stack) {
        return stack.getItem() instanceof CareItem ci ? ci.type : -1;
    }

    private static SecoursConfig.Objet objet(int type) {
        SecoursConfig cfg = SecoursConfig.get();
        return type == DEFIB ? cfg.defibrillateur : type == TROUSSE ? cfg.trousse : cfg.bandage;
    }

    /** Message d'erreur si ce soin n'a pas de sens sur cette personne, sinon null. */
    private static String careError(Injury j, int type) {
        if (type == DEFIB) return j != null && j.coma ? null : "Cette personne n'est pas inconsciente.";
        if (j == null || j.empty()) return "Cette personne n'a aucune blessure.";
        if (type == BANDAGE) return j.bleeding || j.level == SecoursData.LEGERE ? null : "Un bandage ne suffit pas pour cette blessure.";
        if (j.coma) return "Il faut d'abord réanimer cette personne.";
        return null;
    }

    private static boolean startCare(ServerPlayer rescuer, ServerPlayer target, int type) {
        SecoursConfig.Objet o = objet(type);
        if (o.reserve_secours && rank(rescuer) < 0) { bar(rescuer, "§cSeuls les pompiers et le SAMU peuvent utiliser cet objet."); return false; }
        if (o.reserve_secours && !onDuty(rescuer)) { bar(rescuer, "§cPrenez votre service sur la tablette avant de soigner."); return false; }
        if (rescuer != target && isComa(rescuer)) return false;
        if (rescuer == target && type != BANDAGE) return false;
        String err = careError(SecoursData.get(rescuer.server).peek(target.getUUID()), type);
        if (err != null) { bar(rescuer, "§c" + (rescuer == target ? err.replace("Cette personne n'a", "Vous n'avez").replace("cette blessure", "votre blessure") : err)); return false; }
        long now = System.currentTimeMillis();
        if (type == DEFIB) {
            long seed = java.util.concurrent.ThreadLocalRandom.current().nextLong();
            int beats = SecoursConfig.get().defib_battements;
            DEFIBS.put(rescuer.getUUID(), new DefibSession(target.getUUID(), seed, beats, now));
            ModNetwork.send(rescuer, new ModNetwork.DefibStartPacket(seed, beats));
            bar(target, "§b" + display(rescuer.server, rescuer.getUUID()) + " prépare le défibrillateur, ne bougez pas.");
            return true;
        }
        CARES.put(rescuer.getUUID(), new Care(target.getUUID(), type, now, now + Math.max(1, o.secondes) * 1000L));
        if (rescuer != target) bar(target, "§b" + display(rescuer.server, rescuer.getUUID()) + " vous soigne, ne bougez pas.");
        return true;
    }

    /** Résultat du mini-jeu envoyé par le client : la précision est recalculée ici à partir du seed. */
    public static void defibResult(ServerPlayer rescuer, ModNetwork.DefibResultPacket packet) {
        DefibSession ses = DEFIBS.remove(rescuer.getUUID());
        if (ses == null || packet.cancelled()) return;
        ServerPlayer target = rescuer.server.getPlayerList().getPlayer(ses.target());
        double max = SecoursConfig.get().distance_soin;
        if (target == null || rescuer.level() != target.level() || rescuer.distanceToSqr(target) > max * max || careType(rescuer.getMainHandItem()) != DEFIB) {
            bar(rescuer, "§cRéanimation interrompue : restez à côté, le défibrillateur en main.");
            return;
        }
        double acc = DefibScore.evaluate(DefibScore.beats(ses.seed(), ses.beats()), packet.taps(), System.currentTimeMillis() - ses.startMs());
        if (acc >= SecoursConfig.get().defib_precision_min) finishCare(rescuer, target, DEFIB);
        else bar(rescuer, "§cChoc raté (précision " + Math.round(acc * 100) + " %). Recommencez.");
    }

    private static void tickDefibs(MinecraftServer s, SecoursConfig cfg, long now) {
        for (Iterator<Map.Entry<UUID, DefibSession>> it = DEFIBS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, DefibSession> en = it.next();
            DefibSession ses = en.getValue();
            ServerPlayer rescuer = s.getPlayerList().getPlayer(en.getKey()), target = s.getPlayerList().getPlayer(ses.target());
            double max = cfg.distance_soin;
            boolean valid = rescuer != null && target != null && now - ses.startMs() < DEFIB_TIMEOUT_MS && isComa(target) && !isComa(rescuer)
                    && rescuer.level() == target.level() && rescuer.distanceToSqr(target) <= max * max;
            if (!valid) it.remove();
        }
    }

    private static void finishCare(ServerPlayer rescuer, ServerPlayer target, int type) {
        SecoursData d = SecoursData.get(rescuer.server);
        Injury j = d.peek(target.getUUID());
        if (careError(j, type) != null) return;
        SecoursConfig.Objet o = objet(type);
        if (o.consomme && !rescuer.isCreative()) rescuer.getMainHandItem().shrink(1);
        if (type == DEFIB) {
            wake(target, j);
            tell(target, "§aVous avez été réanimé. Votre blessure grave doit encore être soignée.");
            bar(rescuer, "§aRéanimation réussie.");
            d.note(target.getUUID(), "Réanimé par " + display(rescuer.server, rescuer.getUUID()));
            invoice(rescuer, target, SecoursConfig.get().facture_reanimation_euros, "réanimation");
        } else if (type == BANDAGE) {
            j.bleeding = false;
            if (j.level == SecoursData.LEGERE) { j.level = SecoursData.AUCUNE; j.healAt = 0; j.zones = 0; }
            bar(target, "§aBandage posé" + (j.level == SecoursData.AUCUNE ? "." : " : l'hémorragie est arrêtée."));
            if (rescuer != target) bar(rescuer, "§aBandage posé.");
        } else {
            j.bleeding = false; j.level = SecoursData.AUCUNE; j.healAt = 0; j.zones = 0;
            target.setHealth(target.getMaxHealth());
            tell(target, "§aVos blessures ont été soignées.");
            bar(rescuer, "§aSoins terminés.");
            d.note(target.getUUID(), "Soigné par " + display(rescuer.server, rescuer.getUUID()));
            invoice(rescuer, target, SecoursConfig.get().facture_soins_euros, "soins");
        }
        refresh(target);
    }

    /** Facture au patient : prélevée sur son compte (trésor public), le trésor reverse une part au secouriste. */
    private static void invoice(ServerPlayer rescuer, ServerPlayer patient, double euros, String what) {
        long fee = SecoursConfig.cents(euros);
        if (fee <= 0 || rescuer == patient) return;
        MinecraftServer s = rescuer.server;
        BankService bank = MineNorth.bank();
        long paid = bank.hasAccount(s, patient.getUUID()) ? Math.max(0, Math.min(fee, bank.balance(s, patient.getUUID()))) : 0;
        if (paid > 0 && !bank.debit(s, patient.getUUID(), paid, "secours:facture", false).ok()) paid = 0;
        if (paid > 0) {
            double part = Math.max(0, Math.min(1, SecoursConfig.get().facture_part_secouriste));
            long share = Math.round(paid * part);
            if (share > 0 && bank.refund(s, rescuer.getUUID(), share, "secours:part-secouriste")) {
                tell(rescuer, "§aVous recevez " + money(share) + " pour cette intervention.");
            }
        }
        tell(patient, "§eFacture de " + what + " : " + money(fee) + (paid >= fee ? " (prélevée sur votre compte)."
                : paid > 0 ? " (" + money(paid) + " prélevés, solde insuffisant)." : " (non prélevée : pas de solde)."));
        SecoursData.get(s).note(patient.getUUID(), "Facture " + what + " : " + money(fee) + (paid >= fee ? " payée" : " partiellement ou non payée"));
    }

    // ------------------------------------------------------------------ diagnostic et transport (main vide)
    private static void diagnostic(ServerPlayer rescuer, ServerPlayer target) {
        Injury j = SecoursData.get(rescuer.server).peek(target.getUUID());
        tell(rescuer, "§b— Diagnostic : " + display(rescuer.server, target.getUUID()) + " —");
        tell(rescuer, "§7Vie : §f" + (Math.round(target.getHealth()) / 2.0) + " / " + (Math.round(target.getMaxHealth()) / 2.0) + " cœurs");
        if (j == null || j.empty()) { tell(rescuer, "§aAucune blessure."); return; }
        if (j.coma) tell(rescuer, "§cInconscient : défibrillateur nécessaire.");
        if (j.level > SecoursData.AUCUNE) tell(rescuer, "§e" + SecoursData.NIVEAUX[j.level] + (j.level == SecoursData.GRAVE ? " : trousse de soins nécessaire."
                : j.level == SecoursData.LEGERE ? " : un bandage suffit." : " : trousse de soins, ou attendre la guérison."));
        if (j.zones != 0) tell(rescuer, "§cBalle : " + TaczCompat.describe(j.zones) + ".");
        if (j.bleeding) tell(rescuer, "§cHémorragie en cours : bandage nécessaire.");
    }

    private static void passengersChanged(ServerPlayer carrier) {
        // Le client de celui qui porte n'est pas prévenu tout seul que quelqu'un est monté ou descendu de lui.
        carrier.connection.send(new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(carrier));
    }

    /** Fait descendre un blessé de ce qu'il chevauche (porteur ou véhicule), même s'il est inconscient. */
    private static void dismount(ServerPlayer victim) {
        if (!victim.isPassenger()) return;
        var vehicle = victim.getVehicle();
        RELEASING.add(victim.getUUID());
        if (!fr.minenorth.secours.compat.Mts.isSeated(victim) || !fr.minenorth.secours.compat.Mts.unseat(victim)) victim.stopRiding();
        RELEASING.remove(victim.getUUID());
        if (vehicle instanceof ServerPlayer carrier) { CARRY.remove(carrier.getUUID()); passengersChanged(carrier); }
    }

    private static void pickUp(ServerPlayer carrier, ServerPlayer victim) {
        if (CARRY.containsKey(carrier.getUUID())) { bar(carrier, "§cVous portez déjà quelqu'un."); return; }
        if (carrier.isPassenger()) { bar(carrier, "§cDescendez d'abord de votre véhicule."); return; }
        dismount(victim);
        if (!victim.startRiding(carrier, true)) { bar(carrier, "§cImpossible de porter cette personne."); return; }
        CARRY.put(carrier.getUUID(), victim.getUUID());
        passengersChanged(carrier);
        bar(carrier, "§bVous portez le blessé. Accroupissez-vous pour le poser, ou clic droit sur un véhicule pour l'y installer.");
    }

    private static void putDown(ServerPlayer carrier) {
        UUID id = CARRY.remove(carrier.getUUID());
        ServerPlayer victim = id == null ? null : carrier.server.getPlayerList().getPlayer(id);
        if (victim != null && victim.getVehicle() == carrier) {
            dismount(victim);
            victim.teleportTo(carrier.getX(), carrier.getY(), carrier.getZ());
        }
        passengersChanged(carrier);
    }

    private static void loadInto(ServerPlayer carrier, net.minecraft.world.entity.Entity vehicle) {
        ServerPlayer victim = carrier.server.getPlayerList().getPlayer(CARRY.get(carrier.getUUID()));
        if (victim == null) { CARRY.remove(carrier.getUUID()); return; }
        dismount(victim);
        net.minecraft.world.entity.Entity mts = fr.minenorth.secours.compat.Mts.vehicleOf(vehicle);
        if (mts != null) {
            RELEASING.add(victim.getUUID());   // le passage d'un siège à l'autre ne doit pas être bloqué par le coma
            boolean onStretcher = fr.minenorth.secours.compat.Mts.seatPlayer(victim, mts, fr.minenorth.secours.compat.Mts.STRETCHER_SEAT);
            boolean seated = onStretcher || fr.minenorth.secours.compat.Mts.seatPlayer(victim, mts, null);
            RELEASING.remove(victim.getUUID());
            if (seated) {
                bar(carrier, onStretcher ? "§aBlessé installé sur le brancard. Clic droit sur lui, main vide, pour le reprendre."
                        : "§aBlessé installé dans le véhicule. Clic droit sur lui, main vide, pour le reprendre.");
                return;
            }
        }
        if (victim.startRiding(vehicle, true)) {
            bar(carrier, "§aBlessé installé dans le véhicule. Clic droit sur lui, main vide, pour le reprendre.");
        } else {
            // Ce véhicule n'accepte pas le blessé : on le reprend sur soi.
            if (victim.startRiding(carrier, true)) CARRY.put(carrier.getUUID(), victim.getUUID());
            passengersChanged(carrier);
            bar(carrier, "§cImpossible d'installer le blessé dans ce véhicule.");
        }
    }

    /**
     * Secouriste en service, main vide :
     *   sneak + clic droit sur un joueur        = diagnostic
     *   clic droit sur un joueur inconscient    = le porter
     *   clic droit sur un véhicule en portant   = y installer le blessé
     */
    @SubscribeEvent
    public static void emptyHand(PlayerInteractEvent.EntityInteract e) {
        if (e.getLevel().isClientSide || e.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(e.getEntity() instanceof ServerPlayer rescuer) || !rescuer.getMainHandItem().isEmpty() || !onDuty(rescuer) || isComa(rescuer)) return;
        if (e.getTarget() instanceof ServerPlayer target) {
            if (rescuer.isShiftKeyDown()) diagnostic(rescuer, target);
            else if (isComa(target)) pickUp(rescuer, target);
            else return;
        } else if (CARRY.containsKey(rescuer.getUUID()) && !(e.getTarget() instanceof net.minecraft.world.entity.LivingEntity)) {
            loadInto(rescuer, e.getTarget());
        } else return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** Un joueur inconscient ne descend pas tout seul de celui qui le porte ni d'un véhicule. */
    @SubscribeEvent
    public static void mount(net.minecraftforge.event.entity.EntityMountEvent e) {
        if (!e.isDismounting() || !(e.getEntityMounting() instanceof ServerPlayer p)) return;
        if (RELEASING.contains(p.getUUID()) || !isComa(p)) return;
        if (e.getEntityBeingMounted() != null && e.getEntityBeingMounted().isAlive()) e.setCanceled(true);
    }

    /** Chaque tick : celui qui porte un blessé le pose en s'accroupissant. */
    @SubscribeEvent
    public static void carryTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || CARRY.isEmpty()) return;
        for (Map.Entry<UUID, UUID> en : new ArrayList<>(CARRY.entrySet())) {
            ServerPlayer carrier = e.getServer().getPlayerList().getPlayer(en.getKey());
            ServerPlayer victim = e.getServer().getPlayerList().getPlayer(en.getValue());
            if (carrier == null) { CARRY.remove(en.getKey()); continue; }
            if (victim == null || victim.getVehicle() != carrier) { CARRY.remove(en.getKey()); passengersChanged(carrier); continue; }
            if (carrier.isShiftKeyDown() || isComa(carrier)) putDown(carrier);
        }
    }

    @SubscribeEvent
    public static void useOnPlayer(PlayerInteractEvent.EntityInteract e) {
        if (e.getLevel().isClientSide || e.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(e.getEntity() instanceof ServerPlayer rescuer) || !(e.getTarget() instanceof ServerPlayer target)) return;
        int type = careType(rescuer.getMainHandItem());
        if (type < 0) return;
        startCare(rescuer, target, type);
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** Sneak + clic droit avec un bandage : se soigner soi-même. */
    @SubscribeEvent
    public static void useOnSelf(PlayerInteractEvent.RightClickItem e) {
        if (e.getLevel().isClientSide || e.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(e.getEntity() instanceof ServerPlayer p) || !p.isShiftKeyDown()) return;
        if (careType(p.getMainHandItem()) != BANDAGE) return;
        if (startCare(p, p, BANDAGE)) { e.setCanceled(true); e.setCancellationResult(InteractionResult.SUCCESS); }
    }

    // ------------------------------------------------------------------ boucle (une fois par seconde)
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 20 != 0) return;
        MinecraftServer s = e.getServer();
        SecoursConfig cfg = SecoursConfig.get();
        SecoursData d = SecoursData.get(s);
        long now = System.currentTimeMillis();
        boolean slowSync = s.getTickCount() % 100 == 0;

        tickCares(s, cfg, now);
        tickDefibs(s, cfg, now);
        tickClinics(s, cfg, d, now);

        for (ServerPlayer p : new ArrayList<>(s.getPlayerList().getPlayers())) {
            Injury j = d.peek(p.getUUID());
            boolean changed = false;
            if (j != null) {
                if (j.coma && now >= j.comaDeadline) { hospital(p); continue; }
                if (!j.coma && j.level > SecoursData.AUCUNE && j.level < SecoursData.GRAVE && j.healAt > 0 && now >= j.healAt) {
                    j.level = SecoursData.AUCUNE; j.healAt = 0; j.zones = 0; changed = true;
                    bar(p, "§aVotre blessure est guérie.");
                }
                if (j.bleeding && !j.coma && !exempt(p)) {
                    long next = NEXT_BLEED.getOrDefault(p.getUUID(), 0L);
                    long every = Math.max(1000L, Math.round(cfg.hemorragie_intervalle_secondes * 1000.0
                            / (TaczCompat.has(j.zones, TaczCompat.TORSE) ? cfg.hemorragie_torse_multiplicateur : 1)));
                    if (next == 0) NEXT_BLEED.put(p.getUUID(), now + every);
                    else if (now >= next) {
                        NEXT_BLEED.put(p.getUUID(), now + every);
                        p.hurt(p.damageSources().generic(), 1.0f);
                        bar(p, "§cVous perdez du sang…");
                    }
                } else NEXT_BLEED.remove(p.getUUID());
            }
            if (changed) refresh(p);
            else { applyEffects(p, d.peek(p.getUUID())); if (slowSync && d.peek(p.getUUID()) != null) sync(p); }
        }
    }

    private static void tickCares(MinecraftServer s, SecoursConfig cfg, long now) {
        for (Iterator<Map.Entry<UUID, Care>> it = CARES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Care> en = it.next();
            Care c = en.getValue();
            ServerPlayer rescuer = s.getPlayerList().getPlayer(en.getKey()), target = s.getPlayerList().getPlayer(c.target());
            double max = cfg.distance_soin;
            boolean valid = rescuer != null && target != null && rescuer.level() == target.level() && rescuer.distanceToSqr(target) <= max * max
                    && careType(rescuer.getMainHandItem()) == c.type();
            if (!valid) {
                it.remove();
                if (rescuer != null) bar(rescuer, "§c" + CARE_NAMES[c.type()] + " interrompu : restez à côté, l'objet en main.");
                continue;
            }
            if (now >= c.endMs()) {
                it.remove();
                finishCare(rescuer, target, c.type());
                continue;
            }
            int pct = (int) (100 * (now - c.startMs()) / Math.max(1, c.endMs() - c.startMs()));
            bar(rescuer, "§b" + CARE_NAMES[c.type()] + " en cours… " + pct + " %");
            if (rescuer != target) bar(target, "§b" + CARE_NAMES[c.type()] + " en cours… " + pct + " %");
        }
    }

    private static void tickClinics(MinecraftServer s, SecoursConfig cfg, SecoursData d, long now) {
        for (Iterator<Map.Entry<UUID, Clinic>> it = CLINICS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Clinic> en = it.next();
            Clinic c = en.getValue();
            ServerPlayer p = s.getPlayerList().getPlayer(en.getKey());
            double max = cfg.pnj_distance_max;
            if (p == null || p.distanceToSqr(c.x(), c.y(), c.z()) > max * max) {
                it.remove();
                refund(s, en.getKey(), c.price());
                if (p != null) { tell(p, "§cSoins interrompus : vous vous êtes éloigné. Vous êtes remboursé."); sync(p); }
                continue;
            }
            if (now >= c.endMs()) {
                it.remove();
                Injury j = d.peek(p.getUUID());
                if (j != null && !j.coma) { j.level = SecoursData.AUCUNE; j.healAt = 0; j.zones = 0; j.bleeding = false; }
                d.note(p.getUUID(), "Soigné au centre de soins (" + money(c.price()) + ")");
                tell(p, "§aLes soins sont terminés : vous êtes guéri.");
                refresh(p);
            }
        }
    }

    private static void refund(MinecraftServer s, UUID id, long cents) {
        if (cents > 0) MineNorth.bank().refund(s, id, cents, "secours:soins");
    }

    // ------------------------------------------------------------------ PNJ de soins
    private static ModNetwork.ClinicPacket clinicOffer(ServerPlayer p) {
        SecoursConfig cfg = SecoursConfig.get();
        Injury j = SecoursData.get(p.server).peek(p.getUUID());
        if (j == null || j.empty()) return new ModNetwork.ClinicPacket(0, false, 0, 0, false, "Vous n'avez aucune blessure à soigner.");
        int level = Math.max(j.level, SecoursData.LEGERE);
        long price = SecoursConfig.cents(level == SecoursData.GRAVE ? cfg.pnj_prix_grave_euros : level == SecoursData.MOYENNE ? cfg.pnj_prix_moyenne_euros : cfg.pnj_prix_legere_euros);
        int seconds = Math.max(1, level == SecoursData.GRAVE ? cfg.pnj_duree_grave_secondes : level == SecoursData.MOYENNE ? cfg.pnj_duree_moyenne_secondes : cfg.pnj_duree_legere_secondes);
        String reason = "";
        if (CLINICS.containsKey(p.getUUID())) reason = "Des soins sont déjà en cours.";
        else if (level == SecoursData.GRAVE && cfg.pnj_grave_seulement_sans_secours && secoursOnline(p.server))
            reason = "Blessure grave : des secours sont en service, appelez un pompier ou le SAMU.";
        return new ModNetwork.ClinicPacket(j.level, j.bleeding, price, seconds, reason.isEmpty(), reason);
    }

    /** Commande du PNJ : /soins <joueur>. */
    public static void openClinic(ServerPlayer p) {
        if (isComa(p)) return;
        CLINIC_OPEN.add(p.getUUID());
        ModNetwork.send(p, clinicOffer(p));
    }

    private static void clinicPay(ServerPlayer p) {
        if (!CLINIC_OPEN.remove(p.getUUID()) || isComa(p)) return;
        ModNetwork.ClinicPacket offer = clinicOffer(p);
        if (!offer.allowed()) { tell(p, "§c" + offer.reason()); return; }
        if (offer.price() > 0) {
            PayResult pay = MineNorth.bank().charge(p, offer.price(), "secours:soins");
            if (pay != PayResult.OK) { tell(p, "§cPaiement refusé : " + pay.message()); return; }
        }
        CLINICS.put(p.getUUID(), new Clinic(System.currentTimeMillis() + offer.seconds() * 1000L, p.getX(), p.getY(), p.getZ(), offer.price()));
        tell(p, "§bSoins en cours pendant " + offer.seconds() + " secondes. Restez près du médecin.");
        sync(p);
    }

    // ------------------------------------------------------------------ commandes admin
    public static String adminRevive(ServerPlayer target) {
        Injury j = SecoursData.get(target.server).peek(target.getUUID());
        if (j == null || !j.coma) return MineNorth.displayName(target) + " n'est pas inconscient.";
        wake(target, j);
        tell(target, "§aVous avez été réanimé par un administrateur.");
        refresh(target);
        return MineNorth.displayName(target) + " a été réanimé.";
    }

    public static String adminHeal(ServerPlayer target) {
        SecoursData.get(target.server).injuries.remove(target.getUUID());
        CLINICS.remove(target.getUUID());
        NEXT_BLEED.remove(target.getUUID());
        tell(target, "§aToutes vos blessures ont été retirées par un administrateur.");
        refresh(target);
        return "Blessures de " + MineNorth.displayName(target) + " retirées.";
    }

    public static String setHospital(ServerPlayer p) {
        SecoursData d = SecoursData.get(p.server);
        d.hasHospital = true;
        d.hospitalDim = p.level().dimension().location().toString();
        d.hx = p.getX(); d.hy = p.getY(); d.hz = p.getZ();
        d.setDirty();
        return "Point de réveil de l'hôpital défini à votre position.";
    }

    // ------------------------------------------------------------------ effectifs et tablette
    private static boolean hasTablet(ServerPlayer p) { return p.getInventory().contains(new ItemStack(ModItems.TABLET.get())); }

    public static void giveTablet(ServerPlayer p) {
        if (hasTablet(p)) return;
        ItemStack tablet = new ItemStack(ModItems.TABLET.get());
        if (!p.getInventory().add(tablet)) p.drop(tablet, false);
    }

    public static String setGrade(MinecraftServer s, UUID id, String name, int grade) {
        SecoursData d = SecoursData.get(s);
        ServerPlayer on = s.getPlayerList().getPlayer(id);
        if (grade < 0) {
            if (d.staff.remove(id) == null) return name + " ne fait pas partie des secours.";
            d.onDuty.remove(id);
            d.setDirty();
            if (on != null) tell(on, "§eVous ne faites plus partie des secours.");
            return name + " a été retiré des secours.";
        }
        int g = Math.min(2, grade);
        boolean isNew = !d.staff.containsKey(id);
        d.staff.put(id, g);
        d.setDirty();
        if (on != null) {
            if (isNew) giveTablet(on);
            tell(on, "§bSecours : votre grade est maintenant " + SecoursData.GRADES[g] + ".");
        }
        return name + " est maintenant " + SecoursData.GRADES[g] + ".";
    }

    public static void open(ServerPlayer p) {
        if (rank(p) < 0) { bar(p, "§cCette tablette est réservée aux pompiers et au SAMU."); return; }
        TABLETS.add(p.getUUID());
        sendAlerts(p, "", true);
    }

    static void sendAlerts(ServerPlayer p, String msg, boolean ok) {
        MinecraftServer s = p.server;
        SecoursData d = SecoursData.get(s);
        long now = System.currentTimeMillis();
        List<ModNetwork.Alert> out = new ArrayList<>();
        for (ServerPlayer q : s.getPlayerList().getPlayers()) {
            Injury j = d.peek(q.getUUID());
            // Seuls les joueurs inconscients déclenchent une alerte : un blessé grave doit aller voir les secours lui-même.
            if (j == null || !j.coma) continue;
            int dist = q.level() == p.level() ? (int) Math.sqrt(q.distanceToSqr(p)) : -1;
            out.add(new ModNetwork.Alert(q.getUUID(), display(s, q.getUUID()), j.coma, j.level, j.bleeding,
                    q.blockPosition().getX(), q.blockPosition().getY(), q.blockPosition().getZ(), dist,
                    j.coma ? (int) Math.max(0, (j.comaDeadline - now) / 1000) : 0, j.dispatch, j.zones, ModNetwork.Alert.INJURY));
        }
        if (onDuty(p)) {
            for (SecoursData.Incident inc : d.incidents.values()) {
                int dist = inc.dim.equals(p.level().dimension().location().toString()) ? (int) Math.sqrt(p.distanceToSqr(inc.x + 0.5, inc.y, inc.z + 0.5)) : -1;
                long left = inc.startMs + SecoursConfig.ms(SecoursConfig.get().incendie_duree_max_minutes) - now;
                out.add(new ModNetwork.Alert(inc.id, "Incendie : " + inc.site, false, 0, false, inc.x, inc.y, inc.z, dist,
                        (int) Math.max(0, left / 1000), inc.dispatch, 0, ModNetwork.Alert.FIRE));
            }
        }
        // Inconscients d'abord, puis ceux à qui il reste le moins de temps.
        out.sort(Comparator.comparing((ModNetwork.Alert a) -> !a.coma()).thenComparingInt(ModNetwork.Alert::secondsLeft));
        ModNetwork.send(p, new ModNetwork.TabletPacket(ModNetwork.V_ALERTS, rank(p), msg, ok, onDuty(p), out, List.of(), "", List.of()));
    }

    private static void sendRoster(ServerPlayer p, String msg, boolean ok) {
        MinecraftServer s = p.server;
        SecoursData d = SecoursData.get(s);
        List<ModNetwork.Member> out = new ArrayList<>();
        for (Map.Entry<UUID, Integer> e : d.staff.entrySet()) {
            out.add(new ModNetwork.Member(e.getKey(), display(s, e.getKey()), Math.max(0, Math.min(2, e.getValue())),
                    s.getPlayerList().getPlayer(e.getKey()) != null, d.onDuty.contains(e.getKey())));
        }
        out.sort(Comparator.comparingInt(ModNetwork.Member::grade).thenComparing(m -> m.name().toLowerCase(Locale.ROOT)));
        ModNetwork.send(p, new ModNetwork.TabletPacket(ModNetwork.V_ROSTER, rank(p), msg, ok, onDuty(p), List.of(), out, "", List.of()));
    }

    /** Dossier médical : recherche par nom RP ou pseudo, puis historique du plus récent au plus ancien. */
    private static void sendFile(ServerPlayer p, String query) {
        MinecraftServer s = p.server;
        SecoursData d = SecoursData.get(s);
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        UUID found = null;
        if (!q.isEmpty()) {
            for (Map.Entry<UUID, String> e : d.names.entrySet()) {
                String rp = Compat.rpName(s, e.getKey()).toLowerCase(Locale.ROOT);
                boolean exact = e.getValue().equalsIgnoreCase(q) || rp.equals(q);
                if (exact) { found = e.getKey(); break; }
                if (found == null && (e.getValue().toLowerCase(Locale.ROOT).contains(q) || rp.contains(q))) found = e.getKey();
            }
        }
        List<String> lines = new ArrayList<>();
        String name = "", msg = "";
        if (found == null) msg = q.isEmpty() ? "" : "Aucun citoyen trouvé.";
        else {
            name = display(s, found);
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("dd/MM HH:mm");
            List<String> raw = d.files.getOrDefault(found, List.of());
            for (int i = raw.size() - 1; i >= 0; i--) {
                String[] parts = raw.get(i).split("\\|", 2);
                try { lines.add(fmt.format(new java.util.Date(Long.parseLong(parts[0]))) + "  " + (parts.length > 1 ? parts[1] : "")); }
                catch (RuntimeException ex) { lines.add(raw.get(i)); }
            }
            Injury j = d.peek(found);
            if (j != null && !j.empty()) lines.add(0, "ÉTAT ACTUEL : " + (j.coma ? "inconscient, " : "") + SecoursData.NIVEAUX[j.level].toLowerCase(Locale.ROOT)
                    + (j.bleeding ? ", hémorragie" : "") + (j.zones != 0 ? ", balle : " + TaczCompat.describe(j.zones) : ""));
        }
        ModNetwork.send(p, new ModNetwork.TabletPacket(ModNetwork.V_FILE, rank(p), msg, msg.isEmpty(), onDuty(p), List.of(), List.of(), name, lines));
    }

    public static void handle(ServerPlayer p, ActionPacket k) {
        if (k.action() == ModNetwork.A_CLOSE) { TABLETS.remove(p.getUUID()); CLINIC_OPEN.remove(p.getUUID()); return; }
        if (k.action() == ModNetwork.A_CLINIC_PAY) { clinicPay(p); return; }
        int rank = rank(p);
        if (rank < 0 || !TABLETS.contains(p.getUUID()) || isComa(p)) return;
        MinecraftServer s = p.server;
        SecoursData d = SecoursData.get(s);
        switch (k.action()) {
            case ModNetwork.A_ALERTS -> sendAlerts(p, "", true);
            case ModNetwork.A_ROSTER -> { if (rank == SecoursData.CHEF) sendRoster(p, "", true); }
            case ModNetwork.A_FILE -> sendFile(p, k.a());
            case ModNetwork.A_DUTY -> {
                boolean now = setDuty(p, !onDuty(p));
                sendAlerts(p, now ? "Vous êtes en service : vous recevez les alertes." : "Vous êtes hors service.", true);
            }
            case ModNetwork.A_DISPATCH -> {
                SecoursData.Incident fire = d.incidents.get(k.target());
                if (fire != null) {
                    if (!onDuty(p)) { sendAlerts(p, "Prenez votre service pour intervenir.", false); return; }
                    FireService.dispatch(p, fire);
                    sendAlerts(p, "Vous êtes signalé en route" + (fr.minenorth.secours.compat.MapBridge.available() ? " : guidage activé sur la carte." : "."), true);
                    return;
                }
                ServerPlayer victim = s.getPlayerList().getPlayer(k.target());
                Injury j = victim == null ? null : d.peek(victim.getUUID());
                if (j == null || !j.coma) { sendAlerts(p, "Cette alerte n'est plus active.", false); return; }
                String unit = display(s, p.getUUID());
                j.dispatch = unit;
                d.setDirty();
                tell(victim, "§aUne unité de secours est en route : " + unit + ".");
                tellSecours(s, "§b[Secours] " + unit + " est en route vers " + display(s, victim.getUUID()) + ".");
                sync(victim);
                FireService.guideTo(p, victim);
                sendAlerts(p, "Vous êtes signalé en route" + (fr.minenorth.secours.compat.MapBridge.available() ? " : guidage activé sur la carte." : "."), true);
            }
            case ModNetwork.A_GRADE -> {
                if (rank != SecoursData.CHEF) return;
                UUID id = d.staff.containsKey(k.target()) || d.names.containsKey(k.target()) ? k.target() : null;
                String typed = k.a().trim();
                if (id == null && !typed.isEmpty()) {
                    ServerPlayer on = s.getPlayerList().getPlayerByName(typed);
                    id = on != null ? on.getUUID() : d.byName(typed);
                    if (id == null) for (UUID known : d.names.keySet()) if (Compat.rpName(s, known).equalsIgnoreCase(typed)) { id = known; break; }
                }
                if (id == null) { sendRoster(p, "Joueur introuvable (il doit s'être déjà connecté).", false); return; }
                if (id.equals(p.getUUID())) { sendRoster(p, "Vous ne pouvez pas modifier votre propre grade.", false); return; }
                if (k.n() > 2) return;
                sendRoster(p, setGrade(s, id, display(s, id), k.n()), true);
            }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ connexion / déconnexion
    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        SecoursData d = SecoursData.get(p.server);
        String name = p.getGameProfile().getName();
        if (!name.equals(d.names.get(p.getUUID()))) { d.names.put(p.getUUID(), name); d.setDirty(); }
        applyEffects(p, d.peek(p.getUUID()));
        sync(p);
        broadcastComa(p.server);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (!e.isEndConquered() && HOSPITAL_RESPAWN.remove(p.getUUID())) hospitalArrival(p, "Réveil à l'hôpital après un décès pendant le coma");
        applyEffects(p, SecoursData.get(p.server).peek(p.getUUID()));
        sync(p);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        TABLETS.remove(id); HOSPITAL_RESPAWN.remove(id); CLINIC_OPEN.remove(id); CARES.remove(id); DEFIBS.remove(id); DEFIBS.values().removeIf(d -> d.target().equals(id)); FALLS.remove(id); NEXT_BLEED.remove(id);
        if (e.getEntity() instanceof ServerPlayer leaving) {
            SecoursData.get(leaving.server).onDuty.remove(id);
            FireService.onLogout(id);
            if (CARRY.containsKey(id)) putDown(leaving);   // il portait quelqu'un : on le pose
            dismount(leaving);                              // il était porté ou assis : il redescend avant de partir
        }
        Clinic c = CLINICS.remove(id);
        if (c != null && e.getEntity() instanceof ServerPlayer p) refund(p.server, id, c.price());
    }
}
