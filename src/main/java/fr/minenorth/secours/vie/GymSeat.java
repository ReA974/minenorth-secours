package fr.minenorth.secours.vie;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * Siège invisible posé sur une machine de la salle de sport pendant une série : il porte le joueur (assis ou allongé) ou simplement son
 * propriétaire (debout), déclenche les mouvements de bras vus de tous, et disparaît à la fin de la série, si le joueur descend ou s'éloigne.
 * Jamais sauvegardé.
 */
public class GymSeat extends Entity {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(GymSeat.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(GymSeat.class, EntityDataSerializers.INT);

    private BlockPos block = BlockPos.ZERO;
    private Direction facing = Direction.NORTH;
    private long end;
    private int age;

    public GymSeat(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    void setup(BlockPos block, int kind, int owner, long end, Direction facing) {
        this.block = block; this.end = end; this.facing = facing;
        entityData.set(KIND, kind);
        entityData.set(OWNER, owner);
    }

    public int owner() { return entityData.get(OWNER); }

    public GymBlock.Kind kind() {
        GymBlock.Kind[] k = GymBlock.Kind.values();
        return k[Math.max(0, Math.min(k.length - 1, entityData.get(KIND)))];
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(OWNER, -1);
        entityData.define(KIND, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public double getPassengersRidingOffset() { return 0; }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        age++;
        GymBlock.Kind k = kind();
        long now = level().getGameTime();
        ServerPlayer o = level().getEntity(owner()) instanceof ServerPlayer sp ? sp : null;
        boolean over = o == null || !o.isAlive() || o.isSpectator() || now >= end || !(level().getBlockState(block).getBlock() instanceof GymBlock);
        if (!over) {
            if (k.seated) {
                if (age > 5 && getPassengers().isEmpty()) over = true;
            } else {
                double dx = o.getX() - getX(), dz = o.getZ() - getZ();
                if (dx * dx + dz * dz > 2.25 || Math.abs(o.getY() - getY()) > 1.5) over = true;
            }
        }
        if (!over && k.swing > 0 && age % k.swing == 0) {
            o.swing((age / k.swing) % 2 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
        }
        if (over) finish(o, k);
    }

    private void finish(ServerPlayer o, GymBlock.Kind k) {
        if (level().getBlockEntity(block) instanceof GymBlockEntity be && be.end > level().getGameTime()) {
            be.end = level().getGameTime();
            be.setChanged();
        }
        if (o != null && k.seated && o.getVehicle() == this) {
            o.stopRiding();
            Vec3 out = GymBlock.world(block, facing, k.exitDx, k.exitDz, 0);
            o.teleportTo(out.x, block.getY(), out.z);
        }
        discard();
    }
}
