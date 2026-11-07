package com.corkboard.client;

import com.corkboard.CorkboardMod;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;

public class ClientSetup {
    public static void init(IEventBus bus) {
        bus.addListener(ClientSetup::registerRenderers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(CorkboardMod.BOARD.get(), BoardRenderer::new);
    }
}
