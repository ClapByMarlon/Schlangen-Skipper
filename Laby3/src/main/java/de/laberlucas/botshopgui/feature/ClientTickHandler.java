package de.laberlucas.botshopgui.feature;

import de.laberlucas.botshopgui.BotShopAddon;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class ClientTickHandler {

    private final BotShopAddon addon;

    public ClientTickHandler(BotShopAddon addon) {
        this.addon = addon;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onMouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (addon.protectBotshopInput(event.gui)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        addon.tickClient();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.currentScreen != null) {
            addon.getSettings().consumeOpenPress();
            return;
        }

        if (addon.getSettings().consumeOpenPress()) {
            addon.openAllBotsGui();
        }
    }
}
