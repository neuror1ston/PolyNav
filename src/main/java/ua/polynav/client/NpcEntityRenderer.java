package ua.polynav.client;

import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;
import ua.polynav.entity.NpcEntity;

public class NpcEntityRenderer extends BipedEntityRenderer<NpcEntity, PlayerEntityModel<NpcEntity>> {
    private static final Identifier STEVE_SKIN = new Identifier("minecraft", "textures/entity/player/wide/steve.png");

    public NpcEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public Identifier getTexture(NpcEntity entity) {
        return STEVE_SKIN;
    }
}
