package fr.minenorth.secours.network;

import fr.minenorth.secours.MineNorthSecours;
import fr.minenorth.secours.SecoursService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class ModNetwork {
    private ModNetwork() {}

    public static final int V_ALERTS = 0, V_ROSTER = 1, V_FILE = 2;
    public static final int A_ALERTS = 1, A_ROSTER = 2, A_DISPATCH = 3, A_GRADE = 4, A_CLOSE = 5, A_CLINIC_PAY = 6, A_DUTY = 7, A_FILE = 8;
    public static final UUID NONE = new UUID(0, 0);

    private static final String PROTOCOL = "4";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MineNorthSecours.MOD_ID, "network"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static int id = 0;

    public static void register() {
        CHANNEL.registerMessage(id++, StatePacket.class, StatePacket::encode, StatePacket::decode, StatePacket::handle);
        CHANNEL.registerMessage(id++, TabletPacket.class, TabletPacket::encode, TabletPacket::decode, TabletPacket::handle);
        CHANNEL.registerMessage(id++, ClinicPacket.class, ClinicPacket::encode, ClinicPacket::decode, ClinicPacket::handle);
        CHANNEL.registerMessage(id++, ActionPacket.class, ActionPacket::encode, ActionPacket::decode, ActionPacket::handle);
        CHANNEL.registerMessage(id++, ComaListPacket.class, ComaListPacket::encode, ComaListPacket::decode, ComaListPacket::handle);
        CHANNEL.registerMessage(id++, DefibStartPacket.class, DefibStartPacket::encode, DefibStartPacket::decode, DefibStartPacket::handle);
        CHANNEL.registerMessage(id++, DefibResultPacket.class, DefibResultPacket::encode, DefibResultPacket::decode, DefibResultPacket::handle);
        CHANNEL.registerMessage(id++, WakePacket.class, WakePacket::encode, WakePacket::decode, WakePacket::handle);
    }

    public static void send(ServerPlayer p, Object packet) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), packet); }

    /** Liste complète des joueurs inconscients, envoyée à tout le monde : chaque client sait qui dessiner couché. */
    public record ComaListPacket(List<UUID> players, boolean customRender) {
        static void encode(ComaListPacket p, FriendlyByteBuf b) { b.writeCollection(p.players, (x, v) -> x.writeUUID(v)); b.writeBoolean(p.customRender); }
        static ComaListPacket decode(FriendlyByteBuf b) { return new ComaListPacket(b.readList(FriendlyByteBuf::readUUID), b.readBoolean()); }
        static void handle(ComaListPacket p, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> fr.minenorth.secours.client.ClientNetworkHandler.comaList(p)));
            c.get().setPacketHandled(true);
        }
    }

    /** Ouvre le mini-jeu du défibrillateur chez le secouriste. Les battements se déduisent du seed (DefibScore.beats). */
    public record DefibStartPacket(long seed, int beats) {
        static void encode(DefibStartPacket p, FriendlyByteBuf b) { b.writeLong(p.seed); b.writeVarInt(p.beats); }
        static DefibStartPacket decode(FriendlyByteBuf b) { return new DefibStartPacket(b.readLong(), b.readVarInt()); }
        static void handle(DefibStartPacket p, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> fr.minenorth.secours.client.ClientNetworkHandler.defib(p)));
            c.get().setPacketHandled(true);
        }
    }

    /** Fin du mini-jeu : frappes en ms depuis l'ouverture de l'écran, ou abandon. Le serveur recalcule la précision. */
    public record DefibResultPacket(boolean cancelled, long[] taps) {
        public static final int MAX_TAPS = 64;
        static void encode(DefibResultPacket p, FriendlyByteBuf b) {
            b.writeBoolean(p.cancelled); b.writeVarInt(p.taps.length);
            for (long t : p.taps) b.writeVarLong(t);
        }
        static DefibResultPacket decode(FriendlyByteBuf b) {
            boolean cancelled = b.readBoolean();
            int n = b.readVarInt();
            if (n < 0 || n > MAX_TAPS) throw new IllegalArgumentException("trop de frappes");
            long[] taps = new long[n];
            for (int i = 0; i < n; i++) taps[i] = b.readVarLong();
            return new DefibResultPacket(cancelled, taps);
        }
        static void handle(DefibResultPacket p, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> { ServerPlayer sp = c.get().getSender(); if (sp != null) SecoursService.defibResult(sp, p); });
            c.get().setPacketHandled(true);
        }
    }

    /** Touche « se réveiller à l'hôpital » (inconscient, aucun secouriste en service). Le serveur revérifie tout. */
    public record WakePacket() {
        static void encode(WakePacket p, FriendlyByteBuf b) { }
        static WakePacket decode(FriendlyByteBuf b) { return new WakePacket(); }
        static void handle(WakePacket p, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> { ServerPlayer sp = c.get().getSender(); if (sp != null) SecoursService.wake(sp); });
            c.get().setPacketHandled(true);
        }
    }

    /** État de santé du joueur lui-même : sert à l'affichage (HUD) et à la pose au sol. Durées en secondes restantes. */
    public record StatePacket(int level, int healSeconds, boolean bleeding, boolean coma, int comaSeconds, String dispatch,
                              int careSeconds, String pose, int zones, boolean rescuers) {
        static void encode(StatePacket p, FriendlyByteBuf b) {
            b.writeVarInt(p.level); b.writeVarInt(p.healSeconds); b.writeBoolean(p.bleeding); b.writeBoolean(p.coma);
            b.writeVarInt(p.comaSeconds); b.writeUtf(p.dispatch); b.writeVarInt(p.careSeconds); b.writeUtf(p.pose); b.writeVarInt(p.zones); b.writeBoolean(p.rescuers);
        }
        static StatePacket decode(FriendlyByteBuf b) {
            return new StatePacket(b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readVarInt(), b.readUtf(), b.readVarInt(), b.readUtf(), b.readVarInt(), b.readBoolean());
        }
        static void handle(StatePacket p, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> fr.minenorth.secours.client.ClientNetworkHandler.state(p)));
            c.get().setPacketHandled(true);
        }
    }

    public record Alert(UUID id, String name, boolean coma, int level, boolean bleeding, int x, int y, int z, int distance,
                        int secondsLeft, String dispatch, int zones, int kind) {
        public static final int INJURY = 0, FIRE = 1;
        static void encode(FriendlyByteBuf b, Alert a) {
            b.writeUUID(a.id); b.writeUtf(a.name); b.writeBoolean(a.coma); b.writeVarInt(a.level); b.writeBoolean(a.bleeding);
            b.writeInt(a.x); b.writeInt(a.y); b.writeInt(a.z); b.writeInt(a.distance); b.writeVarInt(a.secondsLeft); b.writeUtf(a.dispatch); b.writeVarInt(a.zones); b.writeVarInt(a.kind);
        }
        static Alert decode(FriendlyByteBuf b) {
            return new Alert(b.readUUID(), b.readUtf(), b.readBoolean(), b.readVarInt(), b.readBoolean(),
                    b.readInt(), b.readInt(), b.readInt(), b.readInt(), b.readVarInt(), b.readUtf(), b.readVarInt(), b.readVarInt());
        }
    }
    public record Member(UUID id, String name, int grade, boolean online, boolean duty) {
        static void encode(FriendlyByteBuf b, Member m) { b.writeUUID(m.id); b.writeUtf(m.name); b.writeVarInt(m.grade); b.writeBoolean(m.online); b.writeBoolean(m.duty); }
        static Member decode(FriendlyByteBuf b) { return new Member(b.readUUID(), b.readUtf(), b.readVarInt(), b.readBoolean(), b.readBoolean()); }
    }

    /** distance = -1 quand la victime est dans une autre dimension. */
    public record TabletPacket(int view, int grade, String message, boolean ok, boolean onDuty, List<Alert> alerts, List<Member> members,
                               String fileName, List<String> fileLines) {
        static void encode(TabletPacket p, FriendlyByteBuf b) {
            b.writeVarInt(p.view); b.writeVarInt(p.grade); b.writeUtf(p.message); b.writeBoolean(p.ok); b.writeBoolean(p.onDuty);
            b.writeCollection(p.alerts, Alert::encode); b.writeCollection(p.members, Member::encode);
            b.writeUtf(p.fileName); b.writeCollection(p.fileLines, (x, v) -> x.writeUtf(v));
        }
        static TabletPacket decode(FriendlyByteBuf b) {
            return new TabletPacket(b.readVarInt(), b.readVarInt(), b.readUtf(), b.readBoolean(), b.readBoolean(),
                    b.readList(Alert::decode), b.readList(Member::decode), b.readUtf(), b.readList(FriendlyByteBuf::readUtf));
        }
        static void handle(TabletPacket p, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> fr.minenorth.secours.client.ClientNetworkHandler.tablet(p)));
            c.get().setPacketHandled(true);
        }
    }

    /** Menu du PNJ de soins. allowed = false : le PNJ ne peut pas soigner (voir reason). */
    public record ClinicPacket(int level, boolean bleeding, long price, int seconds, boolean allowed, String reason) {
        static void encode(ClinicPacket p, FriendlyByteBuf b) {
            b.writeVarInt(p.level); b.writeBoolean(p.bleeding); b.writeLong(p.price); b.writeVarInt(p.seconds); b.writeBoolean(p.allowed); b.writeUtf(p.reason);
        }
        static ClinicPacket decode(FriendlyByteBuf b) {
            return new ClinicPacket(b.readVarInt(), b.readBoolean(), b.readLong(), b.readVarInt(), b.readBoolean(), b.readUtf());
        }
        static void handle(ClinicPacket p, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> fr.minenorth.secours.client.ClientNetworkHandler.clinic(p)));
            c.get().setPacketHandled(true);
        }
    }

    public record ActionPacket(int action, UUID target, String a, int n) {
        static void encode(ActionPacket p, FriendlyByteBuf b) { b.writeVarInt(p.action); b.writeUUID(p.target); b.writeUtf(p.a, 64); b.writeInt(p.n); }
        static ActionPacket decode(FriendlyByteBuf b) { return new ActionPacket(b.readVarInt(), b.readUUID(), b.readUtf(64), b.readInt()); }
        static void handle(ActionPacket p, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> { ServerPlayer sp = c.get().getSender(); if (sp != null) SecoursService.handle(sp, p); });
            c.get().setPacketHandled(true);
        }
    }
}
