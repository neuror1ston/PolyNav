package ua.polynav.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import ua.polynav.NpcNavsMod;

@Environment(EnvType.CLIENT)
public class NpcNavsClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(NpcNavsMod.NPC_ENTITY_TYPE, NpcEntityRenderer::new);

        // Continuous 3D route line renderer
        PathRenderer.init();
        WorldRenderEvents.AFTER_ENTITIES.register(PathRenderer::render);
    }
}
