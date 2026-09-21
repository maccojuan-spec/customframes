package com.customframes;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomFramesMod implements ModInitializer {
    public static final String MOD_ID = "customframes";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Block FRAME_BLOCK;
    public static Item FRAME_ITEM;
    public static BlockEntityType<CustomFrameBlockEntity> FRAME_BLOCK_ENTITY;

    @Override
    public void onInitialize() {
        Identifier id = Identifier.of(MOD_ID, "custom_frame");

        RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, id);
        FRAME_BLOCK = Registry.register(
                Registries.BLOCK,
                blockKey,
                new CustomFrameBlock(AbstractBlock.Settings.create()
                        .registryKey(blockKey)
                        .strength(0.5f)
                        .sounds(BlockSoundGroup.WOOD)
                        .nonOpaque()));

        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, id);
        FRAME_ITEM = Registry.register(
                Registries.ITEM,
                itemKey,
                new BlockItem(FRAME_BLOCK, new Item.Settings()
                        .registryKey(itemKey)
                        .useBlockPrefixedTranslationKey()));

        FRAME_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                id,
                FabricBlockEntityTypeBuilder.<CustomFrameBlockEntity>create(CustomFrameBlockEntity::new, FRAME_BLOCK).build());

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(FRAME_ITEM));

        Payloads.registerTypes();
        ServerImages.init();
    }
}
