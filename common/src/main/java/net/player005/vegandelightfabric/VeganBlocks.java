package net.player005.vegandelightfabric;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.player005.vegandelightfabric.fluids.VeganFluids;
import vectorwing.farmersdelight.common.block.WildCropBlock;

import java.util.function.Function;

import static net.player005.vegandelightfabric.VeganDelightMod.getPlatform;

public class VeganBlocks {

    public static final Holder<Block> SOYBEAN_CROP =
        register((props) -> new CropBlock(props) {
            @Override
            protected ItemLike getBaseSeedId() {
                return VeganItems.SOYBEAN.value();
            }
        }, BlockBehaviour.Properties.ofFullCopy(Blocks.WHEAT), "soybean_crop", false);

    public static final Holder<Block> WILD_SOYBEAN = register(
        (props) -> new WildCropBlock(MobEffects.STRENGTH, 12, props), BlockBehaviour.Properties.ofFullCopy(Blocks.ALLIUM),
        "wild_soybean", true
    );

    public static final Holder<Block> POTTED_WILD_SOYBEAN = register(
        (props) -> new FlowerPotBlock(WILD_SOYBEAN.value(), props), BlockBehaviour.Properties.ofFullCopy(Blocks.POTTED_ALLIUM),
        "potted_wild_soybean", false
    );

    public static final Holder<Block> SOYBEAN_BAG = register(
        Block::new, BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_WOOL),
        "soybean_bag", true
    );

    public static final Holder<LiquidBlock> SOYMILK = register(
        properties -> new LiquidBlock(VeganFluids.SOYMILK.get(), properties) {}, BlockBehaviour.Properties.ofFullCopy(Blocks.WATER),
        "soymilk", false);

    public static final Holder<LiquidBlock> APPLESAUCE = register(
            properties -> new LiquidBlock(VeganFluids.APPLESAUCE.get(), properties) {}, BlockBehaviour.Properties.ofFullCopy(Blocks.WATER),
        "applesauce", false);

    public static <T extends Block> Holder<T> register(Function<BlockBehaviour.Properties, T> block, BlockBehaviour.Properties properties, String name, boolean registerItem) {
        ResourceLocation id = ResourceLocation.tryBuild(VeganDelightMod.modID, name);
        assert id != null;
        properties.setId(ResourceKey.create(Registries.BLOCK, id));
        var holder = getPlatform().register(BuiltInRegistries.BLOCK, id, () -> block.apply(properties));

        if (registerItem)
            getPlatform().register(BuiltInRegistries.ITEM, id, () -> new BlockItem(holder.value(), new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))));

        return holder;
    }

    public static Block[] getAllBlockItems() {
        return new Block[] {
            SOYBEAN_BAG.value(), WILD_SOYBEAN.value()
        };
    }

    static void initialise() { }
}
