package ua.polynav;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.polynav.command.NavMeshCommand;
import ua.polynav.command.NpcCommand;
import ua.polynav.config.NavMeshConfig;
import ua.polynav.entity.NpcEntity;
import ua.polynav.item.NavMeshWandItem;
import ua.polynav.navmesh.NavMeshManager;
import ua.polynav.navmesh.pathfinding.AsyncPathProcessor;

public class NpcNavsMod implements ModInitializer {
    public static final String MOD_ID = "polynav";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static NavMeshConfig CONFIG;

    public static final EntityType<NpcEntity> NPC_ENTITY_TYPE = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(MOD_ID, "npc"),
            FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, NpcEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
                    .build()
    );

    public static final Item NAVMESH_WAND = Registry.register(
            Registries.ITEM,
            new Identifier(MOD_ID, "navmesh_wand"),
            new NavMeshWandItem(new Item.Settings().maxCount(1))
    );

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing NPC Navs mod...");

        CONFIG = NavMeshConfig.load();
        NavMeshManager.init(CONFIG);
        AsyncPathProcessor.init(CONFIG);

        // Register Entity Attributes
        FabricDefaultAttributeRegistry.register(NPC_ENTITY_TYPE, NpcEntity.createNpcAttributes());

        // Register Commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            NavMeshCommand.register(dispatcher);
            NpcCommand.register(dispatcher);
        });

        // Register Server Lifecycle Events
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            NavMeshManager.getInstance().onServerStarted(server);
            AsyncPathProcessor.init(CONFIG != null ? CONFIG : NavMeshConfig.load());
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            AsyncPathProcessor.getInstance().shutdown();
        });

        // Left-click with wand sets Pos1
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) {
                if (player.getStackInHand(hand).getItem() instanceof NavMeshWandItem) {
                    NavMeshWandItem.setPos1(serverPlayer, pos);
                    return ActionResult.SUCCESS; // Prevent breaking block
                }
            }
            return ActionResult.PASS;
        });

        // Add wand to Tools item group
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(NAVMESH_WAND);
        });

        LOGGER.info("NPC Navs initialized successfully.");
    }
}
