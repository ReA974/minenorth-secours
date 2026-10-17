package fr.minenorth.secours.vie;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Réseau : le client demande son état (écran « Santé »), le serveur répond. Rien d'autre n'est accepté du client. */
public final class VieNet {
    private VieNet() {}

    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MineNorthVie.NAMESPACE, "network"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static final Map<UUID, Long> LAST = new HashMap<>();

    public static void register() {
        CHANNEL.registerMessage(0, Request.class, Request::encode, Request::decode, Request::handle);
        CHANNEL.registerMessage(1, State.class, State::encode, State::decode, State::handle);
    }

    static void forget(UUID id) { LAST.remove(id); }

    /** Envoie son état au joueur ; open = ouvre l'écran, sinon met seulement à jour l'écran ouvert. */
    public static void send(ServerPlayer p, boolean open) {
        Stats s = Vie.stats(p);
        Disease d = Vie.disease(s);
        double sprint = VieConfig.sprintBonus() * Stats.clamp(s.forme - 30) / 70.0 * 100;
        double hp = VieConfig.healthBonus() * Stats.clamp(s.forme - 40) / 60.0 * Stats.clamp(s.nutrition - 40) / 60.0;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new State(open, (float) s.forme, (float) s.nutrition, (float) s.repos, (float) s.toxines,
                (float) s.immunity(), d == null ? "" : d.label, d == null ? 0 : (int) Math.ceil(s.diseaseLeft), (float) sprint, (float) hp,
                VieConfig.immuneThreshold(), (int) Math.ceil(s.boostLeft)));
    }

    /** Client -> serveur : demande de l'état (limitée à une fois par demi-seconde). */
    public record Request(boolean open) {
        static void encode(Request r, FriendlyByteBuf b) { b.writeBoolean(r.open); }
        static Request decode(FriendlyByteBuf b) { return new Request(b.readBoolean()); }
        static void handle(Request r, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                ServerPlayer p = c.get().getSender();
                if (p == null || !VieConfig.enabled()) return;
                long now = p.serverLevel().getGameTime();
                Long prev = LAST.put(p.getUUID(), now);
                if (prev != null && now - prev < 10) return;
                send(p, r.open);
            });
            c.get().setPacketHandled(true);
        }
    }

    /** Serveur -> client : état affiché par l'écran. */
    public record State(boolean open, float forme, float nutrition, float repos, float toxines, float immunity, String disease, int minutesLeft,
                        float sprintPct, float hpBonus, int immuneThreshold, int boostMinutes) {
        static void encode(State s, FriendlyByteBuf b) {
            b.writeBoolean(s.open);
            b.writeFloat(s.forme); b.writeFloat(s.nutrition); b.writeFloat(s.repos); b.writeFloat(s.toxines); b.writeFloat(s.immunity);
            b.writeUtf(s.disease, 64); b.writeVarInt(s.minutesLeft); b.writeFloat(s.sprintPct); b.writeFloat(s.hpBonus);
            b.writeVarInt(s.immuneThreshold); b.writeVarInt(s.boostMinutes);
        }
        static State decode(FriendlyByteBuf b) {
            return new State(b.readBoolean(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readUtf(64), b.readVarInt(),
                    b.readFloat(), b.readFloat(), b.readVarInt(), b.readVarInt());
        }
        static void handle(State s, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> fr.minenorth.secours.vie.client.SanteScreen.receive(s)));
            c.get().setPacketHandled(true);
        }
    }
}
