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

    private static final String PROTOCOL = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MineNorthVie.NAMESPACE, "network"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static final Map<UUID, Long> LAST = new HashMap<>();

    public static void register() {
        CHANNEL.registerMessage(0, Request.class, Request::encode, Request::decode, Request::handle);
        CHANNEL.registerMessage(1, State.class, State::encode, State::decode, State::handle);
        CHANNEL.registerMessage(2, GymView.class, GymView::encode, GymView::decode, GymView::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3, GymAction.class, GymAction::encode, GymAction::decode, GymAction::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
    }

    static void forget(UUID id) { LAST.remove(id); GymAdmin.forget(id); }

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

    /** Client -> serveur : action sur un portique / une borne (le serveur revérifie tout). */
    public record GymAction(net.minecraft.core.BlockPos pos, int action, int a, int b, UUID uuid) {
        static void encode(GymAction m, FriendlyByteBuf b) { b.writeBlockPos(m.pos); b.writeVarInt(m.action); b.writeInt(m.a); b.writeInt(m.b); b.writeUUID(m.uuid); }
        static GymAction decode(FriendlyByteBuf b) { return new GymAction(b.readBlockPos(), b.readVarInt(), b.readInt(), b.readInt(), b.readUUID()); }
        static void handle(GymAction m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                ServerPlayer p = c.get().getSender();
                if (p != null) GymAdmin.handle(p, m);
            });
            c.get().setPacketHandled(true);
        }
    }

    /** Serveur -> client : réglages et abonnés d'un équipement de salle. */
    public record GymView(net.minecraft.core.BlockPos pos, int company, String companyName, int priceCents, int days, String msg,
                          java.util.List<Company> companies, java.util.List<Sub> subs, java.util.List<Near> near) {
        public record Company(int id, String name) {}
        public record Sub(UUID id, String name, long end) {}
        public record Near(UUID id, String name) {}

        static void encode(GymView v, FriendlyByteBuf b) {
            b.writeBlockPos(v.pos); b.writeVarInt(v.company); b.writeUtf(v.companyName, 64); b.writeVarInt(v.priceCents); b.writeVarInt(v.days); b.writeUtf(v.msg, 160);
            b.writeVarInt(v.companies.size());
            for (Company c : v.companies) { b.writeVarInt(c.id); b.writeUtf(c.name, 64); }
            b.writeVarInt(v.subs.size());
            for (Sub s : v.subs) { b.writeUUID(s.id); b.writeUtf(s.name, 40); b.writeLong(s.end); }
            b.writeVarInt(v.near.size());
            for (Near n : v.near) { b.writeUUID(n.id); b.writeUtf(n.name, 40); }
        }
        static GymView decode(FriendlyByteBuf b) {
            var pos = b.readBlockPos(); int company = b.readVarInt(); String cn = b.readUtf(64); int price = b.readVarInt(), days = b.readVarInt(); String msg = b.readUtf(160);
            java.util.List<Company> cs = new java.util.ArrayList<>(); int n = Math.min(b.readVarInt(), 64);
            for (int i = 0; i < n; i++) cs.add(new Company(b.readVarInt(), b.readUtf(64)));
            java.util.List<Sub> ss = new java.util.ArrayList<>(); n = Math.min(b.readVarInt(), 128);
            for (int i = 0; i < n; i++) ss.add(new Sub(b.readUUID(), b.readUtf(40), b.readLong()));
            java.util.List<Near> ns = new java.util.ArrayList<>(); n = Math.min(b.readVarInt(), 64);
            for (int i = 0; i < n; i++) ns.add(new Near(b.readUUID(), b.readUtf(40)));
            return new GymView(pos, company, cn, price, days, msg, cs, ss, ns);
        }
        static void handle(GymView v, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> fr.minenorth.secours.vie.client.GymAdminScreen.receive(v)));
            c.get().setPacketHandled(true);
        }
    }
}
