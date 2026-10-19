package fr.minenorth.secours.vie;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cœur du mod, côté serveur : entraînement (course, nage), repas, repos, toxines, bonus (vitesse de course, endurance, vie), risque de maladie,
 * progression et contagion. Un passage par seconde et par joueur ; une « minute » de jeu = 60 passages.
 */
public final class VieEvents {
    private static final UUID SPRINT_ID = UUID.fromString("7f0c1d1e-6a52-4c11-9b73-0d6a5e2a0001");
    private static final UUID SICK_SPEED_ID = UUID.fromString("7f0c1d1e-6a52-4c11-9b73-0d6a5e2a0002");
    private static final UUID HEALTH_ID = UUID.fromString("7f0c1d1e-6a52-4c11-9b73-0d6a5e2a0003");

    private final Map<UUID, Vec3> last = new HashMap<>();
    /** [distance de course, distance de nage] cumulées (en blocs) avant le prochain gain de forme. */
    private final Map<UUID, double[]> acc = new HashMap<>();
    private final Map<UUID, Float> exhaustion = new HashMap<>();
    private final Map<UUID, int[]> clocks = new HashMap<>();   // {secondes, minutes depuis le dernier risque, secondes de course continue, cooldown souffle}

    // ------------------------------------------------------------------ passage par tick

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        if (!(e.player instanceof ServerPlayer p) || !VieConfig.enabled() || p.isSpectator()) return;
        if (e.phase == TickEvent.Phase.START) {
            exhaustion.put(p.getUUID(), p.getFoodData().getExhaustionLevel());
            return;
        }
        // Endurance : à bonne forme, la course use moins la faim.
        Float prev = exhaustion.get(p.getUUID());
        if (prev != null && p.isSprinting()) {
            float now = p.getFoodData().getExhaustionLevel(), delta = now - prev;
            double reduction = VieConfig.sprintStamina() * Stats.clamp(Vie.stats(p).forme - 20) / 80.0;
            if (delta > 0 && reduction > 0) p.getFoodData().setExhaustion((float) (prev + delta * (1 - reduction)));
        }
        if (p.tickCount % 20 == 0) second(p);
    }

    private void second(ServerPlayer p) {
        Stats s = Vie.stats(p);
        UUID id = p.getUUID();
        int[] c = clocks.computeIfAbsent(id, k -> new int[4]);

        // Entraînement : distance parcourue en courant ou en nageant (jamais en véhicule, ni sur une téléportation).
        Vec3 now = p.position(), before = last.put(id, now);
        double[] a = acc.computeIfAbsent(id, k -> new double[2]);
        if (before != null && !p.isPassenger()) {
            double d = Math.sqrt((now.x - before.x) * (now.x - before.x) + (now.z - before.z) * (now.z - before.z));
            if (d > 0.3 && d <= 12) {
                if (p.isSwimming() || (p.isInWater() && p.isSprinting())) a[1] += d;
                else if (p.isSprinting() && p.onGround()) a[0] += d;
            }
        }
        double units = a[0] + a[1] * 2;
        if (units >= 100) {
            a[0] = 0; a[1] = 0;
            double gain = VieConfig.trainGain() * Math.max(0.15, 1.15 - s.forme / 100.0);
            if (s.hourGain + gain <= VieConfig.trainMaxHour()) {
                s.forme = Stats.clamp(s.forme + gain);
                s.hourGain += gain;
                s.idleMinutes = 0;
                Vie.dirty(p.server);
            }
        }
        // Essoufflement : à très mauvaise forme, une course trop longue ralentit un moment.
        if (p.isSprinting()) c[2]++; else c[2] = 0;
        if (c[3] > 0) c[3]--;
        if (VieConfig.harsh() && c[2] >= 15 && s.forme < 20 && c[3] == 0) {
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 0, true, false));
            p.displayClientMessage(Component.literal("§eVous êtes essoufflé : entraînez-vous pour tenir plus longtemps."), true);
            c[3] = 40;
            c[2] = 0;
        }

        applyAttributes(p, s);
        if (VieConfig.diseases()) sickSecond(p, s, c);

        if (++c[0] >= 60) {
            c[0] = 0;
            minute(p, s, c);
        }
    }

    // ------------------------------------------------------------------ une minute de jeu

    private void minute(ServerPlayer p, Stats s, int[] c) {
        s.idleMinutes += 1;
        s.hourClock += 1;
        if (s.hourClock >= 60) { s.hourClock = 0; s.hourGain = 0; }
        if (s.idleMinutes > 10) s.forme = Stats.clamp(s.forme - VieConfig.decayForme());
        s.nutrition = Stats.clamp(s.nutrition - 0.08 - (p.getFoodData().getFoodLevel() <= 6 ? 0.1 : 0));
        s.repos = Stats.clamp(s.repos - 0.08);
        s.toxines = Stats.clamp(s.toxines - 0.3);
        if (s.boostLeft > 0) { s.boostLeft -= 1; if (s.boostLeft <= 0) s.boost = 0; }
        if (s.wheyLeft > 0) s.wheyLeft = Math.max(0, s.wheyLeft - 1);
        if (s.creaLeft > 0) s.creaLeft = Math.max(0, s.creaLeft - 1);
        if (VieConfig.diseases()) {
            if (!s.disease.isEmpty()) {
                s.diseaseLeft -= 1 + s.immunity() / 100.0;
                if (s.diseaseLeft <= 0) Vie.cure(p);
            } else if (++c[1] >= VieConfig.diseaseCheckMinutes()) {
                c[1] = 0;
                risk(p, s);
            }
        }
        Vie.dirty(p.server);
    }

    /** Risque de contamination : dépend de l'immunité (immunisé au-dessus du seuil), de la météo, du froid, de la fatigue et des malades proches. */
    private void risk(ServerPlayer p, Stats s) {
        double imm = s.immunity();
        if (imm >= VieConfig.immuneThreshold()) return;
        ServerLevel level = p.serverLevel();
        BlockPos pos = p.blockPosition();
        double chance = VieConfig.diseaseBase();
        if (level.isRainingAt(pos)) chance += 0.08;
        if (level.getBiome(pos).value().coldEnoughToSnow(pos)) chance += 0.05;
        if (s.repos < 25) chance += 0.05;
        if (s.toxines > 30) chance += 0.05;
        if (VieConfig.contagion()) {
            for (ServerPlayer o : level.getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(4))) {
                if (o == p) continue;
                Disease d = Vie.disease(Vie.stats(o));
                if (d != null && d.contagious) { chance += 0.18; break; }
            }
        }
        chance *= Math.pow(1 - imm / 100.0, 1.5);
        if (level.random.nextDouble() >= chance) return;
        double r = level.random.nextDouble();
        Vie.infect(p, r < 0.6 ? Disease.RHUME : r < 0.85 ? Disease.GRIPPE : Disease.GASTRO);
    }

    // ------------------------------------------------------------------ maladie : effets chaque seconde

    private void sickSecond(ServerPlayer p, Stats s, int[] c) {
        Disease d = Vie.disease(s);
        if (d == null) return;
        long t = p.serverLevel().getGameTime();
        if (VieConfig.harsh()) {
            if (t % 40 == 0) for (Disease.Fx fx : d.effects) p.addEffect(new MobEffectInstance(fx.effect(), 100, fx.level(), true, false));
            if ((d == Disease.GASTRO || d == Disease.INTOXICATION) && t % 1200 == 0) p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, true, false));
        }
        if (d.contagious && t % 1500 == 0) {   // toux / éternuement : bruit, particules, et message au malade
            ServerLevel level = p.serverLevel();
            level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 0.9f, 1.5f);
            level.sendParticles(ParticleTypes.SNEEZE, p.getX(), p.getEyeY() - 0.2, p.getZ(), 6, 0.2, 0.1, 0.2, 0.02);
        }
    }

    // ------------------------------------------------------------------ bonus et malus (attributs)

    private static void modifier(AttributeInstance attr, UUID id, String name, double value, AttributeModifier.Operation op) {
        if (attr == null) return;
        AttributeModifier old = attr.getModifier(id);
        if (Math.abs(value) < 1e-4) {
            if (old != null) attr.removeModifier(id);
            return;
        }
        if (old != null && Math.abs(old.getAmount() - value) < 1e-4) return;
        if (old != null) attr.removeModifier(id);
        attr.addTransientModifier(new AttributeModifier(id, name, value, op));
    }

    private void applyAttributes(ServerPlayer p, Stats s) {
        Disease d = Vie.disease(s);
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        boolean sick = d != null;   // malade : les bonus sont suspendus (en mode FUN, c'est le seul inconvénient)
        double sprint = p.isSprinting() && !sick ? VieConfig.sprintBonus() * Stats.clamp(s.forme - 30) / 70.0 : 0;
        modifier(speed, SPRINT_ID, "Forme physique", Math.round(sprint * 1000) / 1000.0, AttributeModifier.Operation.MULTIPLY_BASE);
        modifier(speed, SICK_SPEED_ID, "Maladie", d == null || !VieConfig.harsh() ? 0 : d.speed, AttributeModifier.Operation.MULTIPLY_BASE);
        double hp = sick ? 0 : VieConfig.healthBonus() * Stats.clamp(s.forme - 40) / 60.0 * Stats.clamp(s.nutrition - 40) / 60.0;
        if (d != null && d.healthPenalty > 0 && VieConfig.harsh()) hp -= VieConfig.sickPenaltyHp();
        hp = Math.round(hp * 2) / 2.0;
        AttributeInstance max = p.getAttribute(Attributes.MAX_HEALTH);
        modifier(max, HEALTH_ID, "Mode de vie", hp, AttributeModifier.Operation.ADDITION);
        if (max != null && p.getHealth() > max.getValue()) p.setHealth((float) max.getValue());
    }

    // ------------------------------------------------------------------ repas, toxines, sommeil, mort

    @SubscribeEvent
    public void onEat(LivingEntityUseItemEvent.Finish e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !VieConfig.enabled()) return;
        ItemStack st = e.getItem();
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(st.getItem());
        if (key == null) return;
        String id = key.toString();
        Stats s = Vie.stats(p);
        if (VieConfig.toxic().contains(id)) {
            s.toxines = Stats.clamp(s.toxines + 12);
            Vie.dirty(p.server);
            return;
        }
        if (!st.getItem().isEdible()) return;
        double factor = s.lastFoods.contains(id) ? 0.3 : 1.2;
        int recent = 0;
        for (String f : s.lastFoods) if (f.equals(id)) recent++;
        if (recent > 0 && factor > 0.3) factor = 0.6;
        if (VieConfig.healthy().contains(id)) s.nutrition = Stats.clamp(s.nutrition + 4 * factor);
        else if (VieConfig.junk().contains(id)) s.nutrition = Stats.clamp(s.nutrition - 3);
        else s.nutrition = Stats.clamp(s.nutrition + 1 * factor);
        s.lastFoods.addLast(id);
        while (s.lastFoods.size() > 6) s.lastFoods.removeFirst();
        Vie.dirty(p.server);
        if (VieConfig.diseases() && VieConfig.risky().contains(id) && s.disease.isEmpty()) {
            double imm = s.immunity();
            if (imm < VieConfig.immuneThreshold() && p.serverLevel().random.nextDouble() < 0.4 * (1 - imm / 100.0)) Vie.infect(p, Disease.INTOXICATION);
        }
    }

    @SubscribeEvent
    public void onWake(PlayerWakeUpEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !VieConfig.enabled() || e.wakeImmediately()) return;
        Stats s = Vie.stats(p);
        s.repos = Stats.clamp(s.repos + 50);
        if (!s.disease.isEmpty()) s.diseaseLeft -= 3;
        Vie.dirty(p.server);
        p.displayClientMessage(Component.literal("§aUne bonne nuit de sommeil : vous êtes reposé."), true);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !VieConfig.enabled()) return;
        Stats s = Vie.stats(p);
        s.forme = Stats.clamp(s.forme * (1 - VieConfig.deathLoss()));
        s.toxines = 0;
        if (VieConfig.cureOnDeath() && !s.disease.isEmpty()) { s.disease = ""; s.diseaseLeft = 0; }
        Vie.dirty(p.server);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        last.remove(id);
        acc.remove(id);
        exhaustion.remove(id);
        clocks.remove(id);
        VieNet.forget(id);
    }
}
