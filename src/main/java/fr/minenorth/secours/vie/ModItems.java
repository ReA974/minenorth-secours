package fr.minenorth.secours.vie;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.List;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MineNorthVie.NAMESPACE);

    /** Objet de soin à usage unique : action côté serveur, délai de réutilisation. */
    private static final class Care extends Item {
        private final String tip;
        private final int kind;

        Care(int kind, String tip) {
            super(new Item.Properties().stacksTo(16));
            this.kind = kind;
            this.tip = tip;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack st = player.getItemInHand(hand);
            if (level.isClientSide || !(player instanceof ServerPlayer p)) return InteractionResultHolder.success(st);
            if (p.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(st);
            Stats s = Vie.stats(p);
            switch (kind) {
                case 0 -> {   // médicament : réduit la durée de la maladie
                    if (s.disease.isEmpty()) { p.displayClientMessage(Component.literal("§7Vous n'êtes pas malade."), true); return InteractionResultHolder.fail(st); }
                    s.diseaseLeft *= 0.4;
                    if (s.diseaseLeft < 1) Vie.cure(p);
                    Vie.boost(p, 8, 10);
                    p.displayClientMessage(Component.literal("§aLe médicament fait effet : vous allez mieux."), true);
                }
                case 1 -> {   // vitamines
                    Vie.boost(p, 15, 30);
                    p.displayClientMessage(Component.literal("§aVitamines prises : immunité renforcée pendant 30 minutes."), true);
                }
                default -> {  // vaccin
                    Vie.boost(p, 25, 180);
                    p.displayClientMessage(Component.literal("§aVaccin fait : immunité fortement renforcée pendant 3 heures de jeu."), true);
                }
            }
            level.playSound(null, p.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.7f, 1.2f);
            p.getCooldowns().addCooldown(this, 40);
            if (!p.getAbilities().instabuild) st.shrink(1);
            return InteractionResultHolder.consume(st);
        }

        @Override
        public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
            tip.add(Component.literal("§8" + this.tip));
        }
    }

    /** Compléments de sport (boisson, 32 ticks) : whey +40 % de forme gagnée 20 min, créatine +25 % 30 min ; 45 s entre deux prises. */
    private static final class Supplement extends Item {
        private final boolean whey;

        Supplement(boolean whey) {
            super(new Item.Properties().stacksTo(16));
            this.whey = whey;
        }

        @Override public int getUseDuration(ItemStack st) { return 32; }

        @Override public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack st) { return net.minecraft.world.item.UseAnim.DRINK; }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack st = player.getItemInHand(hand);
            if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(st);
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(st);
        }

        @Override
        public ItemStack finishUsingItem(ItemStack st, Level level, net.minecraft.world.entity.LivingEntity entity) {
            if (!level.isClientSide && entity instanceof ServerPlayer p) {
                Stats s = Vie.stats(p);
                if (whey) {
                    s.wheyLeft = 20;
                    s.nutrition = Stats.clamp(s.nutrition + 6);
                    p.displayClientMessage(Component.literal("§aWhey bue : +40 % de forme gagnée en salle pendant 20 minutes."), true);
                } else {
                    s.creaLeft = 30;
                    p.displayClientMessage(Component.literal("§aCréatine prise : +25 % de forme gagnée en salle pendant 30 minutes."), true);
                }
                Vie.dirty(p.server);
                level.playSound(null, p.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.8f, 0.9f);
                p.getCooldowns().addCooldown(this, 900);
                if (!p.getAbilities().instabuild) st.shrink(1);
            }
            return st;
        }

        @Override
        public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
            tip.add(Component.literal(whey ? "§8Clic droit maintenu : +40 % de forme en salle (20 min)" : "§8Clic droit maintenu : +25 % de forme en salle (30 min)"));
        }
    }

    public static final RegistryObject<Item> WHEY = ITEMS.register("whey", () -> new Supplement(true));
    public static final RegistryObject<Item> CREATINE = ITEMS.register("creatine", () -> new Supplement(false));

    public static final RegistryObject<Item> MEDICAMENT = ITEMS.register("medicament", () -> new Care(0, "Clic droit : réduit fortement la durée d'une maladie"));
    public static final RegistryObject<Item> VITAMINES = ITEMS.register("vitamines", () -> new Care(1, "Clic droit : immunité renforcée 30 minutes"));
    public static final RegistryObject<Item> VACCIN = ITEMS.register("vaccin", () -> new Care(2, "Clic droit : immunité fortement renforcée 3 heures"));

    public static final CreativeModeTab.DisplayItemsGenerator TAB_ITEMS = (params, out) -> {
        out.accept(MEDICAMENT.get());
        out.accept(VITAMINES.get());
        out.accept(VACCIN.get());
        out.accept(WHEY.get());
        out.accept(CREATINE.get());
        for (var it : ModBlocks.GYM_ITEMS) out.accept(it.get());
        out.accept(ModBlocks.GATE_ITEM.get());
        out.accept(ModBlocks.KIOSK_ITEM.get());
    };

    public static void register(IEventBus modBus) { ModBlocks.register(modBus); ModEntities.register(modBus); ITEMS.register(modBus); }
}
